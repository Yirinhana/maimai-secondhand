const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const messageFixture = require('./fixtures/message-ui-041.cjs');
const manifestPath = path.resolve(
  __dirname,
  '../../.local/private/demo-accounts-040.json',
);
const manifest = fs.existsSync(manifestPath)
  ? JSON.parse(fs.readFileSync(manifestPath, 'utf8').replace(/^\uFEFF/, ''))
  : null;
const accounts = () => [
  {
    email: manifest.adminEmail,
    role: 'SUPER_ADMIN',
    route: '/admin',
    section: '管理后台',
  },
  ...manifest.sellers.slice(0, 2).map((user) => ({
    ...user,
    role: 'SELLER',
    route: '/seller/products',
    section: '卖家工作台',
  })),
  ...manifest.buyers.slice(0, 2).map((user) => ({
    ...user,
    role: 'USER',
    route: '/orders',
    section: '买家交易',
  })),
];
const primary = (page) =>
  page.getByRole('navigation', { name: '主导航', exact: true });
// Reuse live sessions in memory only; repeated UI checks must not hammer the login rate limit.
const sessions = new Map();
async function login(page, index) {
  test.skip(
    !manifest,
    'Requires the explicitly authorized, ignored local demo manifest',
  );
  const account = accounts()[index];
  if (sessions.has(index)) {
    await page.context().addCookies(sessions.get(index));
    await page.goto(account.route);
  } else {
    await page.goto('/login?redirect=' + encodeURIComponent(account.route));
    await page.getByLabel('邮箱', { exact: true }).fill(account.email);
    await page.getByLabel('密码', { exact: true }).fill(manifest.password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
  }
  await expect(page).toHaveURL(new RegExp(account.route + '$'));
  await expect(page.locator('.mm-zone__title')).toHaveText(account.section);
  const me = await (await page.request.get('/api/v1/auth/me')).json();
  expect(me.email === account.email).toBe(true);
  expect(me.roles).toContain(account.role);
  sessions.set(index, await page.context().cookies());
  return me;
}
async function noOverflow(page) {
  expect(
    await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth,
    ),
  ).toBeLessThanOrEqual(1);
}
async function shot(page, name) {
  await expect
    .poll(() =>
      page.locator('img').evaluateAll((images) =>
        images
          .filter((img) => {
            const r = img.getBoundingClientRect();
            return (
              r.width > 0 && r.height > 0 && r.top < innerHeight && r.bottom > 0
            );
          })
          .every((img) => img.complete && img.naturalWidth > 0),
      ),
    )
    .toBe(true);
  await page.screenshot({
    path: path.resolve(
      __dirname,
      `../../.local/screenshots/041-${process.env.MAIMAI_041_RUN}-${name}.png`,
    ),
    mask: [page.locator('.mm-account__email strong')],
  });
}

for (let index = 0; index < 5; index++) {
  test(`authorized account ${index + 1}: live login, role and correct section`, async ({
    page,
  }) => {
    await login(page, index);
    const account = accounts()[index];
    await expect(page.locator('.mm-zone__title')).toHaveText(account.section);
    await expect(primary(page).locator('[aria-current="page"]')).toHaveText(
      account.section,
    );
    if (index === 0) {
      await expect(
        page.locator('.mm-admin-overview__metrics strong').first(),
      ).not.toBeEmpty();
      await expect(
        page
          .getByRole('navigation', { name: '后台导航' })
          .getByRole('link', { name: '资金与对账' }),
      ).toBeVisible();
      await shot(page, 'desktop-admin');
    } else {
      await expect(
        primary(page).getByRole('link', { name: '管理后台' }),
      ).toHaveCount(0);
      expect(
        (await page.request.get('/api/v1/admin/stats/overview')).status(),
      ).toBe(403);
      if (index <= 2) {
        await expect(page.locator('.mm-inventory__row')).toHaveCount(5);
        await expect(page.locator('.mm-inventory__review').first()).toHaveText(
          '审核说明：本地演示商品，仅用于体验模拟交易',
        );
        const images = await page
          .locator('.mm-inventory__product img')
          .evaluateAll((imgs) =>
            imgs.every((i) => i.complete && i.naturalWidth > 0),
          );
        expect(images).toBe(true);
        if (index === 1) await shot(page, 'desktop-seller');
        await page
          .getByRole('combobox', { name: /商品状态/ })
          .selectOption('OFF_SHELF');
        await expect(page.getByText('当前状态下暂无商品')).toBeVisible();
        await page.getByRole('combobox', { name: /商品状态/ }).selectOption('');
        await expect(page.locator('.mm-inventory__row')).toHaveCount(5);
        await page
          .getByRole('link', { name: '编辑商品', exact: true })
          .first()
          .click();
        await expect(page).toHaveURL(/\/publish\/\d+$/);
        await expect(page.locator('.mm-zone__title')).toHaveText('卖家工作台');
      } else {
        await expect(page.getByText('暂无相关订单')).toBeVisible();
        await page.getByRole('button', { name: '待收货', exact: true }).click();
        await expect(
          page.getByRole('button', { name: '待收货', exact: true }),
        ).toHaveAttribute('aria-pressed', 'true');
        if (index === 3) {
          await shot(page, 'desktop-buyer-empty');
          const overview = await page.request.get(
            '/api/v1/messages/conversations/overview',
          );
          expect(overview.status()).toBe(200);
          expect(await overview.json()).toEqual({
            conversations: 0,
            unreadConversations: 0,
            unreadMessages: 0,
          });
          const list = await page.request.get(
            '/api/v1/messages/conversations?keyword=Java&unreadOnly=true',
          );
          expect(list.status()).toBe(200);
          expect(await list.json()).toEqual([]);
          expect(
            (
              await page.request.get(
                '/api/v1/messages/conversations?keyword=' + 'x'.repeat(101),
              )
            ).status(),
          ).toBe(400);
          expect(
            (
              await page.request.get(
                '/api/v1/messages/conversations/9223372036854775807/summary',
              )
            ).status(),
          ).toBe(404);
        }
      }
    }
    await noOverflow(page);
  });
}

test('live catalog: pagination, sorting, filters and image cards', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page.locator('.mm-product-card').first()).toBeVisible();
  await shot(page, 'desktop-market');
  await page.goto('/search');
  await expect(page.locator('.mm-product-card').first()).toBeVisible();
  await expect(
    page.locator('.mm-section-nav a[aria-current="page"]'),
  ).toHaveText('全部闲置');
  await shot(page, 'desktop-search');
  const href = await page
    .locator('.mm-product-card')
    .first()
    .getAttribute('href');
  await page.getByRole('button', { name: '下一页', exact: true }).click();
  await expect(page.locator('.mm-pagination__info')).toContainText('第 2 /');
  await expect(page.locator('.mm-product-card').first()).not.toHaveAttribute(
    'href',
    href,
  );
  await page.getByLabel('排序', { exact: true }).selectOption('price_asc');
  await expect(page).toHaveURL(/sort=price_asc/);
  await page.getByLabel('关键词', { exact: true }).fill('Java');
  await page.getByRole('button', { name: '筛选', exact: true }).click();
  await expect(page.locator('.mm-product-card').first()).toContainText('Java');
  const advanced = page.getByRole('button', { name: /高级筛选/ });
  if ((await advanced.getAttribute('aria-expanded')) !== 'true')
    await advanced.click();
  await expect(page.getByRole('combobox', { name: /分类/ })).toBeVisible();
  await noOverflow(page);
});

test('account tabs, avatar menu and section change stay coherent', async ({
  page,
}) => {
  await login(page, 3);
  await page.getByRole('button', { name: '打开个人菜单', exact: true }).click();
  await page.getByRole('menuitem', { name: '个人中心', exact: true }).click();
  await expect(page.locator('.mm-zone__title')).toHaveText('个人中心');
  await expect(page.getByRole('menu')).toHaveCount(0);
  await expect(
    page.getByRole('textbox', { name: '昵称', exact: true }),
  ).not.toBeEmpty();
  await shot(page, 'desktop-account');
  const tabs = page
    .getByRole('tablist', { name: '个人中心分区' })
    .getByRole('tab');
  await tabs.first().focus();
  await page.keyboard.press('ArrowRight');
  await expect(tabs.nth(1)).toBeFocused();
  await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true');
  await expect(
    page.getByRole('tabpanel').filter({ visible: true }),
  ).toHaveCount(1);
  await noOverflow(page);
});

test('order card separates status, items, amount and next step (controlled response)', async ({
  page,
}) => {
  const me = await login(page, 3);
  // UI fixture only: no demo orders, payments or reviews are written to the database.
  await page.route('**/api/v1/orders?*', (route) =>
    route.fulfill({
      json: {
        content: [
          {
            orderNo: 'MM-UI-041',
            buyerId: me.id,
            sellerId: 991041,
            sellerNickname: '界面验收卖家',
            deliveryMethod: 'EXPRESS',
            fulfillmentStatus: 'PENDING_PAYMENT',
            goodsAmountCents: 6900,
            freightCents: 1000,
            platformFeeCents: 2,
            totalCents: 7900,
            createdAt: '2026-09-14T10:00:00+08:00',
            expiresAt: '2026-09-14T10:30:00+08:00',
            items: [
              {
                title: 'Java 教材 · 界面展示样例',
                imagePath: '/uploads/products/seed-9040003.jpg',
                quantity: 1,
              },
            ],
          },
        ],
        number: 0,
        totalPages: 1,
        totalElements: 1,
      },
    }),
  );
  await page.reload();
  await expect(page.locator('.mm-order-list__card')).toHaveCount(1);
  await expect(page.locator('.mm-order-list__card')).toContainText(
    '查看订单详情',
  );
  await shot(page, 'desktop-buyer-order-fixture');
  await page.setViewportSize({ width: 390, height: 844 });
  await noOverflow(page);
  await page.locator('.mm-order-list__card').scrollIntoViewIfNeeded();
  await shot(page, 'mobile-buyer-order-fixture');
});

test('mobile and tablet sections: navigation, wrapping and fixed support entry', async ({
  page,
}) => {
  await login(page, 0);
  for (const width of [320, 390, 820]) {
    await page.setViewportSize({ width, height: 900 });
    for (const [url, section] of [
      ['/search', '商城'],
      ['/orders', '买家交易'],
      ['/seller/products', '卖家工作台'],
      ['/me', '个人中心'],
      ['/admin', '管理后台'],
      ['/messages', '消息中心'],
      ['/official', '麦麦官方'],
    ]) {
      await page.goto(url);
      await expect(page.locator('.mm-zone__title')).toHaveText(section);
      await noOverflow(page);
      const dock = page.getByRole('button', {
        name: '打开缇娜客服',
        exact: true,
      });
      await expect(dock).toBeVisible();
      const bounds = await dock.boundingBox();
      expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
      if (
        width === 390 &&
        [
          '/search',
          '/admin',
          '/me',
          '/seller/products',
          '/messages',
          '/official',
        ].includes(url)
      ) {
        if (url === '/admin')
          await expect(
            page.locator('.mm-admin-overview__metrics'),
          ).toBeVisible();
        if (url === '/search')
          await expect(page.locator('.mm-product-card').first()).toBeVisible();
        await shot(page, `mobile-${section}`);
      }
    }
    if (width < 760) {
      await page
        .getByRole('button', { name: '打开导航菜单', exact: true })
        .click();
      await expect(primary(page)).toBeVisible();
      await noOverflow(page);
      await primary(page)
        .getByRole('link', { name: '买家交易', exact: true })
        .click();
      await expect(primary(page)).toBeHidden();
      await expect(page.locator('.mm-zone__title')).toHaveText('买家交易');
    }
  }
});

test('registration field errors remain readable on a narrow screen', async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 844 });
  await page.goto('/register');
  await page.getByRole('button', { name: '注册并登录', exact: true }).click();
  await expect(page.getByLabel('邮箱', { exact: true })).toHaveAttribute(
    'aria-invalid',
    'true',
  );
  await expect(page.getByLabel('邮箱', { exact: true })).toBeFocused();
  await noOverflow(page);
  await shot(page, 'mobile-register-errors');
});

test('section colors stay consistent across admin, seller and buyer pages; brand stays unchanged', async ({
  page,
}) => {
  await login(page, 0);
  const brand = await page.locator('.mm-brand img').getAttribute('src');
  const adminPaths = await page
    .getByRole('navigation', { name: '后台导航' })
    .getByRole('link')
    .evaluateAll((links) => links.map((a) => a.getAttribute('href')));
  const colors = [];
  for (const [index, paths] of [
    adminPaths,
    [
      '/seller/products',
      '/seller/orders',
      '/seller/bargains',
      '/seller/aftersales',
      '/publish',
    ],
    ['/orders', '/cart', '/me/bargains', '/me/aftersales'],
  ].entries()) {
    let color;
    for (const url of paths) {
      await page.goto(url);
      await expect(page.locator('.mm-zone__title')).toHaveText(
        ['管理后台', '卖家工作台', '买家交易'][index],
      );
      const current = await page
        .locator('.mm-app')
        .evaluate((el) =>
          getComputedStyle(el).getPropertyValue('--mm-primary').trim(),
        );
      if (color) expect(current).toBe(color);
      else color = current;
      await expect(page.locator('.mm-brand img')).toHaveAttribute('src', brand);
      await noOverflow(page);
    }
    colors.push(color);
  }
  expect(new Set(colors).size).toBe(3);
  for (const url of ['/admin/products', '/admin/users', '/admin/categories']) {
    await page.setViewportSize({ width: 390, height: 900 });
    await page.goto(url);
    await expect(page.locator('.mm-zone__title')).toHaveText('管理后台');
    await noOverflow(page);
  }
});

test('inbox can search all conversations, filter unread and restore its query after chat (controlled)', async ({
  page,
}) => {
  const me = await login(page, 3);
  await messageFixture(page, me);
  await page.goto('/messages');
  await expect(page.locator('.mm-inbox__item')).toHaveCount(2);
  await expect(page.locator('.mm-inbox__label')).toContainText(
    '2 个会话 · 2 条未读',
  );
  await shot(page, 'desktop-inbox-fixture');
  await page.getByRole('button', { name: /未读会话/ }).click();
  await expect(page).toHaveURL(/unread=1/);
  await expect(page.locator('.mm-inbox__item')).toHaveCount(1);
  await page.getByLabel('昵称或关联商品').fill('Java');
  await page
    .getByRole('search', { name: '搜索私信会话' })
    .getByRole('button', { name: '搜索', exact: true })
    .click();
  await expect(page).toHaveURL(/keyword=Java/);
  await page.locator('.mm-inbox__item').click();
  await expect(page.locator('.mm-chat__identity')).toContainText('林的小店');
  await expect(page.locator('.mm-chat__product')).toContainText('Java 教材');
  await expect(page.locator('.mm-chat__bubble')).toHaveCount(3);
  await page.getByRole('link', { name: '← 返回会话列表' }).click();
  await expect(page).toHaveURL(/keyword=Java.*unread=1/);
  await expect(page.getByText('没有符合条件的会话')).toBeVisible();
  await page.getByRole('button', { name: '清除筛选', exact: true }).click();
  await expect(page.locator('.mm-inbox__item')).toHaveCount(2);
  await expect(page.locator('.mm-inbox__label')).toContainText('0 条未读');
  await page.setViewportSize({ width: 360, height: 900 });
  await noOverflow(page);
  await shot(page, 'mobile-inbox-fixture');
});

test('private chat clearly separates peer, history, composer and block controls (controlled)', async ({
  page,
}) => {
  const me = await login(page, 3),
    fixture = await messageFixture(page, me);
  await page.goto('/messages/904101');
  await expect(page.locator('.mm-chat__identity')).toContainText('林的小店');
  await expect(page.locator('.mm-chat__bubble')).toHaveCount(3);
  await expect(
    page.getByRole('button', { name: '屏蔽对方', exact: true }),
  ).toBeHidden();
  await expect(
    page.getByRole('button', { name: '发送', exact: true }),
  ).toBeInViewport();
  await shot(page, 'desktop-chat-fixture');
  await page.locator('.mm-chat__management summary').click();
  await page.getByRole('button', { name: '屏蔽对方', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(
    page.getByRole('button', { name: '发送', exact: true }),
  ).toBeDisabled();
  await expect(page.getByLabel('消息内容')).toHaveAttribute('readonly', '');
  await expect(page.locator('.mm-chat__bubble')).toHaveCount(3);
  await page.getByRole('button', { name: '取消屏蔽', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await page.locator('.mm-chat__management summary').click();
  await page.getByLabel('消息内容').fill('这是一条隔离的界面测试消息');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(page.locator('.mm-chat__bubble')).toHaveCount(4);
  expect(fixture.sent).toHaveLength(1);
  await page.setViewportSize({ width: 360, height: 900 });
  await noOverflow(page);
  await page.locator('.mm-chat__peer').scrollIntoViewIfNeeded();
  await shot(page, 'mobile-chat-fixture');
  await page.locator('.mm-chat__management summary').click();
  await expect(
    page.getByRole('link', { name: '举报并联系人工客服' }),
  ).toBeVisible();
  await noOverflow(page);
  await page.locator('.mm-chat__management summary').click();
  await page.locator('.mm-chat__composer').scrollIntoViewIfNeeded();
  await expect(page.getByLabel('消息内容')).toBeInViewport();
  await shot(page, 'mobile-chat-composer-fixture');
});

test('chat below the viewport does not mark messages read until scrolled into view (controlled)', async ({
  page,
}) => {
  const me = await login(page, 3),
    fixture = await messageFixture(page, me);
  await page.setViewportSize({ width: 360, height: 360 });
  await page.goto('/messages/904101');
  await expect(page.locator('.mm-chat__bubble')).toHaveCount(3);
  await expect(page.locator('.mm-chat__identity')).toContainText('林的小店');
  await page.evaluate(
    () =>
      new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(resolve)),
      ),
  );
  expect(
    await page
      .locator('.mm-chat__list')
      .evaluate((el) => el.getBoundingClientRect().top),
  ).toBeGreaterThan(360);
  expect(fixture.read).toBe(false);
  await page.locator('.mm-chat__bubble').last().scrollIntoViewIfNeeded();
  await expect.poll(() => fixture.read).toBe(true);
});
