const { test, expect } = require('@playwright/test');

// Browser-only fixtures: never contact production, the database, maps or a model.
const requests = new WeakMap();
const failures = new WeakMap();
const time = '2026-01-01T10:00:00';
const products = Array.from({ length: 8 }, (_, index) => ({
  id: 31 + index,
  title: `完整配件二手相机 ${index + 1}`,
  condition: 'GOOD',
  region: '上海市',
  priceCents: 15000,
  freightCents: 800,
  deliveryMethods: ['EXPRESS', 'MEETUP'],
  stockAvailable: 3,
  sellerId: 2,
  sellerNickname: '相机体验卖家',
  coverImage: null,
  status: 'ON_SALE',
}));
const product = {
  ...products[0],
  categoryId: 1,
  seller: { id: 2, nickname: '相机体验卖家', avatarUrl: null },
  images: [],
  description: '日常使用留下轻微痕迹。镜头、充电器与收纳包齐全。',
  defects: '机身边角有轻微磨痕，功能正常。',
  supplyNote: '体验账号的相机资料，库存为模拟数量。',
  returnPromise: '具体问题请先联系卖家确认。',
  experienceSource: 'layout-test-fixture',
  specifications: {},
  shippingProvinces: [],
  createdAt: time,
};
const demands = [1, 2].map((id) => ({
  id,
  authorId: 2,
  authorNickname: '求购体验用户',
  title: `寻找配件齐全、功能正常的日常记录相机 ${id}`,
  description: '希望电池能够正常使用，可接受轻微外观磨损。请说明配件和成色。',
  budgetMinCents: 10000,
  budgetMaxCents: 50000,
  region: '上海市',
  status: 'PUBLISHED',
  isClosed: false,
  createdAt: time,
}));
const order = {
  id: 1,
  orderNo: 'LAYOUT001',
  buyerId: 1,
  sellerId: 2,
  sellerNickname: '相机体验卖家',
  deliveryMethod: 'EXPRESS',
  items: [
    {
      productId: 31,
      title: products[0].title,
      quantity: 1,
      unitPriceCents: 15000,
      subtotalCents: 15000,
    },
  ],
  goodsAmountCents: 15000,
  freightCents: 800,
  platformFeeCents: 5,
  retainedPlatformFeeCents: 5,
  totalCents: 15800,
  channelFeeCents: null,
  channelFeeConfirmed: false,
  expectedSellerNetCents: null,
  simulated: true,
  allocationStatus: 'NOT_RECORDED',
  fulfillmentStatus: 'PENDING_PAYMENT',
  payStatus: 'UNPAID',
  refundStatus: 'NONE',
  settleStatus: 'UNSETTLED',
  expiresAt: '2099-01-01T10:00:00',
  paidAt: null,
  shippedAt: null,
  autoConfirmAt: null,
  createdAt: time,
};
const communityPage = (items) => ({
  items,
  total: items.length,
  totalPages: 1,
  page: 0,
  size: 20,
});

test.beforeEach(async ({ page }) => {
  requests.set(page, []);
  failures.set(page, []);
  page.on('pageerror', (error) => failures.get(page).push(error.message));
  await page.addInitScript(() =>
    sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
  );
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.protocol === 'http:' && url.hostname === '127.0.0.1')
      return route.continue();
    return route.abort();
  });
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname.replace('/api/v1', '');
    requests
      .get(page)
      .push({
        path: pathname,
        method: request.method(),
        body: request.postData() ? request.postDataJSON() : null,
      });
    if (pathname === '/auth/csrf') return route.fulfill({ status: 204 });
    if (request.method() !== 'GET') {
      if (pathname === '/messages/conversations')
        return route.fulfill({ json: { id: 5 } });
      return route.fulfill({ json: {} });
    }
    const responses = {
      '/auth/me': {
        id: 1,
        nickname: '布局体验用户',
        email: 'layout@example.invalid',
        roles: ['USER'],
        sellerStatus: 'NONE',
      },
      '/categories': [
        {
          id: 1,
          name: '数码电子',
          children: [{ id: 11, name: '相机摄影', children: [] }],
        },
        { id: 2, name: '生活家居', children: [] },
      ],
      '/categories/1/specifications': [
        { key: 'model', label: '型号', hint: '' },
      ],
      '/products': {
        content: products,
        totalElements: products.length,
        totalPages: 1,
        page: 0,
        size: 20,
      },
      '/products/31': product,
      '/products/31/comments': communityPage([]),
      '/products/31/ratings': communityPage([]),
      '/community/users/2/reputation': {
        level: '暂无真实成交评价',
        scopes: [],
      },
      '/community/demands': communityPage(demands),
      '/community/demands/me': communityPage([]),
      '/community/demands/1/replies': communityPage([
        {
          id: 10,
          demandId: 1,
          authorId: 2,
          authorNickname: '回复体验用户',
          content: '这件相机带原装充电器，可先确认成色照片。',
          status: 'PUBLISHED',
          createdAt: time,
        },
      ]),
      '/community/replies/me': communityPage([]),
      '/orders': {
        content: [
          order,
          {
            ...order,
            id: 2,
            orderNo: 'LAYOUT002',
            fulfillmentStatus: 'CLOSED',
          },
        ],
        totalPages: 1,
        totalElements: 2,
        page: 0,
        size: 10,
      },
      '/orders/LAYOUT001': order,
      '/support/assistant': {
        name: '麦仔',
        enabled: false,
        maxMessageChars: 1000,
      },
      '/support/faq': [
        {
          topic: 'DELIVERY',
          title: '如何确认收到商品？',
          answer: '收到后先核对成色、数量与配件，再在原订单确认收货。',
        },
        {
          topic: 'PAYMENT',
          title: '体验付款是否扣款？',
          answer: '体验付款不扣真实资金。',
        },
      ],
      '/support/tickets': communityPage([
        { id: 8, title: '核对订单交付信息', status: 'OPEN', updatedAt: time },
      ]),
      '/support/chat': [],
      '/me/searches': [],
      '/ai/workflows/availability': { modelEnabled: false },
      '/messages/conversations/5/summary': {
        id: 5,
        otherUserId: 2,
        otherNickname: product.seller.nickname,
        productId: 31,
        productTitle: product.title,
      },
      '/messages/conversations/5/block': {
        blockedByMe: false,
        blockedEitherDirection: false,
      },
      '/messages/conversations/5/messages': {
        items: [],
        hasMore: false,
        nextBeforeId: null,
      },
    };
    return route.fulfill({ json: responses[pathname] ?? [] });
  });
});

test.afterEach(async ({ page }) => {
  expect(failures.get(page), 'uncaught browser errors').toEqual([]);
  expect(
    requests
      .get(page)
      .filter(
        (r) => r.method !== 'GET' && /^\/ai\/|^\/support\/chat/.test(r.path),
      ),
    'no model requests',
  ).toEqual([]);
});

async function expectNoOverflow(page) {
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
}

test('desktop demand board separates publishing, uses both columns, and keeps field validation', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/community/demands');
  const cards = page.locator('.mm-demand-post');
  await expect(cards).toHaveCount(2);
  const left = await cards.nth(0).boundingBox(),
    right = await cards.nth(1).boundingBox();
  expect(Math.abs(left.y - right.y)).toBeLessThan(2);
  expect(right.x).toBeGreaterThan(left.x + left.width);
  const publish = page.getByRole('button', {
    name: '＋ 发布求购',
    exact: true,
  });
  await expect(page.locator('.mm-demand-board__tabs')).not.toContainText(
    '发布求购',
  );
  await publish.click();
  const form = page.locator('.mm-demand-form');
  await expect(form).toBeVisible();
  const formBox = await form.boundingBox(),
    gridBox = await page.locator('.mm-demand-board__posts').boundingBox();
  expect(formBox.width).toBeGreaterThan(gridBox.width * 0.95);
  const details = await page.locator('.mm-demand-form__details').boundingBox(),
    preferences = await page
      .locator('.mm-demand-form__preferences')
      .boundingBox();
  expect(preferences.x).toBeGreaterThan(details.x + details.width);
  await expect(page.getByLabel('想要什么', { exact: true })).toBeFocused();
  await page.getByLabel('想要什么', { exact: true }).fill('寻找完整相机套装');
  await page
    .getByLabel('具体需求', { exact: true })
    .fill('希望有充电器和收纳包，可接受轻微磨损。');
  await page.getByLabel('最低预算（元）', { exact: true }).fill('500');
  await page.getByLabel('最高预算（元）', { exact: true }).fill('100');
  await form.getByRole('button', { name: '提交审核', exact: true }).click();
  const error = page.locator('#demand-budgetMax-error');
  await expect(error).toContainText('最高预算不能低于最低预算');
  const inputBox = await page
      .locator('input[aria-describedby="demand-budgetMax-error"]')
      .boundingBox(),
    errorBox = await error.boundingBox();
  expect(errorBox.y).toBeGreaterThanOrEqual(inputBox.y + inputBox.height);
  expect(
    requests
      .get(page)
      .filter((r) => r.method === 'POST' && r.path === '/community/demands'),
  ).toHaveLength(0);
  await expectNoOverflow(page);
});

test('320px demand form and replies use available width without overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/community/demands');
  const cards = page.locator('.mm-demand-post');
  await expect(cards).toHaveCount(2);
  const first = await cards.nth(0).boundingBox(),
    second = await cards.nth(1).boundingBox();
  expect(second.y).toBeGreaterThanOrEqual(first.y + first.height);
  await page.getByRole('button', { name: '＋ 发布求购', exact: true }).click();
  const details = await page.locator('.mm-demand-form__details').boundingBox(),
    preferences = await page
      .locator('.mm-demand-form__preferences')
      .boundingBox();
  expect(preferences.y).toBeGreaterThan(details.y + details.height);
  await page
    .locator('.mm-demand-form')
    .getByRole('button', { name: '取消', exact: true })
    .click();
  await cards
    .first()
    .getByRole('button', { name: /查看.*回复/ })
    .click();
  const replies = cards.first().locator('.demand-replies');
  await expect(
    replies.getByText('这件相机带原装充电器，可先确认成色照片。', {
      exact: true,
    }),
  ).toBeVisible();
  const composer = replies.locator('.demand-replies__compose');
  const composerBox = await composer.boundingBox(),
    repliesBox = await replies.boundingBox();
  expect(composerBox.width).toBeGreaterThan(repliesBox.width * 0.95);
  await composer
    .getByLabel('我能提供', { exact: true })
    .fill('有同类相机和充电器，可提供完整成色照片。');
  await composer.getByRole('button', { name: '提交审核', exact: true }).click();
  await expect(composer.getByRole('status')).toContainText('回复已提交');
  expect(
    requests
      .get(page)
      .filter(
        (r) => r.method === 'POST' && r.path === '/community/demands/1/replies',
      ),
  ).toHaveLength(1);
  await expectNoOverflow(page);
});

test('product contact is visible near price and creates one conversation with product context', async ({
  page,
}) => {
  await page.goto('/products/31');
  await expect(
    page.getByRole('heading', { name: product.title, exact: true }),
  ).toBeVisible();
  const contact = page.getByRole('button', { name: '联系卖家', exact: true });
  await expect(contact).toHaveCount(1);
  const contactBox = await contact.boundingBox(),
    factsBox = await page.locator('.mm-detail__supply').boundingBox();
  expect(contactBox.y + contactBox.height).toBeLessThan(factsBox.y);
  expect(
    requests
      .get(page)
      .filter(
        (r) => r.method === 'POST' && r.path === '/community/footprints/31',
      ),
  ).toHaveLength(1);
  await contact.click();
  await expect(page).toHaveURL(/\/messages\/5\?productId=31$/);
  const posts = requests
    .get(page)
    .filter((r) => r.method === 'POST' && r.path === '/messages/conversations');
  expect(posts).toHaveLength(1);
  expect(posts[0].body).toEqual({ recipientId: 2, productId: 31 });
});

test('order next step is apparent while each card remains a single non-nested link', async ({
  page,
}) => {
  await page.goto('/orders');
  const cards = page.locator('.mm-order-list__card');
  await expect(cards).toHaveCount(2);
  await expect(cards.first().locator('.mm-order-list__next')).toContainText(
    '核对金额，进入收银台',
  );
  await expect(cards.first().locator('.mm-order-list__action')).toHaveClass(
    /is-pending/,
  );
  await expect(cards.last().locator('.mm-order-list__action')).not.toHaveClass(
    /is-pending/,
  );
  for (const card of await cards.all()) {
    await expect(
      card.locator('button, a, input, select, textarea, [role="button"]'),
    ).toHaveCount(0);
    const box = await card.locator('.mm-order-list__action').boundingBox();
    expect(box.height).toBeGreaterThanOrEqual(44);
  }
  await cards.first().getByText('查看订单', { exact: false }).click();
  await expect(page).toHaveURL(/\/orders\/LAYOUT001$/);
});

for (const width of [1440, 390]) {
  test(`support center sections and editor remain usable at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto('/support');
    await expect(
      page.getByRole('heading', { name: '常见问题', exact: true }),
    ).toBeVisible();
    const main = await page.locator('.mm-support__main').boundingBox(),
      tickets = await page.locator('.mm-support__tickets').boundingBox();
    if (width === 1440) {
      expect(tickets.x).toBeGreaterThan(main.x + main.width);
      expect(Math.abs(tickets.y - main.y)).toBeLessThan(2);
    } else {
      expect(tickets.y).toBeGreaterThanOrEqual(main.y + main.height);
    }
    await page
      .getByRole('button', { name: '＋ 联系人工客服', exact: true })
      .click();
    const form = page.locator('#support-create-form');
    await expect(form).toBeVisible();
    await expect(page.getByLabel('问题标题', { exact: true })).toBeFocused();
    const formBox = await form.boundingBox(),
      mainAfter = await page.locator('.mm-support__main').boundingBox();
    expect(formBox.width).toBeGreaterThan(mainAfter.width * 0.95);
    await page.getByLabel('问题标题', { exact: true }).fill('需要核对交付信息');
    await form.getByRole('button', { name: '暂不提交', exact: true }).click();
    await page
      .getByRole('button', { name: '＋ 联系人工客服', exact: true })
      .click();
    await expect(page.getByLabel('问题标题', { exact: true })).toHaveValue(
      '需要核对交付信息',
    );
    await expectNoOverflow(page);
  });
}

test('desktop search shows the first product row within the initial viewport', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/search');
  const card = page.locator('.mm-product-card').first();
  await expect(card).toBeVisible();
  const title = await card.locator('.mm-product-card__title').boundingBox();
  expect(title.y + title.height).toBeLessThan(1000);
  expect(await page.evaluate(() => scrollY)).toBe(0);
  await expectNoOverflow(page);
});

test('320px category choices collapse after selection and preserve selected subcategory', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/search');
  const toggle = page.locator('.category-browser__toggle');
  const choices = page.locator('#catalog-category-options');
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await expect(choices).toBeHidden();
  await toggle.click();
  await choices.getByRole('button', { name: '数码电子', exact: true }).click();
  await expect(page).toHaveURL(/categoryId=1(?:&|$)/);
  await expect(choices).toBeHidden();
  await toggle.click();
  await choices.getByRole('button', { name: '相机摄影', exact: true }).click();
  await expect(page).toHaveURL(/categoryId=11(?:&|$)/);
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await expect(choices).toBeHidden();
  await toggle.click();
  await expect(
    choices.getByRole('button', { name: '相机摄影', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
  await expectNoOverflow(page);
});
