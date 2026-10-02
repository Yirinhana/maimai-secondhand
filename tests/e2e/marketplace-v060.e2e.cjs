const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'),
  path = require('node:path'),
  crypto = require('node:crypto');
const ROOT = path.resolve(__dirname, '../..'),
  BASE = process.env.MAIMAI_044_BASE || 'http://127.0.0.1:5173',
  local = BASE.startsWith('http:');
if (
  !['http://127.0.0.1:5173', process.env.MAIMAI_E2E_ALLOWED_ORIGIN].includes(BASE)
)
  throw Error('Unapproved host');
const users = JSON.parse(
  fs
    .readFileSync(
      path.join(ROOT, '.local/private/demo-accounts-040.json'),
      'utf8',
    )
    .replace(/^\uFEFF/, ''),
);
const report = { base: BASE, checks: [], errors: [] },
  run = Date.now(),
  shots = path.join(ROOT, '.local/screenshots');
const output = (name) => path.join(shots, `044-${run}-${name}.png`);
async function req(ctx, method, route, data) {
  await ctx.request.get(BASE + '/api/v1/auth/csrf');
  const token = (await ctx.cookies(BASE)).find((c) => c.name === 'XSRF-TOKEN');
  const r = await ctx.request.fetch(BASE + '/api/v1' + route, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token?.value || '') },
  });
  expect(r.ok(), `${method} ${route} status ${r.status()}`).toBe(true);
  return r.status() === 204 ? null : r.json();
}
async function layout(page) {
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth - innerWidth,
    ),
  ).toBeLessThanOrEqual(1);
  await expect
    .poll(() =>
      page.locator('img').evaluateAll((imgs) =>
        imgs
          .filter((i) => {
            const r = i.getBoundingClientRect();
            return r.width && r.height && r.top < innerHeight && r.bottom > 0;
          })
          .every((i) => i.complete && i.naturalWidth > 0),
      ),
    )
    .toBe(true);
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const guest = await browser.newContext({
        viewport: { width: 1440, height: 1000 },
        locale: 'zh-CN',
      }),
      page = await guest.newPage();
    page.on('pageerror', (e) => report.errors.push(e.message));
    await page.goto(BASE + '/search');
    const tree = await req(guest, 'GET', '/categories');
    for (const category of tree)
      await expect(
        page
          .locator('.category-browser')
          .getByRole('button', { name: category.name, exact: true }),
      ).toBeVisible();
    const parent = tree.find((c) => c.children?.length);
    await page
      .locator('.category-browser')
      .getByRole('button', { name: parent.name, exact: true })
      .click();
    for (const c of parent.children)
      await expect(
        page
          .locator('.category-browser')
          .getByRole('button', { name: c.name, exact: true }),
      ).toBeVisible();
    await page
      .locator('.category-browser')
      .getByRole('button', { name: parent.children[0].name, exact: true })
      .click();
    await expect(page).toHaveURL(
      new RegExp('categoryId=' + parent.children[0].id),
    );
    await layout(page);
    await page.screenshot({ path: output('categories-desktop') });
    report.checks.push(
      'All root categories and nested category URL filters visible',
    );
    const all = [];
    for (let i = 0; i < 20; i++) {
      const result = await req(guest, 'GET', `/products?size=50&page=${i}`);
      all.push(...result.content);
      if (result.last) break;
    }
    const copy = JSON.parse(
      fs.readFileSync(path.join(ROOT, 'deploy/catalog-copy-v060.json'), 'utf8'),
    );
    for (const expected of copy) {
      const match = all.filter((p) => p.title === expected.title);
      expect(match).toHaveLength(1);
      const detail = await req(guest, 'GET', `/products/${match[0].id}`);
      expect(detail.description).toBe(expected.description);
      expect(detail.returnPromise).toBe(expected.returnPromise);
      expect(detail.images).toHaveLength(3);
    }
    const product = all.find((p) => p.title.includes('登机箱')) || all[0],
      detail = await req(guest, 'GET', `/products/${product.id}`);
    expect(detail.description.split('\n\n').length).toBeGreaterThanOrEqual(3);
    expect(detail.description).not.toContain('演示');
    expect(
      (await req(guest, 'GET', `/products/${product.id}/ratings`)).items.every(
        (r) => !('orderId' in r),
      ),
    ).toBe(true);
    await page.goto(BASE + `/products/${product.id}`);
    await expect(
      page.getByRole('heading', { name: '买家评价 0' }),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: /留言讨论/ })).toBeVisible();
    await expect(
      page.getByText('图片来源：AI 生成', { exact: true }),
    ).toBeVisible();
    await page.locator('#product-discussion').scrollIntoViewIfNeeded();
    await layout(page);
    await page.screenshot({ path: output('discussion-desktop') });
    await page.goto(BASE + '/community/demands');
    await expect(page.locator('.mm-demand-post')).toHaveCount(12);
    await page
      .getByRole('button', { name: '查看回复', exact: true })
      .first()
      .click();
    await expect(page.getByText('PUBLISHED', { exact: true })).toHaveCount(0);
    await layout(page);
    await page.screenshot({ path: output('community-desktop') });
    report.checks.push(
      '65 catalog copy batch, persistent community posts and public reviews without private order identifiers',
    );
    for (const url of [
      '/search',
      `/products/${product.id}`,
      '/community/demands',
    ]) {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(BASE + url);
      if(url==='/community/demands')await expect(page.locator('.mm-demand-post')).toHaveCount(12);
      else if(url==='/search')await expect(page.locator('.mm-product-card').first()).toBeVisible();
      else await expect(page.locator('.mm-detail__title')).toContainText(product.title);
      await layout(page);
      await page.screenshot({
        path: output('mobile-' + url.replaceAll('/', '-')),
      });
    }
    const buyer = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    await req(buyer, 'POST', '/auth/login', {
      email: users.buyers[28].email,
      password: users.password,
    });
    const chat = await buyer.newPage();
    chat.on('pageerror', (e) => report.errors.push(e.message));
    await chat.goto(BASE + `/products/${product.id}`);
    await chat.getByRole('button', { name: '联系卖家', exact: true }).click();
    await expect(chat).toHaveURL(
      new RegExp('/messages/[0-9]+\\?productId=' + product.id),
    );
    await expect(chat.locator('.mm-chat__context')).toContainText(
      product.title,
    );
    if (local) {
      await chat
        .getByRole('button', { name: '发送商品卡片', exact: true })
        .click();
      await expect(
        chat.locator('.mm-chat__bubble .mm-chat__product-card').last(),
      ).toContainText(product.title);
      await chat.reload();
      await expect(
        chat.locator('.mm-chat__bubble .mm-chat__product-card').last(),
      ).toContainText(product.title);
      const firstConversation = new URL(chat.url()).pathname,
        another = all.find(
          (p) => p.sellerId === product.sellerId && p.id !== product.id,
        );
      if (another) {
        await chat.goto(BASE + `/products/${another.id}`);
        await chat
          .getByRole('button', { name: '联系卖家', exact: true })
          .click();
        await expect(chat).toHaveURL(
          new RegExp(firstConversation + '\\?productId=' + another.id),
        );
        await expect(chat.locator('.mm-chat__context')).toContainText(
          another.title,
        );
        await expect(
          chat.locator('.mm-chat__bubble .mm-chat__product-card').last(),
        ).toContainText(product.title);
      }
      const question = '请问箱轮是否方便更换？' + run;
      await chat.goto(BASE + `/products/${product.id}`);
      await chat.getByLabel('留言内容', { exact: true }).fill(question);
      await chat.getByRole('button', { name: '发布留言', exact: true }).click();
      await expect(
        chat.locator('.product-talk__entry').filter({ hasText: question }),
      ).toBeVisible();
      await chat.reload();
      await expect(
        chat.locator('.product-talk__entry').filter({ hasText: question }),
      ).toBeVisible();
      const mine = await req(buyer, 'GET', `/products/${product.id}/comments`),
        comment = mine.items.find((c) => c.content === question);
      await req(
        buyer,
        'DELETE',
        `/products/${product.id}/comments/${comment.id}`,
      );
      report.checks.push(
        'Local posted comment persists across reload, own delete, product card sends and persists',
      );
    }
    // Controlled latency verifies immediate user bubble, plaintext history and retry UX independently of model uptime.
    let delayed, started;
    const triggered = new Promise((r) => (started = r));
    const replyGate = new Promise((r) => (delayed = r));
    await chat.route('**/api/v1/support/assistant', (r) =>
      r.fulfill({
        json: {
          name: '缇娜',
          enabled: true,
          maxMessageChars: 1000,
          notice: '',
        },
      }),
    );
    await chat.route('**/api/v1/support/chat', async (route) => {
      if (route.request().method() === 'GET')
        return route.fulfill({ json: [] });
      const q = route.request().postDataJSON();
      started();
      await replyGate;
      return route.fulfill({
        json: {
          id: 880001,
          requestId: q.requestId,
          question: q.message,
          answer: '## 费用说明\n**平台服务费**按商品金额计算。\n- 不含运费',
          status: 'COMPLETE',
          createdAt: new Date().toISOString(),
        },
      });
    });
    await chat
      .getByRole('button', { name: '打开缇娜客服', exact: true })
      .click();
    await expect(chat.getByLabel('给缇娜的问题')).toBeEnabled();
    await chat.getByLabel('给缇娜的问题').fill('请帮我解释一下服务费');
    await chat.getByLabel('给缇娜的问题').press('Enter');
    await triggered;
    await expect(chat.locator('.tina-message--user')).toContainText(
      '请帮我解释一下服务费',
    );
    await expect(chat.locator('.tina-thinking')).toBeVisible();
    await expect(chat.getByLabel('给缇娜的问题')).toHaveValue('');
    await chat.screenshot({ path: output('tina-thinking') });
    delayed();
    await expect(chat.locator('.tina-message--assistant')).toContainText(
      '平台服务费',
    );
    expect(
      await chat.locator('.tina-message--assistant').innerText(),
    ).not.toMatch(/\*\*|##/);
    await chat.screenshot({ path: output('tina-reply') });
    await chat.setViewportSize({ width: 390, height: 844 });
    await layout(chat);
    await chat.screenshot({ path: output('tina-mobile') });
    report.checks.push(
      'Controlled delayed response: immediate user bubble, thinking animation, plain text and responsive dialog',
    );
    if (local) {
      await chat.unroute('**/api/v1/support/chat');
      let retryId,
        attempt = 0;
      await chat.route('**/api/v1/support/chat', async (route) => {
        const q = route.request().postDataJSON();
        if (attempt++ === 0) {
          retryId = q.requestId;
          return route.fulfill({
            status: 503,
            json: { code: 'TEMPORARY', message: '暂时无法响应，请重试' },
          });
        }
        expect(q.requestId).toBe(retryId);
        return route.fulfill({
          json: {
            id: 880002,
            requestId: q.requestId,
            question: q.message,
            answer: '已经收到你的问题。',
            status: 'COMPLETE',
            createdAt: new Date().toISOString(),
          },
        });
      });
      await chat.getByLabel('给缇娜的问题').fill('失败后保留原问题');
      await chat.getByLabel('给缇娜的问题').press('Enter');
      await expect(
        chat.getByRole('button', { name: '重试这条问题' }),
      ).toBeVisible();
      await expect(
        chat
          .locator('.tina-message--user')
          .filter({ hasText: '失败后保留原问题' }),
      ).toHaveCount(1);
      await chat.getByRole('button', { name: '重试这条问题' }).click();
      await expect(
        chat.locator('.tina-message--assistant').last(),
      ).toContainText('已经收到');
      await expect(
        chat
          .locator('.tina-message--user')
          .filter({ hasText: '失败后保留原问题' }),
      ).toHaveCount(1);
      report.checks.push(
        'Failed optimistic question retains one bubble and the same request ID on retry',
      );
    }
    expect(report.errors).toEqual([]);
  } finally {
    await browser.close();
    fs.writeFileSync(
      path.join(ROOT, `.local/044-browser-${local ? 'local' : 'public'}.json`),
      JSON.stringify(report, null, 2),
    );
    console.log(JSON.stringify(report));
  }
})().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
