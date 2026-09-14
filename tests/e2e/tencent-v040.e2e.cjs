// Authorized HTTPS preview checks. No email, payment, messages or fixture writes.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'),
  path = require('node:path'),
  crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '../..');
const BASE = 'https://market.example.com';
const RUN = process.env.MAIMAI_042_RUN || Date.now().toString();
const manifest = JSON.parse(
  fs
    .readFileSync(
      path.join(ROOT, '.local/private/demo-accounts-040.json'),
      'utf8',
    )
    .replace(/^\uFEFF/, ''),
);
const report = {
  base: BASE,
  version: '0.4.0',
  assets: 0,
  publicViews: 0,
  accounts: [],
  checks: [],
  errors: [],
};
const out = (name) => path.join(ROOT, `.local/screenshots/042-${RUN}-${name}`);
const digest = (buffer) =>
  crypto.createHash('sha256').update(buffer).digest('hex');
async function post(context, route, data) {
  await context.request.get(BASE + '/api/v1/auth/csrf');
  const cookie = (await context.cookies(BASE)).find(
    (c) => c.name === 'XSRF-TOKEN',
  );
  return context.request.post(BASE + '/api/v1' + route, {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie?.value || '') },
    data,
  });
}
async function layout(page, name) {
  await expect
    .poll(() =>
      page.locator('img').evaluateAll((images) =>
        images
          .filter((i) => {
            const r = i.getBoundingClientRect();
            return r.top < innerHeight && r.bottom > 0 && r.width > 0;
          })
          .every((i) => i.complete && i.naturalWidth > 0),
      ),
    )
    .toBe(true);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth - innerWidth,
    ),
  ).toBeLessThanOrEqual(1);
  await expect(
    page.getByRole('button', { name: '打开缇娜客服', exact: true }),
  ).toBeVisible();
  await page.screenshot({ path: out(name + '.png') });
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const guest = await browser.newContext({
      viewport: { width: 1440, height: 1050 },
      locale: 'zh-CN',
    });
    for (const [route, status] of [
      ['/api/v1/dev/mailbox', 404],
      ['/api/v1/dev/mock-pay/confirm', 404],
      ['/api/v1/admin/users', 401],
      ['/api/v1/messages/conversations/overview', 401],
    ]) {
      expect((await guest.request.get(BASE + route)).status()).toBe(status);
      report.checks.push({ route, status });
    }
    const list = await (
      await guest.request.get(BASE + '/api/v1/products?size=50')
    ).json();
    expect(list.totalElements).toBe(65);
    expect(list.content.every((p) => p.stockAvailable === 0)).toBe(true);
    const firstProduct = list.content[0];
    for (const [image, asset] of new Map(
      manifest.products.map((p) => [p.imagePath, p.asset]),
    )) {
      const response = await guest.request.get(BASE + image);
      expect(response.status()).toBe(200);
      expect(response.headers()['content-type']).toContain('image/jpeg');
      expect(digest(await response.body())).toBe(
        digest(
          fs.readFileSync(
            path.join(ROOT, 'backend/src/main/resources/demo/products', asset),
          ),
        ),
      );
      report.assets++;
    }
    expect(
      (
        await (
          await guest.request.get(BASE + '/api/v1/support/assistant')
        ).json()
      ).enabled,
    ).toBe(false);
    const page = await guest.newPage();
    page.on('pageerror', (e) => report.errors.push(e.message));
    for (const width of [1440, 390]) {
      await page.setViewportSize({
        width,
        height: width === 1440 ? 1050 : 900,
      });
      for (const [name, route] of [
        ['home', '/'],
        ['search', '/search'],
        ['register', '/register'],
        ['official', '/official'],
      ]) {
        const response = await page.goto(BASE + route);
        expect(response.status()).toBe(200);
        expect(response.headers()['x-content-type-options']).toBe('nosniff');
        await expect(page.locator('#main-content h1').first()).toBeVisible();
        if (name === 'search' || name === 'home')
          await expect(page.locator('.mm-product-card').first()).toBeVisible();
        await layout(page, `${name}-${width}`);
        report.publicViews++;
      }
    }
    await page.goto(BASE + '/products/' + firstProduct.id);
    await expect(page.getByText('展示商品', { exact: true })).toBeVisible();
    await expect(
      page.getByRole('button', { name: '立即购买', exact: true }),
    ).toHaveCount(0);
    await layout(page, 'product-390');
    report.publicViews++;
    await guest.close();
    const accounts = [
      {
        email: manifest.adminEmail,
        role: 'SUPER_ADMIN',
        route: '/admin',
        zone: '管理后台',
        color: '#354b60',
      },
      ...manifest.sellers
        .slice(0, 2)
        .map((a) => ({
          ...a,
          role: 'SELLER',
          route: '/seller/products',
          zone: '卖家工作台',
          color: '#2d6554',
        })),
      ...manifest.buyers
        .slice(0, 2)
        .map((a) => ({
          ...a,
          role: 'USER',
          route: '/orders',
          zone: '买家交易',
          color: '#305f87',
        })),
    ];
    for (const [index, account] of accounts.entries()) {
      const context = await browser.newContext({
        viewport: { width: 1440, height: 1050 },
        locale: 'zh-CN',
      });
      const page = await context.newPage();
      page.on('pageerror', (e) => report.errors.push(e.message));
      await page.goto(
        BASE + '/login?redirect=' + encodeURIComponent(account.route),
      );
      await page.getByLabel('邮箱', { exact: true }).fill(account.email);
      await page.getByLabel('密码', { exact: true }).fill(manifest.password);
      await page.getByRole('button', { name: '登录', exact: true }).click();
      await expect(page).toHaveURL(BASE + account.route);
      await expect(page.locator('.mm-zone__title')).toHaveText(account.zone);
      expect(
        await page
          .locator('.mm-app')
          .evaluate((el) =>
            getComputedStyle(el).getPropertyValue('--mm-primary').trim(),
          ),
      ).toBe(account.color);
      const me = await (
        await context.request.get(BASE + '/api/v1/auth/me')
      ).json();
      expect(me.email === account.email).toBe(true);
      expect(me.roles).toContain(account.role);
      const cookie = (await context.cookies(BASE)).find(
        (c) => c.name === 'SESSION',
      );
      expect(
        cookie.secure && cookie.httpOnly && cookie.sameSite === 'Lax',
      ).toBe(true);
      expect(
        (await context.request.get(BASE + '/api/v1/admin/users')).status(),
      ).toBe(index === 0 ? 200 : 403);
      if (account.role === 'SELLER')
        await expect(page.locator('.mm-inventory__row')).toHaveCount(5);
      await layout(page, `account-${index + 1}`);
      if (index === 3) {
        expect(
          await (
            await context.request.get(
              BASE + '/api/v1/messages/conversations/overview',
            )
          ).json(),
        ).toEqual({
          conversations: 0,
          unreadConversations: 0,
          unreadMessages: 0,
        });
        const denied = await post(context, '/checkout', {
          idempotencyKey: 'preview-042-guard',
          items: [
            {
              productId: firstProduct.id,
              quantity: 1,
              deliveryMethod: 'MEETUP',
            },
          ],
          meetupLocation: '课程演示',
          meetupTime: new Date(Date.now() + 86400000).toISOString(),
        });
        expect(denied.status()).toBe(409);
        expect((await denied.json()).code).toBe('SELLER_NOT_QUALIFIED');
        report.checks.push({
          check: 'server rejects preview checkout before order creation',
          passed: true,
        });
        await page.goto(BASE + '/messages');
        await expect(
          page.getByText('还没有会话', { exact: true }),
        ).toBeVisible();
        await page
          .getByRole('button', { name: '打开缇娜客服', exact: true })
          .click();
        await page.getByRole('tab', { name: '问缇娜', exact: true }).click();
        await expect(
          page.getByText('缇娜暂未接通', { exact: true }),
        ).toBeVisible();
        await page
          .getByRole('button', { name: '关闭缇娜客服', exact: true })
          .click();
        await page.setViewportSize({ width: 390, height: 900 });
        await layout(page, 'inbox-390');
      }
      expect((await post(context, '/auth/logout')).status()).toBe(204);
      expect(
        (await context.request.get(BASE + '/api/v1/auth/me')).status(),
      ).toBe(401);
      report.accounts.push({
        index: index + 1,
        role: account.role,
        login: true,
        authorization: true,
        secureCookie: true,
        logout: true,
      });
      await context.close();
    }
    expect(report.errors).toEqual([]);
  } finally {
    fs.writeFileSync(out('results.json'), JSON.stringify(report, null, 2));
    await browser.close();
  }
  console.log(JSON.stringify(report));
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
