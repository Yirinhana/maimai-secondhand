const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch({channel:'chrome', headless:true});
  try {
    const page = await browser.newPage({viewport:{width:360,height:800},locale:'zh-CN'});
    await page.goto('http://127.0.0.1:5173/');
    await page.locator('.mm-home__grid .mm-product-card__cover img').first().waitFor();
    await page.evaluate(() => document.fonts.ready);
    const diagnosis = await page.evaluate(() => ({
      viewport: innerWidth, width: document.documentElement.scrollWidth,
      overflowing: [...document.querySelectorAll('body *')].map(el => {
        const r = el.getBoundingClientRect(), s = getComputedStyle(el);
        return {tag:el.tagName,className:el.className,left:r.left,right:r.right,width:r.width,minWidth:s.minWidth,gridTemplateColumns:s.gridTemplateColumns,overflowWrap:s.overflowWrap};
      }).filter(el=>el.right>361).slice(0,20)
    }));
    console.log(JSON.stringify(diagnosis,null,2));
  } finally { await browser.close(); }
})().catch(e=>{console.error(e.message);process.exitCode=1});
