# 麦麦二手 · Maimai

[![CI](https://github.com/Yirinhana/maimai-secondhand/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Yirinhana/maimai-secondhand/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个基于 Vue 3 与 Spring Boot 的开源二手交易平台，包含商品发布、购物交易、社区交流、售后评价和管理后台。项目从课程实践发展而来，适合学习完整 Web 业务的组织方式，也欢迎在此基础上继续开发。

麦麦把商品、买卖双方、订单、交付、售后和信誉连接在同一条业务流程中；AI 客服「麦仔」提供解释与建议，业务操作仍由用户确认、后端校验。

## 可以做什么

| 模块 | 功能 |
| --- | --- |
| 账号与身份 | 邮箱注册与找回密码、头像、地址、设备账号切换、卖家认证、角色权限 |
| 商品与发现 | 多级分类与分类参数、搜索筛选、保存找货条件和站内提醒、图片、库存、收藏、讨论、跨设备草稿、审核和修改记录 |
| 交易与交付 | 议价、购物车、按卖家拆单、快递与面交、一次性交付码、订单跟踪 |
| 售后与信誉 | 仅退款、退货退款、部分退款、双向评价、分项评分、图片与追评、信誉提醒、争议处理 |
| 社区与沟通 | 求购与回复、关联商品的私信、分类消息中心、业务直达、人工客服工单 |
| 管理后台 | 用户与商品审核、举报证据、订单售后、统计图表、数据来源区分、操作日志 |
| AI 辅助 | 客服问答、发布建议、商品与订单解释、售后和评价建议、举报辅助分析、经用户预览确认的问答交接 |
| 交互 | 响应式页面、可重看的品牌开场、卖家待办与期限提示、可拖动贴边的麦仔客服入口 |

支付体验通过站内记录完成，不发生真实微信扣款、退款、到账或分账。体验收银页的二维码指向订单页面，不是个人微信收款码。真实支付渠道尚需另外实现和验收。种子商品和生成图片是开发素材，不代表真实供货或成交。

未完成的发布表单会先保留在本机，再按账号同步；两台设备发生冲突时由用户选择版本。草稿照片和评价上传原件保存在上传目录同级的 `personal-media/` 私有目录，只能通过受控接口读取。部署时应一并持久化和备份该目录，不要为它配置静态文件访问。找货提醒仅发送站内通知，可暂停，默认每个账号每小时最多三条。

## 技术与结构

前端使用 Vue 3、TypeScript、Vite、Pinia、Vue Router 和 Element Plus；后端使用 Java 21、Spring Boot、Spring Security、Spring Session JDBC 和 MySQL，数据库变更由 Flyway 管理。私信使用 WebSocket，部署样例采用 Nginx 与 systemd。

```text
浏览器 / Vue
    │ 同源 API、WebSocket
    ▼
Nginx（开发时由 Vite 代理）
    ▼
Spring Boot ─── MySQL / 受控图片存储
    │
    ├── 邮件、地图、物流适配器
    └── 独立 Hermes 网关 ─── LLM 服务
```

| 目录 | 阅读入口 |
| --- | --- |
| `frontend/src/` | 页面、路由、状态与公共组件，业务页面位于 `modules/` |
| `backend/src/main/java/com/maimai/` | 按业务模块组织的服务端代码；`MaimaiApplication` 是后端入口 |
| `backend/src/main/resources/db/migration/` | 数据表与增量迁移 |
| `backend/src/test/` | 权限、金额、库存、接口与 MySQL 集成测试 |
| `tests/e2e/` | 浏览器场景测试，部分历史套件需要自行准备体验账号 |
| `scripts/` | 本地启动、构建、测试与公开仓库检查 |
| `deploy/` | 环境变量、Nginx、systemd、备份和 AI 网关样例 |
| `VERSION` | 产品版本的唯一来源 |

## 本地运行

验证环境为 Java 21、Maven 3.9、Node.js 24 / npm 11、MySQL 8.4。MySQL 客户端软件不能代替数据库服务；请先启动自己的独立开发数据库。

### 1. 获取源码与依赖

```sh
git clone https://github.com/Yirinhana/maimai-secondhand.git
cd maimai-secondhand
npm ci --prefix frontend
```

### 2. 准备数据库

在本地 MySQL 中创建 `maimai` 和 `maimai_test`，字符集使用 `utf8mb4`；为项目创建独立账号，只授予这两个数据库的权限。Flyway 会在启动时创建和升级表，无需手动导入 SQL。测试库会写入测试数据，不能使用正式数据库。

在启动后端的终端中配置：

| 变量 | 含义 |
| --- | --- |
| `MAIMAI_DB_HOST` | 数据库地址，默认 `127.0.0.1` |
| `MAIMAI_DB_PORT` | 默认 `3307`；自己的 MySQL 若使用 `3306`，请显式修改 |
| `MAIMAI_DB_USER` | 独立项目账号，默认 `maimai` |
| `MAIMAI_DB_PASSWORD` | 该账号的密码，无默认值 |

不要将密码写入源码或提交到 Git。Windows 可使用以下方式输入密码，避免将密码字面量写入终端命令历史：

```powershell
$env:MAIMAI_DB_HOST = '127.0.0.1'
$env:MAIMAI_DB_PORT = '3306' # 改为自己的数据库端口
$env:MAIMAI_DB_USER = 'maimai'
$env:MAIMAI_DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '数据库密码' -AsSecureString)).Password
./scripts/dev-backend.ps1
```

### 3. 启动前端

在第二个终端运行：

```sh
npm run dev --prefix frontend
```

打开 `http://127.0.0.1:5173/`。后端在 `127.0.0.1:8081`；Vite 代理 API 与 WebSocket。请统一使用 `127.0.0.1`，避免与 `localhost` 混用导致 Cookie 不一致。

Linux / macOS 在设置上述环境变量后，可用以下命令启动后端：

```sh
mvn -f backend/pom.xml "-Drevision=$(tr -d '\r\n' < VERSION)" spring-boot:run \
  -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=8081
```

Windows / VS Code 还提供 [MaimaiLocalApplication.java](backend/src/main/java/com/maimai/MaimaiLocalApplication.java) 全站启动器。准备好 Java、Maven、Node.js、前端依赖和上述数据库配置后，可从项目根目录打开 VS Code，运行该类的 `main` 方法。它不负责安装软件、初始化数据库或重置密码。

`local` 环境会创建以下公开测试账号，密码均为 `Maimai#2026`：

| 身份 | 邮箱 |
| --- | --- |
| 买家 | `buyer@maimai.local` |
| 卖家 | `seller@maimai.local`、`seller2@maimai.local` |
| 超级管理员 | `admin@maimai.local` |
| 运营 / 客服 | `operator@maimai.local`、`support@maimai.local` |

这些账号只用于隔离的本地开发。生产不能开启 `local` / `test`，也不能导入开发用户表。

## 外部服务与 AI

基础业务可以在本地数据库上运行。邮件、地图、物流与模型服务需要部署者提供自己的配置，公开仓库不包含任何可用密钥。

| 服务 | 本地行为 / 接入条件 |
| --- | --- |
| 邮件 | `local` 下将验证码写入忽略的捕获目录，不发送真实邮件；实际发信需 SMTP 配置 |
| 高德地图 | 需要 JS API Key 与安全码；浏览器公开 Key，安全码由后端代理追加 |
| 物流 | 实际轨迹需要物流服务配置与有效单号，模拟轨迹不能用作签收证明 |
| AI | 未配置时拒绝模型调用，可使用帮助内容与人工工单；需独立只读 Hermes 网关或经核验的兼容端点 |

麦仔使用 LLM，不是 JEPA 或独立执行交易的决策引擎。后端按用户权限组装业务上下文并对部分个人字段脱敏；客服自由文本会发送到所配置的模型服务，因此请勿在对话中填写密钥或其他敏感资料。发布、付款、退款、封号和举报裁决均不由模型自动执行。

模型密钥仅在服务端保存，不能使用 `VITE_*` 变量注入浏览器。网站网关令牌与模型供应商 API Key 应分开配置，配置方式和数据边界见 [安全说明](SECURITY.md)。

## 测试与构建

```powershell
python -m unittest discover -s scripts/tests -v
python scripts/check-public-repository.py
python -m unittest discover -s deploy/tests -v
npm test --prefix frontend
./scripts/test.ps1
./scripts/build.ps1
```

`test.ps1` 执行 Maven 验证与前端构建；后端集成测试需要独立 MySQL 测试库。`build.ps1` 在验证后生成 `.local/releases/` 候选包，包含 JAR、前端静态文件和部署样例，不包含凭据、数据库或用户上传文件。Linux / macOS 可参考 `scripts/build-all.sh`。

浏览器测试从 `tests/e2e/` 运行，需要本地服务、Chrome 和各套件的数据条件，说明见 [浏览器测试](tests/e2e/README.md)。它们可能创建测试订单或消息，禁止把默认开发测试指向正式站点。

[GitHub Actions](https://github.com/Yirinhana/maimai-secondhand/actions) 在独立 MySQL 容器中验证后端，并执行前端构建和安全边界检查。请以对应提交的结果为准，不将历史测试数量当作当前承诺。

## 部署与参与

生产部署需构建前端与 JAR、准备独立数据库、配置域名与 HTTPS，再通过 Nginx 将静态页面和后端接口接到同一域名。仅运行 Java 和前端开发服务器并不会自动完成这些步骤。参见 [部署指南](deploy/README.md)。所有示例地址均为占位地址，仓库不提供维护者的服务器入口。

欢迎通过 Issue 反馈问题或提交 PR。开始前请阅读 [贡献指南](CONTRIBUTING.md)；安全问题请使用 [私密漏洞报告](https://github.com/Yirinhana/maimai-secondhand/security/advisories/new)，不要公开密钥、个人资料或攻击细节。

项目原创代码采用 [MIT License](LICENSE)。第三方依赖保留各自许可，生成素材的用途和限制见 [素材说明](ASSETS.md)。课程报告、模板、ER 图和内部开发记录只在本地保留，不随源码分发。公开仓库保留运行、贡献与安全所需说明，不包含真实账号、服务器凭据或线上业务数据，也不承诺开箱即用的商业支付服务。
