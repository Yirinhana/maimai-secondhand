// Runs a no-money transaction with authorized experience accounts, then refunds it to restore stock.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'), path = require('node:path');
const ROOT = path.resolve(__dirname, '../..');
const BASE = process.env.MAIMAI_046_BASE || 'http://127.0.0.1:5173';
if (!['http://127.0.0.1:5173', 'https://market.example.com'].includes(BASE)) throw Error('Unapproved host');
const accounts = JSON.parse(fs.readFileSync(path.join(ROOT, '.local/private/demo-accounts-040.json'), 'utf8').replace(/^\uFEFF/, ''));
const run = Date.now(), report = { base: BASE, checks: [], screenshots: [], errors: [], orders: [] };
async function read(ctx, route) { const r = await ctx.request.get(BASE + '/api/v1' + route); expect(r.ok(), `${route}: ${r.status()}`).toBe(true); return r.json(); }
async function write(ctx, route, data, expected = 200) {
  await ctx.request.get(BASE + '/api/v1/auth/csrf');
  const csrf = (await ctx.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN');
  const r = await ctx.request.post(BASE + '/api/v1' + route, { data, headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) } });
  expect(r.status(), `${route}: ${r.status()} ${r.status() !== expected ? await r.text() : ''}`).toBe(expected);
  return expected === 204 || expected >= 400 ? null : r.json();
}
async function login(ctx, email) { await write(ctx, '/auth/login', { email, password: accounts.password }); return read(ctx, '/auth/me'); }
async function screenshot(page, name) {
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  const file = path.join(ROOT, `.local/screenshots/046-${run}-${name}.png`);
  await page.screenshot({ path: file, fullPage: true }); report.screenshots.push(file);
}
function inspect(page) { page.on('pageerror', e => report.errors.push(e.message)); }
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const desktop = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    const seller = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const guest = await browser.newContext();
    await login(desktop, accounts.buyers[0].email);
    const sm = await login(seller, accounts.sellers[0].email);
    const rows = await read(desktop, `/sellers/${sm.id}/products?size=100`);
    const row = rows.content.find(p => p.deliveryMethods.includes('MEETUP') && p.stockAvailable > 0);
    expect(row).toBeTruthy();
    const product = await read(desktop, `/products/${row.id}`);
    expect(product.experienceSource).toBe('maimai-experience-045');
    const dp = await desktop.newPage(), pp = await phone.newPage(); inspect(dp); inspect(pp);
    await dp.goto(BASE + `/products/${row.id}`);
    if (product.deliveryMethods.length > 1) await dp.getByLabel('交付方式').selectOption('MEETUP');
    await dp.getByRole('button', { name: '体验下单', exact: true }).click();
    await expect(dp).toHaveURL(/\/checkout\?/);
    await dp.getByLabel('面交地点', { exact: true }).fill('上海图书馆正门（体验约定，不安排交付）');
    const time = new Date(Date.now() + 86400000); time.setMinutes(0);
    const localTime = new Date(time.getTime() - time.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    await dp.getByLabel('面交时间', { exact: true }).fill(localTime);
    await expect(dp.getByRole('button', { name: '提交体验订单', exact: true })).toBeDisabled();
    await dp.getByRole('checkbox', { name: /我了解这是体验订单/ }).check();
    await screenshot(dp, 'checkout-desktop');
    await dp.getByRole('button', { name: '提交体验订单', exact: true }).click();
    await expect(dp).toHaveURL(/\/orders\/MM/, { timeout: 15000 });
    const orderNo = new URL(dp.url()).pathname.split('/').pop(); report.orders.push(orderNo);
    const order = await read(desktop, `/orders/${orderNo}`);
    expect(order.experienceSource).toBe('maimai-experience-checkout-v1'); expect(order.simulated).toBe(true);
    await dp.getByRole('button', { name: '生成订单二维码', exact: true }).click();
    await expect(dp.getByAltText('本订单体验收银页二维码')).toBeVisible();
    await expect.poll(() => dp.getByAltText('本订单体验收银页二维码').evaluate(i => i.complete && i.naturalWidth === 320)).toBe(true);
    const first = await write(desktop, `/orders/${orderNo}/experience-pay`, {});
    expect(first.amountCents).toBe(order.totalCents);
    const qr = await desktop.request.get(BASE + first.qrPath);
    expect(qr.headers()['content-type']).toContain('image/png'); expect(qr.headers()['cache-control']).toContain('no-store');
    expect((await guest.request.get(BASE + '/api/v1/experience-pay/' + first.token)).status()).toBe(401);
    expect((await seller.request.get(BASE + first.qrPath)).status()).toBe(403);
    await write(seller, `/experience-pay/${first.token}/result`, { result: 'SUCCESS' }, 403);
    const missingCsrf = await desktop.request.post(BASE + `/api/v1/experience-pay/${first.token}/result`, { data: { result: 'SUCCESS' } });
    expect(missingCsrf.status()).toBe(403);
    // The dev controller does not exist in production; local profile explicitly rejects this channel.
    await write(desktop, '/dev/mock-pay/confirm', { payNo: first.payNo, amountCents: first.amountCents }, BASE.includes('127.0.0.1') ? 403 : 404);
    report.checks.push('Experience-only checkout, consent, private QR, owner enforcement and CSRF protection');
    await screenshot(dp, 'qr-desktop');
    // A second device scans the QR URL while signed out, then returns after login as the same buyer.
    await pp.goto(BASE + first.checkoutPath); await expect(pp).toHaveURL(/\/login\?redirect=/);
    await pp.getByLabel('邮箱', { exact: true }).fill(accounts.buyers[0].email);
    await pp.getByLabel('密码', { exact: true }).fill(accounts.password);
    await pp.getByRole('button', { name: '登录', exact: true }).click();
    await expect(pp).toHaveURL(BASE + first.checkoutPath, { timeout: 15000 });
    await expect(pp.getByRole('heading', { name: '核对订单，继续体验' })).toBeVisible();
    await screenshot(pp, 'cashier-mobile');
    await pp.getByRole('button', { name: '取消本次付款', exact: true }).click();
    await expect(pp.getByRole('heading', { name: '已取消本次付款' })).toBeVisible();
    await expect(dp.getByRole('button', { name: '重新生成二维码' })).toBeVisible({ timeout: 10000 });
    await write(desktop, `/experience-pay/${first.token}/result`, { result: 'SUCCESS' }, 409);
    await dp.getByRole('button', { name: '重新生成二维码' }).click();
    const second = await write(desktop, `/orders/${orderNo}/experience-pay`, {}); expect(second.token).not.toBe(first.token);
    await pp.goto(BASE + second.checkoutPath); await pp.getByText('体验其他付款结果', { exact: true }).click();
    await pp.getByRole('button', { name: '体验支付失败', exact: true }).click();
    await expect(pp.getByRole('heading', { name: '本次付款未成功' })).toBeVisible();
    await expect(dp.getByRole('button', { name: '重新生成二维码' })).toBeVisible({ timeout: 10000 });
    await dp.getByRole('button', { name: '重新生成二维码' }).click();
    const third = await write(desktop, `/orders/${orderNo}/experience-pay`, {});
    await pp.goto(BASE + third.checkoutPath);
    await pp.getByRole('button', { name: '确认体验付款（不扣款）', exact: true }).click();
    await expect(pp.getByRole('heading', { name: '体验付款成功' })).toBeVisible();
    await expect(dp.getByRole('button', { name: '申请售后', exact: true })).toBeVisible({ timeout: 10000 });
    expect((await write(desktop, `/experience-pay/${third.token}/result`, { result: 'SUCCESS' })).status).toBe('PAID');
    expect((await read(desktop, `/products/${row.id}`)).stockAvailable).toBe(product.stockAvailable - 1);
    await screenshot(pp, 'paid-mobile');
    report.checks.push('Phone login returns to QR order; cancellation, failed payment, new QR retry, success and desktop synchronization');
    await dp.getByRole('button', { name: '申请售后', exact: true }).click();
    await dp.getByLabel('原因', { exact: true }).fill('扫码体验验收：申请退款恢复体验库存，不发生真实资金');
    await dp.getByRole('button', { name: '提交申请', exact: true }).click();
    await expect(dp).toHaveURL(/\/aftersales\/\d+/, { timeout: 15000 });
    const aftersaleId = new URL(dp.url()).pathname.split('/').pop();
    await expect(dp.getByText(/体验售后：退款只更新体验记录/)).toBeVisible();
    await write(seller, `/seller/aftersales/${aftersaleId}/respond`, { agree: true, reply: '同意体验退款，未收取任何真实款项' });
    await pp.reload(); await expect(pp.getByRole('heading', { name: '体验退款已完成' })).toBeVisible();
    await screenshot(pp, 'refunded-mobile');
    expect((await read(desktop, `/products/${row.id}`)).stockAvailable).toBe(product.stockAvailable);
    expect((await read(desktop, `/orders/${orderNo}`)).refundStatus).toBe('FULL');
    report.checks.push('Buyer aftersale request, seller approval, persisted no-money refund and restored inventory');
    // Existing, imported history never acquires a payable QR.
    const mine = await read(desktop, '/orders?size=100');
    const imported = mine.content.find(o => o.experienceSource === 'maimai-experience-045');
    if (imported) await write(desktop, `/orders/${imported.orderNo}/experience-pay`, {}, 403);
    expect(report.errors).toEqual([]);
  } finally {
    if (report.checks.length < 3) for (const ctx of browser.contexts()) for (const page of ctx.pages()) {
      if (page.url().includes(BASE) && !page.url().includes('/login')) {
        await page.screenshot({ path: path.join(ROOT, `.local/screenshots/046-${run}-failure-${report.screenshots.length}.png`), fullPage: true }).catch(() => {});
        report.screenshots.push(page.url());
      }
    }
    fs.writeFileSync(path.join(ROOT, `.local/046-browser-${BASE.includes('127.0.0.1') ? 'local' : 'public'}.json`), JSON.stringify(report, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify(report));
})().catch(e => { console.error(e); process.exitCode = 1; });
