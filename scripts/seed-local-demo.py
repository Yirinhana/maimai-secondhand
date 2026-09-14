"""Seed one explicit private demo manifest into the existing project-only MySQL.

Defaults to read-only preview. --apply backs up first and commits one atomic batch.
This script is intentionally local-only: no server, production profile or global DB changes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
PRIVATE = ROOT / '.local/private'
MYSQL = Path('C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe')
BATCH = 'maimai-demo-040'

def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def literal(value):
    return 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4)' if str(value) else "''"

def sql(query):
    result = subprocess.run([str(MYSQL), '--defaults-file=.local/private/mysql-client.ini', '--host=127.0.0.1', '--port=3307', '--protocol=TCP', '--skip-reconnect', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names', 'maimai'], input=query.encode('utf-8'), capture_output=True, cwd=ROOT)
    if result.returncode:
        raise RuntimeError('Local database batch failed; raw SQL and credential-bearing errors are suppressed')
    return result.stdout.decode('utf-8').strip()

def protect(path):
    if os.name == 'nt':
        identity = subprocess.check_output(['whoami'], text=True).strip()
        subprocess.run(['icacls.exe', str(path), '/inheritance:r', '/grant:r', identity + ':(F)', 'SYSTEM:(F)'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    else:
        path.chmod(0o600)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    manifest = Path(args.manifest).resolve()
    if manifest.parent != PRIVATE.resolve():
        raise RuntimeError('Manifest must be inside the ignored project private directory')
    cfg = read_json(ROOT / '.local/environment.json')
    db = cfg['database']
    if db['host'] != '127.0.0.1' or int(db['port']) != 3307 or db['name'] != 'maimai':
        raise RuntimeError('Only the existing local 127.0.0.1:3307 maimai database is supported')
    data = read_json(manifest)
    if data['batch'] != BATCH or len(data['sellers']) != 12 or len(data['buyers']) != 30 or len(data['products']) != 60:
        raise RuntimeError('Unexpected batch shape')
    users = [dict(email=data['adminEmail'], nickname='麦麦管理员', roles=['USER', 'SUPER_ADMIN'])]
    users += [dict(**user, roles=['USER', 'SELLER']) for user in data['sellers']]
    users += [dict(**user, roles=['USER']) for user in data['buyers']]
    emails = [u['email'] for u in users]
    if len(set(emails)) != 43 or any(not re.fullmatch(r'[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}', email) for email in emails):
        raise RuntimeError('Duplicate or invalid account identifiers')
    marker = f"action='DEMO_BATCH_SEED' AND reason={literal(BATCH)}"
    if int(sql(f'SELECT COUNT(*) FROM admin_audit_logs WHERE {marker};')):
        print(json.dumps(dict(batch=BATCH, status='ALREADY_APPLIED', changed=False)))
        return
    # Registering an existing identity must be an explicit separate action, never an accidental overwrite.
    if int(sql('SELECT COUNT(*) FROM users WHERE email IN (' + ','.join(map(literal, emails)) + ');')):
        raise RuntimeError('A requested email already exists; inspect that account before modifying it')
    categories = {row.split('\t')[1]: int(row.split('\t')[0]) for row in sql("SELECT id,name FROM categories WHERE status='ACTIVE';").splitlines()}
    seller_ids = {u['email']: i+1 for i,u in enumerate(data['sellers'])}
    for p in data['products']:
        if p['sellerEmail'] not in seller_ids or p['category'] not in categories or not re.fullmatch(r'/uploads/products/seed-[1-9][0-9]*\.jpg', p['imagePath']):
            raise RuntimeError('Unexpected product owner, category or image path')
        if not 0 < p['priceCents'] < 10000000 or p['condition'] not in ['NEW','LIKE_NEW','GOOD','FAIR','POOR']:
            raise RuntimeError('Unexpected product fields')
    if not args.apply:
        print(json.dumps(dict(batch=BATCH, status='PREVIEW_READY', administrators=1, sellers=12, buyers=30, products=60, changed=False)))
        return

    backup = PRIVATE / '040-before-demo-seed.sql'
    with backup.open('xb') as out:
        protect(backup)
        result = subprocess.run([str(MYSQL.with_name('mysqldump.exe')), '--defaults-file=.local/private/mysql-client.ini', '--host=127.0.0.1', '--port=3307', '--protocol=TCP', '--single-transaction', '--no-tablespaces', '--set-gtid-purged=OFF', 'maimai'], stdout=out, stderr=subprocess.PIPE, cwd=ROOT)
    if result.returncode:
        raise RuntimeError('Local backup failed; nothing imported')
    tool_jar = ROOT / '.local/tools/demo-seed-crypto.jar'
    with zipfile.ZipFile(ROOT / 'backend/target/maimai-backend.jar') as jar:
        names = [n for n in jar.namelist() if n.startswith('BOOT-INF/lib/spring-security-crypto-') and n.endswith('.jar')]
        if len(names) != 1: raise RuntimeError('Cannot identify BCrypt runtime')
        payload = jar.read(names[0])
    if tool_jar.exists() and tool_jar.read_bytes() != payload: raise RuntimeError('Conflicting helper runtime')
    if not tool_jar.exists(): tool_jar.write_bytes(payload)
    encoded = subprocess.run([str(Path(cfg['javaHome']) / 'bin/java.exe'), '-cp', str(tool_jar), str(ROOT / 'scripts/java/DemoPasswordHasher.java')], input=(data['password']+'\n43\n').encode(), capture_output=True)
    hashes = encoded.stdout.decode().splitlines()
    if encoded.returncode or len(hashes) != 43 or any(not re.fullmatch(r'\$2a\$10\$[./A-Za-z0-9]{53}', h) for h in hashes):
        raise RuntimeError('Private password hashing failed')
    upload_dir = ROOT / '.local/uploads/products'
    upload_dir.mkdir(exist_ok=True, parents=True)
    assets = {}
    for p in data['products']:
        if p['asset'] not in assets:
            name = p['asset']
            if name not in ['iphone-blue.jpg','headphones-charcoal.jpg','java-textbook.jpg','wool-coat.jpg','badminton-racket.jpg']:
                raise RuntimeError('Unrecognized demo artwork')
            raw = (ROOT / 'backend/src/main/resources/demo/products' / name).read_bytes()
            target = upload_dir / Path(p['imagePath']).name
            if target.exists() and target.read_bytes() != raw: raise RuntimeError('Conflicting image; refusing overwrite')
            if not target.exists(): target.write_bytes(raw)
            assets[name] = hashlib.sha256(raw).hexdigest()

    # CHECK guards fail the batch atomically; CLI reconnect and continue-on-error are disabled.
    statements = ["CREATE TEMPORARY TABLE seed_guard(ok TINYINT NOT NULL CHECK(ok=1));", f"INSERT INTO seed_guard VALUES(GET_LOCK('{BATCH}',0));", 'START TRANSACTION;', f'INSERT INTO seed_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE {marker};']
    for i,(user,password_hash) in enumerate(zip(users, hashes)):
        statements += [f"INSERT INTO users(email,password_hash,nickname,status) VALUES({literal(user['email'])},{literal(password_hash)},{literal(user['nickname'])},'ACTIVE');", f'SET @u{i}=LAST_INSERT_ID();']
        statements += [f"INSERT INTO user_roles(user_id,role) VALUES(@u{i},'{role}');" for role in user['roles']]
        if 'SELLER' in user['roles']:
            statements.append(f"INSERT INTO seller_applications(user_id,status,channel_status,intro,reason,reviewed_by,reviewed_at) VALUES(@u{i},'APPROVED','QUALIFIED',{literal('本地演示卖家，不代表真实商户')},{literal('仅本地模拟交易使用，禁止将此渠道状态复制到公网')},@u0,NOW(6));")
    # Interleave each seller's different categories so the latest feed is not twelve identical covers.
    for i,p in enumerate(sorted(data['products'], key=lambda item: seller_ids[item['sellerEmail']])):
        seller = seller_ids[p['sellerEmail']]
        category = categories[p['category']]
        stock = i % 5 + 1
        description = p['description'].replace('当前仅供浏览，不接受真实购买。', '本地可使用模拟订单体验，不接受真实购买。')
        marker_text = BATCH + ':' + p['key']
        review_reason = '本地演示商品，仅用于体验模拟交易'
        values = [f'@u{seller}', str(category), literal(p['title']), literal(description), literal(p['condition']), literal(p['defects']), str(p['priceCents']), str(stock), literal(p['region']), literal(','.join(p['deliveryMethods'])), str(p['freightCents']), literal(p['returnPromise']), "'ON_SALE'", literal(review_reason)]
        statements += ['INSERT INTO products(seller_id,category_id,title,description,item_condition,defects,price_cents,stock_available,region,delivery_methods,freight_cents,return_promise,status,review_reason) VALUES('+','.join(values)+');', 'SET @p=LAST_INSERT_ID();', f"INSERT INTO product_images(product_id,path,sort) VALUES(@p,{literal(p['imagePath'])},0);", 'SET @image=LAST_INSERT_ID();', f"INSERT INTO product_review_logs(product_id,reviewer_id,action,reason,to_status) VALUES(@p,@u0,'DEMO_IMPORT',{literal(marker_text)},'ON_SALE');", f"INSERT INTO stock_logs(product_id,delta_available,reason,ref_type,ref_id) VALUES(@p,{stock},'DEMO_IMPORT','PRODUCT',@p);"]
        content = dict(title=p['title'],categoryId=category,description=description,condition=p['condition'],defects=p['defects'],priceCents=p['priceCents'],region=p['region'],deliveryMethods=p['deliveryMethods'],freightCents=p['freightCents'],returnPromise=p['returnPromise'],shippingProvinces=[],latitude=None,longitude=None,status='ON_SALE',reviewReason=review_reason)
        statements.append(f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES(@p,1,'DEMO_IMPORT',@u0,JSON_SET({literal(json.dumps(content,ensure_ascii=False))},'$.images',JSON_ARRAY(JSON_OBJECT('id',@image,'path',{literal(p['imagePath'])},'sort',0))));")
    statements += [f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES(@u0,'DEMO_BATCH_SEED','LOCAL_DEMO',@u0,{literal(BATCH)},{literal('1 administrator, 12 sellers, 30 buyers, 60 products; local mock only')});", 'COMMIT;', f"SELECT RELEASE_LOCK('{BATCH}');"]
    sql('\n'.join(statements))
    print(json.dumps(dict(batch=BATCH,status='APPLIED_LOCAL',administrators=1,sellers=12,buyers=30,products=60,backup=str(backup.relative_to(ROOT)),imageFiles=len(assets))))

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
