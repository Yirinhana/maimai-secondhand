// Run only after the full nine-case run passes and fixture retirement is authorized.
// This performs no DELETE: only evidence-listed, owner-verified ON_SALE products are off-shelved.
const {chromium, expect} = require('@playwright/test');
const fs = require('fs'), path = require('path');
const BASE = 'http://127.0.0.1:5173';
const OUT = path.resolve(__dirname, '../../.local/screenshots');
const run = Date.now().toString();
const ledger = {run, action:'Evidence-listed fixture off-shelf only; no deletion', items:[], screenshots:[]};
const save = () => fs.writeFileSync(path.join(OUT, `022-${run}-fixture-retirement.json`), JSON.stringify(ledger,null,2));
async function api(context, method, endpoint) {
  if (!endpoint.startsWith('/api/v1/')) throw Error('Only local application API allowed');
  await context.request.get(BASE+'/api/v1/auth/csrf');
  const token = (await context.cookies(BASE)).find(c=>c.name==='XSRF-TOKEN')?.value;
  const response = await context.request.fetch(BASE+endpoint, {method,headers:{'X-XSRF-TOKEN':decodeURIComponent(token||'')}});
  if (!response.ok()) throw Error(`${method} ${endpoint} HTTP ${response.status()}`);
  const text = await response.text(); return text ? JSON.parse(text) : null;
}
(async () => {
  const candidates = new Map();
  for (const filename of fs.readdirSync(OUT).filter(n=>/^022-\d+-evidence\.json$/.test(n))) {
    const evidence = JSON.parse(fs.readFileSync(path.join(OUT,filename),'utf8'));
    for (const [slot,seller,suffix] of [['productA','seller','A'],['productB','seller2','B']]) {
      const product = evidence[slot]; if (!product) continue;
      if (!Number.isSafeInteger(product.id) || product.id<=6 || product.seller!==seller ||
          !/^\d+$/.test(evidence.run) || product.title!==`E2E${evidence.run}-${suffix} 本地演示用品`) throw Error('Invalid fixture evidence; no retirement allowed');
      const item = {id:product.id,title:product.title,seller,sourceRun:evidence.run,sourceFile:filename};
      if (candidates.has(item.id) && candidates.get(item.id).title!==item.title) throw Error('Conflicting fixture evidence');
      candidates.set(item.id,item);
    }
  }
  // Root separately authorized these two interrupted-run fixtures using the retained
  // launch screenshot and the first-run API setup entry in report 022.
  const earlyProof='022-1789331577296-desktop-home.png';
  if (fs.existsSync(path.join(OUT,earlyProof))) {
    for (const [id,seller,suffix] of [[7,'seller','A'],[8,'seller2','B']]) {
      candidates.set(id,{id,title:`E2E1789331577296-${suffix} 本地演示用品`,seller,
        sourceRun:'1789331577296',sourceScreenshot:earlyProof,
        sourceReport:'Browser E2E batch 022: first interrupted run API setup'});
    }
  }
  const browser = await chromium.launch({channel:'chrome',headless:true});
  try {
    const actors = {};
    for (const name of ['seller','seller2']) {
      const context = await browser.newContext({viewport:{width:1440,height:960},locale:'zh-CN'});
      const page = await context.newPage(); await page.goto(BASE+'/login');
      await page.getByLabel('邮箱',{exact:true}).fill(name+'@maimai.local');
      await page.getByLabel('密码',{exact:true}).fill('Maimai#2026');
      await page.getByRole('button',{name:'登录',exact:true}).click();
      await expect(page.getByRole('heading',{name:/把喜欢的留下/})).toBeVisible();
      actors[name]={context,me:await api(context,'GET','/api/v1/auth/me')};
    }
    // Preflight all candidates before changing any product.
    for (const candidate of [...candidates.values()].sort((a,b)=>a.id-b.id)) {
      const actor=actors[candidate.seller];
      const own = await api(actor.context,'GET',`/api/v1/seller/products/${candidate.id}`);
      if (own.id!==candidate.id || own.title!==candidate.title) throw Error('Owner-only product details do not match evidence');
      const item={...candidate,verifiedOwnerId:actor.me.id,beforeStatus:own.status,result:'verified'};
      if (own.status==='ON_SALE') {
        const visible = await api(actor.context,'GET',`/api/v1/products/${candidate.id}`);
        if (visible.seller.id!==actor.me.id || visible.title!==candidate.title) throw Error('Public product owner does not match evidence seller');
      }
      ledger.items.push(item);
    }
    save();
    for (const item of ledger.items) {
      if (item.beforeStatus!=='ON_SALE') {item.result='skipped already not ON_SALE';save();continue;}
      const actor=actors[item.seller];
      await api(actor.context,'POST',`/api/v1/seller/products/${item.id}/off-shelf`);
      const after=await api(actor.context,'GET',`/api/v1/seller/products/${item.id}`);
      if (after.status!=='OFF_SHELF') throw Error('Off-shelf status did not persist');
      item.afterStatus=after.status;item.result='off-shelved';save();
    }
    const page=await browser.newPage({viewport:{width:1440,height:960},locale:'zh-CN'});
    const productsResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/products');
    await page.goto(BASE+'/');
    const products=await (await productsResponse).json();
    ledger.remainingHomepageProducts=products.content.map(p=>({id:p.id,title:p.title}));
    const images=page.locator('.mm-home__grid .mm-product-card__cover img');
    await expect(images).toHaveCount(products.content.length);
    for (const image of await images.all()) {
      await image.scrollIntoViewIfNeeded();
      await expect.poll(()=>image.evaluate(img=>img.complete&&img.naturalWidth>0)).toBe(true);
      await expect(image).toHaveAttribute('src',/^\/uploads\/products\//);
    }
    await page.evaluate(async()=>{await document.fonts.ready;window.scrollTo(0,0)});
    const screenshot=`022-${run}-home-after-fixture-retirement.png`;
    await page.screenshot({path:path.join(OUT,screenshot),fullPage:true});ledger.screenshots.push(screenshot);save();
    console.log(JSON.stringify({run,offShelved:ledger.items.filter(i=>i.result==='off-shelved').length,skipped:ledger.items.filter(i=>i.result.startsWith('skipped')).length,remainingHomepageProducts:ledger.remainingHomepageProducts,screenshots:ledger.screenshots},null,2));
  } finally {save();await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1});
