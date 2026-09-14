#!/usr/bin/env python3
"""Provision an explicit private preview batch without importing a development DB.

Default is read-only preview. --apply backs up before one atomic database batch.
Input contains independently salted BCrypt hashes, never a plaintext password.
All demonstration products have zero stock; sellers receive no payment qualification.
"""
import argparse
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import sys
import zipfile

MYSQL = '/opt/maimai/runtime/mysql/usr/bin/mysql'
CLIENT = '/etc/maimai/mysql-root.cnf'
BATCH = 'maimai-public-demo-042'
ASSETS = {'iphone-blue.jpg', 'headphones-charcoal.jpg', 'java-textbook.jpg',
          'wool-coat.jpg', 'badminton-racket.jpg'}


def literal(value):
    return 'CONVERT(0x' + str(value).encode('utf-8').hex() + ' USING utf8mb4)' if str(value) else "''"


def sql(query):
    result = subprocess.run([MYSQL, '--defaults-file=' + CLIENT, '--skip-reconnect',
                             '--default-character-set=utf8mb4', '--batch', '--raw',
                             '--skip-column-names', 'maimai'],
                            input=query, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('Preview database operation failed; SQL and private values suppressed')
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise RuntimeError('Run as root on the explicitly authorized preview server')
    current = Path('/opt/maimai/current')
    if current.resolve().parent != Path('/opt/maimai/releases') or (current / 'VERSION').read_text().strip() != '0.4.0':
        raise RuntimeError('Expected the validated 0.4.0 preview deployment')
    manifest = Path(args.manifest).resolve()
    if manifest.parent != Path('/home/ubuntu/maimai-deploy-042') or manifest.stat().st_mode & 0o077:
        raise RuntimeError('Manifest must be the restricted file in the approved staging directory')
    data = json.loads(manifest.read_text(encoding='utf-8-sig'))
    if 'password' in data or data.get('batch') != BATCH or len(data['sellers']) != 12 or len(data['buyers']) != 30 or len(data['products']) != 60:
        raise RuntimeError('Unexpected private batch shape')
    users = [dict(email=data['adminEmail'], nickname='麦麦管理员', roles=['USER', 'SUPER_ADMIN'])]
    users += [dict(email=u['email'], nickname=u['nickname'], roles=['USER', 'SELLER']) for u in data['sellers']]
    users += [dict(email=u['email'], nickname=u['nickname'], roles=['USER']) for u in data['buyers']]
    emails = [u['email'] for u in users]
    hashes = data['passwordHashes']
    if len(set(emails)) != 43 or any(not re.fullmatch(r'[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}', e) for e in emails):
        raise RuntimeError('Invalid or duplicate account identifiers')
    if len(hashes) != 43 or len(set(hashes)) != 43 or any(not re.fullmatch(r'\$2a\$10\$[./A-Za-z0-9]{53}', h) for h in hashes):
        raise RuntimeError('Expected independent BCrypt hashes')
    marker = f"action='DEMO_BATCH_SEED' AND reason={literal(BATCH)}"
    if int(sql(f'SELECT COUNT(*) FROM admin_audit_logs WHERE {marker};')):
        print('ALREADY_APPLIED; changed=false')
        return
    if int(sql('SELECT COUNT(*) FROM users WHERE email IN (' + ','.join(map(literal, emails)) + ');')):
        raise RuntimeError('Requested identity already exists; no account will be overwritten')
    categories = {r.split('\t')[1]: int(r.split('\t')[0]) for r in sql("SELECT id,name FROM categories WHERE status='ACTIVE';").splitlines()}
    sellers = {u['email']: i + 1 for i, u in enumerate(data['sellers'])}
    images = {}
    for p in data['products']:
        if p['sellerEmail'] not in sellers or p['category'] not in categories or p['asset'] not in ASSETS:
            raise RuntimeError('Unexpected product owner, category or artwork')
        if not re.fullmatch(r'/uploads/products/seed-904000[1-5]\.jpg', p['imagePath']):
            raise RuntimeError('Unexpected demonstration image destination')
        if p['imagePath'] in images and images[p['imagePath']] != p['asset']:
            raise RuntimeError('Conflicting artwork destination')
        images[p['imagePath']] = p['asset']
        if not isinstance(p['priceCents'], int) or not 0 < p['priceCents'] < 10000000 or p['condition'] not in ['NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR']:
            raise RuntimeError('Unexpected product values')
    if len(images) != 5:
        raise RuntimeError('Expected five approved demonstration images')
    if not args.apply:
        print('PREVIEW_READY: 1 administrator, 12 sellers, 30 buyers, 60 products; stock=0; no payment qualification')
        return

    subprocess.run([str(current / 'deploy/backup.sh')], check=True)
    owner = pwd.getpwnam('maimai')
    with zipfile.ZipFile(current / 'backend.jar') as jar:
        for image, asset in images.items():
            raw = jar.read('BOOT-INF/classes/demo/products/' + asset)
            target = Path('/var/lib/maimai/uploads/products') / Path(image).name
            if target.exists():
                if target.read_bytes() != raw:
                    raise RuntimeError('Existing image differs; no overwrite performed')
            else:
                fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, 'wb') as out:
                    out.write(raw)
                os.chown(target, owner.pw_uid, owner.pw_gid)

    statements = ['CREATE TEMPORARY TABLE seed_guard(ok TINYINT NOT NULL CHECK(ok=1));',
                  f"INSERT INTO seed_guard VALUES(GET_LOCK('{BATCH}',0));", 'START TRANSACTION;',
                  f'INSERT INTO seed_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE {marker};']
    for i, (user, password_hash) in enumerate(zip(users, hashes)):
        statements += [f"INSERT INTO users(email,password_hash,nickname,status) VALUES({literal(user['email'])},{literal(password_hash)},{literal(user['nickname'])},'ACTIVE');", f'SET @u{i}=LAST_INSERT_ID();']
        statements += [f"INSERT INTO user_roles(user_id,role) VALUES(@u{i},'{role}');" for role in user['roles']]
        if 'SELLER' in user['roles']:
            statements.append(f"INSERT INTO seller_applications(user_id,status,channel_status,intro,reason,reviewed_by,reviewed_at) VALUES(@u{i},'APPROVED','PENDING',{literal('课程演示卖家，未开通真实支付资质')},{literal('展示与工作台体验；示范商品零库存，不能真实交易')},@u0,UTC_TIMESTAMP(6));")
    for p in sorted(data['products'], key=lambda item: sellers[item['sellerEmail']]):
        seller, category = sellers[p['sellerEmail']], categories[p['category']]
        description = p['description'] + '\n\n公开课程示范：库存为零，仅供浏览，不接受真实购买。'
        reason = '公开课程示范商品；库存为零，卖家未开通支付资质'
        values = [f'@u{seller}', str(category), literal(p['title']), literal(description), literal(p['condition']), literal(p['defects']), str(p['priceCents']), '0', literal(p['region']), literal(','.join(p['deliveryMethods'])), str(p['freightCents']), literal('仅供浏览，无真实交付及售后承诺。'), "'ON_SALE'", literal(reason)]
        statements += ['INSERT INTO products(seller_id,category_id,title,description,item_condition,defects,price_cents,stock_available,region,delivery_methods,freight_cents,return_promise,status,review_reason) VALUES(' + ','.join(values) + ');',
                       'SET @p=LAST_INSERT_ID();', f"INSERT INTO product_images(product_id,path,sort) VALUES(@p,{literal(p['imagePath'])},0);", 'SET @image=LAST_INSERT_ID();',
                       f"INSERT INTO product_review_logs(product_id,reviewer_id,action,reason,to_status) VALUES(@p,@u0,'DEMO_IMPORT',{literal(BATCH + ':' + p['key'])},'ON_SALE');"]
        content = dict(title=p['title'], categoryId=category, description=description, condition=p['condition'], defects=p['defects'], priceCents=p['priceCents'], region=p['region'], deliveryMethods=p['deliveryMethods'], freightCents=p['freightCents'], returnPromise='仅供浏览，无真实交付及售后承诺。', shippingProvinces=[], latitude=None, longitude=None, status='ON_SALE', reviewReason=reason)
        statements.append(f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES(@p,1,'DEMO_IMPORT',@u0,JSON_SET({literal(json.dumps(content, ensure_ascii=False))},'$.images',JSON_ARRAY(JSON_OBJECT('id',@image,'path',{literal(p['imagePath'])},'sort',0))));")
    statements += [f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES(@u0,'DEMO_BATCH_SEED','PUBLIC_PREVIEW',@u0,{literal(BATCH)},{literal('1 administrator, 12 sellers, 30 buyers, 60 zero-stock products; no payment qualification')});", 'COMMIT;', f"SELECT RELEASE_LOCK('{BATCH}');"]
    sql('\n'.join(statements))
    print('APPLIED_PREVIEW: 43 accounts, 60 zero-stock products; previous accounts retained; no orders or payment qualification created')


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
    except Exception:
        print('PREVIEW_SEED_FAILED; private details suppressed; inspect the retained backup and deployment stage', file=sys.stderr)
        raise SystemExit(1)
