# 本地网站端到端验收

缇娜组件专项：`node tests/e2e/node_modules/@playwright/test/cli.js test --config tests/e2e/playwright.039.config.cjs`（在项目根目录运行）。使用真实本地未接入AI的API检查权限和错误；两项明确标注controlled UI的用例使用浏览器受控回复，验证交互与退出隔离，不请求真实Hermes或发送邮件。

依赖项目独立运行的前端 `127.0.0.1:5173`、后端 `8081` 及专用本地种子账号。必须使用隔离本地环境：付款走 MOCK，邮件走捕获邮箱，物流不调用真实服务。真实高德检索只搜索公开上海地点，不请求设备定位。

```powershell
cd tests/e2e
npm ci --no-audit --no-fund
npm test -- --max-failures=1
```

使用项目内 `@playwright/test` 和已安装 Chrome 的独立无界面进程、全新浏览器上下文，不连接用户已有浏览器、配置或调试端口，不下载浏览器。

测试主要通过实际页面点击填写完成。准备商品、上传固定 8×8 本地测试图、审核商品、读取新注册邮箱的本地捕获验证码使用 HTTP setup。权限隔离和状态持久化另以 HTTP 断言补证。API setup 与运行证据写入每轮独立的 `.local/screenshots/022-<run>-evidence.json`。

九条场景包括：购物车拆单和模拟付款/履约/评价/售后证据/部分退款/退货及人工介入；WebSocket 私信、未读、图文历史及屏蔽；人工客服；后台财务和 CSV；真实地图；360px 手机布局；新账号注册与注销；独立测试账号后台授权撤销和新分类启停，以及匿名FAQ仅请求公开接口。

每轮创建自己带 `E2E` / 时间标识的商品、订单、工单、测试分类和测试账号，保留数据库记录及截图供审阅，不清理已有用户资料。账号注销仅针对本轮新注册的专用账号。JSON、CSV、截图和失败页面文本均留在被 Git 忽略的 `.local/screenshots/`。不记录地图 key/securityCode、完整请求 URL或网络 trace。
