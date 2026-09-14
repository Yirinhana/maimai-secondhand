const { test, expect } = require('@playwright/test');
const fs = require('fs'), path = require('path');
const BASE = 'http://127.0.0.1:5173';
const OUT = path.resolve(__dirname, '../../.local/screenshots');
const RUN = process.env.MAIMAI_E2E_RUN || Date.now().toString();
const actors = {}, state = { run: RUN, orders: [], screenshots: [], apiSetup: [], websocket: {}, map: {} };
const png = fs.readFileSync(path.join(__dirname, 'fixture8x8.png'));
async function shot(page, name) { const filename = `022-${RUN}-${name}.png`; await page.screenshot({ path: path.join(OUT, filename), fullPage: true }); state.screenshots.push(filename); }
async function api(ctx, method, endpoint, data, multipart) { if (!endpoint.startsWith('/api/v1/'))
    throw Error('Only local application API allowed'); await ctx.request.get(BASE + '/api/v1/auth/csrf'); const token = (await ctx.cookies(BASE)).find(c => c.name === 'XSRF-TOKEN')?.value; const r = await ctx.request.fetch(BASE + endpoint, { method, headers: { 'X-XSRF-TOKEN': decodeURIComponent(token || '') }, ...(multipart ? { multipart } : data === undefined ? {} : { data }) }); if (!r.ok())
    throw Error(`${method} ${endpoint.split('?')[0]} failed ${r.status()} ${await r.text()}`); const text = await r.text(); return text ? JSON.parse(text) : null; }
async function uiLogin(browser, name) { const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' }); const page = await context.newPage(); await page.goto(BASE + '/login'); await page.getByLabel('邮箱', { exact: true }).fill(name + '@maimai.local'); await page.getByLabel('密码', { exact: true }).fill('Maimai#2026'); await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page.getByRole('heading', { name: '让闲置再次流转' })).toBeVisible(); actors[name] = { context, page, me: await api(context, 'GET', '/api/v1/auth/me') }; }
async function createProduct(seller, suffix, price, freight) { const context = actors[seller].context; const cats = await api(context, 'GET', '/api/v1/categories'); const category = cats.find(c => c.children?.length)?.children[0]?.id || cats[0].id; const title = `E2E${RUN}-${suffix} 本地演示用品`; const product = await api(context, 'POST', '/api/v1/seller/products', { title, categoryId: category, description: '自动化验收专用本地测试商品，不是真实交易。', condition: 'GOOD', defects: '测试图片，仅本地业务验证', priceCents: price, stock: 10, region: '上海市黄浦区', deliveryMethods: ['EXPRESS', 'MEETUP'], freightCents: freight, returnPromise: '按已审核平台规则办理', submit: false }); await api(context, 'POST', `/api/v1/seller/products/${product.id}/images`, undefined, { files: { name: 'fixture8x8.png', mimeType: 'image/png', buffer: png } }); await api(context, 'POST', `/api/v1/seller/products/${product.id}/submit`); await api(actors.admin.context, 'POST', `/api/v1/admin/products/${product.id}/review`, { approve: true, reason: '本地自动化测试资料完整' }); state.apiSetup.push({ action: 'create/upload/submit/review own product', id: product.id, seller }); return { ...product, title, price, freight, seller }; }
function recordSockets(page, name) { const data = { opened: 0, ready: 0, changed: 0 }; state.websocket[name] = data; page.on('websocket', ws => { if (new URL(ws.url()).pathname !== '/api/v1/messages/socket')
    return; data.opened++; ws.on('framereceived', frame => { const value = String(frame.payload); if (value.includes('ready'))
    data.ready++; if (value.includes('messages.changed'))
    data.changed++; }); }); return data; }
async function confirmDialog(page) { await expect(page.getByRole('dialog')).toBeVisible(); await page.getByRole('dialog').getByRole('button', { name: '确认操作', exact: true }).click(); }
async function uiRegister(browser, name) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' });
    const page = await context.newPage(), email = `e2e-${RUN}-${name}@example.invalid`;
    actors[name] = { context, page, email };
    await page.goto(BASE + '/register');
    await expect(page.getByRole('checkbox')).not.toBeChecked();
    await page.getByLabel('邮箱', { exact: true }).fill(email);
    await page.getByRole('button', { name: '发送验证码', exact: true }).click();
    await expect(page.getByText('验证码已发送，10 分钟内有效，请查收邮箱', { exact: true })).toBeVisible();
    const captured = await api(context, 'GET', '/api/v1/dev/mailbox?email=' + encodeURIComponent(email));
    const code = captured[0]?.content.match(/验证码为：(\d{6})/)?.[1];
    if (!code)
        throw Error('Local capture mailbox did not contain registration code');
    state.apiSetup.push({ action: 'read only own local capture registration email', email });
    await page.getByLabel('邮箱验证码', { exact: true }).fill(code);
    await page.getByLabel('昵称', { exact: true }).fill('E2E' + name);
    await page.getByLabel('密码', { exact: true }).fill('E2EAccount#2026');
    await page.getByLabel('确认密码', { exact: true }).fill('E2EAccount#2026');
    await page.getByRole('button', { name: '注册并登录', exact: true }).click();
    await expect(page.getByText('请阅读并勾选同意条款后注册', { exact: true })).toBeVisible();
    await page.getByRole('checkbox').check();
    await page.getByRole('button', { name: '注册并登录', exact: true }).click();
    await expect(page.getByRole('heading', { name: '让闲置再次流转', exact: true })).toBeVisible();
    actors[name].me = await api(context, 'GET', '/api/v1/auth/me');
    return actors[name];
}
test.beforeAll(async ({ browser }) => { test.setTimeout(120000); for (const name of ['buyer', 'seller', 'seller2', 'admin', 'support'])
    await uiLogin(browser, name); state.productA = await createProduct('seller', 'A', 5000, 600); state.productB = await createProduct('seller2', 'B', 8000, 900); });
test.afterEach(async ({}, info) => { if (info.status !== info.expectedStatus) {
    for (const [name, actor] of Object.entries(actors)) {
        try {
            await shot(actor.page, 'failure-' + info.title.slice(0, 2) + '-' + name);
            fs.writeFileSync(path.join(OUT, `022-${RUN}-failure-${info.title.slice(0, 2)}-${name}.txt`), await actor.page.locator('body').innerText());
        }
        catch { }
    }
} });
test.afterAll(async () => { fs.writeFileSync(path.join(OUT, `022-${RUN}-evidence.json`), JSON.stringify(state, null, 2)); for (const actor of Object.values(actors))
    await actor.context.close(); });
test('01 desktop cart split, local mock payment, shipping, receipt, review and partial refund evidence', async () => {
    test.setTimeout(180000);
    const buyer = actors.buyer.page, seller = actors.seller.page;
    await buyer.goto(BASE + '/');
    await expect(buyer.getByRole('heading', { name: '让闲置再次流转' })).toBeVisible();
    await expect(buyer.getByRole('link', { name: new RegExp(state.productA.title) })).toBeVisible();
    await shot(buyer, 'desktop-home');
    const seedProducts = await api(actors.buyer.context, 'GET', '/api/v1/products?keyword=iPhone&page=0&size=1');
    expect(seedProducts.content.length).toBeGreaterThan(0);
    const seedProduct = seedProducts.content[0];
    await buyer.goto(BASE + `/products/${seedProduct.id}`);
    const seedImage = buyer.getByRole('img', { name: seedProduct.title + ' 图片 1', exact: true });
    await expect(seedImage).toHaveAttribute('src', /^\/uploads\/products\/seed-\d+\.jpg$/);
    await expect.poll(() => seedImage.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
    await expect(seedImage).toHaveAttribute('src', /^\/uploads\/products\/seed-\d+\.jpg$/);
    state.seedImage = { id: seedProduct.id, source: await seedImage.getAttribute('src'), loaded: true };
    await shot(buyer, 'seed-product-image-recovered');
    for (const product of [state.productA, state.productB]) {
        await buyer.goto(BASE + `/products/${product.id}`);
        await expect(buyer.getByRole('heading', { name: product.title, exact: true })).toBeVisible();
        await buyer.getByLabel('交付方式').selectOption('EXPRESS');
        const productImage = buyer.getByRole('img', { name: product.title + ' 图片 1', exact: true });
        await expect(productImage).toBeVisible();
        await expect(productImage).toHaveAttribute('src', /^\/uploads\/products\//);
        await expect.poll(() => productImage.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
        await expect(productImage).toHaveAttribute('src', /^\/uploads\/products\//);
        if (product === state.productA)
            await shot(buyer, 'desktop-product');
        await buyer.getByRole('button', { name: '加入购物车', exact: true }).click();
        await expect(buyer.getByRole('status').filter({ hasText: '已加入购物车' })).toBeVisible();
    }
    await buyer.goto(BASE + '/cart');
    for (const product of [state.productA, state.productB])
        await buyer.getByRole('checkbox', { name: '选择 ' + product.title, exact: true }).check();
    await shot(buyer, 'cart-two-sellers');
    await buyer.getByRole('button', { name: '去结算', exact: true }).click();
    await expect(buyer.getByRole('heading', { name: '确认订单', exact: true })).toBeVisible();
    await expect(buyer.getByText('子订单合计', { exact: true })).toHaveCount(2);
    await shot(buyer, 'checkout-split');
    const checkoutResponse = buyer.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/checkout' && r.request().method() === 'POST');
    await buyer.getByRole('button', { name: '提交订单', exact: true }).click();
    const checkout = await checkoutResponse;
    expect(checkout.status()).toBe(200);
    const batch = await checkout.json();
    expect(batch.orders).toHaveLength(2);
    state.orders = batch.orders.map(o => o.orderNo);
    for (const order of batch.orders) {
        await buyer.goto(BASE + `/orders/${order.orderNo}`);
        await buyer.getByRole('button', { name: '发起支付', exact: true }).click();
        await expect(buyer.getByText('本地模拟支付，不会扣款，也不代表微信支付已开通。', { exact: true })).toBeVisible();
        if (order === batch.orders[0])
            await shot(buyer, 'local-mock-payment');
        await buyer.getByRole('button', { name: '模拟支付成功', exact: true }).click();
        await expect(buyer.getByRole('button', { name: '发起支付', exact: true })).toHaveCount(0);
    }
    const order = await api(actors.buyer.context, 'GET', `/api/v1/orders/${batch.orders.find(o => o.sellerId === actors.seller.me.id)?.orderNo || batch.orders[0].orderNo}`);
    expect(order.sellerId).toBe(actors.seller.me.id);
    state.completedOrder = order.orderNo;
    await seller.goto(BASE + `/orders/${order.orderNo}`);
    await seller.getByLabel('快递公司').selectOption('shunfeng');
    await seller.getByLabel('快递单号', { exact: true }).fill('E2E' + RUN);
    await seller.getByRole('button', { name: '确认发货', exact: true }).click();
    await expect(seller.getByRole('heading', { name: '物流进度', exact: true })).toBeVisible();
    await shot(seller, 'seller-shipped');
    await buyer.goto(BASE + `/orders/${order.orderNo}`);
    await buyer.getByRole('button', { name: '确认已收到货物', exact: true }).click();
    await expect(buyer.getByRole('dialog')).toBeVisible();
    await buyer.getByRole('dialog').getByRole('button', { name: '确认操作', exact: true }).click();
    await expect(buyer.getByRole('heading', { name: '交易评价', exact: true })).toBeVisible();
    await buyer.getByLabel('评价内容', { exact: true }).fill('E2E实际收货评价 ' + RUN);
    await buyer.getByRole('button', { name: '提交评价', exact: true }).click();
    await expect(buyer.getByText('E2E实际收货评价 ' + RUN, { exact: true })).toBeVisible();
    await shot(buyer, 'completed-order-rating');
    await buyer.getByRole('button', { name: '申请售后', exact: true }).click();
    await buyer.getByLabel('类型').selectOption('REFUND_ONLY');
    await buyer.getByLabel('商品退款（元）', { exact: true }).fill('10.00');
    await buyer.getByLabel('运费退款（元）', { exact: true }).fill('0');
    await buyer.getByLabel('原因', { exact: true }).fill('本地E2E部分退款验收，不是真实资金');
    await buyer.getByLabel('证据说明', { exact: true }).fill('本地8x8测试图片');
    await buyer.getByRole('button', { name: '提交申请', exact: true }).click();
    await expect(buyer).toHaveURL(/\/aftersales\/\d+$/);
    state.aftersaleId = Number(buyer.url().split('/').pop());
    await buyer.locator('input[type=file]').setInputFiles(path.join(__dirname, 'fixture8x8.png'));
    const evidence = buyer.getByRole('img', { name: '售后证据 1', exact: true });
    await expect(evidence).toBeVisible();
    await expect.poll(() => evidence.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
    state.evidencePath = await evidence.getAttribute('src');
    expect((await actors.seller2.context.request.get(BASE + state.evidencePath)).status()).toBe(404);
    await shot(buyer, 'private-aftersale-evidence');
    await seller.goto(BASE + `/aftersales/${state.aftersaleId}`);
    await seller.getByLabel('同意申请（直接退款）', { exact: true }).check();
    await seller.getByPlaceholder('给买家的答复说明').fill('同意本地模拟部分退款');
    await seller.getByRole('button', { name: '提交答复', exact: true }).click();
    await expect(seller.getByText('已解决', { exact: true })).toBeVisible();
    await buyer.goto(BASE + `/orders/${order.orderNo}`);
    await expect(buyer.getByText(/部分退款/).first()).toBeVisible();
    const updated = await api(actors.buyer.context, 'GET', `/api/v1/orders/${order.orderNo}`);
    expect(updated.refundStatus).toBe('PARTIAL');
    state.refund = { status: updated.refundStatus, goods: 1000 };
    await shot(buyer, 'partial-refund-complete');
    await buyer.getByRole('button', { name: '申请售后', exact: true }).click();
    await buyer.getByLabel('类型').selectOption('RETURN_REFUND');
    await buyer.getByLabel('商品退款（元）', { exact: true }).fill('40');
    await buyer.getByLabel('运费退款（元）', { exact: true }).fill('0');
    await buyer.getByLabel('原因', { exact: true }).fill('E2E剩余商品款退货流程演示');
    await buyer.getByLabel('证据说明', { exact: true }).fill('不寄真实包裹，仅验证页面状态');
    await buyer.getByRole('button', { name: '提交申请', exact: true }).click();
    await expect(buyer).toHaveURL(/\/aftersales\/\d+$/);
    state.returnAftersaleId = Number(buyer.url().split('/').pop());
    await seller.goto(BASE + `/aftersales/${state.returnAftersaleId}`);
    await seller.getByLabel('同意申请（进入退货流程）', { exact: true }).check();
    await seller.getByLabel('退货收件人', { exact: true }).fill('E2E测试收件人');
    await seller.getByLabel('退货联系电话', { exact: true }).fill('13800000001');
    await seller.getByLabel('完整退货地址', { exact: true }).fill('上海市黄浦区 E2E本地测试点，不是真实收件地址');
    await seller.getByRole('button', { name: '提交答复', exact: true }).click();
    await expect(seller.getByText('卖家提供的退货地址', { exact: true })).toBeVisible();
    await shot(seller, 'return-approved-address');
    await buyer.reload();
    await buyer.getByLabel('快递公司', { exact: true }).fill('shunfeng');
    await buyer.getByLabel('运单号', { exact: true }).fill('E2ERETURN' + RUN);
    await buyer.getByRole('button', { name: '确认寄出', exact: true }).click();
    await expect(buyer.getByRole('button', { name: '确认寄出', exact: true })).toHaveCount(0);
    await seller.reload();
    await seller.getByRole('button', { name: '确认退件已实际签收', exact: true }).click();
    await confirmDialog(seller);
    await expect(seller.getByText('验退答复截止', { exact: true })).toBeVisible();
    await buyer.reload();
    await buyer.getByRole('button', { name: '申请人工介入', exact: true }).click();
    await expect(buyer.getByText('该售后单正在等待平台人工核实。已完成的寄回、签收等操作保留在下方处理记录中，请留意后续答复。', { exact: true })).toBeVisible();
    await expect(buyer.getByRole('list', { name: '售后进度', exact: true })).toHaveCount(0);
    await expect(buyer.locator('.mm-aftersale-detail__log-action').filter({ hasText: '卖家确认退件签收' })).toBeVisible();
    await expect(buyer.locator('.mm-aftersale-detail__log-action').filter({ hasText: '转入平台人工处理' })).toBeVisible();
    expect(await buyer.locator('body').innerText()).not.toMatch(/\b(CREATE|RETURN_RECEIVED|PENDING_MANUAL)\b/);
    await shot(buyer, 'return-received-manual-intervention');
    state.returnStatus = (await api(actors.buyer.context, 'GET', `/api/v1/aftersales/${state.returnAftersaleId}`)).status;
    expect(state.returnStatus).toBe('PENDING_MANUAL');
});
test('02 two isolated browser contexts exchange messages with websocket unread and history', async () => {
    const buyer = actors.buyer.page, seller = actors.seller.page;
    const buyerWs = recordSockets(buyer, 'buyer'), sellerWs = recordSockets(seller, 'seller');
    await seller.goto(BASE + '/messages');
    await buyer.goto(BASE + `/products/${state.productA.id}`);
    await buyer.getByRole('button', { name: '联系卖家', exact: true }).click();
    await expect(buyer).toHaveURL(/\/messages\/\d+$/);
    const id = Number(buyer.url().split('/').pop());
    state.conversationId = id;
    const text = 'E2E实时私信 ' + RUN;
    await buyer.getByLabel('消息内容', { exact: true }).fill(text);
    await buyer.getByRole('button', { name: '发送', exact: true }).click();
    await expect(buyer.getByText(text, { exact: true })).toBeVisible();
    const row = seller.locator('.mm-conversations__item').filter({ hasText: text });
    await expect(row).toBeVisible();
    await expect(row.locator('.mm-conversations__unread')).toBeVisible();
    await shot(seller, 'message-unread');
    await row.click();
    await expect(seller.getByText(text, { exact: true })).toBeVisible();
    const reply = 'E2E卖家实时答复 ' + RUN;
    await seller.getByLabel('消息内容', { exact: true }).fill(reply);
    await seller.getByRole('button', { name: '发送', exact: true }).click();
    await expect(buyer.getByText(reply, { exact: true })).toBeVisible();
    await expect.poll(() => buyerWs.ready).toBeGreaterThan(0);
    await expect.poll(() => sellerWs.changed).toBeGreaterThan(0);
    await buyer.reload();
    await expect(buyer.getByText(text, { exact: true })).toBeVisible();
    await expect(buyer.getByText(reply, { exact: true })).toBeVisible();
    await shot(buyer, 'message-history');
    expect(buyerWs.opened).toBeGreaterThan(0);
    const imageResponse = buyer.waitForResponse(r => new URL(r.url()).pathname === `/api/v1/messages/conversations/${id}/images` && r.request().method() === 'POST');
    await buyer.locator('input[type=file]').setInputFiles(path.join(__dirname, 'fixture8x8.png'));
    const uploaded = await imageResponse;
    expect(uploaded.ok()).toBe(true);
    const attachment = await uploaded.json();
    const ownImage = buyer.getByRole('img', { name: '图片消息', exact: true }).last();
    await expect(ownImage).toBeVisible();
    await expect.poll(() => ownImage.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
    const peerImage = seller.locator('img.mm-chat__image').last();
    await expect(peerImage).toBeVisible();
    await expect.poll(() => peerImage.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
    state.messageImagePath = await ownImage.getAttribute('src');
    await expect(peerImage).toHaveAttribute('src', state.messageImagePath);
    expect((await actors.seller2.context.request.get(BASE + state.messageImagePath)).status()).toBe(404);
    state.messageImage = { id: attachment.id, ownerLoaded: true, peerLoaded: true, strangerStatus: 404 };
    await shot(buyer, 'message-image-private');
    await buyer.getByRole('button', { name: '屏蔽对方', exact: true }).click();
    await confirmDialog(buyer);
    await expect(buyer.getByText('当前双方不能发送新消息或图片。历史记录仍可查看，订单通知不受影响。', { exact: true })).toBeVisible();
    await expect(buyer.getByRole('button', { name: '发送', exact: true })).toBeDisabled();
    await expect(buyer.getByText(text, { exact: true })).toBeVisible();
    await seller.getByRole('button', { name: '刷新屏蔽状态', exact: true }).click();
    await expect(seller.getByRole('button', { name: '图片', exact: true })).toBeDisabled();
    await shot(buyer, 'message-blocked-history');
    await buyer.getByRole('button', { name: '取消屏蔽', exact: true }).click();
    await confirmDialog(buyer);
    await seller.getByRole('button', { name: '刷新屏蔽状态', exact: true }).click();
    await expect(seller.getByRole('button', { name: '图片', exact: true })).toBeEnabled();
    await buyer.getByLabel('消息内容', { exact: true }).fill('E2E解除屏蔽 ' + RUN);
    await buyer.getByRole('button', { name: '发送', exact: true }).click();
    await expect(seller.getByText('E2E解除屏蔽 ' + RUN, { exact: true })).toBeVisible();
    state.messageBlock = { historyRetained: true, sendDisabledBothSides: true, unblockResumed: true };
});
test('03 user creates ticket and human support claims and replies via UI', async () => {
    const buyer = actors.buyer.page, staff = actors.support.page;
    await buyer.goto(BASE + '/support');
    await buyer.getByRole('button', { name: '联系人工客服', exact: true }).click();
    const title = 'E2E人工工单 ' + RUN;
    await buyer.getByLabel('问题标题', { exact: true }).fill(title);
    await buyer.getByLabel('问题描述', { exact: true }).fill('验证工单与人工回复闭环，无敏感个人信息。');
    if (state.completedOrder)
        await buyer.getByLabel('关联订单号（可选）', { exact: true }).fill(state.completedOrder);
    await buyer.getByRole('button', { name: '提交工单', exact: true }).click();
    await expect(buyer).toHaveURL(/\/support\/tickets\/\d+$/);
    state.ticketId = Number(buyer.url().split('/').pop());
    await buyer.getByRole('button', { name: '请求规则解释', exact: true }).click();
    await expect(buyer.getByRole('status').filter({ hasText: '人工' })).toBeVisible();
    await shot(buyer, 'ticket-ai-unconfigured-human-available');
    await staff.goto(BASE + '/admin/support');
    await staff.getByRole('link').filter({ hasText: title }).click();
    await staff.getByRole('button', { name: '接管工单', exact: true }).click();
    await staff.getByLabel('回复用户', { exact: true }).fill('人工客服已接管并回复 ' + RUN);
    await staff.getByRole('button', { name: '发送', exact: true }).click();
    await expect(staff.getByText('人工客服已接管并回复 ' + RUN, { exact: true })).toBeVisible();
    await shot(staff, 'support-staff-response');
    if (state.completedOrder) {
        await staff.getByRole('link', { name: '关联订单 ' + state.completedOrder, exact: true }).click();
        await expect(staff.getByRole('heading', { name: '订单详情', exact: true })).toBeVisible();
        await expect(staff.getByText(state.completedOrder, { exact: true })).toBeVisible();
        state.staffOrderLink = 'UI opened related order successfully';
    }
    await buyer.getByRole('button', { name: '刷新回复', exact: true }).click();
    await expect(buyer.getByText('人工客服已接管并回复 ' + RUN, { exact: true })).toBeVisible();
    expect((await actors.seller2.context.request.get(BASE + `/api/v1/support/tickets/${state.ticketId}`)).status()).toBe(404);
});
test('04 admin finance list detail and CSV download use actual persisted records', async () => {
    const admin = actors.admin.page;
    if (state.completedOrder) {
        await admin.goto(BASE + '/admin/aftersales');
        await admin.locator(`a[href="/orders/${state.completedOrder}"]`).first().click();
        await expect(admin.getByRole('heading', { name: '订单详情', exact: true })).toBeVisible();
        await expect(admin.getByText(state.completedOrder, { exact: true })).toBeVisible();
        state.adminOrderLink = 'UI opened aftersales order successfully';
    }
    await admin.goto(BASE + '/admin/finance');
    await expect(admin.getByRole('heading', { name: '资金与对账', exact: true })).toBeVisible();
    await expect(admin.getByText('本站内部流水及预计分配；未核对渠道账单，未执行真实分账。模拟数据不代表资金到账。', { exact: true })).toBeVisible();
    await admin.getByRole('button', { name: '查看内部明细', exact: true }).first().click();
    await expect(admin.getByRole('heading', { name: /内部明细/ })).toBeVisible();
    await shot(admin, 'admin-finance-details');
    const downloaded = admin.waitForEvent('download');
    await admin.getByRole('link', { name: '导出当前日期范围 CSV', exact: true }).click();
    const download = await downloaded;
    const file = path.join(OUT, `022-${RUN}-finance.csv`);
    await download.saveAs(file);
    const csv = fs.readFileSync(file, 'utf8');
    expect(csv).toContain('订单');
    expect(csv.length).toBeGreaterThan(100);
    state.financeCsv = path.basename(file);
});
test('05 real AMap public Shanghai search and chosen address', async () => {
    test.setTimeout(90000);
    const page = actors.buyer.page;
    const observations = [];
    state.map = { provider: 'AMap real JSAPI', query: '上海人民广场', observations };
    page.on('response', async (r) => { try {
        const u = new URL(r.url());
        if (!u.pathname.includes('/_AMapService/'))
            return;
        const item = { path: u.pathname, status: r.status(), responseMs: r.request().timing().responseStart, parameterNames: [...u.searchParams.keys()], parameterCounts: Object.fromEntries([...new Set(u.searchParams.keys())].map(k => [k, u.searchParams.getAll(k).length])) };
        try {
            const raw = await r.text();
            try {
                const body = JSON.parse(raw);
                item.info = body.info;
                item.infocode = body.infocode;
                item.count = body.count;
            }
            catch {
                item.infocode = raw.match(/"infocode"\s*:\s*"([A-Za-z0-9_]+)"/)?.[1];
            }
        }
        catch { }
        observations.push(item);
    }
    catch { } });
    await page.goto(BASE + '/me');
    await page.getByRole('tab', { name: '收货地址', exact: true }).click();
    await page.getByRole('button', { name: '新增地址', exact: true }).click();
    await page.getByRole('button', { name: '使用地图辅助选址', exact: true }).click();
    await expect(page.getByLabel('地点选择地图', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '搜索地点', exact: true })).toBeEnabled({ timeout: 35000 });
    await page.getByLabel('搜索城市或地点', { exact: true }).fill('上海人民广场');
    const poiResponse = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/_AMapService/v3/place/text'));
    await page.getByRole('button', { name: '搜索地点', exact: true }).click();
    expect((await poiResponse).status(), 'Real AMap POI response HTTP status').toBe(200);
    const place = page.getByRole('button').filter({ hasText: /人民广场.*·/ }).first();
    await expect(place).toBeVisible({ timeout: 25000 });
    const addressResponse = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/_AMapService/v3/geocode/regeo'));
    await place.click();
    expect((await addressResponse).status(), 'Real AMap reverse geocode response HTTP status').toBe(200);
    await expect(page.getByRole('button', { name: '使用此地址', exact: true })).toBeVisible({ timeout: 20000 });
    await page.getByRole('button', { name: '使用此地址', exact: true }).click();
    await expect(page.getByLabel('详细地址', { exact: true })).toHaveValue(/上海/);
    for (const endpoint of ['/v3/place/text', '/v3/geocode/regeo']) {
        await expect.poll(() => observations.some(item => item.path.endsWith(endpoint) && item.status === 200 && item.infocode === '10000')).toBe(true);
    }
    state.map = { provider: 'AMap real JSAPI', query: '上海人民广场', selectedAddress: await page.getByLabel('详细地址', { exact: true }).inputValue(), observations };
    await shot(page, 'real-amap-shanghai-selection');
});
test('06 mobile 360px layout has no horizontal overflow', async () => {
    const page = actors.buyer.page;
    state.mobile = [];
    await page.setViewportSize({ width: 360, height: 800 });
    for (const route of ['/', `/products/${state.productA.id}`, '/cart', '/support']) {
        const cartResponse = route === '/cart' ? page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/cart' && r.request().method() === 'GET') : null;
        await page.goto(BASE + route);
        await expect(page.locator('h1').first()).toBeVisible();
        if (route === '/') {
            await expect(page.getByRole('heading', { name: state.productA.title, exact: true })).toBeVisible();
            const images = page.locator('.mm-product__cover img');
            expect(await images.count()).toBeGreaterThan(0);
            for (const image of await images.all()) {
                await image.scrollIntoViewIfNeeded();
                await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
                await expect(image).toHaveAttribute('src', /^\/uploads\/products\//);
            }
        } else if (route.startsWith('/products/')) {
            await expect(page.getByRole('heading', { name: state.productA.title, exact: true })).toBeVisible();
            const image = page.getByRole('img', { name: state.productA.title + ' 图片 1', exact: true });
            await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
            await expect(image).toHaveAttribute('src', /^\/uploads\/products\//);
        } else if (cartResponse) {
            const response = await cartResponse;
            expect(response.ok()).toBe(true);
            const items = await response.json();
            if (items.length) await expect(page.locator('.mm-cart__item')).toHaveCount(items.length);
            else await expect(page.getByText('购物车是空的', { exact: true })).toBeVisible();
            await expect(page.getByText('加载中…', { exact: true })).toHaveCount(0);
        } else {
            await expect(page.locator('summary').first()).toBeVisible();
        }
        await page.evaluate(async () => { await document.fonts.ready; window.scrollTo(0, 0); });
        const widths = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
        expect(widths.document, route + ' has document horizontal overflow').toBeLessThanOrEqual(360);
        expect(widths.body, route + ' has body horizontal overflow').toBeLessThanOrEqual(360);
        state.mobile.push({ route, ...widths });
        await shot(page, 'mobile-' + (route === '/' ? 'home' : route.split('/')[1]));
    }
    await page.setViewportSize({ width: 1440, height: 960 });
});
test('07 explicit policy consent local capture registration and own fresh account closure', async ({ browser }) => {
    const account = await uiRegister(browser, 'closure'), page = account.page;
    state.newClosedAccount = { id: account.me.id, email: account.email };
    await shot(page, 'registered-with-policy-consent');
    await page.goto(BASE + '/me/closure');
    await expect(page.getByRole('button', { name: '申请注销', exact: true })).toBeDisabled();
    await page.getByLabel('当前密码', { exact: true }).fill('E2EAccount#2026');
    await page.getByLabel('申请原因', { exact: true }).fill('本次自动化验收新注册账号自愿注销，保留测试证据');
    await page.getByRole('checkbox', { name: '我已了解停用影响并确认申请', exact: true }).check();
    await page.getByRole('button', { name: '申请注销', exact: true }).click();
    await confirmDialog(page);
    await expect(page.getByRole('status').filter({ hasText: '注销申请已记录，账号已停用' })).toBeVisible();
    expect((await account.context.request.get(BASE + '/api/v1/auth/me')).status()).toBe(401);
    await shot(page, 'fresh-account-closure-recorded');
});
test('08 admin creates disables own category and grants then revokes own test account role', async ({ browser }) => {
    const target = await uiRegister(browser, 'role'), admin = actors.admin.page;
    state.newRoleAccount = { id: target.me.id, email: target.email };
    await admin.goto(BASE + '/admin/categories');
    await admin.getByRole('button', { name: '新增分类', exact: true }).click();
    const category = 'E2E验收分类' + RUN;
    await admin.getByLabel('名称', { exact: true }).fill(category);
    await admin.getByLabel('排序值', { exact: true }).fill('999');
    await admin.getByLabel('变更原因', { exact: true }).fill('本次UI验收创建的独立测试分类');
    const createdResponse = admin.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/admin/categories' && r.request().method() === 'POST');
    await admin.getByRole('button', { name: '保存分类', exact: true }).click();
    const created = await createdResponse;
    expect(created.ok()).toBe(true);
    state.newCategoryId = (await created.json()).id;
    const categoryRow = admin.locator('article').filter({ has: admin.getByText(category, { exact: true }) });
    await expect(categoryRow).toBeVisible();
    await categoryRow.getByRole('button', { name: '编辑', exact: true }).click();
    await admin.getByLabel('状态').selectOption('DISABLED');
    await admin.getByLabel('变更原因', { exact: true }).fill('验收完成，停用测试分类并保留记录');
    await admin.getByRole('button', { name: '保存分类', exact: true }).click();
    await expect(categoryRow).toContainText('已停用');
    await shot(admin, 'admin-category-disabled');
    await admin.goto(BASE + '/admin/users');
    await admin.getByLabel('按邮箱或昵称查询', { exact: true }).fill(target.email);
    await admin.getByRole('button', { name: '查询', exact: true }).click();
    const userRow = admin.locator('article').filter({ hasText: target.email });
    await expect(userRow).toBeVisible();
    for (const grant of [true, false]) {
        await userRow.getByRole('button', { name: '管理授权', exact: true }).click();
        await userRow.getByLabel('管理角色').selectOption('OPERATOR');
        await userRow.getByLabel('操作', { exact: false }).selectOption(grant ? 'true' : 'false');
        await userRow.getByLabel('授权变更原因', { exact: true }).fill(grant ? '本地UI验收临时授予测试账号运营角色' : '验收结束撤销测试角色，保留普通用户');
        await userRow.getByRole('button', { name: '保存授权变更', exact: true }).click();
        await confirmDialog(admin);
        await expect(userRow.getByRole('button', { name: '保存授权变更', exact: true })).toHaveCount(0);
        await expect.poll(async () => (await api(target.context, 'GET', '/api/v1/auth/me')).roles.includes('OPERATOR')).toBe(grant);
        if (grant) {
            await target.page.goto(BASE + '/admin/categories');
            await expect(target.page.getByRole('heading', { name: '分类维护', exact: true })).toBeVisible();
        }
    }
    expect((await target.context.request.get(BASE + '/api/v1/admin/categories')).status()).toBe(403);
    state.roleGovernance = { grantSeenByExistingSession: true, revokedPermissionStatus: 403 };
    await shot(admin, 'admin-role-revoked');
});
test('09 anonymous help loads public FAQ without requesting private tickets or AI', async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, locale: 'zh-CN' }), page = await context.newPage();
    actors.anonymous = { context, page };
    const requests = [];
    page.on('request', request => { const pathname = new URL(request.url()).pathname; if (pathname.startsWith('/api/v1/support/'))
        requests.push({ path: pathname, method: request.method() }); });
    await page.goto(BASE + '/support');
    await expect(page.getByRole('heading', { name: '帮助与客服', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: '登录后提交人工工单', exact: true })).toBeVisible();
    await expect(page.locator('summary').first()).toBeVisible();
    await page.locator('summary').first().click();
    await expect(page.locator('details[open] p')).toBeVisible();
    expect(requests).toContainEqual({ path: '/api/v1/support/faq', method: 'GET' });
    expect(requests.some(r => r.path.includes('tickets') || r.path.includes('ai-help'))).toBe(false);
    expect((await context.request.get(BASE + '/api/v1/support/tickets')).status()).toBe(401);
    state.anonymousHelp = { requests, privateTicketsStatus: 401 };
    await shot(page, 'anonymous-faq-without-private-fetch');
});
