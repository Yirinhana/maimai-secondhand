// Local acceptance creates its own accounts through registration and all records through public APIs.
const { chromium, expect } = require('@playwright/test');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const BASE='http://127.0.0.1:5173',ROOT=path.resolve(__dirname,'../..'),OUT=path.join(ROOT,'.local/screenshots/061');
fs.mkdirSync(OUT,{recursive:true});
const run=Date.now(),evidence={run,checks:[],screenshots:[],errors:[]};
async function api(ctx,method,route,data,multipart){
  await ctx.request.get(BASE+'/api/v1/auth/csrf');
  const csrf=(await ctx.cookies(BASE)).find(c=>c.name==='XSRF-TOKEN');
  const response=await ctx.request.fetch(BASE+'/api/v1'+route,{method,headers:{'X-XSRF-TOKEN':decodeURIComponent(csrf?.value||'')},...(multipart?{multipart}:data===undefined?{}:{data})});
  if(!response.ok())throw Error(`${method} ${route}: ${response.status()} ${await response.text()}`);
  const text=await response.text();return text?JSON.parse(text):null;
}
async function account(browser,name){
  const ctx=await browser.newContext({viewport:{width:1440,height:960},locale:'zh-CN',timezoneId:'Asia/Shanghai'});
  await ctx.addInitScript(()=>sessionStorage.setItem('maimai-welcome-seen-v1','1'));
  const email=`linked-${run}-${name}@example.invalid`;
  await api(ctx,'POST','/auth/register/code',{email});
  const inbox=await api(ctx,'GET','/dev/mailbox?email='+encodeURIComponent(email));
  const code=inbox[0].content.match(/验证码为：(\d{6})/)[1];
  const policy=await api(ctx,'GET','/policies/current');
  const me=await api(ctx,'POST','/auth/register',{email,code,password:'LinkedTest#2026',nickname:`联动验收${name}${run}`,acceptedTerms:true,policyVersion:policy.version});
  return {ctx,me};
}
async function shot(page,name){await page.evaluate(()=>document.fonts.ready);await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);const file=path.join(OUT,name+'.png');await page.screenshot({path:file,fullPage:true});evidence.screenshots.push(file);}
async function pageFor(ctx){const page=await ctx.newPage();page.on('pageerror',e=>evidence.errors.push(e.message));return page;}
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const buyer=await account(browser,'买家'),seller=await account(browser,'卖家');
  const admin=await browser.newContext({viewport:{width:1440,height:960}});
  await admin.addInitScript(()=>sessionStorage.setItem('maimai-welcome-seen-v1','1'));
  await api(admin,'POST','/auth/login',{email:'admin@maimai.local',password:'Maimai#2026'});
  const app=await api(seller.ctx,'POST','/me/seller-application',{intro:'本地自动验收申请，仅验证关联流程，不进行真实收付。'});
  await api(admin,'POST',`/admin/seller-applications/${app.id}/review`,{action:'APPROVE',reason:'本地测试流程审核，非真实渠道资质',channelQualified:true});
  seller.me=await api(seller.ctx,'GET','/auth/me');expect(seller.me.roles).toContain('SELLER');
  const cats=await api(seller.ctx,'GET','/categories');const category=cats.find(c=>c.children?.length)?.children[0]?.id||cats[0].id;
  const title=`联动核查相机 ${run}`;
  const product=await api(seller.ctx,'POST','/seller/products',{title,categoryId:category,description:'本地验证商品，已说明外观划痕、配件和交付方式，不是真实销售。',condition:'GOOD',defects:'外壳轻微划痕，图片为测试素材',priceCents:10000,stock:2,region:'上海市',deliveryMethods:['MEETUP'],freightCents:0,returnPromise:'按平台售后规则',submit:false});
  await api(seller.ctx,'POST',`/seller/products/${product.id}/images`,null,{files:{name:'fixture.png',mimeType:'image/png',buffer:fs.readFileSync(path.join(__dirname,'fixture8x8.png'))}});
  await api(seller.ctx,'POST',`/seller/products/${product.id}/submit`);
  await api(admin,'POST',`/admin/products/${product.id}/review`,{approve:true,reason:'本地验收资料齐全'});
  const checkout=await api(buyer.ctx,'POST','/checkout',{idempotencyKey:crypto.randomUUID(),items:[{productId:product.id,quantity:1,deliveryMethod:'MEETUP'}],meetupLocation:'上海图书馆门口（本地测试，无实际交付）',meetupTime:new Date(Date.now()+86400000).toISOString()});
  const order=checkout.orders[0];evidence.orderNo=order.orderNo;evidence.orderId=order.id;evidence.productId=product.id;evidence.sellerId=seller.me.id;
  const payment=await api(buyer.ctx,'POST',`/orders/${order.orderNo}/pay`);
  await api(buyer.ctx,'POST','/dev/mock-pay/confirm',{payNo:payment.payNo,amountCents:order.totalCents});
  const code=await api(buyer.ctx,'POST',`/orders/${order.orderNo}/delivery-code`);
  await api(seller.ctx,'POST',`/orders/${order.orderNo}/verify-code`,{code:code.code});
  expect((await api(buyer.ctx,'GET',`/orders/${order.orderNo}`)).fulfillmentStatus).toBe('COMPLETED');
  evidence.checks.push('注册→卖家申请审核→商品图片发布审核→下单→模拟付款→一次性交付码核验');
  const bp=await pageFor(buyer.ctx),sp=await pageFor(seller.ctx),ap=await pageFor(admin);
  await bp.goto(BASE+`/orders/${order.orderNo}`);
  await bp.getByLabel('评分',{exact:true}).selectOption('5');await bp.getByLabel('评价内容',{exact:true}).fill('本次为模拟交易，描述清楚，交付步骤完成。');
  await bp.getByRole('button',{name:'提交评价',exact:true}).click();
  await expect(bp.getByText('本次为模拟交易，描述清楚，交付步骤完成。',{exact:true})).toBeVisible();
  await api(seller.ctx,'POST',`/community/orders/${order.id}/ratings`,{rating:4,comment:'模拟买家按约定完成核验，沟通顺畅。'});
  const profile=await api(buyer.ctx,'GET',`/community/users/${seller.me.id}/reputation`);
  expect(profile.scopes.find(s=>s.source==='SIMULATED').ratings).toBe(1);expect(profile.scopes.find(s=>s.source==='LIVE').ratings).toBe(0);
  await sp.goto(BASE+`/sellers/${seller.me.id}`);await expect(sp.getByRole('heading',{name:'在售商品',exact:true})).toBeVisible();
  expect(await sp.locator('#seller-products').evaluate(el=>el.getBoundingClientRect().top)).toBeLessThan(await sp.locator('.reputation-card').evaluate(el=>el.getBoundingClientRect().top));
  await shot(sp,'seller-product-first');evidence.checks.push('双向评价关联真实注册账号，信誉仅计模拟来源，店铺先展示商品');
  const comment=await api(seller.ctx,'POST',`/products/${product.id}/comments`,{content:'联动验收留言：需核对的站外交易说法',replyToId:null});
  const report=await api(buyer.ctx,'POST','/community/reports',{resourceType:'PRODUCT_COMMENT',resourceId:comment.id,reason:'本地验收：核对这条留言是否诱导站外付款'});evidence.reportId=report.id;
  await ap.goto(BASE+'/admin/community?tab=reports');const reportCard=ap.locator('article.mm-panel').filter({has:ap.getByRole('heading',{name:`举报 #${report.id}`,exact:true})});
  await expect(reportCard.getByText(seller.me.nickname,{exact:true})).toBeVisible();await expect(reportCard.getByText('联动验收留言：需核对的站外交易说法',{exact:true})).toBeVisible();
  await reportCard.getByRole('link',{name:'打开对应内容 →'}).click();await expect(ap.locator(`#discussion-${comment.id}`)).toBeVisible();
  await ap.goto(BASE+'/admin/community?tab=reports');
  await reportCard.getByRole('button',{name:'请麦仔辅助核查'}).click();
  await expect(reportCard.getByText(/本次 AI 分析未完成/)).toBeVisible();
  await reportCard.getByLabel('处理动作').selectOption('HIDE');await reportCard.getByLabel('核查说明').fill('本地验收：确认原文后隐藏这条测试留言');await reportCard.getByRole('button',{name:'保存处理'}).click();await expect(reportCard).toHaveCount(0);
  expect((await api(buyer.ctx,'GET',`/community/users/${seller.me.id}/reputation`)).confirmedContentActions).toBe(1);
  evidence.checks.push('评论举报自动关联原文、双方账号和定位链接；AI 未配置时人工仍可处理并通知');
  const ticket=await api(buyer.ctx,'POST','/support/tickets',{title:'联动验收工单 '+run,body:'本地验证订单关联及管理员读取权限。',orderNo:order.orderNo});evidence.ticketId=ticket.id;
  await ap.goto(BASE+`/support/tickets/${ticket.id}`);await ap.getByRole('link',{name:'关联订单 '+order.orderNo}).click();await expect(ap).toHaveURL(BASE+'/admin/orders/'+order.orderNo);
  await expect(ap.getByText(buyer.me.nickname,{exact:false}).first()).toBeVisible();await expect(ap.getByRole('link',{name:ticket.title,exact:true})).toBeVisible();
  const noAccess=await buyer.ctx.request.get(BASE+'/api/v1/admin/orders/'+order.orderNo);expect(noAccess.status()).toBe(403);
  await ap.goto(BASE+'/me');await expect(ap.getByRole('heading',{name:'我的管理概览',exact:true})).toBeVisible();await expect(ap.getByRole('heading',{name:'关联数据核查',exact:true})).toBeVisible();await shot(ap,'admin-personal-overview');
  evidence.checks.push('工单→管理订单→双方账号、商品快照、付款记录回溯；普通用户不可访问管理数据');
  const matrix=[['admin','/admin'],['admin','/admin/orders'],['admin',`/admin/orders/${order.orderNo}`],['admin','/admin/community?tab=reports'],['admin','/me'],['buyer',`/products/${product.id}`],['buyer',`/orders/${order.orderNo}`],['buyer','/me'],['seller','/seller'],['seller',`/sellers/${seller.me.id}`],['seller',`/publish/${product.id}`]];
  for(const width of [320,390,844])for(const [role,route] of matrix){evidence.lastViewport={width,role,route};const page=role==='admin'?ap:role==='buyer'?bp:sp;await page.setViewportSize({width,height:width===844?390:844});await page.goto(BASE+route);await expect(page.locator('#main-content h1').first()).toBeVisible();await expect.poll(()=>page.locator('.mm-skeleton').count()).toBe(0);await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);}
  await ap.setViewportSize({width:390,height:844});await ap.goto(BASE+'/admin');await expect(ap.getByRole('heading',{name:'关联数据核查',exact:true})).toBeVisible();await shot(ap,'admin-mobile');await bp.setViewportSize({width:390,height:844});await bp.goto(BASE+`/orders/${order.orderNo}`);await expect(bp.getByText('本次为模拟交易，描述清楚，交付步骤完成。',{exact:true})).toBeVisible();await shot(bp,'buyer-order-mobile');
  evidence.checks.push('11 个关联页面 × 320/390/844 宽度，无横向溢出');expect(evidence.errors).toEqual([]);
 }finally{if(evidence.checks.length<5){let i=0;for(const ctx of browser.contexts())for(const page of ctx.pages()){await page.screenshot({path:path.join(OUT,`failure-${i}.png`),fullPage:true}).catch(()=>{});fs.writeFileSync(path.join(OUT,`failure-${i++}.txt`),await page.locator('body').innerText().catch(()=>''));}}fs.writeFileSync(path.join(OUT,'result.json'),JSON.stringify(evidence,null,2));await browser.close();}
 console.log(JSON.stringify(evidence,null,2));
})().catch(e=>{console.error(e);process.exitCode=1});
