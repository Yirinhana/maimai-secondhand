# 麦麦二手

面向通用二手实物交易的课程项目，采用 Vue 3、TypeScript、Spring Boot、Java 21 和 MySQL。用户端与管理后台在同一个响应式网站中，商品、订单、社区、私信和客服按业务模块组织。

当前交付目标是可运行的**本地开发版本**。微信真实付款、退款和分账尚未开通，物流、SMTP 邮件及 Hermes 仍需各自配置后验收；没有部署到腾讯云。开发页面中的付款和物流会明确显示模拟状态，内部财务预期不代表卖家已收到钱。

本轮本地验证已完成：后端 179 项测试全部通过，独立 Chrome 浏览器 9/9 场景通过，包含高德真实地点检索与地址选择。详细证据、交付包和剩余条件见 [本地验证记录](docs/testing/local-validation.md)。

## 目录

| 路径 | 用途 |
| --- | --- |
| `frontend/src/` | 页面、公共组件、路由、状态及 API 客户端 |
| `backend/src/main/java/com/maimai/` | 按业务划分的后端模块 |
| `backend/src/main/resources/db/migration/` | Flyway 增量数据库迁移，已应用文件不得修改 |
| `backend/src/test/` | 金额、权限、并发、HTTP 及真实 MySQL 测试 |
| `tests/e2e/` | 独立 headless Chrome 浏览器验收 |
| `scripts/` | 启动、验证及候选包构建 |
| `deploy/` | Ubuntu、Nginx、systemd 配置样例及部署说明 |
| `docs/` | 已确认需求、条款、设计、课程模板映射及验证记录 |
| `VERSION` | 产品版本唯一来源，Maven 构建时由脚本传入 |
| `.local/` | 忽略的本地凭据、数据库、工具、日志、截图及构建包 |

根目录四份课程 DOCX 是原始提交模板，保持原样。需求和条款 DOCX 保存在 `docs/requirements/`、`docs/policies/`；实施记录与最后验收以 `docs/testing/local-validation.md` 为入口。

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
```

测试使用系统 Chrome 的独立 headless 实例，会在本地演示库创建测试订单、消息及售后材料；不操作用户已有浏览器，不发送外部邮件，也不进行真实扣款。

## 核心约定

- 商品实际成交金额按整数分计算，平台费为 `3/10000`，四舍五入到分，不含运费；部分退款按剩余商品金额重算。
- 购物车按卖家与交付信息拆单，分别付款；同子订单运费取最高项，面交运费为零。
- 会话与权限由后端校验。商品上传、私信图片和售后证据分别处理；私有图片必须通过授权端点读取。
- 高德 JavaScript API 的公开 Key 供浏览器加载 SDK，安全码仅由后端代理追加。它与后端 Web 服务 Key 是不同配置。
- `local` 仅监听回环地址；生产不能启用 `local/test`。所有凭据通过环境变量或忽略的本地文件提供。

正式部署步骤、容量假设和外部条件见 [部署说明](deploy/README.md)。
