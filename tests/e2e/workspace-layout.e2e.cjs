const { test, expect } = require('@playwright/test');

// All business responses are local fixtures. These tests never contact a
// database, production host, payment service, email provider or AI model.
async function installFixtures(page, role) {
  const state = {
    userQueries: [],
    roleChanges: [],
    financeReads: [],
    errors: [],
  };
  const me = {
    id: role === 'admin' ? 9 : 21,
    nickname: role === 'admin' ? '布局测试管理员' : '认真整理闲置的小店',
    email: 'layout@example.invalid',
    roles: role === 'admin' ? ['SUPER_ADMIN'] : ['USER', 'SELLER'],
    sellerStatus: role === 'admin' ? 'NONE' : 'APPROVED',
  };
  const products = [
    {
      id: 31,
      title: '富士相机附原装电池、充电器与收纳包，边角有正常使用痕迹',
      status: 'ON_SALE',
      priceCents: 128000,
      stockAvailable: 2,
      stockReserved: 0,
      stockSold: 1,
    },
    {
      id: 32,
      title: '可折叠木质小书桌，同城面交',
      status: 'DRAFT',
      priceCents: 8000,
      stockAvailable: 1,
      stockReserved: 0,
      stockSold: 0,
    },
    {
      id: 33,
      title: '适合通勤的轻便双肩包',
      status: 'PENDING_REVIEW',
      priceCents: 4500,
      stockAvailable: 1,
      stockReserved: 0,
      stockSold: 2,
    },
  ];
  const users = [
    {
      id: 11,
      nickname: '林同学',
      email: 'a-rather-long-personal-account@example.invalid',
      roles: ['USER', 'SELLER'],
      status: 'ACTIVE',
      createdAt: '2026-09-01T10:00:00',
    },
    {
      id: 12,
      nickname: '陈同学',
      email: 'chen@example.invalid',
      roles: ['USER'],
      status: 'ACTIVE',
      createdAt: '2026-09-03T10:00:00',
    },
  ];
  const finance = [101, 102].map((orderId) => ({
    orderId,
    orderNo: `MM20261015${orderId}`,
    buyerId: 11,
    sellerId: 21,
    goodsAmountCents: 10000,
    freightCents: 800,
    goodsRefundedCents: 0,
    freightRefundedCents: 0,
    originalPlatformFeeCents: 3,
    retainedPlatformFeeCents: 3,
    platformFeeRefundedCents: 0,
    platformFeeRefundDueCents: 0,
    channelFeeCents: null,
    channelFeeConfirmed: false,
    expectedSellerNetCents: null,
    simulated: true,
    channel: 'SIMULATED',
    payStatus: 'PAID',
    refundStatus: 'NONE',
    fulfillmentStatus: 'WAITING_SHIPMENT',
    allocationStatus: 'WAITING_CHANNEL',
    createdAt: '2026-10-14T10:00:00',
    paidAt: '2026-10-14T10:03:00',
  }));
  await page.addInitScript(() =>
    sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
  );
  page.on('pageerror', (error) => state.errors.push(error.message));
  await page.route('**/*', async (route) => {
    const host = new URL(route.request().url()).hostname;
    if (host !== '127.0.0.1' && host !== 'localhost') return route.abort();
    return route.continue();
  });
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      url = new URL(request.url());
    const pathname = url.pathname.replace('/api/v1', '');
    if (pathname === '/auth/csrf') return route.fulfill({ status: 204 });
    if (pathname === '/seller/products') {
      const status = url.searchParams.get('status');
      const content = products.filter((p) => !status || p.status === status);
      return route.fulfill({
        json: {
          content,
          totalElements: content.length,
          totalPages: 1,
          page: 0,
          size: 12,
        },
      });
    }
    if (pathname === '/admin/users') {
      const keyword = url.searchParams.get('keyword') || '';
      state.userQueries.push(keyword);
      const content = users.filter((u) =>
        `${u.nickname} ${u.email}`.includes(keyword),
      );
      return route.fulfill({
        json: { content, total: content.length, size: 15 },
      });
    }
    if (/^\/admin\/users\/\d+\/roles$/.test(pathname)) {
      const body = request.postDataJSON();
      state.roleChanges.push(body);
      const user = users.find((u) => u.id === Number(pathname.split('/')[3]));
      user.roles = body.grant
        ? [...new Set([...user.roles, body.role])]
        : user.roles.filter((r) => r !== body.role);
      return route.fulfill({ status: 204 });
    }
    if (pathname === '/admin/finance/orders')
      return route.fulfill({
        json: { content: finance, total: finance.length, size: 20 },
      });
    if (/^\/admin\/finance\/orders\/\d+$/.test(pathname)) {
      const id = Number(pathname.split('/').at(-1));
      state.financeReads.push(id);
      return route.fulfill({
        json: {
          order: finance.find((o) => o.orderId === id),
          notice: '这是用于布局验证的内部账面记录，不代表实际资金到账。',
          reconciliationStatus: 'UNVERIFIED',
          ledger: [
            {
              id,
              entryType: 'PAYMENT',
              amountCents: 10800,
              referenceType: 'ORDER',
              referenceId: id,
              createdAt: '2026-10-14T10:03:00',
            },
          ],
        },
      });
    }
    const responses = {
      '/auth/me': me,
      '/seller/tasks': {
        counts: { SHIP: 1, BARGAIN: 2, AFTERSALE: 0, MESSAGE: 1 },
        items: [
          {
            kind: 'SHIP',
            title: '相机订单待确认包装并发货',
            targetPath: '/seller/orders',
            deadline: null,
          },
        ],
      },
      '/categories': [{ id: 1, name: '生活家居', children: [] }],
      '/support/assistant': {
        name: '麦仔',
        enabled: false,
        maxMessageChars: 1000,
      },
      '/support/chat': [],
      '/support/faq': [],
    };
    return route.fulfill({ json: responses[pathname] ?? [] });
  });
  return state;
}

async function expectNoHorizontalOverflow(page, regions) {
  const result = await page.evaluate(
    (selectors) => ({
      page: document.documentElement.scrollWidth > innerWidth + 1,
      regions: selectors.flatMap((selector) =>
        [...document.querySelectorAll(selector)].flatMap((e) => {
          if (!e.getClientRects().length) return [];
          const rect = e.getBoundingClientRect();
          return rect.left < -1 ||
            rect.right > innerWidth + 1 ||
            e.scrollWidth > e.clientWidth + 1
            ? [
                {
                  selector,
                  left: rect.left,
                  right: rect.right,
                  scroll: e.scrollWidth,
                  width: e.clientWidth,
                },
              ]
            : [];
        }),
      ),
    }),
    regions,
  );
  expect(result).toEqual({ page: false, regions: [] });
}

async function bounds(locator) {
  await expect(locator).toBeVisible();
  const rect = await locator.boundingBox();
  expect(rect).toBeTruthy();
  return rect;
}

test('seller sees useful product content and a prominent publish action before help', async ({
  page,
}) => {
  const state = await installFixtures(page, 'seller');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/seller');
  await expect(
    page.getByRole('heading', { name: '最近发布', exact: true }),
  ).toBeVisible();
  const publish = page
    .locator('.seller-home__heading')
    .getByRole('link', { name: '＋ 发布闲置', exact: true });
  const publishBox = await bounds(publish);
  expect(publishBox.height).toBeGreaterThanOrEqual(44);
  expect(publishBox.y + publishBox.height).toBeLessThan(1000);
  const catalog = await bounds(page.locator('.seller-home__catalog'));
  const tasks = await bounds(page.locator('.seller-home__service'));
  expect(tasks.x).toBeGreaterThanOrEqual(catalog.x + catalog.width);
  expect(Math.abs(tasks.y - catalog.y)).toBeLessThan(2);
  expect(catalog.width).toBeGreaterThan(tasks.width);
  const product = await bounds(page.locator('.seller-home__product').first());
  const guide = await bounds(page.locator('.journey-guide'));
  expect(product.y + product.height).toBeLessThan(guide.y);
  await expect(page.locator('.journey-guide')).not.toHaveAttribute('open');
  await expect(page.locator('.seller-home__product').first()).toContainText(
    '可售 2 件',
  );
  await expect(page.locator('.seller-home__product').first()).toHaveAttribute(
    'href',
    '/publish/31',
  );
  await expect(publish).toHaveAttribute('href', '/publish');
  await publish.click();
  await expect(page).toHaveURL(/\/publish$/);
  expect(state.errors).toEqual([]);
});

test('publishing starts with the form and reports an incomplete draft beside the top save action', async ({
  page,
}) => {
  const state = await installFixtures(page, 'seller');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/publish');
  const title = page.getByLabel('标题', { exact: true });
  const titleBox = await bounds(title);
  expect(titleBox.y + titleBox.height).toBeLessThan(650);
  const formBox = await bounds(page.locator('.mm-edit__form'));
  const helpBox = await bounds(page.getByLabel('发布帮助与草稿状态'));
  expect(helpBox.x).toBeGreaterThanOrEqual(formBox.x + formBox.width);
  await title.fill('填写到一半仍然保留的闲置');
  await page
    .locator('.mm-edit__page-header')
    .getByRole('button', { name: '保存草稿', exact: true })
    .click();
  await expect(page.locator('#publish-save-feedback')).toContainText(
    '草稿已保留',
  );
  await expect(title).toHaveValue('填写到一半仍然保留的闲置');
  await expect(
    page.getByRole('combobox', { name: '分类', exact: true }),
  ).toBeFocused();
  expect(state.errors).toEqual([]);
});

test('user search applies its query and role editing preserves confirmation and closes after success', async ({
  page,
}) => {
  const state = await installFixtures(page, 'admin');
  await page.goto('/admin/users');
  await expect(page.locator('.user-row')).toHaveCount(2);
  await page.getByLabel('按邮箱或昵称查询').fill('林');
  await page.getByRole('button', { name: '查询账号', exact: true }).click();
  await expect(page.locator('.user-row')).toHaveCount(1);
  expect(state.userQueries.at(-1)).toBe('林');
  const row = page.locator('.user-row');
  await expect(row).toContainText('林同学');
  await row.getByRole('button', { name: '管理授权', exact: true }).click();
  const editor = row.locator('form');
  await expect(editor).toBeVisible();
  const rowBox = await bounds(row),
    editorBox = await bounds(editor);
  expect(editorBox.width).toBeGreaterThan(rowBox.width * 0.8);
  expect(editorBox.x).toBeGreaterThanOrEqual(rowBox.x);
  expect(editorBox.x + editorBox.width).toBeLessThanOrEqual(
    rowBox.x + rowBox.width,
  );
  await row
    .getByRole('combobox', { name: '管理角色', exact: true })
    .selectOption('SUPPORT');
  await row.getByLabel('授权变更原因').fill('测试客服工作台权限申请');
  await row.getByRole('button', { name: '保存授权变更' }).click();
  await page.getByRole('button', { name: '暂不操作', exact: true }).click();
  expect(state.roleChanges).toEqual([]);
  await expect(row.getByLabel('授权变更原因')).toHaveValue(
    '测试客服工作台权限申请',
  );
  await row.getByRole('button', { name: '保存授权变更' }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(editor).toBeHidden();
  expect(state.roleChanges).toEqual([
    { role: 'SUPPORT', grant: true, reason: '测试客服工作台权限申请' },
  ]);
  await expect(row).toContainText('已授予角色：普通用户、卖家、客服');
  expect(state.errors).toEqual([]);
});

test('finance detail opens below its own order, switches rows and collapses without refetch', async ({
  page,
}) => {
  const state = await installFixtures(page, 'admin');
  await page.goto('/admin/finance');
  const first = page
    .locator('.finance-order')
    .filter({ hasText: 'MM20261015101' });
  const second = page
    .locator('.finance-order')
    .filter({ hasText: 'MM20261015102' });
  await first.getByRole('button', { name: '查看内部明细' }).click();
  await expect(first.locator('.finance-detail')).toBeVisible();
  await expect(page.locator('.finance-detail')).toHaveCount(1);
  const detailBox = await bounds(first.locator('.finance-detail'));
  const footerBox = await bounds(first.locator('.finance-order__footer'));
  expect(detailBox.y).toBeGreaterThanOrEqual(
    footerBox.y + footerBox.height - 1,
  );
  await expect(first.locator('.finance-detail')).toContainText('支付记录');
  await expect(first.locator('.finance-detail')).toContainText(
    '不代表实际资金到账',
  );
  await second.getByRole('button', { name: '查看内部明细' }).click();
  await expect(first.locator('.finance-detail')).toBeHidden();
  await expect(second.locator('.finance-detail')).toBeVisible();
  await expect(page.locator('.finance-detail')).toHaveCount(1);
  await second.getByRole('button', { name: '收起内部明细' }).click();
  await expect(page.locator('.finance-detail')).toHaveCount(0);
  expect(state.financeReads).toEqual([101, 102]);
  expect(state.errors).toEqual([]);
});

for (const viewport of [
  { width: 320, height: 740 },
  { width: 844, height: 390 },
  { width: 1440, height: 1000 },
]) {
  test(`seller regions and management actions fit at ${viewport.width}px`, async ({
    page,
  }) => {
    const state = await installFixtures(page, 'seller');
    await page.setViewportSize(viewport);
    await page.goto('/seller');
    await expect(page.locator('.seller-home__product')).toHaveCount(3);
    const catalog = await bounds(page.locator('.seller-home__catalog'));
    const tasks = await bounds(page.locator('.seller-home__service'));
    if (viewport.width < 980)
      expect(tasks.y).toBeGreaterThanOrEqual(catalog.y + catalog.height);
    else expect(tasks.x).toBeGreaterThanOrEqual(catalog.x + catalog.width);
    await expectNoHorizontalOverflow(page, [
      '.seller-home__heading',
      '.seller-home__stats',
      '.seller-home__columns',
      '.seller-home__product',
      '.seller-tasks',
    ]);
    const publish = page
      .locator('.seller-home__heading')
      .getByRole('link', { name: '＋ 发布闲置', exact: true });
    const publishBox = await bounds(publish);
    expect(publishBox.width).toBeGreaterThanOrEqual(44);
    expect(publishBox.height).toBeGreaterThanOrEqual(44);
    await page
      .getByRole('link', { name: '管理全部商品 →', exact: true })
      .click();
    await expect(page).toHaveURL(/\/seller\/products$/);
    await expect(page.locator('.mm-inventory__row')).toHaveCount(3);
    await expectNoHorizontalOverflow(page, [
      '.mm-inventory',
      '.mm-inventory__row',
      '.mm-inventory__actions',
    ]);
    for (const action of await page
      .locator('.mm-inventory__actions > a, .mm-inventory__actions > button')
      .all()) {
      const box = await bounds(action);
      expect(box.height).toBeGreaterThanOrEqual(43.9);
      expect(box.width).toBeGreaterThanOrEqual(43.9);
    }
    expect(state.errors).toEqual([]);
  });

  test(`admin expanded controls and finance amounts fit at ${viewport.width}px`, async ({
    page,
  }) => {
    const state = await installFixtures(page, 'admin');
    await page.setViewportSize(viewport);
    await page.goto('/admin/users');
    await expect(page.locator('.user-row')).toHaveCount(2);
    await page
      .locator('.user-row')
      .first()
      .getByRole('button', { name: '管理授权' })
      .click();
    await expect(page.getByLabel('授权变更原因')).toBeVisible();
    await page
      .getByLabel('授权变更原因')
      .fill('用于核验窄屏输入区域及完整操作位置的说明');
    await expectNoHorizontalOverflow(page, [
      '.admin-users__search',
      '.user-row',
      '.user-row__access',
      '.user-row__roles form',
    ]);
    const editorBox = await bounds(page.locator('.user-row__roles form'));
    const saveBox = await bounds(
      page.getByRole('button', { name: '保存授权变更' }),
    );
    expect(saveBox.x + saveBox.width).toBeLessThanOrEqual(
      editorBox.x + editorBox.width + 1,
    );
    expect(saveBox.y + saveBox.height).toBeLessThanOrEqual(
      editorBox.y + editorBox.height + 1,
    );
    if (viewport.width <= 900)
      await page
        .getByRole('button', { name: '展开工作导航', exact: true })
        .click();
    const lastNav = page.locator('#admin-menu a').last();
    await lastNav.scrollIntoViewIfNeeded();
    const navBox = await bounds(page.locator('#admin-menu'));
    const lastBox = await bounds(lastNav);
    expect(lastBox.y).toBeGreaterThanOrEqual(navBox.y - 1);
    expect(lastBox.y + lastBox.height).toBeLessThanOrEqual(
      navBox.y + navBox.height + 1,
    );
    expect(navBox.height).toBeLessThanOrEqual(viewport.height);
    await page.goto('/admin/finance');
    await expect(page.locator('.finance-order')).toHaveCount(2);
    await page
      .locator('.finance-order')
      .first()
      .getByRole('button', { name: '查看内部明细' })
      .click();
    await expect(page.locator('.finance-detail')).toBeVisible();
    await expectNoHorizontalOverflow(page, [
      '.finance__toolbar',
      '.finance-order',
      '.finance-order__amounts',
      '.finance-detail',
      '.finance-detail__facts',
    ]);
    const amountBoxes = await page
      .locator('.finance-order')
      .first()
      .locator('.finance-order__amounts > div')
      .evaluateAll((elements) =>
        elements.map((e) => {
          const r = e.getBoundingClientRect();
          return { x: r.x, y: r.y, right: r.right, bottom: r.bottom };
        }),
      );
    for (let i = 0; i < amountBoxes.length; i++)
      for (let j = i + 1; j < amountBoxes.length; j++) {
        const a = amountBoxes[i],
          b = amountBoxes[j];
        const overlapWidth = Math.min(a.right, b.right) - Math.max(a.x, b.x);
        const overlapHeight = Math.min(a.bottom, b.bottom) - Math.max(a.y, b.y);
        expect(
          overlapWidth > 1 && overlapHeight > 1,
          `amount ${i} overlaps ${j}`,
        ).toBe(false);
      }
    expect(state.errors).toEqual([]);
  });
}
