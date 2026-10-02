// Explicitly scoped to the authorized preview server. No real payment, email,
// product edits or fixture users are created. Credentials never enter reports.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const BASE = process.env.MAIMAI_E2E_ALLOWED_ORIGIN;
if (!BASE || !BASE.startsWith('https://')) throw Error('Set MAIMAI_E2E_ALLOWED_ORIGIN to your explicitly approved HTTPS test site');
const ROOT = path.resolve(__dirname, '../..');
const OUT = path.join(ROOT, '.local/screenshots');
const secretFile = process.env.MAIMAI_PREVIEW_ADMIN_FILE || path.join(ROOT, '.local/private/tencent-preview-admin.json');
const credentials = JSON.parse(fs.readFileSync(secretFile, 'utf8'));
if (credentials.url !== BASE || credentials.email !== 'admin@maimai.invalid') throw Error('Unexpected preview credentials target');
const evidence = { base: BASE, views: [], assets: [], checks: [], errors: [] };
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
async function post(context, endpoint, data) {
  await context.request.get(BASE + '/api/v1/auth/csrf');
  const cookie = (await context.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN');
  return context.request.post(BASE + endpoint, { headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie?.value || '') }, data });
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN' });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.errors.push(error.message));
    for (const [route, status] of [['/api/v1/dev/mailbox', 404], ['/api/v1/admin/users', 401]]) {
      expect((await context.request.get(BASE + route)).status()).toBe(status);
      evidence.checks.push({ route, status });
    }
    const list = await (await context.request.get(BASE + '/api/v1/products')).json();
    expect(list.totalElements).toBe(5);
    const names = { 1: 'iphone-blue', 2: 'headphones-charcoal', 3: 'java-textbook', 4: 'wool-coat', 5: 'badminton-racket' };
    for (const product of list.content) {
      expect(product.stockAvailable).toBe(0);
      const response = await context.request.get(BASE + product.coverImage);
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('image/jpeg');
      const actual = hash(await response.body());
      expect(actual).toBe(hash(fs.readFileSync(path.join(ROOT, `backend/src/main/resources/demo/products/${names[product.id]}.jpg`))));
      evidence.assets.push({ id: product.id, sha256: actual });
    }
    for (const width of [1440, 390, 360]) {
      await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
      for (const [name, route] of [['home', '/'], ['search', '/search'], ['detail', '/products/5'], ['register', '/register'], ['official', '/official']]) {
        const response = await page.goto(BASE + route, { waitUntil: 'networkidle' });
        expect(response.status()).toBe(200);
        expect(response.headers()['x-content-type-options']).toBe('nosniff');
        await expect(page.locator('#main-content h1').first()).toBeVisible();
        await page.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(i => i.decode().catch(() => {}))); });
        if (['home', 'search'].includes(name)) {
          await expect(page.locator('.mm-product-card')).toHaveCount(5);
          await expect(page.locator('.mm-product-card__demo')).toHaveCount(5);
        }
        if (name === 'detail') {
          await expect(page.getByText('展示商品', { exact: true })).toBeVisible();
          await expect(page.getByRole('button', { name: '立即购买', exact: true })).toHaveCount(0);
          await expect(page.getByText('演示示意图 · 非卖家实拍', { exact: true })).toBeVisible();
        }
        if (name === 'register') {
          const image = page.locator('.registration-visual img');
          await expect(image).toBeVisible();
          expect(await image.evaluate(i => i.naturalWidth)).toBe(1024);
        }
        if (name === 'official') await expect(page.getByText('在线体验版本说明', { exact: true })).toBeVisible();
        const dimensions = await page.evaluate(() => ({ body: document.body.scrollWidth, document: document.documentElement.scrollWidth }));
        expect(dimensions.body).toBeLessThanOrEqual(width);
        expect(dimensions.document).toBeLessThanOrEqual(width);
        const screenshot = `038-${name}-${width}.png`;
        await page.screenshot({ path: path.join(OUT, screenshot), fullPage: true });
        evidence.views.push({ name, width, screenshot, ...dimensions });
      }
    }
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(BASE + '/login');
    await page.getByLabel('邮箱', { exact: true }).fill(credentials.email);
    await page.getByLabel('密码', { exact: true }).fill(credentials.password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page).toHaveURL(BASE + '/');
    const me = await (await context.request.get(BASE + '/api/v1/auth/me')).json();
    expect(me.roles).toContain('SUPER_ADMIN');
    const session = (await context.cookies(BASE)).find(c => c.name === 'SESSION');
    expect(session.httpOnly && session.secure && session.sameSite === 'Lax').toBe(true);
    evidence.checks.push({ check: 'administrator UI login and secure session cookie', passed: true });
    expect((await context.request.get(BASE + '/api/v1/admin/users')).status()).toBe(200);
    const blocked = await post(context, '/api/v1/checkout', { idempotencyKey: 'preview-038-guard', items: [{ productId: 1, quantity: 1, deliveryMethod: 'MEETUP' }], meetupLocation: '课程演示', meetupTime: '2026-09-15T04:00:00Z' });
    expect(blocked.status()).toBe(409);
    expect((await blocked.json()).code).toBe('SELLER_INACTIVE');
    evidence.checks.push({ check: 'showcase checkout blocked by server', passed: true });
    const config = await (await context.request.get(BASE + '/api/v1/maps/js-config')).json();
    expect(config.enabled).toBe(true);
    // This is a JSAPI key. Use the real SDK's request shape rather than a
    // handcrafted Web Service request, which the provider correctly rejects.
    await page.goto(BASE + '/me');
    await page.getByRole('tab', { name: '收货地址', exact: true }).click();
    await page.getByRole('button', { name: '新增地址', exact: true }).click();
    await page.getByRole('button', { name: '使用地图辅助选址', exact: true }).click();
    await page.getByRole('button', { name: '搜索地点', exact: true }).waitFor({ timeout: 30000 });
    await page.getByLabel('搜索城市或地点', { exact: true }).fill('上海人民广场');
    await page.getByRole('button', { name: '搜索地点', exact: true }).click();
    const place = page.getByRole('button').filter({ hasText: /人民广场.*·/ }).first();
    await expect(place).toBeVisible({ timeout: 30000 });
    await place.click();
    const useAddress = page.getByRole('button', { name: '使用此地址', exact: true });
    await expect(useAddress).toBeEnabled({ timeout: 30000 });
    await useAddress.click();
    await expect(page.getByLabel('详细地址', { exact: true })).toHaveValue(/上海/);
    await page.screenshot({ path: path.join(OUT, '038-real-map.png'), fullPage: true });
    evidence.checks.push({ check: 'AMap JS SDK live search and reverse geocode; address not saved', passed: true });
    const logout = await post(context, '/api/v1/auth/logout');
    expect(logout.status()).toBe(204);
    expect((await context.request.get(BASE + '/api/v1/auth/me')).status()).toBe(401);
    evidence.checks.push({ check: 'logout invalidates session', passed: true });
    expect(evidence.errors).toEqual([]);
  } finally {
    fs.writeFileSync(path.join(OUT, '038-preview-check.json'), JSON.stringify(evidence, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify({ assets: evidence.assets.length, views: evidence.views.length, checks: evidence.checks, errors: evidence.errors }));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
