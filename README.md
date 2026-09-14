# 麦麦二手

面向通用二手实物交易的课程项目，采用 Vue 3、TypeScript、Spring Boot、Java 21 和 MySQL。用户端与管理后台在同一个响应式网站中，商品、订单、社区、私信和客服按业务模块组织。

当前交付目标是可运行的**本地开发版本**。微信真实付款、退款和分账尚未开通，物流、SMTP 邮件及 Hermes 仍需各自配置后验收；没有部署到腾讯云。开发页面中的付款和物流会明确显示模拟状态，内部财务预期不代表卖家已收到钱。

v0.2.0 本地验证已完成：后端 **191/191**，新版界面专项 **5/5**，原交易流程 **9/9** 全部通过，包含高德真实地点检索。网站改为暖白/炭黑/橙色，分区布局、顶部导航、头像上传与菜单、官方文章管理均可实际使用。证据、交付包和剩余条件见 [v0.2.0验收记录](docs/testing/interface-renewal.md)；[v0.1.0记录](docs/testing/local-validation.md)保留用于追溯。

v0.2.1 继续打磨首页、发现、商品详情、账户和交易页面：手机高级筛选折叠、大图与键盘操作、密码显隐、失败重试、购物车与结算分区、浏览位置恢复。TypeScript/Vite 构建、4/4 独立账号交互用例及 21 个页面视口组合通过。支付方案保持原样。本轮范围与证据见 [v0.2.1验收记录](docs/testing/visual-ux-polish.md)；后端测试数量沿用上一版本记录，不计为本轮重新执行。

v0.2.2 为五件示范商品配置图片，注册页加入独立生活场景配图，并明确标注商品图片为演示示意。后端 **193/193**、前端构建、12 个页面视口组合和候选包校验通过。当前本地实例的五张示范图已经备份后更新，其他上传图保持。用户已授权腾讯云部署，正在等待实际 SSH 接入信息，尚未上线；详见 [v0.2.2 验收与部署进度](docs/testing/demo-imagery.md)。

GitHub 私有仓库 [Yirinhana/maimai-secondhand](https://github.com/Yirinhana/maimai-secondhand) 已建立，本地 origin 已确认直连正式 GitHub。CI 与 PR 模板已准备并通过本地 actionlint 静态检查，平台实际运行状态以 [GitHub Actions](https://github.com/Yirinhana/maimai-secondhand/actions) 为准；静态校验不能替代平台运行通过。远程推送遵循用户确认，流程见 [GitHub 协作说明](docs/development/github-workflow.md)。

## 目录

| 路径 | 用途 |
| --- | --- |
| `frontend/src/` | 页面、公共组件、路由、状态及 API 客户端 |
| `backend/src/main/java/com/maimai/` | 按业务划分的后端模块 |
| `backend/src/main/resources/db/migration/` | Flyway 增量数据库迁移，已应用文件不得修改 |
| `backend/src/test/` | 金额、权限、并发、HTTP 及真实 MySQL 测试 |
| `tests/e2e/` | 独立 headless Chrome 浏览器验收 |
| `.github/` | 真实 MySQL 后端测试、前端构建工作流及 PR 模板 |
| `scripts/` | 启动、验证及候选包构建 |
| `deploy/` | Ubuntu、Nginx、systemd 配置样例及部署说明 |
| `docs/` | 已确认需求、条款、设计、课程模板映射及验证记录 |
| `VERSION` | 产品版本唯一来源，Maven 构建时由脚本传入 |
| `.local/` | 忽略的本地凭据、数据库、工具、日志、截图及构建包 |

根目录四份课程 DOCX 是原始提交模板，保持原样。需求和条款 DOCX 保存在 `docs/requirements/`、`docs/policies/`；本轮实施与验收以 `docs/testing/demo-imagery.md` 为入口。

## 本机启动

本工作区已配置独立 MySQL 开发实例 `127.0.0.1:3307`，与现有 MySQL80 服务分开；需要该实例运行。`.local/environment.json` 保存便携 Java/Maven 路径和本地数据库参数，脚本只设置当前进程环境。

在两个 PowerShell 终端中分别运行：

```powershell
./scripts/dev-backend.ps1
./scripts/dev-frontend.ps1
```

浏览器打开 `http://127.0.0.1:5173`，后端监听 `127.0.0.1:8081`。不要同时启动两份后端。跨机器初始化和环境变量见 [开发说明](docs/development.md)。

仅 `local` 模式会初始化演示账号：`buyer@maimai.local`、`seller@maimai.local`、`seller2@maimai.local`、`admin@maimai.local`、`operator@maimai.local`、`support@maimai.local`，密码均为 `Maimai#2026`。这些是公开的本地测试数据，不能用于生产。

## 构建与验证

```powershell
./scripts/test.ps1   # Maven verify、前端类型检查和构建
./scripts/build.ps1  # 同样验证后生成带时间戳的 .local/releases/ 候选目录
```

后端测试连接独立的 `maimai_test` 数据库，不使用内存数据库。不要把测试配置指向正式数据。需安装 Java 21、Maven、Node 24/npm 11；前后端依赖分别由 Maven 和 npm 锁定/解析。Linux/macOS 可用 `scripts/build-all.sh` 验证，环境变量需事先设置。

首次运行 `test.ps1` 前先执行 `npm ci --prefix frontend` 安装锁定依赖；`build.ps1` 自带这一步。Windows 下执行 `npm ci` 或 `build.ps1` 前先在本项目 Vite 终端按 Ctrl+C，避免其占用 `esbuild.exe`，安装完成后再启动前端。

浏览器测试在两个本地服务启动后执行：

```powershell
cd tests/e2e
npm ci
npm test
npm run test:interface
npm run test:ux
```

测试使用系统 Chrome 的独立 headless 实例，会在本地演示库创建测试订单、消息及售后材料；不操作用户已有浏览器，不发送外部邮件，也不进行真实扣款。

`npm test` 执行九项交易回归，含需要有效配置的真实高德地点搜索。`npm run test:interface` 执行新版分区、菜单、头像、官方内容权限及 360px 布局验收；只依赖本地演示账号和服务，会独立创建自己的资料、商品、模拟订单与售后，不读取历史截图作为输入。两套测试串行执行；界面套件结束后下架本次自己的测试商品，保留交易记录，官方测试文章通过页面撤回。结果与截图写入忽略的 `.local/screenshots/`。

`npm run test:ux` 执行 036 外观交互回归，注册独立的本地账号并保存该账号的头像、昵称、地址及购物车，读取现有商品。结算提交被测试浏览器拦截为 503，用于验证错误恢复与重试幂等，不创建订单、修改公共商品或进行支付。它与其他套件同样串行执行，详情见对应验收记录。

## 核心约定

- 商品实际成交金额按整数分计算，平台费为 `3/10000`，四舍五入到分，不含运费；部分退款按剩余商品金额重算。
- 购物车按卖家与交付信息拆单，分别付款；同子订单运费取最高项，面交运费为零。
- 会话与权限由后端校验。商品上传、私信图片和售后证据分别处理；私有图片必须通过授权端点读取。
- 高德 JavaScript API 的公开 Key 供浏览器加载 SDK，安全码仅由后端代理追加。它与后端 Web 服务 Key 是不同配置。
- `local` 仅监听回环地址；生产不能启用 `local/test`。所有凭据通过环境变量或忽略的本地文件提供。

正式部署步骤、容量假设和外部条件见 [部署说明](deploy/README.md)。用户最新提供的服务器、Hermes、`market.example.com` 备案与证书资料记录在 [服务器待核验清单](docs/development/server-readiness.md)，目前属于用户转述；尚未独立检查目标服务器或部署应用。
