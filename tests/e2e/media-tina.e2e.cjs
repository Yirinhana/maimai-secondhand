// Real avatar persistence and opt-in live AI/media acceptance. No mail or payment writes.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '../..');
const BASE = process.env.MAIMAI_043_BASE || 'http://127.0.0.1:5173';
if (!['http://127.0.0.1:5173', 'https://market.example.com'].includes(BASE)) throw Error('Unapproved test host');
const live = process.env.MAIMAI_043_LIVE_AI === '1';
const catalog = process.env.MAIMAI_043_CATALOG === '1';
const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, '.local/private/demo-accounts-040.json'), 'utf8').replace(/^\uFEFF/, ''));
const report = { base: BASE, liveAi: live, checks: [], errors: [] };
const run = Date.now();
const output = name => path.join(ROOT, `.local/screenshots/043-${run}-${name}`);
async function login(context, user) {
  const res = await post(context, '/auth/login', { email: user.email, password: manifest.password });
  expect(res.status()).toBe(200);
}
async function post(context, route, data) {
  await context.request.get(BASE + '/api/v1/auth/csrf');
  const csrf = (await context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN');
  return context.request.post(BASE + '/api/v1' + route, { headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf?.value || '') }, data, timeout: 25000 });
}
async function layout(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  await expect.poll(() => page.locator('img').evaluateAll(images => images.filter(i => {
    const box = i.getBoundingClientRect(); return box.width && box.height && box.top < innerHeight && box.bottom > 0;
  }).every(i => i.complete && i.naturalWidth > 0))).toBe(true);
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const owner = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN' });
    await login(owner, manifest.buyers[29]);
    const page = await owner.newPage();
    page.on('pageerror', error => report.errors.push(error.message));
    await page.goto(BASE + '/me');
    await expect(page.getByRole('group', { name: '选择默认头像' })).toBeVisible();
    for (const name of ['小橘猫', '小柴犬', '水豚', '猫头鹰']) await expect(page.getByRole('button', { name: `使用${name}头像` })).toBeVisible();
    if (BASE.startsWith('http://127.')) {
      await page.getByRole('button', { name: '使用小柴犬头像' }).click();
      await expect(page.getByRole('status').filter({ hasText: '头像已更新' })).toBeVisible();
      const me = await (await owner.request.get(BASE + '/api/v1/auth/me')).json();
      expect(me.avatarUrl).toMatch(/^\/api\/v1\/avatars\/[a-f0-9-]+\.jpg$/);
      await page.reload();
      await expect(page.locator('.mm-account__portrait img')).toHaveAttribute('src', me.avatarUrl);
      const fresh = await browser.newContext();
      await login(fresh, manifest.buyers[29]);
      expect((await (await fresh.request.get(BASE + '/api/v1/auth/me')).json()).avatarUrl).toBe(me.avatarUrl);
      await fresh.close();
      report.checks.push('Avatar selection persists after reload and independent login');
    }
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 960 }); await layout(page);
      await page.screenshot({ path: output(`avatars-${width}.png`) });
    }
    if (live) {
      expect((await (await owner.request.get(BASE + '/api/v1/support/assistant')).json()).enabled).toBe(true);
      const firstId = crypto.randomUUID(), secondId = crypto.randomUUID();
      const first = await (await post(owner, '/support/chat', { requestId: firstId, message: '100元商品加10元运费，平台服务费是多少？请简短回答。' })).json();
      expect(first.status).toBe('COMPLETE'); expect(first.answer).toContain('0.03'); expect(first.answer).not.toContain('<think');
      const second = await (await post(owner, '/support/chat', { requestId: secondId, message: '那商品金额变成200元，运费不变呢？' })).json();
      expect(second.status).toBe('COMPLETE'); expect(second.answer).toContain('0.06');
      const other = await browser.newContext(); await login(other, manifest.buyers[28]);
      const otherTurns = await (await other.request.get(BASE + '/api/v1/support/chat')).json();
      expect(otherTurns.some(t => [firstId, secondId].includes(t.requestId))).toBe(false); await other.close();
      await page.getByRole('button', { name: '打开缇娜客服', exact: true }).click();
      await expect(page.getByText(second.answer, { exact: true })).toBeVisible();
      await layout(page); await page.screenshot({ path: output('tina-live-mobile.png') });
      await page.getByRole('button', { name: '关闭缇娜客服', exact: true }).click();
      report.checks.push('Live model two-turn fee calculation, website rendering and account isolation');
      report.liveAnswers = [first.answer, second.answer];
    }
    if (catalog) {
      const media = JSON.parse(fs.readFileSync(path.join(ROOT, 'backend/src/main/resources/demo/catalog-v2/manifest.json'), 'utf8'));
      const all = [];
      for (let index = 0; index < 20; index++) {
        const result = await (await owner.request.get(BASE + `/api/v1/products?page=${index}&size=50`)).json();
        all.push(...result.content);
        if (result.last || result.content.length < 50) break;
      }
      if (!BASE.startsWith('http://127.')) expect(all.length).toBe(65);
      let assets = 0;
      for (const item of media.items) {
        const matches = all.filter(p => p.title === item.title);
        expect(matches.length).toBe(1);
        const detail = await (await owner.request.get(BASE + `/api/v1/products/${matches[0].id}`)).json();
        expect(detail.images.map(i => i.path)).toEqual(item.images.map(i => '/uploads/products/' + i.file));
        expect(detail.description).toContain('AI 生成');
        if (item.key >= 'item-006') expect(detail.priceCents).toBe(item.priceCents);
        for (const image of item.images) {
          const response = await owner.request.get(BASE + '/uploads/products/' + image.file);
          expect(response.status()).toBe(200);
          expect(crypto.createHash('sha256').update(await response.body()).digest('hex')).toBe(image.sha256); assets++;
        }
      }
      report.imageFilesVerified = assets;
      const representative = all.find(p => p.title === media.items[6].title);
      await page.goto(BASE + `/products/${representative.id}`);
      for (const view of ['正面', '背面', '侧面']) {
        await page.getByRole('button', { name: `查看${view}图片`, exact: true }).click();
        await expect(page.getByRole('button', { name: `查看${view}图片`, exact: true })).toHaveAttribute('aria-pressed', 'true');
        await layout(page);
      }
      await page.getByRole('button', { name: '放大查看商品图片' }).click();
      await expect(page.getByRole('dialog')).toBeVisible(); await page.keyboard.press('Escape');
      await page.screenshot({ path: output('gallery-mobile.png') });
      report.checks.push('Unique catalog assets, image ordering, three view controls and enlarged viewer');
      const guest = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
      const shop = await guest.newPage();
      shop.on('pageerror', error => report.errors.push(error.message));
      for (const route of ['/', '/search']) {
        await shop.goto(BASE + route);
        await expect(shop.locator('.mm-product-card').first()).toBeVisible();
        await expect.poll(() => shop.locator('img[src*="demo043-"]').count()).toBeGreaterThan(0);
        await shop.locator('img[src*="demo043-"]').evaluateAll(async images => {
          await Promise.all(images.map(async img => { img.loading = 'eager'; await img.decode(); }));
        });
        await layout(shop);
        await shop.screenshot({ path: output(route === '/' ? 'shop-home.png' : 'shop-search.png'), fullPage: true });
      }
      await shop.setViewportSize({ width: 390, height: 960 });
      await layout(shop);
      await shop.screenshot({ path: output('shop-mobile.png') });
      await guest.close();
      report.checks.push('Guest marketplace and search render decoded demo images on desktop and mobile');
    }
    expect(report.errors).toEqual([]);
    fs.writeFileSync(output('results.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
