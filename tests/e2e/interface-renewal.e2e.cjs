const {test,expect} = require('@playwright/test');
const fs = require('fs'), path = require('path');
const BASE='http://127.0.0.1:5173', RUN=process.env.MAIMAI_035_RUN;
const OUT=path.resolve(__dirname,'../../.local/screenshots');
const png=fs.readFileSync(path.join(__dirname,'fixture8x8.png'));
const actors={}, evidence={run:RUN,apiSetup:[],screenshots:[],pages:[],mobile:[]};

async function request(context,method,endpoint,data,multipart) {
  if(!endpoint.startsWith('/api/v1/')) throw Error('Only local application API permitted');
  await context.request.get(BASE+'/api/v1/auth/csrf');
  const token=(await context.cookies(BASE)).find(c=>c.name==='XSRF-TOKEN')?.value;
  return context.request.fetch(BASE+endpoint,{method,headers:{'X-XSRF-TOKEN':decodeURIComponent(token||'')},...(multipart?{multipart}:data===undefined?{}:{data})});
}
async function json(context,method,endpoint,data) {
  const r=await request(context,method,endpoint,data);
  if(!r.ok()) throw Error(`${method} ${endpoint.split('?')[0]} HTTP ${r.status()}`);
  const body=await r.text();return body?JSON.parse(body):null;
}
async function ready(page) {
  await expect(page.locator('#main-content h1').first()).toBeVisible();
  await expect(page.getByText(/^(加载中…|好物加载中…|正在读取内容…|正在整理内容…|正在加载正文…|正在读取商品…|正在读取统计…|正在加载…)$/)).toHaveCount(0);
  await page.evaluate(async()=>{await document.fonts.ready;window.scrollTo({top:0,behavior:'instant'});});
}
async function snapshot(page,name) {
  await ready(page);
  const filename=`035-${RUN}-${name}.png`;
  await page.screenshot({path:path.join(OUT,filename),fullPage:true});evidence.screenshots.push(filename);
}
async function login(browser,name) {
  const context=await browser.newContext({viewport:{width:1440,height:960},locale:'zh-CN'});
  const page=await context.newPage();actors[name]={context,page};
  await page.goto(BASE+'/login');
  await page.getByLabel('邮箱',{exact:true}).fill(name+'@maimai.local');
  await page.getByLabel('密码',{exact:true}).fill('Maimai#2026');
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await expect(page.getByRole('button',{name:'打开个人菜单',exact:true})).toBeVisible();
  actors[name].me=await json(context,'GET','/api/v1/auth/me');
}
async function registerOwnAccount(browser) {
  const context=await browser.newContext({viewport:{width:1440,height:960},locale:'zh-CN'});
  const page=await context.newPage(), email=`e2e035-${RUN}@example.invalid`;
  actors.profile={context,page,email};
  await page.goto(BASE+'/register');
  await page.getByLabel('邮箱',{exact:true}).fill(email);
  await page.getByRole('button',{name:'发送验证码',exact:true}).click();
  await expect(page.getByText('验证码已发送，10 分钟内有效，请查收邮箱',{exact:true})).toBeVisible();
  const mail=await json(context,'GET','/api/v1/dev/mailbox?email='+encodeURIComponent(email));
  const code=mail[0]?.content.match(/验证码为：(\d{6})/)?.[1];
  if(!code) throw Error('Own local capture email did not contain the verification code');
  await page.getByLabel('邮箱验证码',{exact:true}).fill(code);
  await page.getByLabel('昵称',{exact:true}).fill('E2E035头像');
  await page.getByLabel('密码',{exact:true}).fill('E2EAccount#2026');
  await page.getByLabel('确认密码',{exact:true}).fill('E2EAccount#2026');
  await expect(page.getByRole('checkbox')).not.toBeChecked();
  await page.getByRole('checkbox').check();
  await page.getByRole('button',{name:'注册并登录',exact:true}).click();
  await expect(page.getByRole('button',{name:'打开个人菜单',exact:true})).toBeVisible();
  actors.profile.me=await json(context,'GET','/api/v1/auth/me');
  evidence.apiSetup.push({action:'Read only the fresh account local capture mailbox',email});
  evidence.profileAccount={id:actors.profile.me.id,email};
}
async function confirm(page) {
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('dialog').getByRole('button',{name:'确认操作',exact:true}).click();
}
function responseFor(page,method,endpoint) {
  return page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname===endpoint);
}
async function decoded(image) {
  await expect(image).toBeVisible();
  await expect.poll(()=>image.evaluate(img=>img.complete&&img.naturalWidth>0)).toBe(true);
}

test.beforeAll(async({browser})=>{
  test.setTimeout(120000);
  for(const name of ['buyer','seller','admin','operator','support']) await login(browser,name);
  const context=await browser.newContext({viewport:{width:1440,height:960},locale:'zh-CN'});
  actors.anonymous={context,page:await context.newPage()};
  await registerOwnAccount(browser);
});
test.afterEach(async({},info)=>{
  if(info.status===info.expectedStatus) return;
  for(const [name,actor] of Object.entries(actors)) {
    try {
      await actor.page.screenshot({path:path.join(OUT,`035-${RUN}-failure-${info.title.slice(0,2)}-${name}.png`),fullPage:true});
      fs.writeFileSync(path.join(OUT,`035-${RUN}-failure-${info.title.slice(0,2)}-${name}.txt`),await actor.page.locator('body').innerText());
    } catch {}
  }
});
test.afterAll(async()=>{
  if(evidence.contextProduct){
    const own=await json(actors.seller.context,'GET','/api/v1/seller/products/'+evidence.contextProduct.id);
    expect(own.title).toBe(evidence.contextProduct.title);
    if(own.status==='ON_SALE')await json(actors.seller.context,'POST','/api/v1/seller/products/'+own.id+'/off-shelf');
    const current=await json(actors.seller.context,'GET','/api/v1/seller/products/'+own.id);
    evidence.contextProduct.finalStatus=current.status;expect(current.status).toBe('OFF_SHELF');
  }
  fs.writeFileSync(path.join(OUT,`035-${RUN}-evidence.json`),JSON.stringify(evidence,null,2));
  for(const actor of Object.values(actors)) await actor.context.close();
});

test('01 distinct page structures and top navigation identify each workspace',async()=>{
  const buyer=actors.buyer.page;
  await buyer.goto(BASE+'/');
  await expect(buyer.locator('.mm-home__grid .mm-product-card').first()).toBeVisible();
  for(const image of await buyer.locator('.mm-home__grid img').all()) {
    await image.scrollIntoViewIfNeeded();await decoded(image);
    await expect(image).toHaveAttribute('src',/^\/uploads\/products\//);
  }
  const targets=[
    {actor:'buyer',label:'逛逛闲置',section:'market',path:'/',name:'market-home'},
    {actor:'buyer',label:'买家交易',section:'buyer',path:'/orders',name:'buyer-orders'},
    {actor:'seller',label:'卖家工作台',section:'seller',path:'/seller/products',name:'seller-workbench'},
    {actor:'buyer',label:'消息',section:'messages',path:'/messages',name:'message-inbox'},
    {actor:'admin',label:'管理后台',section:'admin',path:'/admin',name:'admin-overview'},
    {actor:'buyer',label:'麦麦官方',section:'official',path:'/official',name:'official-list'},
  ];
  for(const item of targets) {
    const page=actors[item.actor].page;
    const link=page.getByRole('navigation',{name:'主导航',exact:true}).getByRole('link',{name:item.label,exact:true});
    await link.click();await expect(page).toHaveURL(BASE+item.path);await ready(page);
    await expect(link).toHaveClass(/is-active/);
    await expect(page.locator('#main-content')).toHaveClass(new RegExp(`mm-main--${item.section}`));
    if(item.path!=='/') await expect(page.getByRole('navigation',{name:'当前位置',exact:true})).toBeVisible();
    if(item.section==='admin') {
      const navigation=await page.locator('.mm-admin__side').boundingBox();
      const content=await page.locator('.mm-admin__main').boundingBox();
      expect(navigation.y+navigation.height).toBeLessThanOrEqual(content.y+1);
    }
    evidence.pages.push({route:item.path,section:item.section,heading:await page.locator('#main-content h1').first().innerText(),title:await page.title()});
    await snapshot(page,item.name);
  }
});

test('02 avatar upload is persistent and personal menu supports keyboard and dismissal',async()=>{
  const {page,context}=actors.profile;
  await page.getByRole('button',{name:'打开个人菜单',exact:true}).click();
  await page.getByRole('menuitem',{name:'个人中心',exact:true}).click();
  await expect(page).toHaveURL(BASE+'/me');
  const upload=page.getByLabel('上传头像',{exact:true});
  const invalid=responseFor(page,'POST','/api/v1/me/avatar');
  await upload.setInputFiles({name:'invalid.png',mimeType:'image/png',buffer:Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"></svg>')});
  expect((await invalid).status()).toBe(400);
  await expect(page.getByRole('alert')).toBeVisible();
  const firstResponse=responseFor(page,'POST','/api/v1/me/avatar');
  await upload.setInputFiles({name:'fixture8x8.png',mimeType:'image/png',buffer:png});
  const first=await firstResponse;expect(first.ok()).toBe(true);
  const firstUrl=(await first.json()).avatarUrl;
  expect(firstUrl).toMatch(/^\/api\/v1\/avatars\/[0-9a-f-]+\.jpg$/);
  await expect(page.getByText('头像已更新',{exact:true})).toBeVisible();
  await decoded(page.getByRole('img',{name:'我的头像',exact:true}));
  await page.reload();await ready(page);
  expect((await json(context,'GET','/api/v1/auth/me')).avatarUrl).toBe(firstUrl);
  await expect(page.getByRole('img',{name:'我的头像',exact:true})).toHaveAttribute('src',firstUrl);
  const secondResponse=responseFor(page,'POST','/api/v1/me/avatar');
  await page.getByLabel('上传头像',{exact:true}).setInputFiles({name:'replacement.png',mimeType:'image/png',buffer:png});
  const second=await secondResponse;expect(second.ok()).toBe(true);
  const currentUrl=(await second.json()).avatarUrl;
  expect(currentUrl).not.toBe(firstUrl);
  expect((await context.request.get(BASE+firstUrl)).status()).toBe(404);
  const trigger=page.getByRole('button',{name:'打开个人菜单',exact:true});
  await expect(trigger.locator('img')).toHaveAttribute('src',currentUrl);await decoded(trigger.locator('img'));
  await trigger.focus();await trigger.press('ArrowDown');
  const firstItem=page.getByRole('menuitem',{name:'首页',exact:true});
  await expect(firstItem).toBeFocused();
  await firstItem.press('ArrowDown');await expect(page.getByRole('menuitem',{name:'个人中心',exact:true})).toBeFocused();
  await page.keyboard.press('End');await expect(page.getByRole('menuitem',{name:'退出登录',exact:true})).toBeFocused();
  await page.keyboard.press('Home');await expect(firstItem).toBeFocused();
  await page.keyboard.press('Escape');await expect(page.getByRole('menu')).toHaveCount(0);await expect(trigger).toBeFocused();
  await trigger.click();await expect(page.getByRole('menu')).toBeVisible();
  await expect(page.getByRole('menu').locator('img')).toHaveAttribute('src',currentUrl);
  await snapshot(page,'avatar-menu-open');
  await page.locator('#main-content h1').first().click();await expect(page.getByRole('menu')).toHaveCount(0);
  await trigger.click();await page.getByRole('menuitem',{name:'首页',exact:true}).click();await expect(page).toHaveURL(BASE+'/');
  await expect(page.getByRole('heading',{name:/把喜欢的留下/})).toBeVisible();await expect(page.getByRole('menu')).toHaveCount(0);
  await trigger.click();await page.getByRole('menuitem',{name:'个人中心',exact:true}).click();await expect(page).toHaveURL(BASE+'/me');await expect(page.getByRole('menu')).toHaveCount(0);
  evidence.avatar={firstUrl,currentUrl,persistedAfterReload:true,invalidStatus:400,oldUrlStatus:404,keyboardAndOutsideDismissed:true};
});

test('03 official drafts and withdrawals stay private while authorized publishing displays plain text',async()=>{
  const operator=actors.admin.page, anonymous=actors.anonymous.page;
  const article={slug:`e2e035-${RUN}`,title:`E2E035官方验收 ${RUN}`,summary:'本地验收文章，发布后核对展示并撤回。',body:'第一段：本地开发验收，不代表真实支付或上线。\n\n<script>window.__maimaiOfficialInjected = true</script>',category:'NOTICE',status:'DRAFT'};
  await operator.goto(BASE+'/admin/official');
  await operator.getByRole('button',{name:'新建官方内容',exact:true}).click();
  for(const [label,value] of [['标题',article.title],['链接标识',article.slug],['内容摘要',article.summary],['正文',article.body]]) await operator.getByLabel(label,{exact:true}).fill(value);
  await operator.getByLabel(/^发布状态/).selectOption('DRAFT');
  const created=responseFor(operator,'POST','/api/v1/admin/official/articles');
  await operator.getByRole('button',{name:'保存内容',exact:true}).click();
  const creation=await created;expect(creation.ok()).toBe(true);const saved=await creation.json();
  evidence.article={id:saved.id,slug:article.slug,createdBy:'admin',retained:true};
  await expect(operator.getByText('草稿已保存',{exact:true})).toBeVisible();
  await anonymous.goto(BASE+'/official/'+article.slug);await expect(anonymous.getByRole('heading',{name:'内容暂不可用',exact:true})).toBeVisible();
  expect((await actors.anonymous.context.request.get(BASE+'/api/v1/official/articles/'+article.slug)).status()).toBe(404);
  await operator.locator('article').filter({has:operator.getByRole('heading',{name:article.title,exact:true})}).getByRole('button',{name:'编辑内容',exact:true}).click();
  await operator.getByLabel(/^发布状态/).selectOption('PUBLISHED');
  const published=responseFor(operator,'PUT',`/api/v1/admin/official/articles/${saved.id}`);
  await operator.getByRole('button',{name:'保存内容',exact:true}).click();await confirm(operator);expect((await published).ok()).toBe(true);
  await anonymous.goto(BASE+'/official');
  await anonymous.getByRole('navigation',{name:'官方内容分类',exact:true}).getByRole('link',{name:'平台公告',exact:true}).click();
  await expect(anonymous).toHaveURL(BASE+'/official?category=NOTICE');
  await expect(anonymous.getByRole('navigation',{name:'官方内容分类',exact:true}).getByRole('link',{name:'平台公告',exact:true})).toHaveAttribute('aria-current','page');
  await snapshot(anonymous,'official-anonymous-notice-category');
  await anonymous.getByRole('link',{name:article.title,exact:true}).click();
  await expect(anonymous.getByRole('heading',{name:article.title,exact:true})).toBeVisible();
  await expect(anonymous.locator('.official-prose')).toContainText('<script>window.__maimaiOfficialInjected = true</script>');
  expect(await anonymous.evaluate(()=>window.__maimaiOfficialInjected)).toBeUndefined();
  await snapshot(anonymous,'official-published-plain-text');
  await operator.locator('article').filter({has:operator.getByRole('heading',{name:article.title,exact:true})}).getByRole('button',{name:'编辑内容',exact:true}).click();
  const amended=article.body+'\n\n第二次编辑：已核对前端显示与数据库持久化。';
  await operator.getByLabel('正文',{exact:true}).fill(amended);
  const edited=responseFor(operator,'PUT',`/api/v1/admin/official/articles/${saved.id}`);
  await operator.getByRole('button',{name:'保存内容',exact:true}).click();await confirm(operator);expect((await edited).ok()).toBe(true);
  await anonymous.reload();await expect(anonymous.locator('.official-prose')).toContainText('第二次编辑：已核对前端显示与数据库持久化。');
  evidence.article.editedPublicBody=true;
  for(const role of ['support','profile']) {
    expect((await request(actors[role].context,'GET','/api/v1/admin/official/articles')).status()).toBe(403);
    expect((await request(actors[role].context,'POST','/api/v1/admin/official/articles',{...article,slug:`blocked-${role}-${RUN}`})).status()).toBe(403);
  }
  const support=actors.support.page;await support.goto(BASE+'/admin/official');
  await expect(support.getByRole('alert')).toContainText('只有运营与超级管理员');
  await expect(support.getByRole('button',{name:'新建官方内容',exact:true})).toHaveCount(0);
  await expect(support.locator('.mm-admin__side').getByRole('link',{name:'官方内容',exact:true})).toHaveCount(0);
  await snapshot(support,'official-support-denied');
  const admin=actors.admin.page;await admin.goto(BASE+'/admin/official');
  await admin.locator('article').filter({has:admin.getByRole('heading',{name:article.title,exact:true})}).getByRole('button',{name:'编辑内容',exact:true}).click();
  await admin.getByLabel(/^发布状态/).selectOption('WITHDRAWN');
  const withdrawn=responseFor(admin,'PUT',`/api/v1/admin/official/articles/${saved.id}`);
  await admin.getByRole('button',{name:'保存内容',exact:true}).click();expect((await withdrawn).ok()).toBe(true);
  await expect(admin.getByText('官方内容已撤回',{exact:true})).toBeVisible();
  await anonymous.reload();await expect(anonymous.getByRole('heading',{name:'内容暂不可用',exact:true})).toBeVisible();
  expect((await actors.anonymous.context.request.get(BASE+'/api/v1/official/articles/'+article.slug)).status()).toBe(404);
  await snapshot(admin,'official-withdrawn-admin');
  evidence.article={...evidence.article,finalStatus:'WITHDRAWN',draftStatus:404,withdrawnStatus:404,unauthorizedReadWrite:403,htmlRenderedAsText:true};
});

test('04 mobile pages remain within 360px after content and avatar load',async()=>{
  const targets=[['profile','/'],['profile','/me'],['buyer','/orders'],['buyer','/messages'],['seller','/seller/products'],['admin','/admin/official'],['profile','/official'],['profile','/support']];
  for(const [actor,route] of targets) {
    const page=actors[actor].page;await page.setViewportSize({width:360,height:800});
    await page.goto(BASE+route);await ready(page);
    if(route==='/') {
      await expect(page.locator('.mm-home__grid .mm-product-card').first()).toBeVisible();
      for(const image of await page.locator('.mm-home__grid img').all()){await image.scrollIntoViewIfNeeded();await decoded(image);await expect(image).toHaveAttribute('src',/^\/uploads\/products\//);}
    }
    if(route==='/me') await decoded(page.getByRole('img',{name:'我的头像',exact:true}));
    if(route==='/official') await expect(page.locator('.official-story').first()).toBeVisible();
    if(route==='/seller/products') await expect(page.getByRole('link',{name:'发布新闲置',exact:true})).toBeVisible();
    if(route==='/admin/official') await expect(page.getByRole('button',{name:'新建官方内容',exact:true})).toBeVisible();
    if(route==='/support') await expect(page.locator('summary').first()).toBeVisible();
    await ready(page);
    const widths=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth,body:document.body.scrollWidth}));
    expect(widths.document,route+' document overflow').toBeLessThanOrEqual(360);expect(widths.body,route+' body overflow').toBeLessThanOrEqual(360);
    evidence.mobile.push({actor,route,...widths});await snapshot(page,'mobile-'+(route==='/'?'home':route.slice(1).replaceAll('/','-')));
    await page.getByRole('button',{name:'打开导航菜单',exact:true}).click();
    const adminLink=page.getByRole('navigation',{name:'主导航',exact:true}).getByRole('link',{name:'管理后台',exact:true});
    if(actor==='admin') await expect(adminLink).toBeVisible();else await expect(adminLink).toHaveCount(0);
    await page.keyboard.press('Escape');
  }
  const page=actors.profile.page;
  await page.getByRole('button',{name:'打开导航菜单',exact:true}).click();
  await expect(page.getByRole('navigation',{name:'主导航',exact:true})).toBeVisible();
  await snapshot(page,'mobile-navigation-open');
  await page.keyboard.press('Escape');await expect(page.getByRole('navigation',{name:'主导航',exact:true})).not.toBeVisible();
  await page.getByRole('button',{name:'打开个人菜单',exact:true}).click();
  await expect(page.getByRole('menu')).toBeVisible();
  const box=await page.getByRole('menu').boundingBox();expect(box.x).toBeGreaterThanOrEqual(0);expect(box.x+box.width).toBeLessThanOrEqual(360);
  await snapshot(page,'mobile-personal-menu');
});


test('05 order and aftersale details retain the authenticated transaction workspace after refresh',async()=>{
  const buyer=actors.profile.context,seller=actors.seller.context;
  const categories=await json(seller,'GET','/api/v1/categories');
  const categoryId=categories.find(c=>c.children?.length)?.children[0]?.id||categories[0].id;
  const title='E2E035'+RUN+'-A 本地分区验收用品';
  const product=await json(seller,'POST','/api/v1/seller/products',{title,categoryId,description:'本地自动化测试商品，不是真实交易。',condition:'GOOD',defects:'仅测试用图片',priceCents:5000,stock:3,region:'上海市黄浦区',deliveryMethods:['EXPRESS'],freightCents:600,returnPromise:'按平台已审核条款办理',submit:false});
  evidence.contextProduct={id:product.id,title,seller:'seller',verifiedOwnerId:actors.seller.me.id};
  const uploaded=await request(seller,'POST','/api/v1/seller/products/'+product.id+'/images',undefined,{files:{name:'fixture8x8.png',mimeType:'image/png',buffer:png}});expect(uploaded.ok()).toBe(true);
  await json(seller,'POST','/api/v1/seller/products/'+product.id+'/submit');
  await json(actors.admin.context,'POST','/api/v1/admin/products/'+product.id+'/review',{approve:true,reason:'035本地验收资料完整'});
  const address=await json(buyer,'POST','/api/v1/me/addresses',{receiver:'E2E035测试收件人',phone:'13800000001',region:'上海市黄浦区',detail:'本地测试点，不寄真实包裹',isDefault:true});
  const batch=await json(buyer,'POST','/api/v1/checkout',{idempotencyKey:'e2e035-'+RUN,items:[{productId:product.id,quantity:1,deliveryMethod:'EXPRESS'}],addressId:address.id});
  const orderNo=batch.orders[0].orderNo;
  const payment=await json(buyer,'POST','/api/v1/orders/'+orderNo+'/pay');expect(payment.simulated).toBe(true);
  const paid=await json(buyer,'POST','/api/v1/dev/mock-pay/confirm',{payNo:payment.payNo,amountCents:payment.amountCents});expect(paid.simulated).toBe(true);
  await json(seller,'POST','/api/v1/orders/'+orderNo+'/ship',{carrier:'shunfeng',trackingNo:'E2E035'+RUN});
  await json(buyer,'POST','/api/v1/orders/'+orderNo+'/confirm-receipt');
  const completed=await json(buyer,'GET','/api/v1/orders/'+orderNo);expect(completed.fulfillmentStatus).toBe('COMPLETED');
  const aftersale=await json(buyer,'POST','/api/v1/orders/'+orderNo+'/aftersales',{type:'RETURN_REFUND',reason:'035分区回归，不寄真实包裹',goodsAmountCents:5000,freightAmountCents:0,evidence:'独立本地测试，无真实资金'});
  await json(seller,'POST','/api/v1/seller/aftersales/'+aftersale.id+'/respond',{agree:true,reply:'本地退货流程',returnRecipient:'E2E035测试卖家',returnPhone:'13800000002',returnAddress:'上海市黄浦区 E2E本地退货点，不是真实地址'});
  await json(buyer,'POST','/api/v1/me/aftersales/'+aftersale.id+'/return-ship',{carrier:'shunfeng',trackingNo:'E2E035RETURN'+RUN});
  await json(seller,'POST','/api/v1/seller/aftersales/'+aftersale.id+'/receive-return');
  await json(buyer,'POST','/api/v1/me/aftersales/'+aftersale.id+'/escalate');
  evidence.apiSetup.push({action:'Own product/image/review/address/checkout/local mock pay/shipping/receipt/return/manual setup; subsequent context checks use UI',productId:product.id,orderNo,aftersaleId:aftersale.id,simulated:true});
  const routes=['/orders/'+orderNo,'/aftersales/'+aftersale.id];
  evidence.contextPages=[];
  for(const [actor,section,label] of [['profile','buyer','买家交易'],['seller','seller','卖家工作台'],['admin','admin','管理后台']]) {
    const {page,context}=actors[actor];await page.setViewportSize({width:1440,height:960});
    const detail=await json(context,'GET','/api/v1/orders/'+orderNo);
    if(actor==='profile')expect(detail.buyerId).toBe(actors[actor].me.id);
    if(actor==='seller')expect(detail.sellerId).toBe(actors[actor].me.id);
    for(const route of routes) {
      await page.goto(BASE+route);
      await expect(page.locator('#main-content')).toHaveClass(new RegExp('mm-main--'+section));
      const selected=page.getByRole('navigation',{name:'主导航',exact:true}).getByRole('link',{name:label,exact:true});
      await expect(selected).toHaveClass(/is-active/);
      await page.reload();await ready(page);
      await expect(page.locator('#main-content')).toHaveClass(new RegExp('mm-main--'+section));
      await expect(selected).toHaveClass(/is-active/);
      await snapshot(page,actor+'-'+(route.startsWith('/orders')?'order':'aftersale')+'-context');
      evidence.contextPages.push({actor,section,route,source:'035 own API setup',afterReload:true});
    }
    await page.getByRole('navigation',{name:'主导航',exact:true}).getByRole('link',{name:'逛逛闲置',exact:true}).click();
    await expect(page.locator('#main-content')).toHaveClass(/mm-main--market/);
  }
});
