const { test, expect } = require('@playwright/test');
const fs = require('fs'),
  path = require('path');
const BASE = 'http://127.0.0.1:5173',
  RUN = process.env.MAIMAI_036_RUN;
const OUT = path.resolve(__dirname, '../../.local/screenshots');
const EMAIL = `e2e036-${RUN}@example.invalid`,
  PASSWORD = 'E2EAccount#2026';
const png = fs.readFileSync(path.join(__dirname, 'fixture8x8.png'));
const evidence = {
  run: RUN,
  email: EMAIL,
  screenshots: [],
  widths: [],
  mockedCheckout: [],
  prohibitedWrites: [],
  pageErrors: [],
};
let context, page, product, cartQuantity;
test.describe.configure({ mode: 'serial' });

async function read(endpoint) {
  if (!endpoint.startsWith('/api/v1/'))
    throw Error('Local application GET only');
  const response = await context.request.get(BASE + endpoint);
  expect(response.ok(), endpoint + ' HTTP ' + response.status()).toBe(true);
  return response.json();
}
function responseFor(method, endpoint) {
  return page.waitForResponse(
    (r) =>
      r.request().method() === method && new URL(r.url()).pathname === endpoint,
  );
}
async function decoded(image) {
  await expect(image).toBeVisible();
  await expect
    .poll(() => image.evaluate((img) => img.complete && img.naturalWidth > 0))
    .toBe(true);
}
async function stable() {
  await expect(page.locator('#main-content h1').first()).toBeVisible();
  await expect(page.locator('.mm-skeleton')).toHaveCount(0);
  await page.evaluate(async () => {
    await document.fonts.ready;
    window.scrollTo({ top: 0, behavior: 'instant' });
  });
}
async function screenshot(name) {
  await stable();
  const filename = `036-${RUN}-${name}.png`;
  await page.screenshot({ path: path.join(OUT, filename), fullPage: true });
  evidence.screenshots.push(filename);
}
async function widthCheck(label) {
  const result = await page.evaluate(() => ({
    viewport: innerWidth,
    body: document.body.scrollWidth,
    document: document.documentElement.scrollWidth,
  }));
  expect(result.body, label + ' body').toBeLessThanOrEqual(result.viewport);
  expect(result.document, label + ' document').toBeLessThanOrEqual(
    result.viewport,
  );
  evidence.widths.push({ label, ...result });
}
const tab = (name) => page.getByRole('tab', { name, exact: true });

test.beforeAll(async ({ browser }) => {
  context = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    locale: 'zh-CN',
  });
  // Never forward order, payment or refund writes from this test browser.
  await context.route('**/api/v1/**', async (route) => {
    const request = route.request(),
      endpoint = new URL(request.url()).pathname;
    if (request.method() === 'POST' && endpoint === '/api/v1/checkout') {
      const data = request.postDataJSON();
      evidence.mockedCheckout.push({
        idempotencyKey: data.idempotencyKey,
        items: data.items,
        addressId: data.addressId,
      });
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'E2E_SERVICE_UNAVAILABLE',
          message: '036 测试模拟暂时不可用，未创建订单。',
        }),
      });
      return;
    }
    if (
      !['GET', 'HEAD'].includes(request.method()) &&
      /\/(orders|payments?|refunds?|aftersales|dev\/mock-pay)(\/|$)/.test(
        endpoint,
      )
    ) {
      evidence.prohibitedWrites.push({ method: request.method(), endpoint });
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'E2E_PROHIBITED_WRITE',
          message: '036 测试禁止订单与资金写入。',
        }),
      });
      return;
    }
    await route.continue();
  });
  page = await context.newPage();
  page.on('pageerror', (error) => evidence.pageErrors.push(error.message));
  const listing = await read('/api/v1/products?page=0&size=50');
  for (const candidate of listing.content) {
    const detail = await read('/api/v1/products/' + candidate.id);
    if (
      detail.stockAvailable >= 1 &&
      detail.deliveryMethods.includes('EXPRESS') &&
      (!detail.shippingProvinces?.length ||
        detail.shippingProvinces.includes('上海市'))
    ) {
      product = detail;
      break;
    }
  }
  expect(product, 'An existing on-sale express product is needed').toBeTruthy();
  cartQuantity = Math.min(2, product.stockAvailable);
  evidence.product = {
    id: product.id,
    title: product.title,
    stockAvailable: product.stockAvailable,
  };
});
test.afterEach(async ({}, info) => {
  expect(evidence.pageErrors).toEqual([]);
  if (info.status === info.expectedStatus || !page) return;
  await page.screenshot({
    path: path.join(OUT, `036-${RUN}-failure-${info.title.slice(0, 2)}.png`),
    fullPage: true,
  });
  fs.writeFileSync(
    path.join(OUT, `036-${RUN}-failure-${info.title.slice(0, 2)}.txt`),
    await page.locator('body').innerText(),
  );
});
test.afterAll(async () => {
  fs.writeFileSync(
    path.join(OUT, `036-${RUN}-evidence.json`),
    JSON.stringify(evidence, null, 2),
  );
  await context?.close();
});

test('01 one fresh account registers with explicit consent and password reveal', async () => {
  await page.goto(BASE + '/register');
  await expect(page.getByRole('checkbox')).not.toBeChecked();
  await page.getByLabel('邮箱', { exact: true }).fill(EMAIL);
  await page.getByRole('button', { name: '发送验证码', exact: true }).click();
  await expect(
    page.getByText('验证码已发送，10 分钟内有效，请查收邮箱', { exact: true }),
  ).toBeVisible();
  const mailbox = await read(
    '/api/v1/dev/mailbox?email=' + encodeURIComponent(EMAIL),
  );
  const code = mailbox[0]?.content.match(/验证码为：(\d{6})/)?.[1];
  expect(code).toBeTruthy();
  await page.getByLabel('邮箱验证码', { exact: true }).fill(code);
  await page.getByLabel('昵称', { exact: true }).fill('036新同学');
  const password = page.getByLabel('密码', { exact: true });
  await password.fill(PASSWORD);
  await page.getByRole('button', { name: '显示密码', exact: true }).click();
  await expect(password).toHaveAttribute('type', 'text');
  await expect(password).toBeFocused();
  await page.getByRole('button', { name: '隐藏密码', exact: true }).click();
  await expect(password).toHaveAttribute('type', 'password');
  await expect(password).toBeFocused();
  await page.getByLabel('确认密码', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: '注册并登录', exact: true }).click();
  await expect(
    page.getByText('请阅读并勾选同意条款后注册', { exact: true }),
  ).toBeVisible();
  await page.getByRole('checkbox').check();
  const registered = responseFor('POST', '/api/v1/auth/register');
  await page.getByRole('button', { name: '注册并登录', exact: true }).click();
  expect((await registered).ok()).toBe(true);
  await expect(
    page.getByRole('button', { name: '打开个人菜单', exact: true }),
  ).toBeVisible();
  const me = await read('/api/v1/auth/me');
  expect(me.email).toBe(EMAIL);
  evidence.accountId = me.id;
  evidence.registration = {
    explicitConsent: true,
    captureMailboxOnly: EMAIL,
    passwordRevealAndFocus: true,
  };
});

test('02 own nickname avatar and address persist while account tabs and personal menu work', async () => {
  await page.getByRole('button', { name: '打开个人菜单', exact: true }).click();
  await page.getByRole('menuitem', { name: '个人中心', exact: true }).click();
  await expect(page).toHaveURL(BASE + '/me');
  const nickname = '036资料-' + RUN;
  await page.getByLabel('昵称', { exact: true }).fill(nickname);
  await page.getByRole('button', { name: '保存昵称', exact: true }).click();
  await expect(page.getByText('昵称已更新', { exact: true })).toBeVisible();
  const uploaded = responseFor('POST', '/api/v1/me/avatar');
  await page
    .getByLabel('上传头像', { exact: true })
    .setInputFiles({
      name: '036-avatar.png',
      mimeType: 'image/png',
      buffer: png,
    });
  const avatarResponse = await uploaded;
  expect(avatarResponse.ok()).toBe(true);
  const avatar = (await avatarResponse.json()).avatarUrl;
  await expect(page.getByText('头像已更新', { exact: true })).toBeVisible();
  await page.reload();
  await stable();
  await expect(page.getByLabel('昵称', { exact: true })).toHaveValue(nickname);
  await expect(
    page.getByRole('img', { name: '我的头像', exact: true }),
  ).toHaveAttribute('src', avatar);
  await decoded(page.getByRole('img', { name: '我的头像', exact: true }));
  await screenshot('profile-persisted-desktop');
  await tab('基本资料').focus();
  await page.keyboard.press('ArrowRight');
  await expect(tab('收货地址')).toBeFocused();
  await expect(tab('收货地址')).toHaveAttribute(
    'aria-controls',
    'account-panel-addresses',
  );
  await expect(page.getByRole('tabpanel')).toHaveCount(1);
  await page.keyboard.press('End');
  await expect(tab(/站内通知/)).toBeFocused();
  await page.keyboard.press('Home');
  await expect(tab('基本资料')).toBeFocused();
  await tab('收货地址').click();
  await page.getByRole('button', { name: '新增地址', exact: true }).click();
  await expect(page.getByLabel('收货人', { exact: true })).toBeFocused();
  const address = {
    receiver: '036测试收件人',
    phone: '13800000036',
    region: '上海市黄浦区',
    detail: '036本地联调点-' + RUN,
  };
  for (const [label, value] of [
    ['收货人', address.receiver],
    ['手机号', address.phone],
    ['所在地区', address.region],
    ['详细地址', address.detail],
  ])
    await page.getByLabel(label, { exact: true }).fill(value);
  await page.getByLabel('设为默认地址', { exact: true }).check();
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect(page.getByText('收货地址已保存', { exact: true })).toBeVisible();
  await page.reload();
  await stable();
  await expect(page).toHaveURL(BASE + '/me?tab=addresses');
  await expect(
    page.getByRole('tabpanel', { name: '收货地址', exact: true }),
  ).toContainText(address.detail);
  const addresses = await read('/api/v1/me/addresses');
  expect(addresses).toHaveLength(1);
  expect(addresses[0]).toMatchObject({ ...address, isDefault: true });
  evidence.profile = { nickname, avatar, persistedAfterReload: true };
  evidence.address = {
    ...address,
    id: addresses[0].id,
    persistedAfterReload: true,
  };
  const trigger = page.getByRole('button', {
    name: '打开个人菜单',
    exact: true,
  });
  await trigger.click();
  await expect(page.getByRole('menu')).toContainText(nickname);
  await expect(page.getByRole('menu').locator('img')).toHaveAttribute(
    'src',
    avatar,
  );
  await screenshot('personal-menu-persisted');
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
  await expect(page.getByRole('menu')).toHaveCount(0);
});

test('03 existing product gallery and own cart reach checkout without creating orders', async () => {
  await page.goto(BASE + '/products/' + product.id);
  await stable();
  const zoom = page.getByRole('button', {
    name: '放大查看商品图片',
    exact: true,
  });
  await decoded(zoom.locator('img'));
  await expect(zoom.locator('img')).toHaveAttribute(
    'src',
    /^\/uploads\/products\//,
  );
  await zoom.click();
  const dialog = page.getByRole('dialog', { name: '商品大图', exact: true });
  await expect(dialog).toBeVisible();
  expect(
    await dialog.evaluate((d) => d instanceof HTMLDialogElement && d.open),
  ).toBe(true);
  await decoded(dialog.locator('img'));
  await screenshot('product-native-gallery');
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(zoom).toBeFocused();
  if (product.deliveryMethods.length > 1)
    await page.getByLabel('交付方式').selectOption('EXPRESS');
  await page.getByRole('button', { name: '加入购物车', exact: true }).click();
  await expect(
    page.getByRole('status').filter({ hasText: '已加入购物车' }),
  ).toBeVisible();
  await page.getByRole('link', { name: '去购物车查看 →', exact: true }).click();
  if (cartQuantity > 1) {
    await page.getByRole('button', { name: '增加数量', exact: true }).click();
  } else {
    await expect(
      page.getByRole('button', { name: '增加数量', exact: true }),
    ).toBeDisabled();
    await expect(
      page.getByRole('button', { name: '减少数量', exact: true }),
    ).toBeDisabled();
  }
  await expect(page.locator('.mm-cart__qty')).toHaveText(String(cartQuantity));
  await page.reload();
  await stable();
  await expect(page.locator('.mm-cart__qty')).toHaveText(String(cartQuantity));
  const cart = await read('/api/v1/cart');
  expect(cart).toHaveLength(1);
  expect(cart[0]).toMatchObject({
    productId: product.id,
    quantity: cartQuantity,
  });
  evidence.cart = {
    itemId: cart[0].id,
    productId: product.id,
    quantity: cartQuantity,
    quantityChanged: cartQuantity > 1,
    singleStockBoundaryChecked: cartQuantity === 1,
    persistedAfterReload: true,
  };
  await page
    .getByRole('checkbox', { name: '选择 ' + product.title, exact: true })
    .check();
  await screenshot('cart-own-quantity-desktop');
  await page.getByRole('button', { name: /^去结算/ }).click();
  await expect(
    page.getByRole('heading', { name: '确认订单', exact: true }),
  ).toBeVisible();
  await expect(page.locator('.mm-checkout__line-title')).toHaveText(
    product.title,
  );
  await expect(page.locator('.mm-checkout__addresses')).toContainText(
    evidence.address.detail,
  );
  await expect(page.locator('.mm-checkout__summary')).toContainText(
    cartQuantity + ' 件商品',
  );
  await expect(
    page.getByRole('button', { name: '提交订单', exact: true }),
  ).toBeEnabled();
  await screenshot('checkout-own-address-desktop');
  const before = await read('/api/v1/orders');
  expect(before.content).toHaveLength(0);
  for (let i = 0; i < 2; i++) {
    const rejected = responseFor('POST', '/api/v1/checkout');
    await page.getByRole('button', { name: '提交订单', exact: true }).click();
    expect((await rejected).status()).toBe(503);
    await expect(page.getByRole('alert')).toContainText(
      '036 测试模拟暂时不可用',
    );
    await expect(
      page.getByRole('button', { name: '提交订单', exact: true }),
    ).toBeEnabled();
  }
  expect(evidence.mockedCheckout).toHaveLength(2);
  expect(evidence.mockedCheckout[0].idempotencyKey).toBeTruthy();
  expect(evidence.mockedCheckout[1]).toEqual(evidence.mockedCheckout[0]);
  expect((await read('/api/v1/orders')).content).toHaveLength(0);
  expect(evidence.prohibitedWrites).toEqual([]);
  evidence.checkout = {
    mockedFailureOnly: true,
    sameIdempotencyKey: true,
    persistedOrderCount: 0,
  };
  await page.setViewportSize({ width: 360, height: 800 });
  await stable();
  await widthCheck('checkout-360');
  await screenshot('checkout-503-no-order-mobile');
});

test('04 public navigation search recovery and own mobile cart stay usable at three widths', async () => {
  for (const width of [360, 390, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto(BASE + '/');
    await expect(
      page.locator('.mm-home__grid .mm-product-card').first(),
    ).toBeVisible();
    for (const image of await page.locator('.mm-home__grid img').all()) {
      await image.scrollIntoViewIfNeeded();
      await decoded(image);
    }
    await stable();
    await widthCheck('home-' + width);
    await screenshot('home-' + width);
    await expect(page.locator('.mm-header__cart')).toBeVisible();
    await page.locator('#main-content h1').first().click();
    await page.keyboard.press('/');
    await expect(page.getByLabel('搜索商品', { exact: true })).toBeFocused();
    if (width < 760) {
      await page
        .getByRole('button', { name: '打开导航菜单', exact: true })
        .click();
      await expect(
        page.getByRole('navigation', { name: '主导航', exact: true }),
      ).toBeVisible();
      await widthCheck('navigation-open-' + width);
      await screenshot('navigation-open-' + width);
      await page.keyboard.press('Escape');
      await expect(
        page.getByRole('navigation', { name: '主导航', exact: true }),
      ).not.toBeVisible();
    }
    await page.locator('.mm-header__cart').click();
    await stable();
    await expect(page.locator('.mm-cart__qty')).toHaveText(
      String(cartQuantity),
    );
    await widthCheck('cart-' + width);
    await screenshot('cart-' + width);
  }
  await page.setViewportSize({ width: 360, height: 900 });
  await page.goto(BASE + '/search');
  await stable();
  const advanced = page.getByRole('button', { name: /^高级筛选/ });
  await expect(advanced).toHaveAttribute('aria-expanded', 'false');
  await advanced.click();
  await page.getByLabel('最低价（元）', { exact: true }).fill('500');
  await page.getByLabel('最高价（元）', { exact: true }).fill('10');
  await page.getByRole('button', { name: '应用筛选', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: '重置', exact: true }).click();
  await page.getByLabel('关键词', { exact: true }).fill('036无匹配商品' + RUN);
  await page.getByRole('button', { name: '筛选', exact: true }).click();
  await expect(
    page.getByText('没有找到符合条件的商品', { exact: true }),
  ).toBeVisible();
  await screenshot('search-empty-recovery-mobile');
  await page.getByRole('button', { name: '重置筛选', exact: true }).click();
  await expect(
    page.locator('.mm-search__grid .mm-product-card').first(),
  ).toBeVisible();
  await expect(page.getByLabel('关键词', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('搜索商品', { exact: true })).toHaveValue('');
  await stable();
  await widthCheck('search-restored-360');
  await screenshot('search-restored-mobile');
  evidence.search = {
    mobileAdvancedToggle: true,
    invalidPriceRejected: true,
    emptyResultReset: true,
    searchShortcutFocused: true,
  };
});
