const { test, expect } = require('@playwright/test');

// All API/model/image responses are local fixtures. No production or model calls.
const fixtures = new WeakMap();
const images = [1, 2, 3, 4].map(
  (id) => `00000000-0000-4000-8000-${String(id).padStart(12, '0')}`,
);
const recognized = {
  title: '银色便携数码相机',
  categoryId: 11,
  description: '照片可见银色机身、黑色镜头与挂绳，具体型号和功能请核对实物。',
  visibleFeatures: ['银色机身', '可见黑色镜头'],
  checklist: ['请核对型号、功能与配件'],
};
const categories = [
  {
    id: 1,
    name: '数码电子',
    children: [
      { id: 11, name: '相机摄影', children: [] },
      {
        id: 12,
        name: '电脑',
        children: [{ id: 121, name: '笔记本电脑', children: [] }],
      },
    ],
  },
  {
    id: 2,
    name: '生活家居',
    children: [{ id: 21, name: '厨具', children: [] }],
  },
  { id: 3, name: '其他闲置', children: [] },
];
const items = [23000, 15000, 38000].map((priceCents, i) => ({
  id: i + 31,
  title: `银色相机样本 ${i + 1}`,
  priceCents,
  condition: 'GOOD',
  region: '上海市',
  coverImage: null,
  sellerNickname: `卖家 ${i + 1}`,
  experienceSource: i === 0 ? 'experience' : null,
  url: `/products/${i + 31}`,
}));
const response = (sampleItems = items) => ({
  currency: 'CNY',
  priceBasis: 'ASKING',
  sampleCount: sampleItems.length,
  minCents: sampleItems.length
    ? Math.min(...sampleItems.map((i) => i.priceCents))
    : null,
  maxCents: sampleItems.length
    ? Math.max(...sampleItems.map((i) => i.priceCents))
    : null,
  medianCents: sampleItems.length
    ? sampleItems.length === 1
      ? sampleItems[0].priceCents
      : 23000
    : null,
  matchLevel: sampleItems.length ? 'CATEGORY_KEYWORD' : 'NONE',
  keyword: '相机',
  items: sampleItems,
  fetchedAt: '2026-10-16T10:00:00',
  notice: '当前挂牌价参考，并非历史价格走势或成交价。',
});

test.beforeEach(async ({ page }) => {
  const state = {
    calls: [],
    pageErrors: [],
    items,
    enabled: true,
    remainingUserToday: 3,
    processing: true,
    fail: false,
    cached: false,
    cacheMiss: false,
    resultDelay: 0,
  };
  fixtures.set(page, state);
  page.on('pageerror', (error) => state.pageErrors.push(error.message));
  await page.addInitScript(
    ({ images }) => {
      sessionStorage.setItem('maimai-welcome-seen-v1', '1');
      if (!localStorage.getItem('listing-test-initialized')) {
        localStorage.setItem(
          'maimai:product-form:v1:1:new',
          JSON.stringify({
            schema: 1,
            form: {
              title: '',
              categoryId: '',
              description: '',
              priceYuan: '',
              stock: '1',
              condition: 'GOOD',
            },
            imageIds: images,
          }),
        );
        localStorage.setItem('listing-test-initialized', '1');
      }
    },
    { images },
  );
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.protocol === 'http:' && url.hostname === '127.0.0.1')
      return route.continue();
    return route.abort();
  });
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname.replace('/api/v1', ''),
      method = request.method();
    state.calls.push({
      path,
      method,
      body: request.postData() ? request.postDataJSON() : null,
    });
    if (path === '/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/auth/me')
      return route.fulfill({
        json: {
          id: 1,
          nickname: '识图体验卖家',
          email: 'listing@example.invalid',
          roles: ['USER', 'SELLER'],
          sellerStatus: 'APPROVED',
        },
      });
    if (path.startsWith('/me/listing-drafts/')) {
      if (method === 'PUT') {
        const payload = request.postDataJSON().payload;
        const allowed = [
          'schema',
          'form',
          'limitShipping',
          'imageIds',
          'productId',
        ];
        if (
          payload &&
          Object.keys(payload).some((key) => !allowed.includes(key))
        )
          return route.fulfill({
            status: 400,
            json: { code: 'DRAFT_INVALID', message: '草稿包含不支持的字段' },
          });
      }
      return route.fulfill({
        json: {
          version: 1,
          payload: method === 'PUT' ? request.postDataJSON().payload : null,
          updatedAt: null,
        },
      });
    }
    if (path.startsWith('/me/media/'))
      return route.fulfill({
        contentType: 'image/png',
        body: Buffer.from(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
          'base64',
        ),
      });
    if (path === '/me/listing-assistant/availability')
      return route.fulfill({
        json: {
          enabled: state.enabled,
          remainingToday: 20,
          remainingUserToday: state.remainingUserToday,
          maxImages: 3,
        },
      });
    if (path === '/me/listing-assistant/recognize') {
      if (state.cacheMiss && request.postDataJSON().cacheOnly)
        return route.fulfill({
          status: 404,
          json: {
            code: 'LISTING_AI_CACHE_MISS',
            message:
              '这组照片暂无可复用的识图结果，本次没有调用模型，可以继续手动填写。',
          },
        });
      return route.fulfill({
        json: {
          id: 7,
          status: state.processing
            ? 'PROCESSING'
            : state.fail
              ? 'FAILED'
              : 'SUCCEEDED',
          cached: state.cached,
          recognition: state.processing || state.fail ? null : recognized,
          message: state.fail ? '图片暂时无法识别，请换一张清晰照片。' : '',
        },
      });
    }
    if (path === '/me/listing-assistant/runs/7') {
      if (state.resultDelay)
        await new Promise((resolve) => setTimeout(resolve, state.resultDelay));
      return route.fulfill({
        json: {
          id: 7,
          status: state.fail ? 'FAILED' : 'SUCCEEDED',
          cached: false,
          recognition: state.fail ? null : recognized,
          message: state.fail ? '图片暂时无法识别，请换一张清晰照片。' : '',
        },
      });
    }
    if (path === '/me/listing-assistant/comparables')
      return route.fulfill({ json: response(state.items) });
    const defaults = {
      '/categories': categories,
      '/shipping-provinces': ['上海市'],
      '/support/assistant': { name: '麦仔', enabled: false },
      '/ai/workflows/availability': { modelEnabled: false },
      '/messages/unread-count': { count: 0 },
    };
    return route.fulfill({ json: defaults[path] ?? [] });
  });
});
test.afterEach(async ({ page }) =>
  expect(fixtures.get(page).pageErrors).toEqual([]),
);
const recognitionCalls = (page) =>
  fixtures
    .get(page)
    .calls.filter((call) => call.path === '/me/listing-assistant/recognize');
const comparisonCalls = (page) =>
  fixtures
    .get(page)
    .calls.filter((call) => call.path === '/me/listing-assistant/comparables');
async function open(page) {
  await page.goto('/publish');
  await expect(
    page.getByRole('combobox', { name: '商品大类', exact: true }),
  ).toBeVisible();
}
async function noOverflow(page) {
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
}

test('clicking recognition sends only selected images once, polls, fills blank editable fields and queries asking prices', async ({
  page,
}) => {
  await open(page);
  await expect(page.locator('input[type="file"]')).toHaveCount(1);
  await expect(
    page.getByRole('button', { name: '识图填写草稿', exact: true }),
  ).toBeEnabled();
  await page.getByRole('button', { name: '识图填写草稿', exact: true }).click();
  await expect(
    page.getByRole('status').filter({ hasText: '麦仔正在识别照片' }),
  ).toBeVisible();
  await expect(page.locator('#product-title')).toHaveValue(recognized.title);
  await expect(
    page.getByRole('combobox', { name: '商品大类', exact: true }),
  ).toHaveValue('1');
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveValue('11');
  await expect(page.getByLabel('商品描述', { exact: true })).toHaveValue(
    recognized.description,
  );
  await expect(page.getByLabel('价格（元）', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('库存', { exact: true })).toHaveValue('1');
  await expect(page.locator('.price-comparison__point')).toHaveCount(3);
  await expect(page.locator('.price-comparison__line')).toHaveCount(1);
  expect(recognitionCalls(page)).toHaveLength(1);
  expect(recognitionCalls(page)[0].body.imageIds).toEqual(images.slice(0, 3));
  expect(recognitionCalls(page)[0].body.ownerId).toBe(1);
  expect(recognitionCalls(page)[0].body.requestKey).toMatch(/^[a-f0-9-]{36}$/);
  expect(comparisonCalls(page)).toHaveLength(1);
  expect(
    fixtures
      .get(page)
      .calls.some((call) => call.path === '/me/listing-assistant/runs/7'),
  ).toBe(true);
  const saved = await page.evaluate(() =>
    JSON.parse(localStorage.getItem('maimai:product-form:v1:1:new')),
  );
  expect(saved.form.title).toBe(recognized.title);
  await noOverflow(page);
});

test('in-flight edits including typing then clearing are retained', async ({
  page,
}) => {
  await open(page);
  await page.getByRole('button', { name: '识图填写草稿', exact: true }).click();
  await page.locator('#product-title').fill('自己填写');
  await page.locator('#product-title').fill('');
  await page
    .getByLabel('商品描述', { exact: true })
    .fill('保留我自己填写的使用情况');
  await page
    .getByRole('combobox', { name: '商品大类', exact: true })
    .selectOption('2');
  await page
    .getByRole('combobox', { name: '小分类', exact: true })
    .selectOption('21');
  await expect(
    page.getByText('已保留你填写或调整过的标题、商品描述、分类。', {
      exact: false,
    }),
  ).toBeVisible();
  await expect(page.locator('#product-title')).toHaveValue('');
  await expect(page.getByLabel('商品描述', { exact: true })).toHaveValue(
    '保留我自己填写的使用情况',
  );
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveValue('21');
});

test('root-first selection clears mismatched children and restores a saved leaf', async ({
  page,
}) => {
  await open(page);
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole('combobox', { name: '商品大类', exact: true })
    .selectOption('1');
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).not.toContainText('厨具');
  await page
    .getByRole('combobox', { name: '小分类', exact: true })
    .selectOption('121');
  await page.reload();
  await expect(
    page.getByRole('combobox', { name: '商品大类', exact: true }),
  ).toHaveValue('1');
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveValue('121');
  await page
    .getByRole('combobox', { name: '商品大类', exact: true })
    .selectOption('2');
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveValue('');
  await page
    .getByRole('button', { name: '立即同步 / 重试', exact: true })
    .click();
  await expect(
    page.getByText('已同步云端，可在其他设备继续填写', { exact: true }),
  ).toBeVisible();
  const cloudWrite = fixtures
    .get(page)
    .calls.filter(
      (call) => call.path === '/me/listing-drafts/new' && call.method === 'PUT',
    )
    .at(-1);
  expect(Object.keys(cloudWrite.body.payload).sort()).toEqual([
    'form',
    'imageIds',
    'limitShipping',
    'productId',
    'schema',
  ]);
  expect(cloudWrite.body.payload.form.rootCategoryId).toBe('2');
  await page.reload();
  await expect(
    page.getByRole('combobox', { name: '商品大类', exact: true }),
  ).toHaveValue('2');
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveValue('');
  await page
    .getByRole('combobox', { name: '商品大类', exact: true })
    .selectOption('3');
  await expect(
    page.getByRole('combobox', { name: '小分类', exact: true }),
  ).toHaveCount(0);
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem('maimai:product-form:v1:1:new')).form
          .categoryId,
    ),
  ).toBe('3');
});

test('comparison does not call model and replacing a seller price requires confirmation', async ({
  page,
}) => {
  await open(page);
  await page.getByLabel('比价关键词', { exact: true }).fill('相机');
  await page.getByRole('button', { name: '查看本站报价', exact: true }).click();
  await expect(
    page.getByRole('list', { name: '比价样本商品' }).getByRole('link'),
  ).toHaveCount(3);
  await expect(
    page.getByRole('list', { name: '比价样本商品' }).getByRole('link').first(),
  ).toHaveAttribute('href', '/products/32');
  await expect(
    page.getByText('当前挂牌价参考，并非历史价格走势或成交价。', {
      exact: true,
    }),
  ).toBeVisible();
  await page.getByLabel('价格（元）', { exact: true }).fill('88');
  await expect(page.locator('.price-comparison__reference')).toHaveCount(1);
  await page
    .getByRole('button', { name: '采用中位参考价', exact: true })
    .click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: '暂不操作', exact: true }).click();
  await expect(page.getByLabel('价格（元）', { exact: true })).toHaveValue(
    '88',
  );
  await page
    .getByRole('button', { name: '采用中位参考价', exact: true })
    .click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(page.getByLabel('价格（元）', { exact: true })).toHaveValue(
    '230.00',
  );
  expect(recognitionCalls(page)).toHaveLength(0);
});

test('empty and singleton comparisons do not manufacture a line', async ({
  page,
}) => {
  fixtures.get(page).items = [];
  await open(page);
  await page.getByLabel('比价关键词', { exact: true }).fill('相机');
  await page.getByRole('button', { name: '查看本站报价', exact: true }).click();
  await expect(
    page.getByText('暂时没有匹配的在售商品。', { exact: false }),
  ).toBeVisible();
  await expect(page.locator('.price-comparison__line')).toHaveCount(0);
  await expect(
    page.getByRole('button', { name: '采用中位参考价' }),
  ).toHaveCount(0);
  fixtures.get(page).items = [items[0]];
  await page.getByRole('button', { name: '查看本站报价', exact: true }).click();
  await expect(page.locator('.price-comparison__point')).toHaveCount(1);
  await expect(page.locator('.price-comparison__line')).toHaveCount(0);
  await expect(
    page.getByText('目前只有 1 件匹配商品', { exact: false }),
  ).toBeVisible();
});

test('failed recognition keeps the draft and never silently retries', async ({
  page,
}) => {
  fixtures.get(page).fail = true;
  fixtures.get(page).processing = false;
  await open(page);
  await page.locator('#product-title').fill('我的手工标题');
  await page.getByRole('button', { name: '识图填写草稿', exact: true }).click();
  await expect(
    page.getByRole('alert').filter({ hasText: '图片暂时无法识别' }),
  ).toBeVisible();
  await expect(page.locator('#product-title')).toHaveValue('我的手工标题');
  expect(recognitionCalls(page)).toHaveLength(1);
  expect(comparisonCalls(page)).toHaveLength(0);
});

test('exhausted quota allows explicit cache lookup and keeps comparison available', async ({
  page,
}) => {
  fixtures.get(page).remainingUserToday = 0;
  await open(page);
  await expect(
    page.getByRole('button', { name: '查看已有识图结果', exact: true }),
  ).toBeEnabled();
  await expect(
    page.getByText('今日新识图次数已用完', { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByText('没有缓存时不会调用模型或消耗额度', { exact: false }),
  ).toBeVisible();
  await page.getByLabel('比价关键词', { exact: true }).fill('相机');
  await page.getByRole('button', { name: '查看本站报价', exact: true }).click();
  await expect(page.locator('.price-comparison__point')).toHaveCount(3);
  fixtures.get(page).enabled = false;
  await page.reload();
  await expect(
    page.getByText('识图服务暂未开启，可继续手动填写和本站比价。', {
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole('button', { name: '识图填写草稿', exact: true }),
  ).toBeDisabled();
  expect(recognitionCalls(page)).toHaveLength(0);
});

test('exhausted quota reuses cached photos only after an explicit click', async ({
  page,
}) => {
  const state = fixtures.get(page);
  state.remainingUserToday = 0;
  state.cached = true;
  state.processing = false;
  await open(page);
  expect(recognitionCalls(page)).toHaveLength(0);
  await page
    .getByRole('button', { name: '查看已有识图结果', exact: true })
    .click();
  await expect(page.locator('#product-title')).toHaveValue(recognized.title);
  await expect(
    page.getByText('已取回这组照片的识图结果，本次未重复调用模型。', {
      exact: true,
    }),
  ).toBeVisible();
  expect(recognitionCalls(page)).toHaveLength(1);
  expect(recognitionCalls(page)[0].body.cacheOnly).toBe(true);
});

test('cache miss preserves input and never automatically starts a new recognition', async ({
  page,
}) => {
  const state = fixtures.get(page);
  state.remainingUserToday = 0;
  state.cacheMiss = true;
  await page.setViewportSize({ width: 320, height: 740 });
  await open(page);
  await page.locator('#product-title').fill('保留卖家自己填写的名称');
  await page
    .getByRole('button', { name: '查看已有识图结果', exact: true })
    .click();
  await expect(
    page.getByRole('alert').filter({ hasText: '本次没有调用模型' }),
  ).toBeVisible();
  await expect(page.locator('#product-title')).toHaveValue(
    '保留卖家自己填写的名称',
  );
  expect(recognitionCalls(page)).toHaveLength(1);
  expect(recognitionCalls(page)[0].body.cacheOnly).toBe(true);
  expect(comparisonCalls(page)).toHaveLength(0);
  await noOverflow(page);
});

test('clearing the form while recognition is pending discards its late response', async ({
  page,
}) => {
  await open(page);
  await page.getByRole('button', { name: '识图填写草稿', exact: true }).click();
  await page.getByRole('button', { name: '一键清空', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(page.locator('#product-title')).toHaveValue('');
  await expect(page.locator('.draft-photo-grid figure')).toHaveCount(0);
  await page.waitForTimeout(2200);
  await expect(page.locator('#product-title')).toHaveValue('');
  expect(comparisonCalls(page)).toHaveLength(0);
});

test('late product load cannot replace the new listing draft after route change', async ({
  page,
}) => {
  let releaseProduct;
  const pendingProduct = new Promise((resolve) => {
    releaseProduct = resolve;
  });
  let requested = false;
  await page.route('**/api/v1/seller/products/11', async (route) => {
    requested = true;
    await pendingProduct;
    await route.fulfill({
      json: {
        id: 11,
        title: '不应覆盖新草稿的旧商品',
        categoryId: 11,
        description: '旧商品描述',
        condition: 'GOOD',
        defects: '',
        priceCents: 20000,
        stockAvailable: 1,
        region: '上海市',
        deliveryMethods: ['EXPRESS'],
        freightCents: 0,
        returnPromise: '',
        latitude: null,
        longitude: null,
        shippingProvinces: [],
        status: 'DRAFT',
        images: [],
        seller: { id: 1, nickname: '卖家' },
        createdAt: '2026-10-16T10:00:00',
      },
    });
  });
  await page.goto('/publish/11');
  await expect.poll(() => requested).toBe(true);
  await page.getByRole('link', { name: '发布闲置', exact: true }).click();
  await expect(page).toHaveURL(/\/publish$/);
  await expect(page.locator('#product-title')).toHaveValue('');
  await page.locator('#product-title').fill('继续填写的新草稿');
  releaseProduct();
  await page.waitForTimeout(500);
  await expect(page.locator('#product-title')).toHaveValue('继续填写的新草稿');
  await expect(page.locator('.mm-edit__state')).toHaveCount(0);
  expect(
    await page.evaluate(
      () =>
        JSON.parse(localStorage.getItem('maimai:product-form:v1:1:new')).form
          .title,
    ),
  ).toBe('继续填写的新草稿');
  expect(
    fixtures
      .get(page)
      .calls.filter(
        (call) =>
          call.path === '/me/listing-drafts/new' && call.method === 'PUT',
      )
      .every(
        (call) => call.body.payload?.form?.title !== '不应覆盖新草稿的旧商品',
      ),
  ).toBe(true);
});

for (const [width, height] of [
  [320, 740],
  [390, 844],
  [844, 390],
  [1280, 900],
]) {
  test(`recognition and comparison controls fit ${width} × ${height}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height });
    await open(page);
    await page.getByLabel('比价关键词', { exact: true }).fill('相机');
    await page
      .getByRole('button', { name: '查看本站报价', exact: true })
      .click();
    await expect(page.locator('.price-comparison__point')).toHaveCount(3);
    await noOverflow(page);
    const input = page.getByLabel('比价关键词', { exact: true });
    if (width < 600)
      expect(
        await input.evaluate((element) =>
          parseFloat(getComputedStyle(element).fontSize),
        ),
      ).toBeGreaterThanOrEqual(16);
    await expect(
      page.getByRole('button', { name: '采用中位参考价', exact: true }),
    ).toBeVisible();
    await page.screenshot({
      path: `.local/listing-assistant-qa/listing-${width}.png`,
      fullPage: true,
    });
  });
}
