"""Install the explicitly approved 65-item diversified demonstration catalog.

Preview by default. Apply backs up and locks every selected product in one transaction.
Only known demo records can be replaced. Old files, stock and sellers are retained.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

BATCH = 'maimai-generated-media-043'
PREFIX = 'BOOT-INF/classes/demo/catalog-v2/'
ROOT = Path(__file__).resolve().parent.parent
ALIASES = {
    'item-001': 'iPhone 12 128GB 蓝色 自用一手',
    'item-003': 'Java 核心技术 卷I（第11版）',
    'item-004': '优衣库羊毛混纺大衣 M 码',
    'item-005': '尤尼克斯羽毛球拍 天斧77',
}


def literal(value):
    return 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4)' if str(value) else "''"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=['local', 'tencent'], required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--metadata-only', action='store_true', help='Local read-only identity/content preview while images are being generated')
    args = parser.parse_args()
    local = args.target == 'local'
    if args.metadata_only and (args.apply or not local):
        parser.error('--metadata-only is local and read-only')
    archive = None
    if local:
        cfg = json.loads((ROOT / '.local/environment.json').read_text(encoding='utf-8-sig'))['database']
        if (cfg['host'], int(cfg['port']), cfg['name']) != ('127.0.0.1', 3307, 'maimai'):
            raise RuntimeError('Expected the project-only local database')
        binary = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
        flags = ['--defaults-file=.local/private/mysql-client.ini', '--host=127.0.0.1', '--port=3307', '--protocol=TCP']
        source = ROOT / 'backend/src/main/resources/demo/catalog-v2'
        read = lambda name: (source / name).read_bytes()
        uploads = ROOT / '.local/uploads/products'
    else:
        if os.name == 'nt' or os.geteuid() != 0:
            raise RuntimeError('Tencent apply requires the authorized server root context')
        current = Path('/opt/maimai/current')
        if current.resolve().parent != Path('/opt/maimai/releases'):
            raise RuntimeError('Unexpected release directory')
        binary = '/opt/maimai/runtime/mysql/usr/bin/mysql'
        flags = ['--defaults-file=/etc/maimai/mysql-root.cnf']
        archive = zipfile.ZipFile(current / 'backend.jar')
        read = lambda name: archive.read(PREFIX + name)
        uploads = Path('/var/lib/maimai/uploads/products')

    def sql(query):
        result = subprocess.run([binary, *flags, '--skip-reconnect', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', 'maimai'],
                                input=query.encode('utf-8'), capture_output=True, cwd=ROOT if local else None)
        if result.returncode:
            raise RuntimeError('Database operation failed; statement and private values suppressed')
        return result.stdout.decode('utf-8').strip()

    manifest = {'batch': BATCH, 'items': json.loads((ROOT / 'scripts/data/demo-catalog-plan.json').read_text(encoding='utf-8'))} if args.metadata_only else json.loads(read('manifest.json'))
    items = manifest['items']
    if manifest.get('batch') != BATCH or len(items) != 65 or len({p['key'] for p in items}) != 65:
        raise RuntimeError('Expected the approved 65-item image manifest')
    fingerprints = set()
    for p in items:
        if args.metadata_only:
            continue
        if not re.fullmatch(r'item-\d{3}', p['key']) or [v['view'] for v in p['images']] != ['front', 'back', 'side']:
            raise RuntimeError('Invalid image set')
        for image in p['images']:
            name = f"demo043-{p['key']}-{image['view']}.jpg"
            if image['file'] != name:
                raise RuntimeError('Unexpected filename')
            raw = read(name)
            digest = hashlib.sha256(raw).hexdigest()
            if digest != image['sha256'] or digest in fingerprints or not raw.startswith(b'\xff\xd8') or len(raw) > 1048576:
                raise RuntimeError('Image validation or uniqueness check failed')
            fingerprints.add(digest)
            destination = uploads / name
            if destination.exists() and (destination.is_symlink() or hashlib.sha256(destination.read_bytes()).hexdigest() != digest):
                raise RuntimeError('Conflicting destination; old files will not be overwritten')

    marker = f"action='DEMO_MEDIA_BATCH' AND reason={literal(BATCH)}"
    if int(sql(f'SELECT COUNT(*) FROM admin_audit_logs WHERE {marker};')):
        print('ALREADY_APPLIED; changed=false')
        return
    admin = int(sql("SELECT MIN(user_id) FROM user_roles WHERE role='SUPER_ADMIN';"))
    rows = []
    for p in items:
        title = ALIASES.get(p['key'], p['originalTitle']) if local else p['originalTitle']
        query = """SELECT JSON_OBJECT('id',p.id,'sellerId',p.seller_id,'stockAvailable',p.stock_available,'stockReserved',p.stock_reserved,'lockVersion',p.version,'title',p.title,'categoryId',p.category_id,
          'description',p.description,'condition',p.item_condition,'defects',p.defects,'priceCents',p.price_cents,
          'region',p.region,'deliveryMethods',p.delivery_methods,'freightCents',p.freight_cents,
          'returnPromise',p.return_promise,'shippingProvinces',p.shipping_provinces,'latitude',p.latitude,'longitude',p.longitude,
          'status',p.status,'reviewReason',p.review_reason,'images',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',i.id,'path',i.path,'sort',i.sort)) FROM product_images i WHERE i.product_id=p.id))
          FROM products p WHERE p.title=""" + literal(title) + ';'
        records = sql(query).splitlines()
        if len(records) != 1:
            raise RuntimeError('Demo title did not resolve to exactly one product: ' + p['key'])
        row = json.loads(records[0])
        if not local and (row['id'] != p['originalPublicId'] or row['sellerId'] != p['originalSellerId'] or row['stockAvailable'] != 0 or row['stockReserved'] != 0):
            raise RuntimeError('Public demonstration identity or zero-stock guard failed: ' + p['key'])
        if p['key'] >= 'item-006':
            seed_prefix = 'maimai-demo-040:' if local else 'maimai-public-demo-042:'
            if not int(sql(f"SELECT COUNT(*) FROM product_review_logs WHERE product_id={row['id']} AND action='DEMO_IMPORT' AND reason LIKE {literal(seed_prefix + '%')};")):
                raise RuntimeError('Missing original demo import provenance: ' + p['key'])
        elif local and row['id'] != {'item-001': 1, 'item-002': 2, 'item-003': 3, 'item-004': 5, 'item-005': 6}[p['key']]:
            raise RuntimeError('Initial local demo identity mismatch')
        if not local and int(sql(f"SELECT COUNT(*) FROM order_items WHERE product_id={row['id']};")):
            raise RuntimeError('Public product has orders; preserve its original identity')
        if not row['images'] or any(not re.fullmatch(r'(?:/uploads/)?products/seed-\d+\.jpg', im['path']) for im in row['images']):
            raise RuntimeError('Refusing to replace user-uploaded or already changed images: ' + p['key'])
        for field in ['deliveryMethods', 'shippingProvinces']:
            row[field] = [x.strip() for x in (row[field] or '').split(',') if x.strip()]
        category = sql(f"SELECT id FROM categories WHERE name={literal(p['category'])} AND status='ACTIVE';").splitlines()
        if len(category) != 1:
            raise RuntimeError('Category must resolve uniquely: ' + p['category'])
        p['categoryId'] = int(category[0])
        if not (0 < p['priceCents'] < 100000000 and 0 < len(p['title']) <= 120 and len(p['defects']) <= 500):
            raise RuntimeError('Invalid demo content')
        rows.append((p, row))
    print(json.dumps({'status': 'METADATA_PREVIEW_READY' if args.metadata_only else 'PREVIEW_READY', 'products': len(rows), 'verifiedImages': len(fingerprints), 'target': args.target}))
    if not args.apply:
        return

    if local:
        backup = ROOT / '.local/private' / ('043-before-media-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.sql')
        with backup.open('xb') as output:
            identity = subprocess.check_output(['whoami'], text=True).strip()
            subprocess.run(['icacls.exe', str(backup), '/inheritance:r', '/grant:r', identity + ':(F)', 'SYSTEM:(F)'], check=True, stdout=subprocess.DEVNULL)
            result = subprocess.run([str(Path(binary).with_name('mysqldump.exe')), *flags, '--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF', 'maimai'], stdout=output, stderr=subprocess.PIPE, cwd=ROOT)
        if result.returncode:
            raise RuntimeError('Backup failed; images not installed')
    else:
        subprocess.run(['bash', '/opt/maimai/current/deploy/backup.sh'], check=True)
        import pwd
        owner = pwd.getpwnam('maimai')
    uploads.mkdir(parents=True, exist_ok=True)
    if uploads.is_symlink():
        raise RuntimeError('Upload directory must not be a symlink')
    for p, _ in rows:
        for image in p['images']:
            file = uploads / image['file']
            if not file.exists():
                with file.open('xb') as out:
                    out.write(read(image['file']))
                if not local:
                    os.chown(file, owner.pw_uid, owner.pw_gid)
                    file.chmod(0o600)
    statements = ["CREATE TEMPORARY TABLE media_guard(ok TINYINT NOT NULL CHECK(ok=1));",
                  f"INSERT INTO media_guard VALUES(GET_LOCK('{BATCH}',0));", 'START TRANSACTION;',
                  f'INSERT INTO media_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE {marker};']
    for p, row in rows:
        pid, version = row['id'], row['lockVersion']
        before = {k: v for k, v in row.items() if k not in ['id', 'sellerId', 'stockAvailable', 'stockReserved', 'lockVersion']}
        after = dict(before, title=p['title'], categoryId=p['categoryId'], description=p['description'], condition=p['condition'], defects=p['defects'], priceCents=p['priceCents'])
        if p['key'] < 'item-006':
            after['priceCents'] = before['priceCents']
        old_ids = ','.join(str(im['id']) for im in row['images'])
        statements += [f'SELECT id FROM products WHERE id={pid} FOR UPDATE;',
                       f"INSERT INTO media_guard SELECT IF(COUNT(*)=1,1,0) FROM products WHERE id={pid} AND version={version} AND seller_id={row['sellerId']} AND stock_available={row['stockAvailable']} AND stock_reserved={row['stockReserved']} AND title={literal(row['title'])};",
                       f'INSERT INTO media_guard SELECT IF(COUNT(*)={len(row["images"])} AND SUM(id IN ({old_ids}))={len(row["images"])},1,0) FROM product_images WHERE product_id={pid};',
                       f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) SELECT {pid},1,'BASELINE',{admin},{literal(json.dumps(before, ensure_ascii=False))} WHERE NOT EXISTS(SELECT 1 FROM product_revisions WHERE product_id={pid});",
                       f'DELETE FROM product_images WHERE product_id={pid};']
        for index, image in enumerate(p['images']):
            statements.append(f"INSERT INTO product_images(product_id,path,sort) VALUES({pid},{literal('/uploads/products/' + image['file'])},{index});")
        if not local:
            statements.append(f'INSERT INTO media_guard SELECT IF(COUNT(*)=0,1,0) FROM order_items WHERE product_id={pid};')
        assignments = ','.join(f'{column}={literal(after[field])}' for column, field in [('title','title'),('description','description'),('item_condition','condition'),('defects','defects')])
        statements += [f"UPDATE products SET {assignments},category_id={after['categoryId']},price_cents={after['priceCents']},version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id={pid};",
                       f'SET @rv=(SELECT MAX(version)+1 FROM product_revisions WHERE product_id={pid});',
                       f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES({pid},@rv,'DEMO_CATALOG_UPDATED',{admin},JSON_SET({literal(json.dumps(after, ensure_ascii=False))},'$.images',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',id,'path',path,'sort',sort)) FROM product_images WHERE product_id={pid})));",
                       f"INSERT INTO product_review_logs(product_id,reviewer_id,action,reason,to_status) VALUES({pid},{admin},'DEMO_CATALOG_UPDATED',{literal(BATCH)},{literal(row['status'])});"]
    statements += [f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES({admin},'DEMO_MEDIA_BATCH','DEMO_CATALOG',0,{literal(BATCH)},{literal('65 diversified demo products, 195 unique generated images; stock, sellers and payment qualification preserved')});", 'COMMIT;', f"SELECT RELEASE_LOCK('{BATCH}');"]
    sql('\n'.join(statements))
    print('APPLIED: 65 products, 195 images; old files retained')


if __name__ == '__main__':
    main()
