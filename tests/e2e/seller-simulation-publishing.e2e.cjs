const { test, expect } = require('@playwright/test');

// Only browser fixtures. No real model, database, uploads, email or payment calls.
const scenarios = new WeakMap();
const source = 'maimai-seller-simulation-v1';
const time = '2026-10-16T12:00:00';
const imageId = '00000000-0000-4000-8000-000000000001';
const pageResult = content => ({ content, totalElements: content.length, totalPages: 1, page: 0, size: 20 });
const communityResult = { items: [], total: 0, totalPages: 0, page: 0, size: 10 };
const initialProduct = () => ({ id: 901, title: '卖家自行拍摄的台灯', categoryId: 11,
  description: '台灯灯罩有轻微磨痕，卖家自行核实功能。', condition: 'GOOD', defects: '灯罩轻微磨痕',
  priceCents: 3500, stockAvailable: 2, stockReserved: 0, stockSold: 0, region: '上海市',
  deliveryMethods: ['EXPRESS'], freightCents: 0, returnPromise: '按平台规则处理', status: 'DRAFT',
  seller: { id: 1, nickname: '台灯卖家', avatarUrl: null }, images: [{ id: 1, path: '/uploads/products/test-lamp.jpg', sort: 0 }],
  shippingProvinces: [], specifications: {}, createdAt: time, updatedAt: time, reviewReason: null,
  experienceSource: null, supplyNote: null });

test.beforeEach(async ({ page }) => {
  const state = { role: 'seller', sellerStatus: 'APPROVED', mode: 'SIMULATED', channelStatus: 'PENDING',
    product: initialProduct(), calls: [], pageErrors: [], cart: false };
  scenarios.set(page, state);
  page.on('pageerror', error => state.pageErrors.push(error.message));
  await page.addInitScript(({ imageId }) => {
    sessionStorage.setItem('maimai-welcome-seen-v1', '1');
    if (!localStorage.getItem('publishing-fixture-ready')) {
      localStorage.setItem('maimai:product-form:v1:1:new', JSON.stringify({ schema: 1,
        form: { title: '卖家自行拍摄的台灯', categoryId: '11', rootCategoryId: '1', description: '台灯灯罩有轻微磨痕，卖家自行核实功能。',
          priceYuan: '35.00', condition: 'GOOD', defects: '灯罩轻微磨痕', stock: '2', region: '上海市', deliveryMethods: ['EXPRESS'], freightYuan: '0', specifications: {} },
        imageIds: [imageId] }));
      localStorage.setItem('publishing-fixture-ready', '1');
    }
  }, { imageId });
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.protocol === 'http:' && url.hostname === '127.0.0.1') return route.continue();
    return route.abort();
  });
  await page.route('**/uploads/products/**', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#ccc"/></svg>' }));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), endpoint = url.pathname.replace('/api/v1', ''), method = request.method();
    const body = request.postData() ? request.postDataJSON() : null;
    state.calls.push({ endpoint, method, body });
    if (endpoint === '/auth/csrf') return route.fulfill({ status: 204 });
    const approved = state.sellerStatus === 'APPROVED';
    const canPublish = approved && (state.mode === 'SIMULATED' || state.channelStatus === 'QUALIFIED');
    if (endpoint === '/auth/me') return route.fulfill({ json: { id: state.role === 'seller' ? 1 : state.role === 'admin' ? 99 : 2,
      email: 'fixture@example.invalid', nickname: state.role === 'seller' ? '台灯卖家' : '验收用户',
      roles: state.role === 'admin' ? ['USER', 'SUPER_ADMIN'] : approved && state.role === 'seller' ? ['USER', 'SELLER'] : ['USER'],
      sellerStatus: state.role === 'seller' ? state.sellerStatus : 'NONE' } });
    if (endpoint === '/me/seller-application') return route.fulfill({ json: { id: 1, status: state.sellerStatus,
      channelStatus: state.channelStatus, intro: '个人闲置', reason: null, createdAt: time, publishingMode: state.mode, canPublish,
      publishingMessage: !approved ? '卖家审核通过后才能提交商品审核。' : state.mode === 'SIMULATED'
        ? '当前可发布模拟交易商品，无需开通真实收款渠道。' : canPublish ? '真实收款渠道已开通，可提交商品审核。' : '请先完成真实收款渠道资格，草稿仍可保存。' } });
    if (endpoint.startsWith('/me/listing-drafts/')) return route.fulfill({ json: { version: 1, payload: method === 'PUT' ? body.payload : null, updatedAt: null } });
    if (endpoint.startsWith('/me/media/')) return route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64') });
    if (endpoint === '/seller/products' && method === 'POST') {
      if (!approved) return route.fulfill({ status: 403, json: { code: 'SELLER_NOT_APPROVED', message: '卖家尚未通过审核' } });
      Object.assign(state.product, body, { status: 'DRAFT' });
      return route.fulfill({ json: { id: 901 } });
    }
    if (endpoint === '/seller/products/901' && method === 'PUT') { Object.assign(state.product, body); return route.fulfill({ json: {} }); }
    if (endpoint === '/seller/products/901/draft-images') return route.fulfill({ json: {} });
    if (endpoint === '/seller/products/901/submit') {
      if (!canPublish) return route.fulfill({ status: 403, json: { code: 'SELLER_NOT_QUALIFIED', message: '真实交易需先开通收款渠道，草稿已保留。' } });
      state.product.status = 'PENDING_REVIEW';
      if (state.mode === 'SIMULATED') state.product.experienceSource = source;
      return route.fulfill({ json: {} });
    }
    if (endpoint === '/admin/products/901/review') {
      expect(state.role).toBe('admin'); expect(state.product.status).toBe('PENDING_REVIEW');
      state.product.status = body.approve ? 'ON_SALE' : 'REJECTED'; return route.fulfill({ json: {} });
    }
    if (['/seller/products/901', '/admin/products/901', '/products/901'].includes(endpoint)) return route.fulfill({ json: state.product });
    if (endpoint === '/seller/products' || endpoint === '/admin/products') {
      const selected = url.searchParams.get('status');
      return route.fulfill({ json: pageResult(!selected || selected === state.product.status ? [{ ...state.product, sellerId: 1, sellerNickname: '台灯卖家', coverImage: null }] : []) });
    }
    if (endpoint === '/cart') {
      if (method === 'POST') { state.cart = true; return route.fulfill({ json: {} }); }
      return route.fulfill({ json: state.cart ? [{ id: 7, productId: 901, title: state.product.title, coverImage: null,
        priceCents: 3500, quantity: 1, deliveryMethod: 'EXPRESS', stockAvailable: 2, sellerId: 1, sellerNickname: '台灯卖家', productStatus: 'ON_SALE', invalid: false }] : [] });
    }
    if (endpoint === '/experience/checkout' || endpoint === '/checkout') return route.fulfill({ json: { batchNo: 'FIXTURE-BATCH', orders: [] } });
    if (/\/reputation$/.test(endpoint)) return route.fulfill({ json: { level: '暂无评价', scopes: [] } });
    if (/\/comments$|\/ratings$/.test(endpoint)) return route.fulfill({ json: communityResult });
    if (/\/revisions$/.test(endpoint)) return route.fulfill({ json: pageResult([]) });
    const defaults = {
      '/categories': [{ id: 1, name: '生活家居', children: [{ id: 11, name: '照明灯具', children: [] }] }],
      '/categories/11/specifications': [], '/shipping-provinces': ['上海市'],
      '/support/assistant': { name: '麦仔', enabled: false }, '/ai/workflows/availability': { modelEnabled: false },
      '/me/listing-assistant/availability': { enabled: false, remainingToday: 0, remainingUserToday: 0, maxImages: 3 },
      '/messages/unread-count': { count: 0 }, '/me/notifications/unread-count': { count: 0 },
      '/me/notifications': pageResult([]), '/me/addresses': [{ id: 5, receiver: '测试买家', phone: '13800000000', region: '上海市', detail: '测试用收货信息', isDefault: true, createdAt: time }],
      '/seller/tasks': { counts: {}, items: [] }, '/me/orders': pageResult([]), '/orders': pageResult([]),
      '/me/bargains': [], '/seller/bargains': [], '/community/favorites': [], '/community/follows': [],
    };
    return route.fulfill({ json: defaults[endpoint] ?? [] });
  });
});
test.afterEach(async ({ page }) => {
  const state = scenarios.get(page);
  expect(state.pageErrors).toEqual([]);
  expect(state.calls.some(call => /recognize|\/ai\/workflows\//.test(call.endpoint) && call.method === 'POST')).toBe(false);
});
const notice = page => page.getByRole('status', { name: '发布与收款说明' });
const noOverflow = async page => expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

test('publishing: approved seller sees consistent simulated capability in account, workspace and publish at 320 and 390px', async ({ page }) => {
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    for (const route of ['/me?tab=seller', '/seller', '/publish']) {
      await page.goto(route);
      await expect(notice(page)).toContainText('模拟交易发布');
      await expect(notice(page)).toContainText('可提交商品审核');
      await expect(notice(page)).toContainText('无需开通真实收款渠道');
      await expect(notice(page)).toContainText('不代表已开通真实收款');
      await noOverflow(page);
    }
  }
});
test('publishing: pending seller retains text without creating or submitting a product', async ({ page }) => {
  const state = scenarios.get(page); state.sellerStatus = 'PENDING';
  await page.goto('/publish');
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click();
  await expect(page.locator('#publish-save-feedback')).toHaveText('填写内容已保存到云端。完成卖家审核后，可继续保存为商品并提交审核。');
  expect(state.calls.filter(call => call.method === 'POST' && call.endpoint.startsWith('/seller/products'))).toHaveLength(0);
  await expect(page.locator('#product-title')).toHaveValue('卖家自行拍摄的台灯');
});
test('publishing: real mode keeps channel qualification and preserves the rejected draft', async ({ page }) => {
  const state = scenarios.get(page); state.mode = 'REAL_PAYMENT';
  await page.goto('/publish/901');
  await expect(notice(page)).toContainText('真实交易发布');
  await expect(notice(page)).toContainText('发布条件待满足');
  await page.getByRole('button', { name: '保存并提交审核', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: '真实交易需先开通收款渠道，草稿已保留。' }).first()).toBeVisible();
  await expect(page.locator('#product-title')).toHaveValue(state.product.title);
  expect(state.product.status).toBe('DRAFT');
  expect(state.product.experienceSource).toBeNull();
});
test('publishing: saved seller photo draft still needs admin review then cart opens experience checkout', async ({ page }) => {
  const state = scenarios.get(page);
  await page.goto('/publish');
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click();
  await expect(page).toHaveURL(/\/publish\/901$/);
  expect(state.product.status).toBe('DRAFT');
  await page.getByRole('button', { name: '保存并提交审核', exact: true }).click();
  await expect(page.getByRole('region', { name: '商品当前状态' })).toContainText('商品已进入审核队列');
  expect(state.product.status).toBe('PENDING_REVIEW');
  state.role = 'admin';
  await page.goto('/admin/products');
  await page.getByRole('button', { name: '查看完整审核材料', exact: true }).click();
  await page.getByLabel('审核理由', { exact: true }).fill('已核对商品资料，按模拟交易流程审核通过。');
  await page.getByRole('button', { name: '通过', exact: true }).click();
  await expect(page.getByText('暂无此状态商品')).toBeVisible();
  expect(state.product.status).toBe('ON_SALE');
  state.role = 'buyer';
  await page.goto('/products/901');
  await expect(page.getByText('商品资料与照片由卖家提供；当前仅供模拟交易，不发生扣款或实际发货。')).toBeVisible();
  await expect(page.getByText(/图片为生成素材/)).toHaveCount(0);
  await expect(page.getByRole('button', { name: '体验下单', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '加入购物车', exact: true }).click();
  await page.goto('/cart');
  await page.getByLabel('全选可购买商品', { exact: true }).check();
  await page.getByRole('button', { name: /^去结算/ }).click();
  await expect(page).toHaveURL(/cartItemIds=7/);
  const submit = page.getByRole('button', { name: '提交体验订单', exact: true });
  await expect(submit).toBeDisabled();
  await page.getByLabel('我了解这是体验订单，扫码不扣款，也不安排实际发货。').check();
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect.poll(() => state.calls.filter(call => call.endpoint === '/experience/checkout' && call.method === 'POST').length).toBe(1);
  expect(state.calls.some(call => call.endpoint === '/checkout' && call.method === 'POST')).toBe(false);
  expect(state.calls.find(call => call.endpoint === '/experience/checkout').body.removeCartItemIds).toEqual([7]);
});
for (const productSource of ['maimai-experience-045', 'maimai-experience-catalog-021']) {
  test(`publishing: ${productSource} keeps explicit simulation checkout consent`, async ({ page }) => {
    const state = scenarios.get(page); state.role = 'buyer'; state.product.status = 'ON_SALE'; state.product.experienceSource = productSource;
    await page.goto('/checkout?items=' + encodeURIComponent(JSON.stringify([{ productId: 901, quantity: 1, deliveryMethod: 'EXPRESS' }])));
    await expect(page.getByRole('button', { name: '提交体验订单', exact: true })).toBeDisabled();
    await page.getByLabel('我了解这是体验订单，扫码不扣款，也不安排实际发货。').check();
    await expect(page.getByRole('button', { name: '提交体验订单', exact: true })).toBeEnabled();
    await noOverflow(page);
  });
}
test('publishing: arbitrary source never grants experience payment or labels seller photos generated', async ({ page }) => {
  const state = scenarios.get(page); state.role = 'buyer'; state.product.status = 'ON_SALE'; state.product.experienceSource = 'maimai-seller-simulation-v1-extra';
  await page.goto('/products/901');
  await expect(page.getByRole('button', { name: '立即购买', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: '体验下单', exact: true })).toHaveCount(0);
  await expect(page.getByText(/图片为生成素材/)).toHaveCount(0);
  await page.goto('/checkout?items=' + encodeURIComponent(JSON.stringify([{ productId: 901, quantity: 1, deliveryMethod: 'EXPRESS' }])));
  await expect(page.getByText('部分商品暂未开放体验结算，请返回购物车调整')).toBeVisible();
  await expect(page.getByRole('button', { name: '提交订单', exact: true })).toBeDisabled();
});
test('publishing: ordinary products retain the real checkout route and do not show simulated consent', async ({ page }) => {
  const state = scenarios.get(page); state.role = 'buyer'; state.product.status = 'ON_SALE'; state.product.experienceSource = null;
  await page.goto('/checkout?items=' + encodeURIComponent(JSON.stringify([{ productId: 901, quantity: 1, deliveryMethod: 'EXPRESS' }])));
  await expect(page.locator('.mm-checkout__experience')).toHaveCount(0);
  const submit = page.getByRole('button', { name: '提交订单', exact: true });
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect.poll(() => state.calls.filter(call => call.endpoint === '/checkout' && call.method === 'POST').length).toBe(1);
  expect(state.calls.some(call => call.endpoint === '/experience/checkout' && call.method === 'POST')).toBe(false);
});
