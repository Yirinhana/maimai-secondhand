"""Audited, idempotent import for the user's approved experience accounts and 65 catalog items."""
import argparse, json, os, subprocess
from datetime import datetime, timezone, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BATCH = 'maimai-experience-045'
def literal(value):
    return 'CONVERT(0x'+str(value).encode().hex()+' USING utf8mb4)' if str(value) else "''"

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=['local','tencent'],required=True)
    parser.add_argument('--accounts',required=True,help='Private SHA256 account manifest, no passwords')
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args(); local=args.target=='local'
    if local:
        binary='C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
        flags=['--defaults-file=.local/private/mysql-client.ini','--host=127.0.0.1','--port=3307','--protocol=TCP']
    else:
        assert os.geteuid()==0 and Path('/opt/maimai/current').resolve()==ROOT
        assert (ROOT/'VERSION').read_text().strip()=='0.7.0'
        assert Path(args.accounts).stat().st_mode & 0o077 == 0
        binary='/opt/maimai/runtime/mysql/usr/bin/mysql';flags=['--defaults-file=/etc/maimai/mysql-root.cnf']
    def sql(query):
        result=subprocess.run([binary,*flags,'--skip-reconnect','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','maimai'],input=query.encode(),capture_output=True,cwd=ROOT)
        if result.returncode:
            # SQL may contain account and order data; never print raw failed commands.
            raise RuntimeError('Experience transaction failed; details withheld')
        return result.stdout.decode().strip()
    marker=f"action='EXPERIENCE_CATALOG_BATCH' AND reason={literal(BATCH)}"
    if int(sql(f'SELECT COUNT(*) FROM admin_audit_logs WHERE {marker}')):
        print('ALREADY_APPLIED');return
    assert int(sql("SELECT COUNT(*) FROM admin_audit_logs WHERE action='DEMO_MEDIA_BATCH' AND reason='maimai-generated-media-043'"))==1
    admin=int(sql("SELECT MIN(u.id) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='SUPER_ADMIN' AND u.status='ACTIVE'"))
    data=json.loads((ROOT/'deploy/catalog-experience-v070.json').read_text('utf-8'))
    hashes=json.loads(Path(args.accounts).read_text('utf-8'))
    assert data['source']==BATCH and len(data['items'])==65 and len(hashes['buyers'])==30 and len(hashes['sellers'])==12
    buyers=[]
    for hashed in hashes['buyers']:
        assert len(hashed)==64 and all(c in '0123456789abcdef' for c in hashed)
        uid=sql(f"SELECT id FROM users WHERE SHA2(email,256)='{hashed}' AND status='ACTIVE'").splitlines()
        assert len(uid)==1,'Expected active experience buyer'
        buyers.append(int(uid[0]))
    sellers=[]
    for hashed in hashes['sellers']:
        assert len(hashed)==64 and all(c in '0123456789abcdef' for c in hashed)
        uid=sql(f"SELECT id FROM users WHERE SHA2(email,256)='{hashed}' AND status='ACTIVE'").splitlines()
        assert len(uid)==1,'Expected active experience seller'
        seller_id=int(uid[0])
        assert sql(f"SELECT status FROM seller_applications WHERE user_id={seller_id} ORDER BY created_at DESC,id DESC LIMIT 1")=='APPROVED'
        sellers.append(seller_id)
    statements=["CREATE TEMPORARY TABLE experience_guard(ok TINYINT NOT NULL CHECK(ok=1))",f"INSERT INTO experience_guard VALUES(GET_LOCK('{BATCH}',0))",'START TRANSACTION',
        f'INSERT INTO experience_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE {marker}',
        "SELECT lock_name FROM governance_locks WHERE lock_name IN ('CATEGORIES','ACCOUNT_ROLES') ORDER BY lock_name FOR UPDATE"]
    for n,(parent,children) in enumerate(data['categories'].items()):
        for name,par,sort in [(parent,None,n+1)]+[(c,parent,i+1) for i,c in enumerate(children)]:
            condition=f'name={literal(name)} AND '+('parent_id IS NULL' if par is None else f'parent_id=(SELECT id FROM categories WHERE name={literal(par)} AND parent_id IS NULL)')
            existing=sql(f'SELECT id,status FROM categories WHERE {condition}').splitlines()
            assert len(existing)<=1 and (not existing or existing[0].endswith('\tACTIVE')),'Ambiguous or inactive category'
            parent_expr='NULL' if par is None else f'(SELECT id FROM (SELECT id FROM categories WHERE name={literal(par)} AND parent_id IS NULL) cat_parent)'
            statements.append(f"INSERT INTO categories(name,parent_id,sort,status) SELECT {literal(name)},{parent_expr},{sort},'ACTIVE' WHERE NOT EXISTS(SELECT 1 FROM categories WHERE {condition})")
    for index,item in enumerate(data['items']):
        matched=sql(f"SELECT JSON_OBJECT('id',p.id,'seller',seller_id,'version',version,'available',stock_available,'reserved',stock_reserved,'sold',stock_sold) FROM products p WHERE title={literal(item['title'])} AND EXISTS(SELECT 1 FROM product_images i WHERE i.product_id=p.id AND i.path LIKE {literal('/uploads/products/demo043-'+item['key']+'-%')})").splitlines()
        assert len(matched)==1,'Expected known catalog item'
        old=json.loads(matched[0]);pid=old['id'];seller=old['seller'];buyer=buyers[index%len(buyers)]
        if sql(f'SELECT status FROM users WHERE id={seller}')=='DISABLED':
            # Only the original, generated, never-traded starter set can leave a retired seed account.
            assert index<5 and int(sql(f'SELECT COUNT(*) FROM order_items WHERE product_id={pid}'))==0
            assert int(sql(f"SELECT COUNT(*) FROM product_images WHERE product_id={pid} AND path LIKE {literal('/uploads/products/demo043-'+item['key']+'-%')}"))==3
            seller=sellers[index%len(sellers)]
        assert buyer!=seller and int(sql(f"SELECT COUNT(*) FROM users WHERE id={seller} AND status='ACTIVE'"))==1
        revision=json.loads(sql(f'SELECT content FROM product_revisions WHERE product_id={pid} ORDER BY version DESC LIMIT 1'))
        available=max(old['available'],item['available'])
        # Supply is added first, then exactly one unit is consumed by the imported completed transaction.
        amount=int(sql(f'SELECT price_cents FROM products WHERE id={pid}')); fee=(amount*3+5000)//10000
        created=datetime.now(timezone.utc)-timedelta(days=3+index%10,hours=index%7)
        at=lambda hours:literal((created+timedelta(hours=hours)).strftime('%Y-%m-%d %H:%M:%S'))
        code=f'MX045{index+1:03d}'
        cat=f'(SELECT c.id FROM categories c JOIN categories parent ON c.parent_id=parent.id WHERE c.name={literal(item["category"])} AND parent.name={literal(item["parent"])} AND parent.parent_id IS NULL)'
        revision.update({'experienceSource':BATCH,'supplyNote':item['supplyNote']})
        statements += [f'SELECT id FROM products WHERE id={pid} FOR UPDATE',
            f"INSERT INTO experience_guard SELECT IF(COUNT(*)=1,1,0) FROM products WHERE id={pid} AND seller_id={old['seller']} AND version={old['version']} AND stock_available={old['available']} AND stock_reserved={old['reserved']} AND stock_sold={old['sold']} AND experience_source IS NULL",
            f"INSERT INTO experience_guard SELECT IF(COUNT(*)=2,1,0) FROM users WHERE id IN ({buyer},{seller}) AND status='ACTIVE'",
            f"INSERT INTO experience_guard SELECT IF(COUNT(*)=1,1,0) FROM categories c JOIN categories parent ON c.parent_id=parent.id WHERE c.id={cat} AND c.status='ACTIVE' AND parent.status='ACTIVE'",
            f"INSERT INTO experience_guard SELECT IF({seller}={old['seller']} OR COUNT(*)=0,1,0) FROM order_items WHERE product_id={pid}",
            f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,before_state,after_state) SELECT {admin},'EXPERIENCE_SELLER','PRODUCT',{pid},{literal(BATCH)},'{old['seller']}','{seller}' WHERE {old['seller']}<>{seller}",
            f"UPDATE products SET seller_id={seller},category_id={cat},stock_available={available},stock_sold=stock_sold+1,experience_source={literal(BATCH)},supply_note={literal(item['supplyNote'])},version=version+1 WHERE id={pid}",
            f"INSERT INTO stock_logs(product_id,delta_available,reason,ref_type,ref_id) VALUES({pid},{available-old['available']+1},'EXPERIENCE_SUPPLY','ADMIN',{admin})",
            f"INSERT INTO checkout_batches(batch_no,user_id,idempotency_key,created_at) VALUES('{code}',{buyer},'{BATCH}-{index+1}',{at(0)})",'SET @batch=LAST_INSERT_ID()',
            f"INSERT INTO orders(order_no,batch_id,buyer_id,seller_id,delivery_method,region,goods_amount_cents,freight_cents,platform_fee_cents,total_cents,fulfillment_status,pay_status,settle_status,expires_at,paid_at,completed_at,created_at,experience_source) SELECT '{code}',@batch,{buyer},{seller},'MEETUP',region,price_cents,0,{fee},price_cents,'COMPLETED','PAID','NONE',{at(.5)},{at(.1)},{at(24)},{at(0)},{literal(BATCH)} FROM products WHERE id={pid}",'SET @order=LAST_INSERT_ID()',
            f"INSERT INTO order_items(order_id,product_id,title,item_condition,defects,price_cents,quantity,return_promise,image_path) SELECT @order,id,title,item_condition,defects,price_cents,1,return_promise,(SELECT path FROM product_images WHERE product_id={pid} ORDER BY sort LIMIT 1) FROM products WHERE id={pid}",
            f"INSERT INTO payment_requests(pay_no,order_id,amount_cents,channel,status,simulated,created_at,paid_at) VALUES('PX045{index+1:03d}',@order,{amount},'MOCK_LOCAL','PAID',1,{at(.1)},{at(.1)})",
            f"INSERT INTO stock_logs(product_id,delta_available,delta_sold,reason,ref_type,ref_id) VALUES({pid},-1,1,'EXPERIENCE_TRADE','ORDER',@order)",
            f"INSERT INTO community_order_ratings(order_id,rater_id,ratee_id,rating,comment,created_at) VALUES(@order,{buyer},{seller},{item['rating']},{literal(item['review'])},{at(26)})",
            f"INSERT INTO product_comments(product_id,author_id,content,created_at) VALUES({pid},{buyer},{literal(item['question'])},{at(1)})",'SET @comment=LAST_INSERT_ID()',
            f"INSERT INTO product_comments(product_id,author_id,reply_to_id,content,created_at) VALUES({pid},{seller},@comment,{literal(item['answer'])},{at(2)})",
            f"SET @revision={literal(json.dumps(revision,ensure_ascii=False))}",
            f"SET @revision=JSON_SET(@revision,'$.categoryId',{cat})",
            f'SET @next_revision=(SELECT MAX(version)+1 FROM product_revisions WHERE product_id={pid})',
            f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES({pid},@next_revision,'EXPERIENCE_SUPPLY',{admin},@revision)"]
    print(f'PREVIEW_READY target={args.target} products=65 buyers=30 reviews=65 discussions=130')
    if not args.apply:return
    if local:
        dest=ROOT/'.local/private'/('045-before-experience-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.sql')
        with dest.open('xb') as out:
            identity=subprocess.check_output(['whoami'],text=True).strip()
            subprocess.run(['icacls.exe',str(dest),'/inheritance:r','/grant:r',identity+':(F)','SYSTEM:(F)'],check=True,stdout=subprocess.DEVNULL)
            backup=subprocess.run([str(Path(binary).with_name('mysqldump.exe')),*flags,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','maimai'],stdout=out,stderr=subprocess.PIPE,cwd=ROOT)
        assert backup.returncode==0,'Backup failed'
    else:subprocess.run(['bash',str(ROOT/'deploy/backup.sh')],check=True)
    statements += [f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES({admin},'EXPERIENCE_CATALOG_BATCH','CATALOG',0,{literal(BATCH)},'65 marked supply records and completed simulated orders; 65 ratings; 130 linked comments; no live payments or channel qualification changes')",'COMMIT',f"SELECT RELEASE_LOCK('{BATCH}')"]
    sql(';\n'.join(statements)+';')
    print('APPLIED products=65 orders=65 reviews=65 discussions=130')
if __name__=='__main__':main()
