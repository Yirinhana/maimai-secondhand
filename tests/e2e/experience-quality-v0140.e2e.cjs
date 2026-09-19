const { test, expect, request } = require('@playwright/test');
const path = require('path');
const BASE = 'http://127.0.0.1:5173';
const OUT = path.resolve(__dirname, '../../.local/screenshots/057');
const RUN = Date.now().toString();
async function api(context, method, endpoint, data) {
  if (!endpoint.startsWith('/api/v1/'))
    throw Error('Local application only');
  await context.request.get(BASE + '/api/v1/auth/csrf');
  const token = (await context.cookies(BASE)).find(
    (c) => c.name === 'XSRF-TOKEN',
  )?.value;
  const response = await context.request.fetch(BASE + endpoint, {
    method,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token || '') },
    ...(data === undefined ? {} : { data }),
  });
  expect(
    response.ok(),
    method + ' ' + endpoint + ': ' + response.status(),
  ).toBeTruthy();
  const body = await response.text();
  return body ? JSON.parse(body) : null;
}
const sessions = new Map();
async function actor(name) {
  if (sessions.has(name)) return sessions.get(name);
  const other = await request.newContext({ baseURL: BASE });
  await other.get('/api/v1/auth/csrf');
  const cookie = (await other.storageState()).cookies.find(
    (c) => c.name === 'XSRF-TOKEN',
  );
  const result = await other.post('/api/v1/auth/login', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(cookie?.value || '') },
    data: { email: name + '@maimai.local', password: 'Maimai#2026' },
  });
  expect(
    result.ok(),
    'login ' + name + ': ' + result.status(),
  ).toBeTruthy();
  const me = await (await other.get('/api/v1/auth/me')).json();
  const state = await other.storageState();
  await other.dispose();
  expect(me.id).toBeGreaterThan(0);
  const session = { me, state };
  sessions.set(name, session);
  return session;
}
async function login(context, name = 'buyer') {
  const session = await actor(name);
  await context.addCookies(session.state.cookies);
  return session.me;
}
async function openChat(context, recipient = 'seller') {
  // Reuse local test sessions to respect the application's login rate limit.
  const { me } = await actor(recipient);
  return api(context, 'POST', '/api/v1/messages/conversations', {
    recipientId: me.id,
  });
}
async function ready(page, target) {
  await page.goto(target);
  await expect(page.locator('#main-content h1').first()).toBeVisible();
  await expect(page.locator('.mm-skeleton')).toHaveCount(0);
}
async function shot(page, name) {
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  await page.screenshot({
    path: path.join(OUT, name + '.png'),
    fullPage: true,
  });
}
test.beforeEach(async ({ context }) => {
  await context.addInitScript(() =>
    sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
  );
});

test('search validation, category filters and return navigation preserve user choices', async ({
  page,
}) => {
  await ready(page, '/search');
  await page.getByRole('button', { name: /^高级筛选/ }).click();
  await page.getByLabel('最低价（元）', { exact: true }).fill('100');
  await page.getByLabel('最高价（元）', { exact: true }).fill('10');
  await page
    .getByRole('button', { name: '应用筛选', exact: true })
    .click();
  await expect(
    page.getByLabel('最低价（元）', { exact: true }),
  ).toBeFocused();
  await expect(page.getByRole('alert')).toContainText(
    '最低价不能高于最高价',
  );
  await page.getByLabel('最高价（元）', { exact: true }).fill('9999');
  await page
    .getByRole('button', { name: '应用筛选', exact: true })
    .click();
  await expect(page).toHaveURL(/minPrice=100/);
  await page.locator('.mm-product-card').first().click();
  await expect(page).toHaveURL(/\/products\/\d+/);
  await page.goBack();
  await expect(
    page.getByLabel('最低价（元）', { exact: true }),
  ).toHaveValue('100');
  await expect(
    page.getByRole('button', { name: '移除筛选：最低 ¥100' }),
  ).toBeVisible();
});

test('HTML service failure is readable, retry restores products, offline notice follows connectivity', async ({
  page,
  context,
}) => {
  let fail = true;
  await page.route('**/api/v1/products?**', (route) =>
    fail
      ? route.fulfill({
          status: 502,
          contentType: 'text/html',
          body: '<h1>private upstream nginx</h1>',
        })
      : route.continue(),
  );
  await ready(page, '/search');
  await expect(
    page.getByText('服务暂时繁忙，请稍后重试。', { exact: true }),
  ).toBeVisible();
  await expect(page.locator('body')).not.toContainText('private upstream');
  fail = false;
  await page.getByRole('button', { name: '重试', exact: true }).click();
  await expect(page.locator('.mm-product-card').first()).toBeVisible();
  await context.setOffline(true);
  await expect(
    page.getByText('网络已断开，请检查连接。恢复后可重试当前操作。'),
  ).toBeVisible();
  await context.setOffline(false);
  await expect(page.locator('.mm-connectivity')).toHaveCount(0);
});

test('orders retain filters and page across refresh; delayed older requests cannot overwrite the latest tab', async ({
  page,
  context,
}) => {
  await login(context);
  let releaseOld;
  let oldReceived;
  const received = new Promise((resolve) => {
    oldReceived = resolve;
  });
  await page.route('**/api/v1/orders?**', async (route) => {
    const status = new URL(route.request().url()).searchParams.get(
      'status',
    );
    if (status === 'PENDING_PAYMENT') {
      oldReceived();
      await new Promise((resolve) => {
        releaseOld = resolve;
      });
      await route.fulfill({
        status: 503,
        json: {
          code: 'SLOW_OLD_REQUEST',
          message: '旧筛选请求不应覆盖当前结果',
        },
      });
    } else
      await route.fulfill({
        json: { content: [], totalElements: 0, totalPages: 0 },
      });
  });
  await ready(page, '/orders');
  await page.getByRole('button', { name: '待付款', exact: true }).click();
  await received;
  await page.getByRole('button', { name: '已完成', exact: true }).click();
  await expect(
    page.getByRole('button', { name: '已完成', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
  releaseOld();
  await page.waitForResponse((r) => r.status() === 503);
  await expect(page.locator('.mm-order-list')).not.toContainText('旧筛选');
  await page.reload();
  await expect(
    page.getByRole('button', { name: '已完成', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
  await expect(page).toHaveURL(/status=COMPLETED/);
  await ready(page, '/orders?status=COMPLETED&page=2');
  await page.reload();
  await expect(page).toHaveURL(/page=2/);
});

test('seller filters remain shareable and failed loading has an actionable retry', async ({
  page,
  context,
}) => {
  await login(context, 'seller');
  await ready(page, '/seller/products?status=DRAFT');
  const select = page.locator('.mm-my-products select');
  const filter = (await select.count())
    ? select
    : page.locator('#main-content select').first();
  await expect(filter).toHaveValue('DRAFT');
  await filter.selectOption('ON_SALE');
  await expect(page).toHaveURL(/status=ON_SALE/);
  await page.reload();
  await expect(filter).toHaveValue('ON_SALE');
  let fail = true;
  await page.route('**/api/v1/seller/products?**', (r) =>
    fail ? r.fulfill({ status: 503, body: 'unavailable' }) : r.continue(),
  );
  await page.reload();
  await expect(
    page.getByRole('button', { name: '重新加载商品' }),
  ).toBeVisible();
  fail = false;
  await page.getByRole('button', { name: '重新加载商品' }).click();
  await expect(page.locator('.mm-recovery')).toHaveCount(0);
});

test('private chat preserves multiline drafts per conversation and sends optimistically with idempotent retry', async ({
  page,
  context,
}) => {
  await login(context);
  const chat = await openChat(context);
  const other = await openChat(context, 'seller2');
  await ready(page, `/messages/${chat.id}`);
  const input = page.getByRole('textbox', { name: '消息内容' });
  await input.fill('第一行');
  await input.press('Shift+Enter');
  await input.pressSequentially('第二行');
  await expect(input).toHaveValue('第一行\n第二行');
  await page.reload();
  await expect(input).toHaveValue('第一行\n第二行');
  await ready(page, `/messages/${other.id}`);
  await expect(input).toHaveValue('');
  await ready(page, `/messages/${chat.id}`);
  await expect(input).toHaveValue('第一行\n第二行');
  const body = '057断线重试 ' + RUN;
  await input.fill(body);
  const attempts = [];
  let release;
  await page.route(
    `**/api/v1/messages/conversations/${chat.id}`,
    async (route) => {
      if (route.request().method() !== 'POST') return route.continue();
      attempts.push(route.request().postDataJSON());
      if (attempts.length === 1) {
        await new Promise((resolve) => {
          release = resolve;
        });
        return route.abort('connectionreset');
      }
      return route.continue();
    },
  );
  await input.press('Enter');
  await expect(page.locator('.is-pending')).toContainText(body);
  await expect(page.locator('.is-pending')).toContainText('正在发送');
  release();
  await expect(page.locator('.is-pending')).toContainText('尚未确认送达');
  await page
    .locator('.is-pending')
    .getByRole('button', { name: '重试发送' })
    .click();
  await expect(page.locator('.is-pending')).toHaveCount(0);
  await expect(input).toHaveValue('');
  expect(attempts).toHaveLength(2);
  expect(attempts[0].clientId).toBe(attempts[1].clientId);
  const history = await api(
    context,
    'GET',
    `/api/v1/messages/conversations/${chat.id}?size=50`,
  );
  expect(
    history.items.filter((item) => item.clientId === attempts[0].clientId),
  ).toHaveLength(1);
  await shot(page, 'chat-desktop');
});

test('mobile Enter inserts a line break; IME confirmation never submits prematurely', async ({
  browser,
}) => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  });
  try {
    await login(context);
    const chat = await openChat(context);
    const page = await context.newPage();
    await ready(page, `/messages/${chat.id}`);
    const input = page.getByRole('textbox', { name: '消息内容' });
    await input.fill('移动草稿');
    await expect(
      page.getByRole('button', { name: '打开麦仔客服', exact: true }),
    ).toBeHidden();
    await input.press('Enter');
    await expect(input).toHaveValue('移动草稿\n');
    await input.dispatchEvent('keydown', {
      key: 'Enter',
      code: 'Enter',
      isComposing: true,
    });
    await expect(page.locator('.is-pending')).toHaveCount(0);
    await shot(page, 'chat-mobile');
    await input.blur();
    await expect(
      page.getByRole('button', { name: '打开麦仔客服', exact: true }),
    ).toBeVisible();
  } finally {
    await context.close();
  }
});

test('chat history prepending preserves the reading anchor and does not duplicate the loaded history', async ({
  page,
  context,
}) => {
  await login(context);
  const chat = await openChat(context);
  const me = await api(context, 'GET', '/api/v1/auth/me');
  const message = (id) => ({
    id,
    senderId: me.id,
    conversationId: chat.id,
    clientId: `00000000-0000-4000-8000-${String(id).padStart(12, '0')}`,
    body: '本地历史阅读测试 ' + id,
    createdAt: '2026-09-19T00:00:00Z',
  });
  await page.route(
    `**/api/v1/messages/conversations/${chat.id}?**`,
    (route) => {
      const older = new URL(route.request().url()).searchParams.has(
        'beforeId',
      );
      return route.fulfill({
        json: {
          items: Array.from({ length: 30 }, (_, i) =>
            message((older ? 30 : 60) - i),
          ),
          hasMore: !older,
          nextBeforeId: older ? null : 31,
        },
      });
    },
  );
  await ready(page, `/messages/${chat.id}`);
  await page.locator('.mm-chat__list').evaluate((el) => {
    el.scrollTop = 0;
  });
  const anchor = page.locator('[data-message-id="31"]');
  const before = await anchor.boundingBox();
  await page
    .getByRole('button', { name: '加载更多', exact: true })
    .click();
  await expect(page.locator('[data-message-id]')).toHaveCount(60);
  const after = await anchor.boundingBox();
  expect(Math.abs(after.y - before.y)).toBeLessThan(4);
});

test('gallery swipes change photos without opening a dialog; vertical gestures preserve the image', async ({
  page,
  context,
}) => {
  const products = await api(context, 'GET', '/api/v1/products?size=100');
  let product;
  for (const item of products.content) {
    const detail = await api(
      context,
      'GET',
      `/api/v1/products/${item.id}`,
    );
    if (detail.images.length >= 3) {
      product = detail;
      break;
    }
  }
  expect(product).toBeTruthy();
  await ready(page, `/products/${product.id}`);
  const main = page.locator('.product-gallery__main');
  await main.dispatchEvent('touchstart', {
    touches: [{ identifier: 1, clientX: 200, clientY: 150 }],
  });
  await main.dispatchEvent('touchend', {
    changedTouches: [{ identifier: 1, clientX: 90, clientY: 150 }],
  });
  await expect(page.locator('.product-gallery__caption')).toContainText(
    '2 /',
  );
  await main.click();
  await expect(page.getByRole('dialog', { name: '商品大图' })).toHaveCount(
    0,
  );
  await main.dispatchEvent('touchstart', {
    touches: [{ identifier: 1, clientX: 200, clientY: 150 }],
  });
  await main.dispatchEvent('touchend', {
    changedTouches: [{ identifier: 1, clientX: 190, clientY: 20 }],
  });
  await expect(page.locator('.product-gallery__caption')).toContainText(
    '2 /',
  );
  await page.waitForTimeout(460);
  await main.click();
  await expect(
    page.getByRole('dialog', { name: '商品大图' }),
  ).toBeVisible();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('dialog').getByRole('status')).toContainText(
    '3',
  );
  await page.keyboard.press('Escape');
  await expect(main).toBeFocused();
  await shot(page, 'product-desktop');
});

test('checkout retains its idempotency key after an accepted order response is lost and the page reloads', async ({
  page,
  context,
}) => {
  await login(context);
  const products = await api(context, 'GET', '/api/v1/products?size=100');
  const product = products.content.find(
    (p) => p.deliveryMethods.includes('MEETUP') && p.stockAvailable >= 2,
  );
  expect(product).toBeTruthy();
  await ready(
    page,
    '/checkout?items=' +
      encodeURIComponent(
        JSON.stringify([
          { productId: product.id, quantity: 1, deliveryMethod: 'MEETUP' },
        ]),
      ),
  );
  const consent = page.locator('.mm-checkout__experience input');
  async function complete() {
    if (await consent.count()) await consent.check();
    await page
      .getByRole('button', { name: '去完善', exact: true })
      .click();
    await expect(
      page.getByLabel('面交地点', { exact: true }),
    ).toBeFocused();
    await page
      .getByLabel('面交地点', { exact: true })
      .fill('本地验收公共广场，不实际交付');
    await page
      .getByLabel('面交时间', { exact: true })
      .fill('2026-10-01T12:00');
  }
  await complete();
  const keys = [],
    results = [];
  await page.route(
    /\/api\/v1\/(experience\/)?checkout$/,
    async (route) => {
      keys.push(route.request().postDataJSON().idempotencyKey);
      const response = await route.fetch();
      expect(response.ok()).toBeTruthy();
      results.push(await response.json());
      if (keys.length === 1) return route.abort('connectionreset');
      return route.fulfill({ response });
    },
  );
  await page.getByRole('button', { name: /^提交(体验)?订单$/ }).click();
  await expect(page.getByRole('alert')).toContainText('暂未确认订单结果');
  await expect(
    page.getByRole('link', { name: '先查看我的订单' }),
  ).toBeVisible();
  const firstKey = await page.evaluate(
    () => history.state.maimaiCheckout.key,
  );
  await page.reload();
  await expect(
    page.getByRole('button', { name: /^提交(体验)?订单$/ }),
  ).toBeVisible();
  expect(await page.evaluate(() => history.state.maimaiCheckout.key)).toBe(
    firstKey,
  );
  await complete();
  await shot(page, 'checkout-desktop');
  await page.getByRole('button', { name: /^提交(体验)?订单$/ }).click();
  await expect(page).toHaveURL(/\/orders\//);
  expect(keys).toHaveLength(2);
  expect(keys[1]).toBe(keys[0]);
  expect(results[1].orders[0].orderNo).toBe(results[0].orders[0].orderNo);
});

test('a slow old chat response cannot leak messages into the next conversation', async ({
  page,
  context,
}) => {
  await login(context);
  const first = await openChat(context),
    next = await openChat(context, 'seller2');
  await ready(page, `/messages/${next.id}`);
  await page.getByRole('link', { name: '← 返回会话列表' }).click();
  await expect(
    page.locator(`a[href="/messages/${first.id}"]`),
  ).toBeVisible();
  let release, started;
  const received = new Promise((resolve) => {
    started = resolve;
  });
  await page.route(
    `**/api/v1/messages/conversations/${first.id}?**`,
    async (route) => {
      started();
      await new Promise((resolve) => {
        release = resolve;
      });
      await route.fulfill({
        json: {
          items: [
            {
              id: 999999,
              senderId: 999999,
              clientId: crypto.randomUUID(),
              body: '绝不能串入另一个会话的内容',
              createdAt: '2026-09-19T00:00:00Z',
            },
          ],
          hasMore: false,
          nextBeforeId: null,
        },
      });
    },
  );
  await page.locator(`a[href="/messages/${first.id}"]`).click();
  await received;
  // Jump over the inbox history entry, exercising same-component route reuse.
  await page.evaluate(() => history.go(-2));
  await expect(page).toHaveURL(new RegExp('/messages/' + next.id + '$'));
  const response = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname ===
      `/api/v1/messages/conversations/${first.id}`,
  );
  release();
  await response;
  await expect(page.locator('.mm-chat__list')).not.toContainText(
    '绝不能串入另一个会话的内容',
  );
});

test('nearby center survives refresh and detail return without disclosing coordinates in the URL', async ({
  page,
  context,
}) => {
  await login(context);
  const me = await api(context, 'GET', '/api/v1/auth/me');
  await ready(page, '/search');
  await page.evaluate(
    (id) =>
      sessionStorage.setItem(
        'maimai-nearby-origin-v1:' + id,
        JSON.stringify({
          origin: {
            latitude: 31.23,
            longitude: 121.47,
            label: '上海人民广场',
          },
          savedAt: Date.now(),
        }),
      ),
    me.id,
  );
  await ready(page, '/search?sort=distance_asc');
  await expect(
    page.getByRole('button', { name: '移除筛选：附近：上海人民广场' }),
  ).toBeVisible();
  await expect(page.locator('.mm-search__results')).not.toContainText(
    '尚未选择或已过期',
  );
  await page.reload();
  await expect(
    page.getByRole('button', { name: '移除筛选：附近：上海人民广场' }),
  ).toBeVisible();
  expect(page.url()).not.toContain('31.23');
  await page
    .getByRole('button', { name: '移除筛选：附近：上海人民广场' })
    .click();
  expect(
    await page.evaluate(
      (id) => sessionStorage.getItem('maimai-nearby-origin-v1:' + id),
      me.id,
    ),
  ).toBeNull();
});

test('detail return buttons restore the filtered order and inventory lists', async ({
  page,
  context,
}) => {
  await login(context);
  await ready(page, '/orders?status=PENDING_PAYMENT');
  await page.locator('.mm-order-list__card').first().click();
  await expect(page.locator('.mm-order-detail__back')).toBeVisible();
  await page.locator('.mm-order-detail__back').click();
  await expect(page).toHaveURL(/\/orders\?status=PENDING_PAYMENT$/);
  await expect(
    page.getByRole('button', { name: '待付款', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
  await login(context, 'seller');
  await ready(page, '/seller/products?status=ON_SALE');
  await page.locator('a[href^="/publish/"]').first().click();
  await page.getByRole('button', { name: '返回我的商品' }).click();
  await expect(page).toHaveURL(/\/seller\/products\?status=ON_SALE$/);
});

test('failed lazy navigation offers recovery while preserving the current page', async ({
  page,
}) => {
  await ready(page, '/search');
  await page.route('**/modules/official/OfficialListPage.vue', (route) =>
    route.abort('connectionreset'),
  );
  await page.locator('a[href="/official"]').first().click();
  await expect(page.getByRole('alert')).toContainText(
    '这个页面暂时没有打开',
  );
  await expect(page.locator('.mm-search')).toBeVisible();
  await page.getByRole('button', { name: '留在此页' }).click();
  await expect(page.locator('.mm-navigation-error')).toHaveCount(0);
});

for (const role of ['buyer', 'seller', 'admin']) {
  test(`${role} real pages fit desktop and 390/320px mobile layouts`, async ({
    page,
    context,
  }) => {
    await login(context, role);
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const routes =
      role === 'buyer'
        ? [
            '/',
            '/search',
            '/cart',
            '/orders',
            '/me/aftersales',
            '/me',
            '/community/demands',
            '/messages',
          ]
        : role === 'seller'
          ? [
              '/seller',
              '/seller/products',
              '/seller/orders',
              '/seller/aftersales',
              '/publish',
            ]
          : [
              '/admin',
              '/admin/users',
              '/admin/products',
              '/admin/finance',
              '/admin/categories',
            ];
    for (const route of routes) {
      await ready(page, route);
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 960 });
        const bounds = await page.evaluate(() => ({
          body: document.body.scrollWidth,
          root: document.documentElement.scrollWidth,
          viewport: innerWidth,
        }));
        expect(
          Math.max(bounds.body, bounds.root),
          role + ' ' + route + ' ' + width,
        ).toBeLessThanOrEqual(bounds.viewport);
        if (
          width === 390 &&
          ['/', '/seller', '/admin', '/search'].includes(route)
        )
          await shot(
            page,
            role + '-' + route.replaceAll('/', '_') + '-mobile',
          );
      }
      await page.setViewportSize({ width: 1440, height: 960 });
      if (['/', '/seller', '/admin'].includes(route))
        await shot(page, role + '-overview-desktop');
    }
    expect(errors).toEqual([]);
  });
}
