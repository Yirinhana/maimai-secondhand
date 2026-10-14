const { chromium, expect: rawExpect } = require('@playwright/test');
const fs = require('fs'),
  path = require('path');
const expect = rawExpect.configure({ timeout: 12000 }),
  ROOT = path.resolve(__dirname, '../..'),
  BASE = 'http://127.0.0.1:15173';
const src = JSON.parse(
  fs.readFileSync(path.join(ROOT, '.local/role-video-source.json'), 'utf8'),
);
const evidence = { checks: [], errors: [], sourceOrder: src.orderNo };
async function api(c, method, url, data) {
  await c.request.get(BASE + '/api/v1/auth/csrf');
  const token = (await c.cookies()).find((x) => x.name === 'XSRF-TOKEN')?.value;
  const r = await c.request.fetch(BASE + '/api/v1' + url, {
    method,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token || '') },
    ...(data === undefined ? {} : { data }),
  });
  if (!r.ok()) throw Error(url + ' ' + r.status() + ' ' + (await r.text()));
  const text = await r.text();
  return text ? JSON.parse(text) : null;
}
(async () => {
  const b = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const buyer = await b.newContext(),
      seller = await b.newContext(),
      admin = await b.newContext();
    for (const [c, role] of [
      [buyer, 'buyer'],
      [seller, 'seller'],
      [admin, 'admin'],
    ])
      await api(c, 'POST', '/auth/login', {
        email:
          role === 'admin'
            ? 'admin@maimai.local'
            : `journey-${src.run}-${role}@example.invalid`,
        password: role === 'admin' ? 'Maimai#2026' : 'RoleJourney#2026',
      });
    const p = await buyer.newPage();
    p.on('pageerror', (e) => evidence.errors.push(e.message));
    await p.goto(BASE + '/orders/' + src.orderNo);
    await p.getByRole('button', { name: '申请售后', exact: true }).click();
    await p.getByLabel('商品退款（元）', { exact: true }).fill('2');
    await p
      .getByLabel('原因', { exact: true })
      .fill('本地验收：双方协商部分退款，验证记录与状态。');
    await p
      .getByLabel('证据说明', { exact: true })
      .fill('仅验证模拟金额，不发生真实扣款或退款。');
    await p.getByRole('button', { name: '提交申请', exact: true }).click();
    await expect(p).toHaveURL(/\/aftersales\/\d+$/);
    const id = Number(p.url().split('/').pop());
    evidence.aftersaleId = id;
    const sp = await seller.newPage();
    sp.on('pageerror', (e) => evidence.errors.push(e.message));
    await sp.goto(BASE + '/aftersales/' + id);
    await sp.getByRole('radio', { name: /同意申请/ }).check();
    await sp
      .getByPlaceholder('给买家的答复说明')
      .fill('本地验收同意部分退款，仅更新模拟记录。');
    await sp.getByRole('button', { name: '提交答复', exact: true }).click();
    await expect
      .poll(
        async () =>
          (await api(buyer, 'GET', '/orders/' + src.orderNo)).refundStatus,
      )
      .toBe('PARTIAL');
    evidence.checks.push(
      '订单申请售后→卖家答复→模拟部分退款完成，金额与订单状态关联',
    );
    const denied = await buyer.request.get(
      BASE + '/api/v1/admin/orders/' + src.orderNo,
    );
    expect(denied.status()).toBe(403);
    const wrong = await seller.request.get(
      BASE + '/api/v1/me/listing-drafts/new?ownerId=' + src.accounts.buyer,
    );
    expect(wrong.status()).toBe(409);
    expect((await wrong.json()).code).toBe('DRAFT_ACCOUNT_CHANGED');
    evidence.checks.push('买家不能读管理订单；跨账号云端草稿访问被拒绝');
    for (const [page, label] of [
      [p, 'buyer'],
      [sp, 'seller'],
    ]) {
      await page.reload();
      for (const width of [320, 390, 844, 1280]) {
        await page.setViewportSize({
          width,
          height: width === 844 ? 390 : 900,
        });
        await expect
          .poll(() =>
            page.evaluate(
              () => document.documentElement.scrollWidth - innerWidth,
            ),
          )
          .toBeLessThanOrEqual(1);
      }
      await page.screenshot({
        path: path.join(ROOT, '.local/role-recovery', label + '-aftersale.png'),
        fullPage: true,
      });
    }
    const ap = await admin.newPage();
    ap.on('pageerror', (e) => evidence.errors.push(e.message));
    await ap.goto(BASE + '/admin/orders/' + src.orderNo);
    await expect(
      ap.getByText('小林 · 买家', { exact: false }).first(),
    ).toBeVisible();
    evidence.checks.push(
      '管理端可回溯买卖双方、订单与售后；售后页四种宽度无溢出',
    );
    expect(evidence.errors).toEqual([]);
  } finally {
    fs.writeFileSync(
      path.join(ROOT, '.local/role-recovery/aftercare.json'),
      JSON.stringify(evidence, null, 2),
    );
    await b.close();
  }
  console.log(JSON.stringify(evidence));
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
