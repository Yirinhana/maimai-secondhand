const { chromium, expect } = require('@playwright/test');
const path = require('path');

// Diagnose the SDK's query shape only. Never print map parameter values or full URLs.
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext({viewport:{width:1440,height:960},locale:'zh-CN'});
    const page = await context.newPage();
    await page.goto('http://127.0.0.1:5173/login');
    await page.getByLabel('邮箱', { exact: true }).fill('buyer@maimai.local');
    await page.getByLabel('密码', { exact: true }).fill('Maimai#2026');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.getByRole('heading', { name: /把喜欢的留下/ }).waitFor();
    await page.goto('http://127.0.0.1:5173/me');
    await page.getByRole('tab', { name: '收货地址', exact: true }).click();
    await page.getByRole('button', { name: '新增地址', exact: true }).click();
    await page.getByRole('button', { name: '使用地图辅助选址', exact: true }).click();
    await page.getByRole('button', { name: '搜索地点', exact: true }).waitFor();
    await page.getByLabel('搜索城市或地点', { exact: true }).fill('上海人民广场');
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/_AMapService/v3/place/text');
    await page.getByRole('button', { name: '搜索地点', exact: true }).click();
    const search = await response;
    if (!search.ok()) throw Error('POI did not succeed');
    const geocode = page.waitForResponse(r => new URL(r.url()).pathname === '/_AMapService/v3/geocode/regeo');
    await page.getByRole('button').filter({hasText:/人民广场.*·/}).first().click();
    const received = await geocode;
    const values = new URL(received.url()).searchParams.getAll('s');
    const counts = {};
    const parameters = new URL(received.url()).searchParams;
    for (const name of parameters.keys()) counts[name] = (counts[name] || 0) + 1;
    const duplicateValuesIdentical = {};
    for (const [name, count] of Object.entries(counts)) if(count > 1) { const entries=parameters.getAll(name); duplicateValuesIdentical[name]=entries.every(value=>value===entries[0]); }
    let code = null, message = null, format = 'unknown';
    try {
      const raw = await received.text();
      let body;
      try { body = JSON.parse(raw); format = 'JSON'; }
      catch { const match = raw.match(/^[A-Za-z_$][\w.$]*\((\{[\s\S]*\})\);?$/); if(match){body=JSON.parse(match[1]);format='JSONP';} }
      if(body){code=body.code??null;message=body.message??null;}
    } catch {}
    let screenshot;
    if (received.ok()) {
      await page.getByRole('button',{name:'使用此地址',exact:true}).click();
      await expect(page.getByLabel('详细地址',{exact:true})).toHaveValue(/上海/);
      await page.evaluate(async()=>{await document.fonts.ready;window.scrollTo({top:0,behavior:'instant'});});
      await expect.poll(()=>page.evaluate(()=>scrollY)).toBe(0);
      screenshot=`022-${Date.now()}-real-amap-final-top.png`;
      await page.screenshot({path:path.resolve(__dirname,'../../.local/screenshots',screenshot),fullPage:true});
    }
    console.log(JSON.stringify({status:received.status(),parameterCounts:counts,duplicateValuesIdentical,sCount:values.length,sLengths:values.map(v=>v.length),sEmpty:values.map(v=>v.length===0),repeatedSIdentical:values.length>1&&values.every(v=>v===values[0]),format,code,message,screenshot}));
  } finally {
    await browser.close();
  }
})().catch(() => { console.error('Map diagnostic did not complete; no raw URL retained'); process.exitCode = 1; });
