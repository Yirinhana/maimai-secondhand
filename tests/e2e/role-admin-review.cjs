// Controlled UI case; does not create or close a real moderation case.
const { chromium, expect } = require('@playwright/test');
const fs = require('fs'),
  path = require('path');
const BASE = 'http://127.0.0.1:15173',
  OUT = path.resolve(__dirname, '../../.local/role-recovery');
(async () => {
  const b = await chromium.launch({ channel: 'chrome', headless: true });
  const result = { checks: [] };
  try {
    const c = await b.newContext();
    await c.request.get(BASE + '/api/v1/auth/csrf');
    const t = (await c.cookies()).find((x) => x.name === 'XSRF-TOKEN');
    expect(
      (
        await c.request.post(BASE + '/api/v1/auth/login', {
          headers: { 'X-XSRF-TOKEN': decodeURIComponent(t.value) },
          data: { email: 'admin@maimai.local', password: 'Maimai#2026' },
        })
      ).ok(),
    ).toBe(true);
    const p = await c.newPage();
    const item = {
      id: 99001,
      lane: 'PUBLIC',
      category: 'NEEDS_REVIEW',
      priority: 'NORMAL',
      status: 'OPEN',
      assignedTo: null,
      assignedName: null,
      title: '受控测试：核对完整语境',
      evidenceCount: 1,
      summary: '请依据原文核对。',
      updatedAt: new Date().toISOString(),
    };
    let writes = 0;
    await p.route('**/api/v1/admin/governance/cases**', (r) => {
      const url = r.request().url();
      if (r.request().method() === 'POST') {
        writes++;
        expect(r.request().postDataJSON().note).toBe(
          '已核对完整原文及对应账号，没有发现违反规则的内容。',
        );
        return r.fulfill({
          json: {
            item: { ...item, status: 'DISMISSED' },
            evidence: [],
            actions: [],
          },
        });
      }
      return r.fulfill({
        json: /\/99001(?:\?|$)/.test(url)
          ? { item, evidence: [], actions: [] }
          : { items: [item], total: 1, page: 0, totalPages: 1 },
      });
    });
    await p.goto(BASE + '/admin/governance?caseId=99001');
    await expect(
      p.getByRole('heading', { name: '核查材料', exact: true }),
    ).toBeVisible();
    await p.getByLabel('跟进动作').selectOption('DISMISS');
    await p.getByRole('button', { name: '保存跟进记录' }).click();
    await expect(
      p.getByText('请先核对原文、账号及关联业务，再确认处理结论。', {
        exact: true,
      }),
    ).toBeVisible();
    expect(writes).toBe(0);
    await p.getByRole('checkbox', { name: /我已核对原文/ }).check();
    await p.getByRole('button', { name: '保存跟进记录' }).click();
    await expect(
      p.getByText('请至少填写6个字，说明核对的依据或接下来要做什么。', {
        exact: true,
      }),
    ).toBeVisible();
    expect(writes).toBe(0);
    result.checks.push(
      '结案前须确认人工核对并填写依据；错误提示在对应填写区，不发空操作',
    );
    await p
      .getByLabel('核查说明')
      .fill('已核对完整原文及对应账号，没有发现违反规则的内容。');
    await p.getByRole('button', { name: '关闭材料', exact: true }).click();
    await expect(p.getByRole('dialog')).toBeVisible();
    await p
      .getByRole('dialog')
      .getByRole('button', { name: '暂不操作', exact: true })
      .click();
    await expect(p.getByLabel('核查说明')).toHaveValue(
      '已核对完整原文及对应账号，没有发现违反规则的内容。',
    );
    result.checks.push('未保存核查说明离开前提醒，取消后内容仍保留');
    await p.getByRole('button', { name: '保存跟进记录' }).click();
    await expect.poll(() => writes).toBe(1);
    await expect(p.getByText(/跟进记录已保存/)).toBeVisible();
    result.checks.push('人工选择与说明一次提交，跟进结果明确区分原业务处理');
  } finally {
    fs.writeFileSync(
      path.join(OUT, 'admin-review.json'),
      JSON.stringify(result, null, 2),
    );
    await b.close();
  }
  console.log(JSON.stringify(result));
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
