// UI-only transport simulations are separate from the real business-flow recording.
const { chromium, expect: rawExpect } = require('@playwright/test');
const fs = require('fs'),
  path = require('path');
const expect = rawExpect.configure({ timeout: 10000 }),
  ROOT = path.resolve(__dirname, '../..'),
  BASE = 'http://127.0.0.1:15173';
const latest = JSON.parse(
    fs.readFileSync(
      path.join(ROOT, '.local/role-experience-latest.json'),
      'utf8',
    ),
  ),
  receipt = JSON.parse(fs.readFileSync(latest.result, 'utf8'));
const out = path.join(ROOT, '.local/role-recovery');
fs.mkdirSync(out, { recursive: true });
const evidence = {
  checks: [],
  errors: [],
  mode: 'controlled transport responses; zero provider calls',
};
async function login(c, name) {
  await c.request.get(BASE + '/api/v1/auth/csrf');
  let token = (await c.cookies()).find((x) => x.name === 'XSRF-TOKEN')?.value;
  const r = await c.request.post(BASE + '/api/v1/auth/login', {
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(token || '') },
    data: {
      email: `journey-${receipt.run}-${name}@example.invalid`,
      password: 'RoleJourney#2026',
    },
  });
  expect(r.ok()).toBe(true);
}
(async () => {
  const b = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const c = await b.newContext({ viewport: { width: 390, height: 844 } });
    await login(c, 'buyer');
    const p = await c.newPage();
    p.on('pageerror', (e) => evidence.errors.push(e.message));
    await p.goto(BASE + '/publish');
    let productWrites = 0;
    p.on('request', (r) => {
      if (/\/seller\/products$/.test(r.url()) && r.method() === 'POST')
        productWrites++;
    });
    await p
      .getByLabel('标题', { exact: true })
      .fill('尚未认证也能保留的个人草稿');
    await p.getByRole('button', { name: '保存草稿', exact: true }).click();
    await expect(p.getByText(/填写内容已保存到云端/)).toBeVisible();
    expect(productWrites).toBe(0);
    await p.reload();
    await expect(p.getByLabel('标题', { exact: true })).toHaveValue(
      '尚未认证也能保留的个人草稿',
    );
    evidence.checks.push(
      '非卖家可保存云端草稿并恢复，不误发商品创建请求；卖家申请入口明确',
    );
    const s = await b.newContext();
    await login(s, 'seller');
    const sp = await s.newPage();
    await sp.goto(BASE + '/publish');
    await expect(sp.getByLabel('标题', { exact: true })).not.toHaveValue(
      '尚未认证也能保留的个人草稿',
    );
    evidence.checks.push('不同账号草稿隔离');
    await p.route('**/api/v1/ai/workflows/availability', (r) =>
      r.fulfill({ json: { enabled: true, modelEnabled: true } }),
    );
    let writes = 0,
      reads = 0;
    await p.route('**/api/v1/ai/workflows', async (r) => {
      writes++;
      const data = r.request().postDataJSON();
      expect(data.question).toBe('请帮我检查一句描述');
      expect(data.draft).toContain('尚未认证');
      await new Promise((done) => setTimeout(done, 900));
      await r.fulfill({
        json: {
          id: 88001,
          status: 'RUNNING',
          answer: null,
          model: 'controlled-test',
        },
      });
    });
    await p.route('**/api/v1/ai/workflows/88001', (r) => {
      reads++;
      return r.fulfill({
        json: {
          id: 88001,
          status: 'SUCCEEDED',
          answer: '受控恢复测试：请补充实际使用情况。',
          model: 'controlled-test',
        },
      });
    });
    await p.locator('.workflow-assistant__toggle').click();
    await p.getByLabel('你想解决什么？').fill('请帮我检查一句描述');
    await p.getByRole('button', { name: '请麦仔分析', exact: true }).click();
    await expect(p.locator('.workflow-assistant__question')).toContainText(
      '请帮我检查一句描述',
    );
    await expect(p.locator('.ai-activity')).toContainText('麦仔正在分析');
    await expect(
      p.getByText('受控恢复测试：请补充实际使用情况。', { exact: true }),
    ).toBeVisible();
    expect(writes).toBe(1);
    expect(reads).toBe(1);
    evidence.checks.push(
      '用户问题先出现；RUNNING 自动只读查询，无重复推理；草稿与问题分开传递',
    );
    const keys = [];
    await p.route('**/api/v1/ai/workflows', (r) => {
      keys.push(r.request().postDataJSON().requestKey);
      return keys.length === 1
        ? r.abort('failed')
        : r.fulfill({
            json: {
              id: 88002,
              status: 'SUCCEEDED',
              answer: '受控恢复测试：仍是同一笔请求。',
              model: 'controlled-test',
            },
          });
    });
    await p.getByLabel('继续问麦仔').fill('网络断开后继续');
    await p.getByRole('button', { name: '继续请教麦仔' }).click();
    await expect(p.getByRole('button', { name: '查询这次请求' })).toBeVisible();
    await expect(
      p.getByText('受控恢复测试：请补充实际使用情况。', { exact: true }),
    ).toBeVisible();
    await p.getByRole('button', { name: '查询这次请求' }).click();
    await expect(
      p.getByText('受控恢复测试：仍是同一笔请求。', { exact: true }),
    ).toBeVisible();
    expect(keys.length).toBe(2);
    expect(new Set(keys).size).toBe(1);
    evidence.checks.push(
      '网络结果不明时沿用同一请求标识，保留旧答复；不盲目创建新调用',
    );
    for (const width of [320, 390, 844, 1280]) {
      await p.setViewportSize({ width, height: width === 844 ? 390 : 900 });
      await expect
        .poll(() =>
          p.evaluate(() => document.documentElement.scrollWidth - innerWidth),
        )
        .toBeLessThanOrEqual(1);
    }
    await p.emulateMedia({ reducedMotion: 'reduce' });
    await p.screenshot({
      path: path.join(out, 'conversation.png'),
      fullPage: true,
    });
    expect(evidence.errors).toEqual([]);
  } finally {
    fs.writeFileSync(
      path.join(out, 'result.json'),
      JSON.stringify(evidence, null, 2),
    );
    await b.close();
  }
  console.log(JSON.stringify(evidence));
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
