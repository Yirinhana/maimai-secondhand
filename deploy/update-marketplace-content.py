"""Apply the reviewed 0.6 catalog copy to known seeded records; preview by default."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parent.parent
BATCH='maimai-marketplace-content-044-final'
def literal(value):
    return 'CONVERT(0x'+str(value).encode().hex()+' USING utf8mb4)' if str(value) else "''"
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=['local','tencent'],required=True)
    parser.add_argument('--accounts',required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args();local=args.target=='local'
    if local:
        binary='C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
        flags=['--defaults-file=.local/private/mysql-client.ini','--host=127.0.0.1','--port=3307','--protocol=TCP']
    else:
        assert os.geteuid()==0 and Path('/opt/maimai/current').resolve()==ROOT
        assert (ROOT/'VERSION').read_text().strip()=='0.6.0'
        binary='/opt/maimai/runtime/mysql/usr/bin/mysql';flags=['--defaults-file=/etc/maimai/mysql-root.cnf']
        assert Path(args.accounts).stat().st_mode&0o077==0
    def sql(query):
        result=subprocess.run([binary,*flags,'--skip-reconnect','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','maimai'],input=query.encode(),capture_output=True,cwd=ROOT)
        if result.returncode: raise RuntimeError('Content transaction failed; private values suppressed')
        return result.stdout.decode().strip()
    marker=f"action='CATALOG_COPY_BATCH' AND reason={literal(BATCH)}"
    if int(sql(f'SELECT COUNT(*) FROM admin_audit_logs WHERE {marker}')):print('ALREADY_APPLIED');return
    assert int(sql("SELECT COUNT(*) FROM admin_audit_logs WHERE action='DEMO_MEDIA_BATCH' AND reason='maimai-generated-media-043'"))==1
    admin=int(sql("SELECT MIN(user_id) FROM user_roles WHERE role='SUPER_ADMIN'"))
    rows=json.loads((ROOT/'deploy/catalog-copy-v060.json').read_text(encoding='utf-8'))
    accounts=json.loads(Path(args.accounts).read_text(encoding='utf-8'))
    assert len(rows)==65 and len(accounts)==42
    statements=["CREATE TEMPORARY TABLE copy_guard(ok TINYINT NOT NULL CHECK(ok=1))",f"INSERT INTO copy_guard VALUES(GET_LOCK('{BATCH}',0))",'START TRANSACTION',f'INSERT INTO copy_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE {marker}']
    for p in rows:
        records=sql(f"SELECT JSON_OBJECT('id',id,'seller',seller_id,'version',version,'description',description,'available',stock_available,'reserved',stock_reserved) FROM products WHERE title={literal(p['title'])}").splitlines()
        assert len(records)==1,p['key'];old=json.loads(records[0]);pid=old['id']
        if not local:assert pid==p['productId'] and old['seller']==p['sellerId'] and old['available']==old['reserved']==0
        assert int(sql(f"SELECT COUNT(*) FROM product_images WHERE product_id={pid} AND path LIKE {literal('/uploads/products/demo043-'+p['key']+'-%')}"))==3
        revision=sql(f'SELECT content FROM product_revisions WHERE product_id={pid} ORDER BY version DESC LIMIT 1');snap=json.loads(revision)
        assert snap['description']==old['description']
        snap['description']=p['description'];snap['returnPromise']=p['returnPromise'];updated=json.dumps(snap,ensure_ascii=False)
        statements += [f'SELECT id FROM products WHERE id={pid} FOR UPDATE',
            f"INSERT INTO copy_guard SELECT IF(COUNT(*)=1,1,0) FROM products WHERE id={pid} AND version={old['version']} AND description={literal(old['description'])}",
            f"UPDATE products SET description={literal(p['description'])},return_promise={literal(p['returnPromise'])},version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id={pid}",
            f'SET @rv=(SELECT MAX(version)+1 FROM product_revisions WHERE product_id={pid})',
            f"INSERT INTO product_revisions(product_id,version,action,actor_id,content) VALUES({pid},@rv,'CATALOG_COPY_UPDATED',{admin},{literal(updated)})",
            f"INSERT INTO product_review_logs(product_id,reviewer_id,action,reason,to_status) SELECT id,{admin},'CATALOG_COPY_UPDATED',{literal(BATCH)},status FROM products WHERE id={pid}"]
    renamed=0
    for a in accounts:
        assert len(a['emailHash'])==64 and all(c in '0123456789abcdef' for c in a['emailHash'])
        assert a['old'].endswith('（演示）') and a['new']==a['old'].removesuffix('（演示）')
        done=sql(f"SELECT id FROM users WHERE SHA2(email,256)='{a['emailHash']}' AND nickname={literal(a['new'])}").splitlines()
        if len(done)==1:continue
        results=sql(f"SELECT id FROM users WHERE SHA2(email,256)='{a['emailHash']}' AND nickname={literal(a['old'])}").splitlines()
        assert len(results)==1,'Expected original seeded account';uid=int(results[0]);renamed+=1
        statements += [f'SELECT id FROM users WHERE id={uid} FOR UPDATE',f"UPDATE users SET nickname={literal(a['new'])} WHERE id={uid} AND nickname={literal(a['old'])}",
            'INSERT INTO copy_guard VALUES(IF(ROW_COUNT()=1,1,0))',
            f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,before_state,after_state) VALUES({admin},'SEED_NICKNAME_COPY','USER',{uid},{literal(BATCH)},{literal(a['old'])},{literal(a['new'])})"]
    print(f'PREVIEW_READY products=65 nicknames={renamed} target={args.target}')
    if not args.apply:return
    if local:
        dest=ROOT/'.local/private'/('044-before-content-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.sql')
        with dest.open('xb') as out:
            identity=subprocess.check_output(['whoami'],text=True).strip()
            subprocess.run(['icacls.exe',str(dest),'/inheritance:r','/grant:r',identity+':(F)','SYSTEM:(F)'],check=True,stdout=subprocess.DEVNULL)
            result=subprocess.run([str(Path(binary).with_name('mysqldump.exe')),*flags,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','maimai'],stdout=out,stderr=subprocess.PIPE,cwd=ROOT)
        assert result.returncode==0,'Backup failed'
    else:subprocess.run(['bash',str(ROOT/'deploy/backup.sh')],check=True)
    statements += [f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES({admin},'CATALOG_COPY_BATCH','CATALOG',0,{literal(BATCH)},'65 descriptions and 42 seeded nicknames; inventory and payment qualification unchanged')",'COMMIT',f"SELECT RELEASE_LOCK('{BATCH}')"]
    sql(';\n'.join(statements)+';');print(f'APPLIED products=65 renamed={renamed}')
if __name__=='__main__':main()
