// Authorized experience accounts only. Preserve prior follows; never create shipments or payments.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'), path = require('node:path');
const ROOT = path.resolve(__dirname, '../..');
const BASE = process.env.MAIMAI_047_BASE || 'http://127.0.0.1:5173';
if (!['http://127.0.0.1:5173', process.env.MAIMAI_E2E_ALLOWED_ORIGIN].includes(BASE)) throw Error('Unapproved host');
const accounts = JSON.parse(fs.readFileSync(path.join(ROOT, '.local/private/demo-accounts-040.json'), 'utf8').replace(/^\uFEFF/, ''));
const report = { base: BASE, sellers: [], orders: [], checks: [], screenshots: [], errors: [] }, run = Date.now();
let lastLogin = 0;
async function read(ctx, route) {
  const r = await ctx.request.get(BASE + '/api/v1' + route);
  expect(r.ok(), route + ': ' + r.status()).toBe(true); return r.json();
}
async function mutate(ctx, method, route, data, status) {
  await ctx.request.get(BASE + '/api/v1/auth/csrf');
  const csrf = (await ctx.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN');
  const r = await ctx.request.fetch(BASE + '/api/v1' + route, { method, data, headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) } });
  expect(r.status(), route).toBe(status);
}
async function login(ctx, email) {
  await new Promise(resolve => setTimeout(resolve, Math.max(0, 7100 - (Date.now() - lastLogin))));
  lastLogin = Date.now();
  await mutate(ctx, 'POST', '/auth/login', { email, password: accounts.password }, 200);
  return read(ctx, '/auth/me');
}
async function capture(page, name) {
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  const file = path.join(ROOT, `.local/screenshots/047-${run}-${name}.png`);
  await page.screenshot({ path: file, fullPage: true }); report.screenshots.push(file);
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const buyer = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const guest = await browser.newContext();
  let followed = null;
  try {
    for (const account of accounts.sellers) {
      const ctx = await browser.newContext();
      const me = await login(ctx, account.email);
      expect(me.sellerStatus).toBe('APPROVED'); expect(me.roles).toContain('SELLER');
      const application = await read(ctx, '/me/seller-application');
      expect(application.status).toBe('APPROVED');
      const products = await read(ctx, '/seller/products?size=100');
      expect(products.content.length).toBeGreaterThan(0);
      expect((await read(ctx, '/sellers/' + me.id)).sellerApproved).toBe(true);
      report.sellers.push({ id: me.id, status: me.sellerStatus, channelStatus: application.channelStatus, products: products.content.length });
      await ctx.close();
    }
    report.checks.push('All 12 existing seller logins, applications, roles, public profiles and private product lists agree');
    console.log('SELLERS_VERIFIED', report.sellers.length);
    const me = await login(buyer, accounts.buyers[0].email);
    const page = await buyer.newPage(); page.on('pageerror', e => report.errors.push(e.message));
    const sellerId = report.sellers[0].id;
    const follows = await read(buyer, '/community/follows?size=100');
    const existing = follows.items.some(f => f.sellerId === sellerId);
    await page.goto(BASE + '/sellers/' + sellerId);
    await expect(page.getByText('平台卖家审核已通过', { exact: true })).toBeVisible();
    if (!existing) followed = sellerId;
    await page.getByRole('button', { name: '关注卖家', exact: true }).click();
    await expect(page.getByText('已关注，可在我的社区管理', { exact: true })).toBeVisible();
    await capture(page, 'seller-profile');
    await page.goto(BASE + '/sellers/' + me.id);
    await expect(page.getByText('用户主页', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '关注卖家', exact: true })).toHaveCount(0);
    // Check the same ordinary buyer profile while signed out: hidden because of qualification, not self identity.
    const gp = await guest.newPage(); await gp.goto(BASE + '/sellers/' + me.id);
    await expect(gp.getByText('用户主页', { exact: true })).toBeVisible();
    await expect(gp.getByRole('button', { name: '关注卖家', exact: true })).toHaveCount(0);
    report.checks.push('Approved seller can be followed; ordinary user is not presented as an approved seller');
    const meetup = await read(buyer, '/orders/MX045001');
    expect(meetup.buyerId).toBe(me.id); expect(meetup.meetupLocation).toContain('虚拟地点');
    expect(meetup.meetupTime).toBeTruthy(); expect(meetup.deliveryMethod).toBe('MEETUP');
    expect((await read(buyer, '/products/' + meetup.items[0].productId)).seller.id).toBe(meetup.sellerId);
    await page.goto(BASE + '/orders/MX045001');
    await expect(page.getByText(meetup.meetupLocation, { exact: true })).toBeVisible();
    await expect(page.getByText(/体验交付资料/)).toBeVisible();
    await capture(page, 'meetup-desktop'); report.orders.push(meetup.orderNo);
    // MX045011 belongs to the eleventh fixture buyer and its Fuzhou product supports express only.
    const expressBuyer = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const eb = await login(expressBuyer, accounts.buyers[10].email);
    const express = await read(expressBuyer, '/orders/MX045011');
    expect(express.buyerId).toBe(eb.id); expect(express.deliveryMethod).toBe('EXPRESS');
    expect(express.receiver).toBe(eb.nickname); expect(express.phone).toBe('00000000000'); expect(express.addressDetail).toContain('虚拟地址');
    const shipment = await read(expressBuyer, '/orders/MX045011/shipment');
    expect(shipment.status).toBe('DELIVERED'); expect(shipment.traces).toContain('收件人已确认签收');
    expect(shipment.queryErrorCode).toBe('EXPERIENCE_NO_TRACKING'); expect(shipment.lastQueryAttemptAt).toBeNull();
    const ep = await expressBuyer.newPage(); ep.on('pageerror', e => report.errors.push(e.message));
    await ep.goto(BASE + '/orders/MX045011');
    await expect(ep.getByText(shipment.trackingNo, { exact: false })).toBeVisible();
    await expect(ep.getByText(/收件人已确认签收/)).toBeVisible();
    await expect(ep.getByText(/虚拟地址/)).toBeVisible();
    await capture(ep, 'express-mobile'); report.orders.push(express.orderNo);
    expect((await guest.request.get(BASE + '/api/v1/orders/MX045011/shipment')).status()).toBe(401);
    expect([403, 404]).toContain((await buyer.request.get(BASE + '/api/v1/orders/MX045011/shipment')).status());
    report.checks.push('Linked buyer/product/seller, full meetup and express details, private virtual traces and responsive rendering');
    expect(report.errors).toEqual([]);
  } finally {
    if (followed !== null) await mutate(buyer, 'DELETE', '/community/follows/' + followed, undefined, 204);
    fs.writeFileSync(path.join(ROOT, `.local/047-browser-${BASE.includes('127.0.0.1') ? 'local' : 'public'}.json`), JSON.stringify(report, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify(report));
})().catch(e => { console.error(e); process.exitCode = 1; });
