// Destructive business actions are restricted to an isolated loopback QA environment.
const { chromium, expect: baseExpect } = require('@playwright/test');
const fs = require('node:fs'),
  path = require('node:path'),
  crypto = require('node:crypto');
const expect = baseExpect.configure({ timeout: 15000 });
const ROOT = path.resolve(__dirname, '../..'),
  BASE = process.env.MAIMAI_QA_URL || 'http://127.0.0.1:15173';
if (
  !['127.0.0.1', 'localhost'].includes(new URL(BASE).hostname) ||
  new URL(BASE).port === '5173'
)
  throw Error(
    'Use an isolated loopback QA instance, not the normal development instance',
  );
const RECORD = process.env.MAIMAI_RECORD === '1',
  run = Date.now(),
  OUT = path.join(ROOT, '.local/role-experience', String(run));
fs.mkdirSync(OUT, { recursive: true });
const result = {
  run,
  environment:
    'isolated local QA; capture email; simulated money; no live model requests',
  checks: [],
  errors: [],
  chapters: [],
};
const password = 'RoleJourney#2026';
async function api(c, method, url, data, multipart) {
  await c.request.get(BASE + '/api/v1/auth/csrf');
  const csrf = (await c.cookies(BASE)).find((x) => x.name === 'XSRF-TOKEN');
  const r = await c.request.fetch(BASE + '/api/v1' + url, {
    method,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf?.value || '') },
    ...(multipart ? { multipart } : data === undefined ? {} : { data }),
  });
  if (!r.ok())
    throw Error(method + ' ' + url + ' ' + r.status() + ' ' + (await r.text()));
  const text = await r.text();
  return text ? JSON.parse(text) : null;
}
async function register(browser, name) {
  const c = await browser.newContext();
  const email = `journey-${run}-${name}@example.invalid`;
  await api(c, 'POST', '/auth/register/code', { email });
  const mail = await api(
    c,
    'GET',
    '/dev/mailbox?email=' + encodeURIComponent(email),
  );
  const code = mail[0].content.match(/验证码为：(\d{6})/)[1];
  const policy = await api(c, 'GET', '/policies/current');
  const me = await api(c, 'POST', '/auth/register', {
    email,
    code,
    password,
    nickname: name === 'buyer' ? '小林 · 买家' : '小陈 · 闲置小店',
    acceptedTerms: true,
    policyVersion: policy.version,
  });
  return { c, me, email };
}
async function settle(p) {
  await expect.poll(() => p.locator('.mm-skeleton').count()).toBe(0);
}
async function shot(p, name) {
  await p.screenshot({ path: path.join(OUT, name + '.png'), fullPage: true });
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  let c, p;
  try {
    const buyer = await register(browser, 'buyer'),
      seller = await register(browser, 'seller');
    const admin = await browser.newContext();
    await api(admin, 'POST', '/auth/login', {
      email: 'admin@maimai.local',
      password: 'Maimai#2026',
    });
    const application = await api(seller.c, 'POST', '/me/seller-application', {
      intro: '本地流程验收：整理自有图书，信息为测试资料，不进行真实买卖。',
    });
    await api(
      admin,
      'POST',
      `/admin/seller-applications/${application.id}/review`,
      {
        action: 'APPROVE',
        reason: '本地验收卖家资料与账号关联',
        channelQualified: true,
      },
    );
    const sm = await api(seller.c, 'GET', '/auth/me');
    expect(sm.sellerStatus).toBe('APPROVED');
    result.accounts = { buyer: buyer.me.id, seller: seller.me.id };
    result.checks.push(
      '通过验证码注册、条款同意与卖家申请审核建立账号，未直接写库',
    );
    c = await browser.newContext({
      viewport: { width: 1440, height: 960 },
      locale: 'zh-CN',
      timezoneId: 'Asia/Shanghai',
      ...(RECORD
        ? { recordVideo: { dir: OUT, size: { width: 1440, height: 960 } } }
        : {}),
    });
    await c.addInitScript(() =>
      localStorage.setItem('maimai-welcome-seen-v1', '1'),
    );
    p = await c.newPage();
    p.on('pageerror', (e) => result.errors.push(e.message));
    async function login(role) {
      await c.clearCookies();
      await c.addCookies(
        await (
          role === 'admin' ? admin : role === 'buyer' ? buyer.c : seller.c
        ).cookies(BASE),
      );
    }
    async function chapter(role, title) {
      result.chapters.push({ role, title, at: Date.now() });
      if (RECORD) {
        await p.evaluate(
          ({ role, title }) => {
            document.getElementById('qa-caption')?.remove();
            const e = document.createElement('div');
            e.id = 'qa-caption';
            Object.assign(e.style, {
              position: 'fixed',
              bottom: '12px',
              left: '16px',
              zIndex: '99999',
              background: '#172b32ed',
              color: 'white',
              padding: '14px 22px',
              borderRadius: '12px',
              font: '15px system-ui',
              pointerEvents: 'none',
              boxShadow: '0 6px 20px #0002',
            });
            e.textContent = `${role}  ·  ${title}　｜本地验收 · 模拟资金`;
            document.body.appendChild(e);
          },
          { role, title },
        );
        await p.waitForTimeout(1800);
      }
    }
    async function pause() {
      if (RECORD) await p.waitForTimeout(2300);
    }
    await login('seller');
    await p.goto(BASE + '/publish');
    await expect(p.locator('.workflow-assistant__toggle')).toBeVisible();
    await chapter('卖家', '从不知道卖什么开始');
    await p.locator('.workflow-assistant__toggle').click();
    await p
      .getByRole('button', { name: '我不知道卖什么', exact: true })
      .click();
    const first = p.waitForResponse(
      (r) =>
        r.url().endsWith('/ai/workflows') && r.request().method() === 'POST',
    );
    await p.getByRole('button', { name: '请麦仔分析', exact: true }).click();
    const firstRun = await (await first).json();
    expect(firstRun.model).toBe('platform-guide');
    await expect(
      p.getByText('平台入门指引 · 未调用模型', { exact: true }),
    ).toBeVisible();
    await expect(p.locator('.workflow-assistant__answer')).toContainText(
      '读完的书',
    );
    await pause();
    await p
      .getByRole('button', { name: '我有一些读完的书', exact: true })
      .click();
    const follow = p.waitForResponse(
      (r) =>
        r.url().endsWith('/ai/workflows') && r.request().method() === 'POST',
    );
    await p.getByRole('button', { name: '继续请教麦仔', exact: true }).click();
    const followRun = await (await follow).json();
    expect(followRun.model).toBe('platform-guide');
    expect((await follow).request().postDataJSON().previousRunId).toBe(
      firstRun.id,
    );
    await expect(p.locator('.workflow-assistant__answer')).toHaveCount(2);
    await expect(p.locator('.workflow-assistant__answer').last()).toContainText(
      '书页是否完整',
    );
    await pause();
    await shot(p, 'seller-guidance');
    result.checks.push(
      '选品求助→书籍追问，保留上下文，平台指引零模型调用，未自动填写或提交商品',
    );
    await p.locator('.workflow-assistant__toggle').click();
    const title = `读完的编程书 · ${run}`;
    await p.getByLabel('标题', { exact: true }).fill(title);
    await p
      .getByLabel('商品描述', { exact: true })
      .fill(
        '本地验收用图书：书页完整，部分页面有铅笔笔记，封面有使用痕迹。图片为生成的体验素材，不代表真实供货。',
      );
    await p.goto(BASE + '/seller');
    await p.goto(BASE + '/publish');
    await expect(p.getByLabel('标题', { exact: true })).toHaveValue(title);
    await expect(p.getByLabel('商品描述', { exact: true })).toHaveValue(
      /本地验收/,
    );
    const categories = await api(c, 'GET', '/categories');
    const cat = categories.find((x) => x.name === '图书教材') || categories[0];
    let leaf = cat;
    while (leaf.children?.length) leaf = leaf.children[0];
    await p.getByRole('combobox', { name: '商品大类', exact: true }).selectOption(String(cat.id));
    if (leaf.id !== cat.id)
      await p.getByRole('combobox', { name: '小分类', exact: true }).selectOption(String(leaf.id));
    await p.getByLabel('价格（元）', { exact: true }).fill('28');
    await p.getByLabel('库存', { exact: true }).fill('2');
    await p.getByLabel('所在地区', { exact: true }).fill('上海市徐汇区');
    await p
      .getByLabel('缺陷说明', { exact: false })
      .fill('封面有折角，部分页面有铅笔笔记');
    await p.getByRole('checkbox', { name: '快递', exact: true }).uncheck();
    await p.getByRole('checkbox', { name: '面交', exact: true }).check();
    await chapter('卖家', '草稿保留，补充实物资料和交付方式');
    await pause();
    await p.getByRole('button', { name: '保存草稿', exact: true }).click();
    await expect(p).toHaveURL(/\/publish\/\d+$/);
    const productId = Number(p.url().split('/').pop());
    result.productId = productId;
    let imageFile =
      process.env.MAIMAI_DEMO_IMAGE ||
      path.join(ROOT, '.local/uploads/products/demo043-item-008-front.jpg');
    if (!fs.existsSync(imageFile)) {
      if (RECORD)
        throw Error(
          'Set MAIMAI_DEMO_IMAGE to a local book image for recording',
        );
      imageFile = path.join(__dirname, 'fixture8x8.png');
    }
    await p.locator('input[type=file]').first().setInputFiles(imageFile);
    await p.getByRole('button', { name: '上传所选图片', exact: true }).click();
    await expect(
      p.getByAltText(title + ' 图片 1', { exact: true }),
    ).toBeVisible();
    await pause();
    await p
      .getByRole('button', { name: '保存并提交审核', exact: true })
      .click();
    await expect(
      p.getByText('已提交审核，可在「我的商品」查看进度', { exact: true }),
    ).toBeVisible();
    expect(
      (await api(seller.c, 'GET', `/seller/products/${productId}`)).status,
    ).toBe('PENDING_REVIEW');
    result.checks.push(
      '填写未完成草稿跨页面恢复→保存商品→上传图片→主动提交审核',
    );
    await login('admin');
    await p.goto(BASE + '/admin/products?productId=' + productId);
    await expect(p.getByLabel('审核理由')).toBeVisible();
    await chapter('管理员', '核对原文与图片后审核，不替卖家编造资料');
    await p
      .getByLabel('审核理由')
      .fill('本地验收：资料和瑕疵说明齐全，图片为标注来源的体验素材。');
    await pause();
    const reviewed = p.waitForResponse(
      (r) =>
        r.url().endsWith(`/admin/products/${productId}/review`) &&
        r.request().method() === 'POST',
    );
    await p.getByRole('button', { name: '通过', exact: true }).click();
    expect((await reviewed).ok()).toBe(true);
    expect((await api(buyer.c, 'GET', `/products/${productId}`)).status).toBe(
      'ON_SALE',
    );
    await login('buyer');
    await p.goto(BASE + `/products/${productId}`);
    await expect(
      p.getByRole('heading', { name: title, exact: true }),
    ).toBeVisible();
    await chapter('买家', '查看瑕疵、卖家和总价，再决定购买');
    await pause();
    await p.getByRole('button', { name: '立即购买', exact: true }).click();
    await expect(
      p.getByRole('heading', { name: '确认订单', exact: true }),
    ).toBeVisible();
    await p
      .getByLabel('面交地点', { exact: true })
      .fill('上海图书馆公共入口（验收用，无实际见面）');
    await p.getByLabel('面交时间', { exact: true }).fill('2026-12-01T14:00');
    const checkoutPromise = p.waitForResponse(
      (r) => r.url().endsWith('/checkout') && r.request().method() === 'POST',
    );
    await pause();
    await p.getByRole('button', { name: '提交订单', exact: true }).click();
    const checkoutResponse = await checkoutPromise;
    expect(checkoutResponse.ok()).toBe(true);
    const order = (await checkoutResponse.json()).orders[0];
    result.orderNo = order.orderNo;
    result.orderId = order.id;
    await p.goto(BASE + '/orders/' + order.orderNo);
    await chapter('买家', '模拟付款仅更新站内记录，不扣真实资金');
    await p.getByRole('button', { name: '发起支付', exact: true }).click();
    await expect(
      p.getByText('本地模拟支付，不会扣款，也不代表微信支付已开通。', {
        exact: true,
      }),
    ).toBeVisible();
    await pause();
    await p.getByRole('button', { name: '模拟支付成功', exact: true }).click();
    await expect(
      p.getByRole('button', { name: '生成一次性交付码', exact: true }),
    ).toBeVisible();
    await p
      .getByRole('button', { name: '生成一次性交付码', exact: true })
      .click();
    await expect(p.locator('.mm-order-detail__code strong')).toBeVisible();
    const deliveryCode = await p
      .locator('.mm-order-detail__code strong')
      .innerText();
    await pause();
    await login('seller');
    await p.goto(BASE + '/orders/' + order.orderNo);
    await chapter('卖家', '只由对应卖家核验一次性交付码');
    await p.getByLabel('买家出示的交付码').fill(deliveryCode);
    await pause();
    await p
      .getByRole('button', { name: '核验并完成面交', exact: true })
      .click();
    const dialog = p.getByRole('dialog');
    if (await dialog.isVisible())
      await dialog
        .getByRole('button', { name: '确认操作', exact: true })
        .click();
    await expect(p.locator('#order-status-title')).toHaveText('已完成');
    expect(
      (await api(buyer.c, 'GET', '/orders/' + order.orderNo)).fulfillmentStatus,
    ).toBe('COMPLETED');
    result.checks.push(
      '商品审核→买家下单→模拟支付→对应卖家核验交付码，订单状态与库存真实持久化',
    );
    await login('buyer');
    await p.goto(BASE + '/orders/' + order.orderNo);
    await p.getByLabel('评分', { exact: true }).selectOption('4');
    await p
      .getByLabel('评价内容', { exact: true })
      .fill('本地体验交易：描述与交付步骤清楚，封面折角已提前说明。');
    await chapter('买家', '评价记录体验，退款诉求另外走订单售后');
    await p.getByRole('button', { name: '提交评价', exact: true }).click();
    await expect(
      p.getByText('本地体验交易：描述与交付步骤清楚，封面折角已提前说明。', {
        exact: true,
      }),
    ).toBeVisible();
    await pause();
    const profile = await api(
      buyer.c,
      'GET',
      `/community/users/${seller.me.id}/reputation`,
    );
    expect(profile.scopes.find((x) => x.source === 'SIMULATED').ratings).toBe(
      1,
    );
    expect(profile.scopes.find((x) => x.source === 'LIVE').ratings).toBe(0);
    const denied = await buyer.c.request.get(BASE + '/api/v1/admin/governance');
    expect(denied.status()).toBe(403);
    const comment = await api(
      seller.c,
      'POST',
      `/products/${productId}/comments`,
      {
        content: '本地审核测试留言：请不要离开站内订单到其他地方转账。',
        replyToId: null,
      },
    );
    const report = await api(buyer.c, 'POST', '/community/reports', {
      resourceType: 'PRODUCT_COMMENT',
      resourceId: comment.id,
      reason:
        '本地验收：这条留言含转账词，请核实完整语境，不应只凭关键词处罚。',
    });
    result.reportId = report.id;
    await login('admin');
    await p.goto(BASE + `/admin/community?tab=reports&reportId=${report.id}`);
    await chapter('管理员', '核对举报人、原文和语境，避免关键词误判');
    await expect(
      p.getByText(seller.me.nickname, { exact: true }),
    ).toBeVisible();
    await expect(p.getByText(comment.content, { exact: true })).toBeVisible();
    await p.getByLabel('处理动作').selectOption('REJECT');
    await p
      .getByLabel('核查说明', { exact: true })
      .fill(
        '已核对完整原文：这是提醒不要站外转账，不是诱导转账。本地测试举报不成立，保持内容。',
      );
    await pause();
    await p.getByRole('button', { name: '保存处理', exact: true }).click();
    await expect(
      p.getByText(
        '处理说明：已核对完整原文：这是提醒不要站外转账，不是诱导转账。本地测试举报不成立，保持内容。',
        { exact: true },
      ),
    ).toBeVisible();
    await p.goto(BASE + '/admin');
    await chapter('管理员', '真实业务待办与来源统计，人工决定后留痕');
    await expect(
      p.getByRole('heading', { name: '关联数据核查', exact: true }),
    ).toBeVisible();
    await p
      .getByText('管理员处理顺序：线索、事实、结果', { exact: false })
      .click();
    await pause();
    await shot(p, 'admin-guide');
    result.checks.push(
      '评价与模拟信誉关联；举报原文与账号可追溯；人工依据语境驳回，不自动处罚；买家无管理权限',
    );
    if (!RECORD)
      for (const [role, url] of [
        ['buyer', '/orders'],
        ['seller', '/seller'],
        ['seller', '/publish'],
        ['admin', '/admin'],
        ['admin', '/admin/governance'],
      ]) {
        await login(role);
        await p.goto(BASE + url);
        await settle(p);
        for (const width of [320, 390, 844, 1280]) {
          await p.setViewportSize({ width, height: width === 844 ? 390 : 900 });
          await expect
            .poll(() =>
              p.evaluate(
                () => document.documentElement.scrollWidth - innerWidth,
              ),
            )
            .toBeLessThanOrEqual(1);
          result.viewportChecks = (result.viewportChecks || 0) + 1;
        }
      }
    if (RECORD) {
      await login('buyer');
      await p.setViewportSize({ width: 390, height: 844 });
      await p.goto(BASE + '/orders/' + result.orderNo);
      await chapter('手机端', '同一订单可继续查看交付与评价');
      await pause();
      await p.setViewportSize({ width: 1440, height: 960 });
      await p.goto(BASE + '/orders');
      await chapter('验收完成', '三种角色各有下一步，关键操作由本人确认');
      await pause();
    }
    expect(result.errors).toEqual([]);
    result.completedAt = new Date().toISOString();
  } catch (e) {
    if (p) {
      await shot(p, 'failure');
      fs.writeFileSync(
        path.join(OUT, 'failure.txt'),
        await p
          .locator('body')
          .innerText()
          .catch(() => ''),
      );
    }
    throw e;
  } finally {
    if (c) {
      const video = p?.video();
      await c.close();
      if (video) {
        const destination = path.join(OUT, 'role-journey.webm');
        await video.saveAs(destination);
        result.video = destination;
      }
    }
    fs.writeFileSync(
      path.join(OUT, 'result.json'),
      JSON.stringify(result, null, 2),
    );
    fs.writeFileSync(
      path.join(ROOT, '.local/role-experience-latest.json'),
      JSON.stringify(
        { out: OUT, result: path.join(OUT, 'result.json') },
        null,
        2,
      ),
    );
    await browser.close();
  }
  console.log(JSON.stringify(result, null, 2));
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
