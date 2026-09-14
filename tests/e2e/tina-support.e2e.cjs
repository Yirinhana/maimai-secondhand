const { test, expect } = require('@playwright/test');
const path = require('node:path');
const BASE = 'http://127.0.0.1:5173';
const launcher = (page) =>
  page.getByRole('button', { name: '打开缇娜客服', exact: true });
const panel = (page) => page.locator('#tina-dialog');
const input = (page) => page.getByRole('textbox', { name: '给缇娜的问题' });
const shot = (page, name) =>
  page.screenshot({
    path: path.resolve(
      __dirname,
      `../../.local/screenshots/039-${process.env.MAIMAI_039_RUN}-${name}.png`,
    ),
  });
async function open(page) {
  await launcher(page).click();
  await expect(panel(page)).toBeVisible();
}
async function fixture(page) {
  // Only this browser receives controlled UI responses. This is not a real AI or persistence test.
  let loggedIn = true,
    history = [],
    fail = false,
    ids = [],
    paused = null;
  const me = {
    id: 990039,
    email: 'tina-ui@example.invalid',
    nickname: '界面测试',
    roles: ['USER'],
    sellerStatus: null,
    avatarUrl: null,
  };
  await page.route('**/api/v1/auth/me', (r) =>
    r.fulfill({
      status: loggedIn ? 200 : 401,
      json: loggedIn ? me : { code: 'UNAUTHORIZED', message: '请先登录' },
    }),
  );
  await page.route('**/api/v1/auth/logout', (r) => {
    loggedIn = false;
    return r.fulfill({ status: 204 });
  });
  await page.route('**/api/v1/support/assistant', (r) =>
    r.fulfill({
      json: {
        name: '缇娜',
        enabled: true,
        maxMessageChars: 1000,
        notice: 'AI 回复仅供参考。',
      },
    }),
  );
  await page.route('**/api/v1/support/chat', async (r) => {
    const method = r.request().method();
    if (method === 'GET') return r.fulfill({ json: history });
    if (method === 'DELETE') {
      history = [];
      return r.fulfill({ status: 204 });
    }
    if (method !== 'POST') throw Error('Unexpected chat mutation');
    const data = r.request().postDataJSON();
    ids.push(data.requestId);
    if (fail) {
      fail = false;
      return r.abort('failed');
    }
    const turn = {
      id: history.length + 1,
      requestId: data.requestId,
      question: data.message,
      answer:
        '受控界面测试回复：平台费为商品成交额的0.03%。<img src=x onerror=alert(1)>',
      status: 'COMPLETE',
      errorCode: null,
      createdAt: new Date().toISOString(),
    };
    history.push(turn);
    if (paused) await paused;
    return r.fulfill({ json: turn });
  });
  return {
    failNext() {
      fail = true;
    },
    ids,
    pause(promise) {
      paused = promise;
    },
  };
}

test('live guest FAQ, fixed launcher, keyboard tabs and focus restoration', async ({
  page,
}) => {
  await page.goto('/search');
  await open(page);
  await expect(panel(page).getByText('登录后与缇娜对话')).toBeVisible();
  await page.getByRole('tab', { name: '问缇娜' }).focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab', { name: '常见问题' })).toBeFocused();
  await expect(page.locator('.tina-faq')).not.toHaveCount(0);
  await page.locator('.tina-faq summary').first().click();
  await shot(page, 'desktop-faq');
  await page.keyboard.press('Escape');
  await expect(panel(page)).not.toBeVisible();
  await expect(launcher(page)).toBeFocused();
  const first = await launcher(page).boundingBox();
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await expect
    .poll(async () =>
      Math.abs((await launcher(page).boundingBox()).y - first.y),
    )
    .toBeLessThan(1);
});

test('live API requires session and CSRF, unavailable AI remains explicit', async ({
  page,
  context,
}) => {
  const meta = await context.request.get(BASE + '/api/v1/support/assistant');
  expect(meta.status()).toBe(200);
  expect((await meta.json()).enabled).toBe(false);
  expect(
    (await context.request.get(BASE + '/api/v1/support/chat')).status(),
  ).toBe(401);
  await context.request.get(BASE + '/api/v1/auth/csrf');
  let token = (await context.cookies()).find(
    (c) => c.name === 'XSRF-TOKEN',
  ).value;
  const login = await context.request.post(BASE + '/api/v1/auth/login', {
    headers: { 'X-XSRF-TOKEN': token },
    data: { email: 'buyer@maimai.local', password: 'Maimai#2026' },
  });
  expect(login.status()).toBe(200);
  expect(
    (await context.request.get(BASE + '/api/v1/support/chat')).headers()[
      'cache-control'
    ],
  ).toContain('no-store');
  expect(
    (await context.request.delete(BASE + '/api/v1/support/chat')).status(),
  ).toBe(403);
  token = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN').value;
  const response = await context.request.post(BASE + '/api/v1/support/chat', {
    headers: { 'X-XSRF-TOKEN': token },
    data: {
      requestId: require('node:crypto').randomUUID(),
      message: '当前AI是否可用？',
    },
  });
  expect(response.status()).toBe(503);
  expect((await response.json()).code).toBe('AI_NOT_CONFIGURED');
  await page.goto('/search');
  await open(page);
  await expect(
    panel(page).getByText('缇娜暂未接通', { exact: true }),
  ).toBeVisible();
  await expect(input(page)).toBeDisabled();
  await shot(page, 'not-connected');
});

test('controlled UI: failed request preserves draft and nonce, reply is plain text, history can clear', async ({
  page,
}) => {
  const mock = await fixture(page);
  await page.goto('/search');
  await open(page);
  await expect(input(page)).toBeEnabled();
  mock.failNext();
  await input(page).fill('平台费怎么算？');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(panel(page).getByRole('alert')).toBeVisible();
  await expect(input(page)).toHaveValue('平台费怎么算？');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect(page.locator('.tina-message--assistant')).toHaveCount(1);
  expect(mock.ids).toHaveLength(2);
  expect(mock.ids[0]).toBe(mock.ids[1]);
  await expect(page.locator('.tina-message--assistant img')).toHaveCount(0);
  await expect(page.locator('.tina-message--assistant')).toContainText(
    '<img src=x',
  );
  await shot(page, 'controlled-reply');
  await page.reload();
  await open(page);
  await expect(page.locator('.tina-message--assistant')).toHaveCount(1);
  await page.getByRole('button', { name: '清空对话', exact: true }).click();
  await page.getByRole('button', { name: '确认操作', exact: true }).click();
  await expect(page.locator('.tina-message--assistant')).toHaveCount(0);
});

test('controlled UI: logout drops a late reply and clears private draft', async ({
  page,
}) => {
  const mock = await fixture(page);
  let release;
  mock.pause(
    new Promise((resolve) => {
      release = resolve;
    }),
  );
  await page.goto('/search');
  await open(page);
  await expect(input(page)).toBeEnabled();
  await input(page).fill('不能在退出后出现的私密测试文本');
  await page.getByRole('button', { name: '发送', exact: true }).click();
  await expect.poll(() => mock.ids.length).toBe(1);
  await page.getByRole('button', { name: '关闭缇娜客服' }).click();
  await page.getByRole('button', { name: '打开个人菜单' }).click();
  await page.getByRole('menuitem', { name: '退出登录' }).click();
  await expect(page.getByText('登录 / 注册', { exact: true })).toBeVisible();
  release();
  await open(page);
  await expect(panel(page).getByText('登录后与缇娜对话')).toBeVisible();
  await expect(page.locator('.tina-messages li')).toHaveCount(0);
});

test('360px and short mobile viewport: launcher and dialog remain on screen', async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await page.goto('/search');
  await open(page);
  await page.getByRole('tab', { name: '常见问题' }).click();
  await expect(page.locator('.tina-faq')).not.toHaveCount(0);
  await shot(page, 'mobile-faq');
  for (const height of [740, 460]) {
    await page.setViewportSize({ width: 360, height });
    const rect = await panel(page).boundingBox();
    expect(rect.x).toBeGreaterThanOrEqual(0);
    expect(rect.y).toBeGreaterThanOrEqual(0);
    expect(rect.x + rect.width).toBeLessThanOrEqual(await page.evaluate(() => document.documentElement.clientWidth));
    expect(rect.y + rect.height).toBeLessThanOrEqual(height);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth),
    ).toBeLessThanOrEqual(360);
    await expect(
      page.getByRole('button', { name: '关闭缇娜客服' }),
    ).toBeInViewport();
  }
  await page.keyboard.press('Escape');
  await expect(launcher(page)).toBeInViewport();
});
