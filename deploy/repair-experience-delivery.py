"""Repair only the approved MX045 historical experience batch. Preview by default."""
import argparse, json, os, re, subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = 'maimai-experience-045'
BATCH = 'maimai-delivery-repair-047'
PLACES = {
    '上海市': ('上海市', '徐汇区'), '福州市': ('福建省 福州市', '鼓楼区'),
    '杭州市': ('浙江省 杭州市', '拱墅区'), '南京市': ('江苏省 南京市', '鼓楼区'),
    '苏州市': ('江苏省 苏州市', '姑苏区'), '厦门市': ('福建省 厦门市', '思明区'),
    '宁波市': ('浙江省 宁波市', '鄞州区'), '合肥市': ('安徽省 合肥市', '蜀山区'),
    '北京市': ('北京市', '朝阳区'), '武汉市': ('湖北省 武汉市', '洪山区'),
    '成都市': ('四川省 成都市', '武侯区'), '广州市': ('广东省 广州市', '天河区'),
    '重庆市': ('重庆市', '渝中区'),
}
def literal(value):
    if value is None: return 'NULL'
    return 'CONVERT(0x' + str(value).encode().hex() + ' USING utf8mb4)' if str(value) else "''"
def at(value): return value.strftime('%Y-%m-%d %H:%M:%S.%f')
def delivery(row):
    matches = [v for k,v in PLACES.items() if k in row['region']]
    assert len(matches) == 1, 'Unexpected catalog region; review before applying'
    region,district = matches[0]
    n = int(row['orderNo'][-3:])
    paid = datetime.fromisoformat(row['paidAt']); completed = datetime.fromisoformat(row['completedAt'])
    assert completed > paid + timedelta(hours=6)
    if 'MEETUP' in row['allowed'].split(','):
        return dict(method='MEETUP', region=region, location=f'{region} · {district}麦麦邻里交流点 {n%4+1} 号交接台（虚拟地点）', time=at(completed))
    assert 'EXPRESS' in row['allowed'].split(',')
    shipped = paid + timedelta(hours=2)
    events = [(shipped, '服务站已收寄'), (shipped+timedelta(hours=3), '包裹已到达同城分拨点'),
              (completed-timedelta(hours=1), '包裹正在派送'), (completed, '收件人已确认签收')]
    traces = '\n'.join(f'{(when+timedelta(hours=8)):%Y-%m-%d %H:%M}  {text}' for when,text in events)
    return dict(method='EXPRESS', region=region, receiver=row['buyerName'], phone='00000000000',
                detail=f'{district}麦麦生活街 {100+n} 号，邻里服务站 {n%5+1} 号柜（虚拟地址）',
                carrier=['shunfeng','zhongtong','jd'][n%3], tracking=f'MXEXP047{n:05d}',
                shipped=at(shipped), completed=at(completed), traces=traces)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=['local','tencent'],required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args(); local=args.target=='local'
    if local:
        binary='C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
        flags=['--defaults-file=.local/private/mysql-client.ini','--host=127.0.0.1','--port=3307','--protocol=TCP']
    else:
        assert os.geteuid()==0 and ROOT==Path('/opt/maimai/current').resolve()
        assert (ROOT/'VERSION').read_text().strip()=='0.8.1'
        binary='/opt/maimai/runtime/mysql/usr/bin/mysql';flags=['--defaults-file=/etc/maimai/mysql-root.cnf']
    def sql(query):
        result=subprocess.run([binary,*flags,'--skip-reconnect','--default-character-set=utf8mb4','--batch','--raw','--skip-column-names','maimai'],input=query.encode(),capture_output=True,cwd=ROOT)
        if result.returncode: raise RuntimeError('Delivery repair SQL failed; private data and SQL withheld')
        return result.stdout.decode().strip()
    marker=f"action='EXPERIENCE_DELIVERY_BATCH' AND reason={literal(BATCH)}"
    if int(sql(f'SELECT COUNT(*) FROM admin_audit_logs WHERE {marker}')):
        print('ALREADY_APPLIED; no changes');return
    rows=[json.loads(line) for line in sql(f"""SELECT JSON_OBJECT(
        'id',o.id,'orderNo',o.order_no,'buyer',o.buyer_id,'buyerName',buyer.nickname,'seller',o.seller_id,
        'region',o.region,'allowed',p.delivery_methods,'delivery',o.delivery_method,'paidAt',o.paid_at,
        'completedAt',o.completed_at,'createdAt',o.created_at,'fulfillment',o.fulfillment_status,
        'receiver',o.receiver,'phone',o.phone,'detail',o.address_detail,'freight',o.freight_cents,
        'goods',o.goods_amount_cents,'total',o.total_cents,'product',p.id,'productSeller',p.seller_id,
        'buyerActive',buyer.status,'sellerActive',seller.status,
        'sellerStatus',(SELECT status FROM seller_applications WHERE user_id=o.seller_id ORDER BY created_at DESC,id DESC LIMIT 1),
        'hasSellerRole',EXISTS(SELECT 1 FROM user_roles WHERE user_id=o.seller_id AND role='SELLER'),
        'appointments',(SELECT COUNT(*) FROM meetup_appointments WHERE order_id=o.id),
        'shipments',(SELECT COUNT(*) FROM shipments WHERE order_id=o.id),
        'paymentOK',(SELECT COUNT(*) FROM payment_requests WHERE order_id=o.id AND simulated=1 AND status='PAID' AND amount_cents=o.total_cents),
        'ledgers',(SELECT COUNT(*) FROM ledger_entries WHERE order_id=o.id),
        'allocations',(SELECT COUNT(*) FROM finance_allocation_expectations WHERE order_id=o.id))
        FROM orders o JOIN order_items i ON i.order_id=o.id JOIN products p ON p.id=i.product_id
        JOIN users buyer ON buyer.id=o.buyer_id JOIN users seller ON seller.id=o.seller_id
        WHERE o.experience_source={literal(SOURCE)} ORDER BY o.id""").splitlines()]
    assert len(rows)==65 and {r['orderNo'] for r in rows}=={f'MX045{i:03d}' for i in range(1,66)}
    for r in rows:
        assert r['buyerActive']==r['sellerActive']=='ACTIVE' and r['sellerStatus']=='APPROVED' and r['hasSellerRole']
        assert r['seller']==r['productSeller'] and r['buyer']!=r['seller']
        assert r['delivery']=='MEETUP' and r['fulfillment']=='COMPLETED'
        assert r['receiver'] is None and r['phone'] is None and r['detail'] is None
        assert r['appointments']==r['shipments']==r['ledgers']==r['allocations']==0 and r['paymentOK']==1
        assert r['freight']==0 and r['goods']==r['total']
    specs=[delivery(r) for r in rows]
    counts={method:sum(s['method']==method for s in specs) for method in ['MEETUP','EXPRESS']}
    print(json.dumps({'status':'PREVIEW_READY','orders':65,'delivery':counts,'sellerAssociations':'approved and linked','amountsAndStock':'unchanged'},ensure_ascii=True))
    if not args.apply:return
    admin=int(sql("SELECT MIN(u.id) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='SUPER_ADMIN' AND u.status='ACTIVE'"))
    if local:
        dest=ROOT/'.local/private'/('047-before-delivery-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.sql')
        with dest.open('xb') as out:
            identity=subprocess.check_output(['whoami'],text=True).strip()
            subprocess.run(['icacls.exe',str(dest),'/inheritance:r','/grant:r',identity+':(F)','SYSTEM:(F)'],check=True,stdout=subprocess.DEVNULL)
            result=subprocess.run([str(Path(binary).with_name('mysqldump.exe')),*flags,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','maimai'],stdout=out,stderr=subprocess.PIPE,cwd=ROOT)
        assert result.returncode==0,'Backup failed'
    else:subprocess.run(['bash',str(ROOT/'deploy/backup.sh')],check=True)
    statements=["CREATE TEMPORARY TABLE delivery_guard(ok TINYINT NOT NULL CHECK(ok=1))",
        f"INSERT INTO delivery_guard VALUES(GET_LOCK('{BATCH}',0))",'START TRANSACTION',
        f'INSERT INTO delivery_guard SELECT IF(COUNT(*)=0,1,0) FROM admin_audit_logs WHERE {marker}']
    for r,s in zip(rows,specs):
        oid=r['id'];pid=r['product']
        statements += [f'SELECT id FROM orders WHERE id={oid} FOR UPDATE',
            f"INSERT INTO delivery_guard SELECT IF(COUNT(*)=1,1,0) FROM orders o JOIN products p ON p.id={pid} WHERE o.id={oid} AND o.order_no={literal(r['orderNo'])} AND o.experience_source={literal(SOURCE)} AND o.delivery_method='MEETUP' AND o.fulfillment_status='COMPLETED' AND o.pay_status='PAID' AND o.receiver IS NULL AND o.phone IS NULL AND o.address_detail IS NULL AND o.buyer_id={r['buyer']} AND o.seller_id={r['seller']} AND p.seller_id=o.seller_id AND p.delivery_methods={literal(r['allowed'])} AND o.total_cents={r['total']} AND o.completed_at={literal(r['completedAt'])}",
            f'INSERT INTO delivery_guard SELECT IF(COUNT(*)=0,1,0) FROM meetup_appointments WHERE order_id={oid}',
            f'INSERT INTO delivery_guard SELECT IF(COUNT(*)=0,1,0) FROM shipments WHERE order_id={oid}']
        if s['method']=='MEETUP':
            statements += [f"INSERT INTO meetup_appointments(order_id,location,scheduled_at,status,created_at,updated_at) VALUES({oid},{literal(s['location'])},{literal(s['time'])},'COMPLETED',{literal(r['createdAt'])},{literal(r['completedAt'])})"]
        else:
            statements += [f"UPDATE orders SET delivery_method='EXPRESS',receiver={literal(s['receiver'])},phone={literal(s['phone'])},region={literal(s['region'])},address_detail={literal(s['detail'])},shipped_at={literal(s['shipped'])} WHERE id={oid}",
                f"INSERT INTO shipments(order_id,carrier,tracking_no,status,traces,last_trace_at,created_at,updated_at) VALUES({oid},{literal(s['carrier'])},{literal(s['tracking'])},'DELIVERED',{literal(s['traces'])},{literal(s['completed'])},{literal(s['shipped'])},{literal(s['completed'])})"]
        before={k:r[k] for k in ['delivery','region','receiver','phone','detail','appointments','shipments']}
        statements.append(f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,before_state,after_state) VALUES({admin},'EXPERIENCE_DELIVERY_REPAIR','ORDER',{oid},{literal(BATCH)},{literal(json.dumps(before,ensure_ascii=False))},{literal(json.dumps(s,ensure_ascii=False))})")
    statements += [f"INSERT INTO admin_audit_logs(admin_id,action,target_type,target_id,reason,after_state) VALUES({admin},'EXPERIENCE_DELIVERY_BATCH','ORDERS',0,{literal(BATCH)},{literal(json.dumps(counts))})",'COMMIT',f"SELECT RELEASE_LOCK('{BATCH}')"]
    sql(';\n'.join(statements)+';')
    print('APPLIED; 65 historical delivery records repaired; identities, prices, inventory and ratings retained')
if __name__=='__main__':main()
