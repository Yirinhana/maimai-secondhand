// Real local API fixtures. This script intentionally cannot target production.
const { chromium } = require('@playwright/test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const BASE = 'http://127.0.0.1:5173';
const OUT = path.resolve(__dirname, '../../.local/screenshots/058');
fs.mkdirSync(OUT, { recursive: true });
async function api(c, method, url, data) {
  await c.request.get(BASE + '/api/v1/auth/csrf');
  const token = (await c.cookies()).find(
    (c) => c.name === 'XSRF-TOKEN',
  )?.value;
  const r = await c.request.fetch(BASE + '/api/v1' + url, {
    method,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token || '') },
    ...(data ? { data } : {}),
  });
  if (!r.ok())
    throw Error(`${method} ${url}: ${r.status()} ${await r.text()}`);
  return r.status() === 204 ? null : r.json();
}
(async () => {
  const browser = await chromium.launch({
    channel: 'chrome',
    headless: true,
  });
  const roles = {};
  for (const name of ['public', 'buyer', 'seller', 'admin']) {
    const cached = path.join(OUT, name + '-state.json');
    const c = await browser.newContext({
      baseURL: BASE,
      viewport: { width: 390, height: 844 },
      hasTouch: true,
      isMobile: true,
      locale: 'zh-CN',
      ...(name !== 'public' && fs.existsSync(cached)
        ? { storageState: cached }
        : {}),
    });
    await c.addInitScript(() =>
      sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
    );
    if (name !== 'public') {
      const check = await c.request.get(BASE + '/api/v1/auth/me');
      if (
        !check.ok() ||
        (await check.json()).email !== name + '@maimai.local'
      )
        await api(c, 'POST', '/auth/login', {
          email: name + '@maimai.local',
          password: 'Maimai#2026',
        });
      await c.storageState({ path: cached });
    }
    roles[name] = c;
  }
  const products = (await api(roles.public, 'GET', '/products?size=100'))
    .content;
  let product;
  for (const item of products.filter(
    (p) => p.stockAvailable >= 2 && p.deliveryMethods.includes('MEETUP'),
  )) {
    const detail = await api(roles.public, 'GET', '/products/' + item.id);
    if (detail.experienceSource === 'maimai-experience-045') {
      product = item;
      break;
    }
  }
  assert.ok(
    product,
    'Experience product with available stock is required',
  );
  const own = (await api(roles.seller, 'GET', '/seller/products?size=20'))
    .content[0];
  const official = (
    await api(roles.public, 'GET', '/official/articles?size=10')
  ).content[0];
  const seller = await api(roles.seller, 'GET', '/auth/me');
  const chat = await api(roles.buyer, 'POST', '/messages/conversations', {
    recipientId: seller.id,
  });
  let tickets = (await api(roles.buyer, 'GET', '/support/tickets?size=15'))
    .items;
  if (!tickets.length)
    tickets = [
      await api(roles.buyer, 'POST', '/support/tickets', {
        title: '移动端兼容性验证工单',
        body: '本地页面排版检查，不联系外部服务。',
        orderNo: null,
      }),
    ];
  const orders = (
    await api(roles.buyer, 'GET', '/orders?role=buyer&size=20')
  ).content;
  const after = (await api(roles.buyer, 'GET', '/me/aftersales?size=20'))
    .content;
  assert.ok(
    after.length,
    'A persisted local aftersale fixture is required',
  );
  const order = (
    await api(roles.buyer, 'POST', '/experience/checkout', {
      idempotencyKey: crypto.randomUUID(),
      items: [
        { productId: product.id, quantity: 1, deliveryMethod: 'MEETUP' },
      ],
      meetupLocation: '本地移动端验收公共广场，不实际交付',
      meetupTime: new Date(Date.now() + 86400000).toISOString(),
    })
  ).orders[0];
  const payment = await api(
    roles.buyer,
    'POST',
    `/orders/${order.orderNo}/experience-pay`,
  );
  console.log(
    JSON.stringify({
      product: product.id,
      own: own.id,
      official: official.slug,
      ticket: tickets[0].id,
      order: orders[0]?.orderNo,
      after: after[0]?.id,
      chat: chat.id,
    }),
  );
  const checkout =
    '/checkout?items=' +
    encodeURIComponent(
      JSON.stringify([
        { productId: product.id, quantity: 1, deliveryMethod: 'MEETUP' },
      ]),
    );
  const groups = {
    public: [
      '/welcome',
      '/',
      '/search',
      '/products/' + product.id,
      '/sellers/' + seller.id,
      '/official',
      '/official/' + official.slug,
      '/policies',
      '/support',
      '/community/demands',
      '/login',
      '/register',
      '/forgot',
      '/missing-mobile-page',
    ],
    buyer: [
      '/me/closure',
      '/support/tickets/' + tickets[0].id,
      '/me/community',
      '/me/bargains',
      '/cart',
      checkout,
      '/orders',
      '/orders/' + order.orderNo,
      '/me/aftersales',
      '/aftersales/' + after[0].id,
      '/me',
      '/messages',
      '/messages/' + chat.id,
      payment.checkoutPath,
    ],
    seller: [
      '/publish',
      '/publish/' + own.id,
      '/seller',
      '/seller/products',
      '/seller/bargains',
      '/seller/orders',
      '/seller/aftersales',
    ],
    admin: [
      '/admin',
      '/admin/official',
      '/admin/categories',
      '/admin/finance',
      '/admin/trade-todos',
      '/admin/support',
      '/admin/community',
      '/admin/seller-apps',
      '/admin/products',
      '/admin/aftersales',
      '/admin/users',
      '/admin/audit-logs',
    ],
  };
  const patterns = [
    ...fs
      .readFileSync(
        path.resolve(__dirname, '../../frontend/src/router/index.ts'),
        'utf8',
      )
      .matchAll(/path: '([^']*)'/g),
  ].map((m) =>
    m[1].startsWith('/') ? m[1] : '/admin' + (m[1] ? '/' + m[1] : ''),
  );
  const paths = Object.values(groups)
    .flat()
    .map((p) => p.split('?')[0]);
  for (const p of new Set(patterns)) {
    if (p.includes('pathMatch')) {
      assert.ok(paths.includes('/missing-mobile-page'));
      continue;
    }
    assert.ok(
      paths.some((value) =>
        new RegExp('^' + p.replace(/:[^/]+/g, '[^/]+') + '$').test(value),
      ),
      'Missing route coverage: ' + p,
    );
  }
  const results = [];
  for (const [role, routes] of Object.entries(groups)) {
    const page = await roles[role].newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    for (const route of routes) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      const title = await page.locator('h1').first().textContent();
      assert.equal(
        new URL(page.url()).pathname,
        route.split('?')[0],
        'Unexpected redirect: ' + route,
      );
      for (const [width, height] of [
        [320, 740],
        [390, 844],
        [430, 932],
        [844, 390],
      ]) {
        await page.setViewportSize({ width, height });
        const metrics = await page.evaluate(() => {
          const visible = (e) =>
            e.getClientRects().length &&
            getComputedStyle(e).visibility !== 'hidden';
          return {
            width: innerWidth,
            root: document.documentElement.scrollWidth,
            body: document.body.scrollWidth,
            smallInputs: [
              ...document.querySelectorAll(
                'input:not([type=hidden]):not([type=checkbox]):not([type=radio]),textarea,select',
              ),
            ]
              .filter(
                (e) =>
                  visible(e) &&
                  parseFloat(getComputedStyle(e).fontSize) < 16,
              )
              .map((e) => ({
                tag: e.tagName,
                cls: e.className,
                type: e.type,
                font: getComputedStyle(e).fontSize,
              })),
            overflow: [...document.querySelectorAll('main *')]
              .filter(
                (e) =>
                  visible(e) &&
                  e.getBoundingClientRect().right > innerWidth + 1,
              )
              .slice(0, 10)
              .map((e) => ({
                tag: e.tagName,
                cls: e.className,
                text: e.textContent.slice(0, 30),
              })),
          };
        });
        results.push({
          role,
          route,
          title,
          width,
          height,
          ...metrics,
          errors: [...errors],
        });
        if (width === 390)
          await page.screenshot({
            path: path.join(
              OUT,
              role +
                '-' +
                route.split('?')[0].replaceAll('/', '_') +
                '.png',
            ),
          });
      }
      console.log(
        role,
        route.split('?')[0],
        results
          .slice(-4)
          .filter((r) => r.body > r.width || r.root > r.width)
          .map((r) => r.width),
      );
      fs.writeFileSync(
        path.join(OUT, 'audit.json'),
        JSON.stringify(results, null, 2),
      );
    }
    await page.close();
  }
  const interactions = [];
  async function inspect(page, name) {
    for (const [width, height] of [
      [320, 740],
      [390, 844],
      [844, 390],
    ]) {
      await page.setViewportSize({ width, height });
      const info = await page.evaluate(() => {
        const visible = (e) =>
          e.getClientRects().length &&
          getComputedStyle(e).visibility !== 'hidden';
        return {
          width: innerWidth,
          body: document.body.scrollWidth,
          root: document.documentElement.scrollWidth,
          dialogs: [...document.querySelectorAll('dialog[open]')].map(
            (d) => {
              const r = d.getBoundingClientRect();
              return {
                left: r.left,
                right: r.right,
                top: r.top,
                bottom: r.bottom,
                viewport: innerHeight,
                overflow: d.scrollWidth > d.clientWidth + 1,
              };
            },
          ),
          small: [
            ...document.querySelectorAll(
              'input:not([type=checkbox]):not([type=radio]):not([type=hidden]),textarea,select',
            ),
          ]
            .filter(
              (e) =>
                visible(e) &&
                parseFloat(getComputedStyle(e).fontSize) < 16,
            )
            .map((e) => e.className),
        };
      });
      interactions.push({ name, width, height, ...info });
      if (width === 390)
        await page.screenshot({
          path: path.join(OUT, 'interaction-' + name + '.png'),
        });
    }
    console.log('interaction', name);
  }
  const buyerPage = await roles.buyer.newPage();
  await buyerPage.goto('/me');
  await buyerPage.waitForLoadState('networkidle');
  for (const name of ['基本资料', '收货地址', '卖家入驻', '站内通知']) {
    await buyerPage
      .getByRole('tab', { name: new RegExp('^' + name) })
      .click();
    await buyerPage.waitForLoadState('networkidle');
    await inspect(buyerPage, 'account-' + name);
    if (name === '收货地址') {
      await buyerPage
        .getByRole('button', { name: '新增地址', exact: true })
        .click();
      await inspect(buyerPage, 'address-editor');
    }
  }
  await buyerPage.goto('/community/demands');
  await buyerPage.waitForLoadState('networkidle');
  await buyerPage
    .getByRole('button', { name: '发布求购', exact: true })
    .click();
  await inspect(buyerPage, 'demand-editor');
  await buyerPage
    .getByRole('button', { name: '查看回复', exact: true })
    .first()
    .click();
  await inspect(buyerPage, 'demand-replies');
  await buyerPage.goto('/products/' + product.id);
  await buyerPage.waitForLoadState('networkidle');
  await buyerPage
    .getByRole('button', { name: '放大查看商品图片' })
    .click();
  await inspect(buyerPage, 'gallery-dialog');
  await buyerPage.keyboard.press('Escape');
  await buyerPage
    .getByRole('button', { name: '议价', exact: true })
    .click();
  await inspect(buyerPage, 'bargain-dialog');
  await buyerPage.keyboard.press('Escape');
  await buyerPage
    .getByRole('button', { name: '举报', exact: true })
    .first()
    .click();
  await inspect(buyerPage, 'product-report');
  await buyerPage.goto('/support');
  await buyerPage.waitForLoadState('networkidle');
  await buyerPage
    .getByRole('button', { name: '联系人工客服', exact: true })
    .click();
  await inspect(buyerPage, 'support-editor');
  await buyerPage.locator('.tina-launcher').click();
  await buyerPage.locator('#tina-dialog[open]').waitFor();
  await inspect(buyerPage, 'assistant-dialog');
  await buyerPage
    .getByRole('tab', { name: '常见问题', exact: true })
    .click();
  await inspect(buyerPage, 'assistant-faq');
  await buyerPage
    .getByRole('tab', { name: '问麦仔', exact: true })
    .click();
  await buyerPage.setViewportSize({ width: 390, height: 844 });
  await buyerPage.evaluate(() => {
    Object.defineProperty(visualViewport, 'height', {
      configurable: true,
      value: 380,
    });
    Object.defineProperty(visualViewport, 'offsetTop', {
      configurable: true,
      value: 120,
    });
    visualViewport.dispatchEvent(new Event('resize'));
  });
  await buyerPage.locator('.tina-dialog--compact').waitFor();
  const keyboard = await buyerPage.locator('.tina-compose').boundingBox();
  assert.ok(
    keyboard.y >= 120 && keyboard.y + keyboard.height <= 500,
    'Assistant composer hidden by keyboard',
  );
  await buyerPage.screenshot({
    path: path.join(OUT, 'assistant-keyboard.png'),
  });
  await buyerPage.evaluate(() => {
    delete visualViewport.height;
    delete visualViewport.offsetTop;
    visualViewport.dispatchEvent(new Event('resize'));
  });
  await buyerPage.getByRole('button', { name: '关闭麦仔客服' }).click();
  await buyerPage.goto('/me/closure');
  await buyerPage.waitForLoadState('networkidle');
  await buyerPage.getByLabel('当前密码').fill('NOT_SUBMITTED');
  await buyerPage
    .getByLabel('申请原因')
    .fill('仅检验确认窗口排版，取消操作');
  await buyerPage.getByRole('checkbox').check();
  await buyerPage
    .getByRole('button', { name: '申请注销', exact: true })
    .click();
  await buyerPage.locator('.mm-confirm-dialog[open]').waitFor();
  await inspect(buyerPage, 'confirmation-dialog');
  await buyerPage.getByRole('button', { name: '暂不操作' }).click();
  const adminPage = await roles.admin.newPage();
  for (const [route, button] of [
    ['/admin/categories', '新增分类'],
    ['/admin/official', '新建官方内容'],
  ]) {
    await adminPage.goto(route);
    await adminPage.waitForLoadState('networkidle');
    await adminPage
      .getByRole('button', { name: button, exact: true })
      .click();
    await inspect(adminPage, button);
  }
  await adminPage.goto('/admin/community');
  await adminPage.waitForLoadState('networkidle');
  for (const name of ['求购审核', '回复审核', '举报处理']) {
    await adminPage.getByRole('button', { name, exact: true }).click();
    await adminPage.waitForLoadState('networkidle');
    await inspect(adminPage, name);
  }
  await buyerPage.close();
  await adminPage.close();
  // Release stock through the normal cancellation flow; never delete fixture data.
  await api(roles.buyer, 'POST', `/orders/${order.orderNo}/cancel`);
  fs.writeFileSync(
    path.join(OUT, 'interactions.json'),
    JSON.stringify(interactions, null, 2),
  );
  for (const [role, c] of Object.entries(roles))
    await c.storageState({ path: path.join(OUT, role + '-state.json') });
  fs.writeFileSync(
    path.join(OUT, 'routes.json'),
    JSON.stringify(groups, null, 2),
  );
  await browser.close();
  const issues = results.filter(
    (r) =>
      r.body > r.width ||
      r.root > r.width ||
      r.smallInputs.length ||
      r.errors.length,
  );
  const dialogIssues = interactions.filter(
    (r) =>
      r.body > r.width ||
      r.root > r.width ||
      r.small.length ||
      r.dialogs.some(
        (d) =>
          d.left < 0 ||
          d.right > r.width + 1 ||
          d.top < 0 ||
          d.bottom > d.viewport + 1 ||
          d.overflow,
      ),
  );
  assert.deepEqual(issues, [], 'Route compatibility failures');
  assert.deepEqual(
    dialogIssues,
    [],
    'Expanded component compatibility failures',
  );
  console.log(
    `PASS ${paths.length} routes, ${results.length} viewport checks, ${interactions.length} expanded-state checks, keyboard viewport verified`,
  );
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
