const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'), path = require('node:path');
const ROOT=path.resolve(__dirname,'../..'), BASE=process.env.MAIMAI_045_BASE || 'http://127.0.0.1:5173';
if(!['http://127.0.0.1:5173','https://market.example.com'].includes(BASE)) throw Error('Unapproved host');
const users=JSON.parse(fs.readFileSync(path.join(ROOT,'.local/private/demo-accounts-040.json'),'utf8').replace(/^\uFEFF/,''));
const catalog=JSON.parse(fs.readFileSync(path.join(ROOT,'deploy/catalog-experience-v070.json'),'utf8'));
const report={base:BASE,checks:[],images:{},screenshots:[],errors:[]}, run=Date.now();
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function api(ctx,route){const r=await ctx.request.get(BASE+'/api/v1'+route);expect(r.ok(),`${route}: ${r.status()}`).toBe(true);return r.json()}
async function layout(page,name){
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);
  await expect.poll(()=>page.locator('img').evaluateAll(imgs=>imgs.filter(i=>{const r=i.getBoundingClientRect();return r.width&&r.height&&r.top<innerHeight&&r.bottom>0}).every(i=>i.complete&&i.naturalWidth>0)),{timeout:15000}).toBe(true);
  const scroll = await page.evaluate(() => scrollY);
  for(let y=0;y<await page.evaluate(()=>document.documentElement.scrollHeight);y+=700){await page.evaluate(v=>scrollTo(0,v),y);await delay(100)}
  await expect.poll(()=>page.locator('img').evaluateAll(imgs=>imgs.filter(i=>i.getBoundingClientRect().width>0).every(i=>i.complete&&i.naturalWidth>0)),{timeout:15000}).toBe(true);
  await page.evaluate(v=>scrollTo(0,v),scroll);
  const file=path.join(ROOT,`.local/screenshots/045-${run}-${name}.png`);await page.screenshot({path:file,fullPage:true});report.screenshots.push(file);
}
async function login(ctx,email,target){
  const p=await ctx.newPage();await p.goto(BASE+'/login');await p.getByLabel('邮箱',{exact:true}).fill(email);await p.getByLabel('密码',{exact:true}).fill(users.password);await p.getByRole('button',{name:'登录',exact:true}).click();await expect(p).toHaveURL(BASE+target,{timeout:15000});return p;
}
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const guest=await browser.newContext({viewport:{width:1440,height:1000}});
  const tree=await api(guest,'/categories');
  for(const [parent,children] of Object.entries(catalog.categories)){const node=tree.find(c=>c.name===parent);expect(node).toBeTruthy();for(const name of children)expect(node.children.some(c=>c.name===name),name).toBe(true)}
  report.checks.push('All 8 catalog groups and 31 subcategories exist');
  const details=[];
  for(const item of catalog.items){
    const found=await api(guest,'/products?keyword='+encodeURIComponent(item.title));const row=found.content.find(p=>p.title===item.title);expect(row,item.key).toBeTruthy();
    const d=await api(guest,`/products/${row.id}`);expect(d.experienceSource).toBe(catalog.source);expect(d.stockAvailable).toBeGreaterThan(0);expect(d.supplyNote).toBe(item.supplyNote);expect(d.images).toHaveLength(3);
    const ratings=await api(guest,`/products/${row.id}/ratings`);expect(ratings.items.some(r=>r.simulated&&r.comment===item.review)).toBe(true);for(const r of ratings.items){expect(r).not.toHaveProperty('orderId');expect(r.reviewerId).not.toBe(d.seller.id)}
    const comments=await api(guest,`/products/${row.id}/comments`);expect(comments.items.some(c=>c.seller&&c.authorId===d.seller.id&&c.replyToId)).toBe(true);
    details.push(d);
  }
  report.checks.push('65 stocked products, source notes, account-linked simulated ratings and seller replies');
  const sample=details[0], original=await guest.request.get(BASE+sample.images[0].path), thumbUrl=BASE+sample.images[0].path.replace('/uploads/products/','/uploads/thumbnails/products/480/');
  const thumb=await guest.request.get(thumbUrl);expect(thumb.ok()).toBe(true);const originalBytes=(await original.body()).length,thumbnailBytes=(await thumb.body()).length;expect(thumbnailBytes).toBeLessThan(originalBytes*.65);const tag=thumb.headers().etag;expect(tag).toBeTruthy();expect((await guest.request.get(thumbUrl,{headers:{'If-None-Match':tag}})).status()).toBe(304);
  report.images={originalBytes,thumbnailBytes,reductionPercent:Math.round((1-thumbnailBytes/originalBytes)*100)};
  const page=await guest.newPage();page.on('pageerror',e=>report.errors.push(e.message));
  await page.goto(BASE+'/search');await expect(page.locator('.mm-product-card').first()).toBeVisible();await layout(page,'search-desktop');
  const clothing=tree.find(c=>c.name==='服饰鞋包'), coats=clothing.children.find(c=>c.name==='上衣外套');
  await page.getByRole('button',{name:'服饰鞋包',exact:true}).click();await page.getByRole('button',{name:'上衣外套',exact:true}).click();await expect(page).toHaveURL(new RegExp('categoryId='+coats.id));await expect(page.locator('.mm-product-card').first()).toBeVisible();
  const categoryItems=await api(guest,`/products?categoryId=${coats.id}`);expect(categoryItems.content.length).toBeGreaterThan(0);
  await page.goto(BASE+`/products/${sample.id}#product-ratings-title`);await expect(page.locator('#product-ratings-title')).toBeInViewport();await expect(page.locator('.product-talk__source').first()).toContainText('体验成交');await layout(page,'product-desktop');
  await expect(page.locator('.product-gallery__viewer img')).toHaveCount(0);
  await page.getByRole('button',{name:'放大查看商品图片'}).click();await expect(page.locator('.product-gallery__viewer img')).toHaveAttribute('src',sample.images[0].path);await page.getByRole('button',{name:'关闭商品大图'}).click();await expect(page.locator('.product-gallery__viewer img')).toHaveCount(0);
  report.checks.push('Hidden gallery does not eagerly load original photos; full image mounts only when opened');
  await page.goto(BASE+'/register');await page.getByRole('button',{name:'用户协议与交易售后规则',exact:true}).click();await expect(page.getByRole('dialog')).toBeVisible();expect(guest.pages()).toHaveLength(1);await page.getByRole('button',{name:'关闭用户协议与交易售后规则'}).click();await expect(page).toHaveURL(BASE+'/register');
  await page.goto(BASE+'/missing-page-045');await expect(page.getByRole('heading',{name:'这个地址没有找到内容'})).toBeVisible();
  const buyer=await browser.newContext({viewport:{width:1440,height:1000}}),bp=await login(buyer,users.buyers[0].email,'/');
  const accent=await bp.locator('.mm-app').evaluate(el=>getComputedStyle(el).getPropertyValue('--mm-primary').trim());
  for(const url of ['/community/demands','/orders','/messages']){await bp.goto(BASE+url);await expect.poll(()=>bp.locator('.mm-app').evaluate(el=>getComputedStyle(el).getPropertyValue('--mm-primary').trim())).toBe(accent)}
  await bp.goto(BASE+'/orders/MX045001');await expect(bp.locator('.mm-order-detail__simulation')).toContainText('体验成交');await expect(bp.getByRole('button',{name:'申请售后',exact:true})).toHaveCount(0);await layout(bp,'buyer-order');
  if(BASE.startsWith('http:')) {
    const item=details.find(p=>p.deliveryMethods.includes('EXPRESS'));
    await bp.goto(BASE+'/checkout?items='+encodeURIComponent(JSON.stringify([{productId:item.id,quantity:1,deliveryMethod:'EXPRESS'}])));
    await bp.getByRole('button',{name:'＋ 在这里新增收货地址'}).click();
    const fields=bp.getByRole('group',{name:'新增收货地址',exact:true});
    await fields.getByLabel('收件人',{exact:true}).fill('地址交互验收');await fields.getByLabel('联系电话').fill('00000000000');await fields.getByLabel('省市区').fill('上海市 黄浦区');await fields.getByLabel('详细地址').fill('仅用于本地功能验收');
    const response=bp.waitForResponse(r=>r.url().endsWith('/api/v1/me/addresses')&&r.request().method()==='POST');
    await fields.getByRole('button',{name:'保存并选用此地址'}).click();const saved=await response;expect(saved.ok()).toBe(true);const created=await saved.json();
    await expect(bp.locator(`input[type=radio][value="${created.id}"]`)).toBeChecked();expect(buyer.pages()).toHaveLength(1);
    await buyer.request.get(BASE+'/api/v1/auth/csrf');const token=(await buyer.cookies(BASE)).find(c=>c.name==='XSRF-TOKEN');
    expect((await buyer.request.delete(BASE+`/api/v1/me/addresses/${created.id}`,{headers:{'X-XSRF-TOKEN':decodeURIComponent(token.value)}})).ok()).toBe(true);
    report.checks.push('Inline checkout address persists and is selected without opening another tab; owned local fixture removed');
  }
  const seller=await browser.newContext({viewport:{width:1440,height:1000}}),sp=await login(seller,users.sellers[0].email,'/seller');await expect(sp.locator('.seller-home__stats strong').first()).not.toHaveText('');await layout(sp,'seller-dashboard');
  await sp.locator('.seller-home__stats > a').nth(1).click();await expect(sp).toHaveURL(/seller\/products\?status=ON_SALE/);await expect(sp.getByRole('combobox').first()).toHaveValue('ON_SALE');
  const admin=await browser.newContext({viewport:{width:1440,height:1000}}),ap=await login(admin,users.adminEmail,'/admin');await expect(ap.locator('.mm-header__search')).toHaveCount(0);await expect(ap.getByRole('navigation',{name:'主导航',exact:true})).toHaveCount(0);await expect(ap.getByRole('navigation',{name:'后台导航'})).toBeVisible();await layout(ap,'admin-dashboard');
  for(const url of ['/admin/categories','/admin/community']){await ap.goto(BASE+url);await expect(ap.locator('.mm-app')).toHaveClass(/mm-app--admin/);await expect(ap.locator('.mm-admin__main')).toBeVisible()}
  report.checks.push('Buyer unified colors, role-specific login destinations, working seller stats filter and independent admin shell');
  for(const [p,name,url] of [[page,'market','/search'],[bp,'community','/community/demands'],[sp,'seller','/seller'],[ap,'admin','/admin']]){await p.setViewportSize({width:390,height:844});await p.goto(BASE+url);await layout(p,name+'-mobile')}
  await ap.getByRole('button',{name:'展开工作导航'}).click();await ap.getByRole('link',{name:'分类维护',exact:true}).click();await expect(ap).toHaveURL(BASE+'/admin/categories');await expect(ap.getByRole('navigation',{name:'后台导航'})).toBeHidden();
  // Controlled map responses verify precision and out-of-order callbacks; no real location access.
  await bp.addInitScript(()=>{
    window.__mapOptions={};window.__location={accuracy:50000,location_type:'ip'};
    const pos=x=>({getLng:()=>x,getLat:()=>31.23});
    window.AMap={plugin:(_names,cb)=>cb(),Marker:class{setPosition(){}},Map:class{constructor(el){this.el=el;this.handler=null;el.innerHTML='<button type="button">选点 A</button><button type="button">选点 B</button>';[...el.children].forEach((b,i)=>b.onclick=()=>this.handler?.({lnglat:pos(121.47+i*.01)}))}on(_e,cb){this.handler=cb}destroy(){}setCenter(){}setZoom(){}add(){}},Geocoder:class{getAddress(p,cb){const second=p.getLng()>121.475;setTimeout(()=>cb('complete',{regeocode:{formattedAddress:second?'位置 B':'位置 A',addressComponent:{province:'上海市',city:'上海市',district:'黄浦区'}}}),second?10:180)}},PlaceSearch:class{search(_q,cb){cb('complete',{poiList:{pois:[]}})}},Geolocation:class{constructor(o){window.__mapOptions=o}getCurrentPosition(cb){setTimeout(()=>cb('complete',{position:pos(121.47),...window.__location}),30)}}};
  });
  await bp.goto(BASE+'/search');await bp.getByRole('button',{name:/高级筛选/}).click();await bp.getByText('查找附近闲置',{exact:true}).click();await bp.getByRole('button',{name:'使用地图辅助选址'}).click();await bp.getByRole('button',{name:'定位我',exact:true}).click();await expect(bp.getByRole('alert').filter({hasText:'没有提供可靠精度'})).toBeVisible();expect(await bp.evaluate(()=>window.__mapOptions.noIpLocate)).toBe(3);
  await bp.getByRole('button',{name:'选点 A',exact:true}).click();await bp.getByRole('button',{name:'选点 B',exact:true}).click();await delay(240);await expect(bp.getByText('位置 B',{exact:true})).toBeVisible();await expect(bp.getByText('位置 A',{exact:true})).toHaveCount(0);
  await bp.evaluate(()=>window.__location={accuracy:1200,location_type:'html5'});await bp.getByRole('button',{name:'定位我',exact:true}).click();await expect(bp.getByRole('alert').filter({hasText:'误差约 1200 米'})).toBeVisible();await expect(bp.getByRole('button',{name:'确认使用此地址'})).toHaveCount(0);
  await bp.evaluate(()=>window.__location={accuracy:42,location_type:'html5'});await bp.getByRole('button',{name:'定位我',exact:true}).click();await expect(bp.getByRole('button',{name:'确认使用此地址'})).toBeVisible();await expect(bp.getByText('设备定位精度约 42 米',{exact:false})).toBeVisible();
  report.checks.push('Map disables IP fallback, rejects 1200m accuracy, accepts 42m fix and ignores stale geocoder responses');
  expect(report.errors).toEqual([]);
 }finally{await browser.close();fs.writeFileSync(path.join(ROOT,`.local/045-browser-${BASE.startsWith('http:')?'local':'public'}.json`),JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
})().catch(e=>{console.error(e.message);process.exitCode=1});
