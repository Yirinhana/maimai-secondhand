const { test, expect } = require('@playwright/test');

// All APIs and map SDK functions are fixtures. No database, model, email or payment calls.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() =>
    sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
  );
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname.replace(
      '/api/v1',
      '',
    );
    const responses = {
      '/auth/me': {
        id: 1,
        nickname: '交互测试用户',
        email: 'test@example.invalid',
        roles: ['USER'],
        sellerStatus: 'NONE',
      },
      '/products': {
        content: [],
        totalElements: 0,
        totalPages: 0,
        page: 0,
        size: 20,
      },
      '/categories': [{ id: 1, name: '生活家居', children: [] }],
      '/support/assistant': {
        name: '麦仔',
        enabled: false,
        maxMessageChars: 1000,
      },
      '/support/faq': [],
      '/support/chat': [],
      '/me/searches': [],
    };
    if (pathname === '/auth/csrf') return route.fulfill({ status: 204 });
    return route.fulfill({ json: responses[pathname] ?? [] });
  });
  await page.goto('/search');
  await expect(page.getByRole('heading', { name: '发现好物' })).toBeVisible();
});

test('applied filters collapse and retain selected values; invalid values stay editable', async ({
  page,
}) => {
  const advanced = page.locator('#catalog-advanced');
  const toggle = page.getByRole('button', { name: /^高级筛选/ });
  await toggle.click();
  await page.getByRole('combobox', { name: '成色', exact: true }).click();
  await page.keyboard.press('Escape');
  await expect(advanced).toBeVisible();
  await page
    .getByRole('combobox', { name: '成色', exact: true })
    .selectOption('GOOD');
  await page.getByRole('button', { name: '应用筛选', exact: true }).click();
  await expect(advanced).toBeHidden();
  await expect(page).toHaveURL(/condition=GOOD/);
  await toggle.click();
  await expect(
    page.getByRole('combobox', { name: '成色', exact: true }),
  ).toHaveValue('GOOD');
  await page.getByLabel('最低价（元）').fill('300');
  await page.getByLabel('最高价（元）').fill('100');
  await page.getByRole('button', { name: '应用筛选', exact: true }).click();
  await expect(advanced).toBeVisible();
  await expect(page.locator('#catalog-filter-error')).toContainText(
    '最低价不能高于最高价',
  );
  await expect(page.getByLabel('最低价（元）')).toHaveValue('300');
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await expect(advanced).toBeHidden();
  await expect(page).toHaveURL(/\/search$/);
});

test('outside click and Escape dismiss a filter without discarding unfinished edits', async ({
  page,
}) => {
  const toggle = page.getByRole('button', { name: /^高级筛选/ });
  await toggle.click();
  await page.getByLabel('地区', { exact: true }).fill('上海市');
  await page.getByRole('heading', { name: '发现好物' }).click();
  await expect(page.locator('#catalog-advanced')).toBeHidden();
  await toggle.click();
  await expect(page.getByLabel('地区', { exact: true })).toHaveValue('上海市');
  await page.getByRole('button', { name: '收起筛选' }).focus();
  await page.keyboard.press('Escape');
  await expect(page.locator('#catalog-advanced')).toBeHidden();
  await expect(toggle).toBeFocused();
});

test('native select commits a choice and dismisses through keyboard or outside click', async ({
  page,
}) => {
  const select = page.getByRole('combobox', { name: '排序', exact: true });
  await select.click();
  await expect
    .poll(() => select.evaluate((e) => e.matches(':open')))
    .toBe(true);
  await page.getByRole('option', { name: '价格从低到高', exact: true }).click();
  await expect(page).toHaveURL(/sort=price_asc/);
  await expect
    .poll(() => select.evaluate((e) => e.matches(':open')))
    .toBe(false);
  await select.click();
  await page.keyboard.press('Escape');
  await expect
    .poll(() => select.evaluate((e) => e.matches(':open')))
    .toBe(false);
  await select.click();
  await page.getByRole('heading', { name: '发现好物' }).click();
  await expect
    .poll(() => select.evaluate((e) => e.matches(':open')))
    .toBe(false);
});

test('saved-search success collapses with feedback; rejection preserves the form', async ({
  page,
}) => {
  let reject = true;
  await page.route('**/api/v1/me/searches', (route) =>
    route.fulfill(
      route.request().method() === 'POST' && reject
        ? {
            status: 400,
            json: { code: 'INVALID_NAME', message: '请修改条件名称' },
          }
        : { json: [] },
    ),
  );
  await page.locator('.saved-toggle').click();
  await page.getByLabel('给当前筛选取个名字').fill('常用找货条件');
  await page.getByRole('button', { name: '保存当前条件', exact: true }).click();
  await expect(page.locator('.saved-searches .mm-error')).toContainText(
    '请修改条件名称',
  );
  await expect(page.getByLabel('给当前筛选取个名字')).toHaveValue(
    '常用找货条件',
  );
  reject = false;
  await page.getByRole('button', { name: '保存当前条件', exact: true }).click();
  await expect(page.locator('.saved-form')).toBeHidden();
  await expect(page.locator('.saved-searches [role="status"]')).toContainText(
    '已保存',
  );
});

test('mobile navigation closes when its current-page link is chosen', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: '打开导航菜单', exact: true }).click();
  await page
    .locator('#mm-main-nav')
    .getByRole('link', { name: '全部闲置', exact: true })
    .click();
  await expect(
    page.getByRole('button', { name: '打开导航菜单', exact: true }),
  ).toHaveAttribute('aria-expanded', 'false');
});

for (const viewport of [
  { width: 320, height: 740 },
  { width: 844, height: 390 },
]) {
  test(`compact controls remain tappable without overflowing at ${viewport.width}px`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.route('**/api/v1/cart', (route) =>
      route.fulfill({
        json: [
          {
            id: 1,
            productId: 2,
            sellerId: 3,
            sellerNickname: '测试卖家',
            title: '带完整配件和收纳包的二手相机',
            priceCents: 10000,
            quantity: 2,
            stockAvailable: 5,
            invalid: false,
            deliveryMethod: 'EXPRESS',
            productStatus: 'ON_SALE',
          },
        ],
      }),
    );
    await page.route('**/api/v1/community/demands*', (route) =>
      route.fulfill({
        json: {
          items: [
            {
              id: 1,
              authorId: 2,
              authorNickname: '测试买家',
              title: '寻找一台适合日常记录的相机',
              description: '希望配件齐全，功能正常。',
              budgetMinCents: 10000,
              budgetMaxCents: 50000,
              region: '上海',
              status: 'PUBLISHED',
              isClosed: false,
              createdAt: '2026-01-01T10:00:00',
            },
          ],
          total: 1,
          totalPages: 1,
          page: 0,
          size: 20,
        },
      }),
    );
    for (const route of ['/search', '/cart', '/community/demands', '/login']) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      if (route === '/cart')
        await expect(page.getByLabel('增加数量')).toBeVisible();
      if (route === '/community/demands')
        await expect(
          page.getByRole('button', { name: '查看与回复' }),
        ).toBeVisible();
      const layout = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth,
        small: [...document.querySelectorAll('.mm-app button, .mm-app summary')]
          .filter(
            (e) =>
              e.getClientRects().length &&
              getComputedStyle(e).visibility !== 'hidden',
          )
          .filter((e) => {
            const r = e.getBoundingClientRect();
            return r.width < 43.9 || r.height < 43.9;
          })
          .map((e) => e.getAttribute('aria-label') || e.textContent.trim()),
      }));
      expect(layout, route).toEqual({ overflow: false, small: [] });
    }
  });
}

test('landscape support keeps its composer and footer inside the dialog', async ({
  page,
}) => {
  await page.route('**/api/v1/support/assistant', (r) =>
    r.fulfill({ json: { name: '麦仔', enabled: true, maxMessageChars: 1000 } }),
  );
  await page.route('**/api/v1/support/chat', (r) =>
    r.fulfill({
      json: [
        {
          id: 1,
          requestId: 'fixture',
          question: '如何买东西？',
          answer: '先查看商品说明和交付方式。',
          status: 'COMPLETE',
          source: 'SITE',
          createdAt: '2026-01-01T10:00:00',
        },
      ],
    }),
  );
  await page.setViewportSize({ width: 844, height: 390 });
  await page.getByRole('button', { name: '打开麦仔客服', exact: true }).click();
  await expect(page.getByLabel('给麦仔的问题')).toBeEnabled();
  const dialog = page.locator('#tina-dialog');
  const bounds = await dialog.boundingBox();
  for (const locator of [
    page.getByLabel('给麦仔的问题'),
    dialog.getByRole('button', { name: '发送', exact: true }),
    dialog.getByRole('button', { name: '清空对话', exact: true }),
    dialog.getByRole('link', { name: '帮助与人工工单 ↗' }),
  ]) {
    const box = await locator.boundingBox();
    expect(box).toBeTruthy();
    expect(box.y + box.height).toBeLessThanOrEqual(bounds.y + bounds.height);
    expect(box.x + box.width).toBeLessThanOrEqual(bounds.x + bounds.width);
  }
  expect(
    (await dialog.locator('.tina-body').boundingBox()).height,
  ).toBeGreaterThan(60);
  await page.getByRole('button', { name: '关闭麦仔客服', exact: true }).click();
  await expect(dialog).toBeHidden();
});

test('map confirmation closes the picker and keeps an address receipt without external calls', async ({
  page,
}) => {
  await page.evaluate(() => {
    const position = { getLng: () => 121.47, getLat: () => 31.23 };
    window.AMap = {
      plugin: (_names, cb) => cb(),
      Map: class {
        on() {}
        setCenter() {}
        setZoom() {}
        add() {}
        destroy() {}
      },
      Marker: class {
        setPosition() {}
      },
      Geocoder: class {
        getAddress(_point, cb) {
          cb('complete', {
            regeocode: {
              formattedAddress: '上海市测试广场',
              addressComponent: {
                province: '上海市',
                city: [],
                district: '测试区',
              },
            },
          });
        }
      },
      PlaceSearch: class {
        search(_query, cb) {
          cb('complete', {
            poiList: {
              pois: [
                { name: '测试广场', address: '上海市', location: position },
              ],
            },
          });
        }
      },
    };
  });
  await page.getByRole('button', { name: /^高级筛选/ }).click();
  await page.getByText('查找附近闲置', { exact: true }).click();
  await page
    .getByRole('button', { name: '使用地图辅助选址', exact: true })
    .click();
  await page.getByRole('textbox', { name: '搜索城市或地点' }).fill('测试广场');
  await page.getByRole('button', { name: '搜索地点', exact: true }).click();
  await page.getByRole('button', { name: /测试广场 · 上海市/ }).click();
  await page
    .getByRole('button', { name: '确认使用此地址', exact: true })
    .click();
  await expect(page.locator('#catalog-advanced')).toBeHidden();
  await page.getByRole('button', { name: /^高级筛选/ }).click();
  await expect(
    page.getByRole('button', { name: '重新选择地址', exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('textbox', { name: '搜索城市或地点' }),
  ).toBeHidden();
  await expect(
    page.getByText('已使用：上海市测试广场', { exact: true }),
  ).toBeVisible();
});

test('chat management dismisses outside and closes only after a confirmed successful action', async ({
  page,
}) => {
  let blocked = false;
  await page.route('**/api/v1/messages/**', (route) => {
    const request = route.request(),
      url = new URL(request.url()).pathname;
    if (url.endsWith('/block')) {
      if (request.method() === 'PUT') blocked = request.postDataJSON().blocked;
      return route.fulfill({
        json: { blockedByMe: blocked, blockedEitherDirection: blocked },
      });
    }
    if (url.endsWith('/summary'))
      return route.fulfill({
        json: { id: 5, otherUserId: 2, otherNickname: '测试会话对象' },
      });
    return route.fulfill({
      json: { items: [], hasMore: false, nextBeforeId: null },
    });
  });
  await page.goto('/messages/5');
  const menu = page.locator('.mm-chat__management');
  await menu.locator('summary').click();
  await page.getByText('与对方的交易沟通', { exact: true }).click();
  await expect(menu).not.toHaveAttribute('open');
  await menu.locator('summary').click();
  await page.getByRole('button', { name: '屏蔽对方', exact: true }).click();
  await page.getByRole('button', { name: '暂不操作', exact: true }).click();
  expect(blocked).toBe(false);
  await expect(menu).toHaveAttribute('open');
  await page.getByRole('button', { name: '屏蔽对方', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(menu).not.toHaveAttribute('open');
  expect(blocked).toBe(true);
  await expect(menu.locator('summary')).toBeFocused();
});

test('admin navigation dismisses; folding review materials preserves an unfinished reason', async ({
  page,
}) => {
  const product = {
    id: 3,
    title: '待审核测试物品',
    sellerId: 2,
    sellerNickname: '测试卖家',
    region: '上海',
    priceCents: 1000,
    status: 'PENDING_REVIEW',
    images: [],
    description: '真实使用情况',
    freightCents: 0,
  };
  await page.route('**/api/v1/auth/me', (r) =>
    r.fulfill({
      json: { id: 9, nickname: '测试管理员', roles: ['SUPER_ADMIN'] },
    }),
  );
  await page.route('**/api/v1/admin/products*', (r) =>
    r.fulfill({ json: { content: [product], totalPages: 1 } }),
  );
  await page.route('**/api/v1/admin/products/3', (r) =>
    r.fulfill({ json: product }),
  );
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/admin/products');
  const nav = page.getByRole('button', { name: '展开工作导航', exact: true });
  await nav.click();
  await page.getByRole('heading', { name: '商品审核', exact: true }).click();
  await expect(nav).toHaveAttribute('aria-expanded', 'false');
  await page
    .getByRole('button', { name: '查看完整审核材料', exact: true })
    .click();
  await page.getByLabel('审核理由', { exact: true }).fill('需要核对物品照片');
  await page.getByRole('button', { name: '收起审核材料', exact: true }).click();
  await expect(page.getByLabel('审核理由', { exact: true })).toBeHidden();
  await page
    .getByRole('button', { name: '查看完整审核材料', exact: true })
    .click();
  await expect(page.getByLabel('审核理由', { exact: true })).toHaveValue(
    '需要核对物品照片',
  );
});
