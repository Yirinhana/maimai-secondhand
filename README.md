# 麦麦二手 · Maimai

[![CI](https://github.com/Yirinhana/maimai-secondhand/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Yirinhana/maimai-secondhand/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个基于 Vue 3 与 Spring Boot 的开源二手交易平台，包含商品发布、购物交易、社区交流、售后评价和管理后台。项目从课程实践发展而来，适合学习完整 Web 业务的组织方式，也欢迎在此基础上继续开发。

麦麦把商品、买卖双方、订单、交付、售后和信誉连接在同一条业务流程中；AI 客服「麦仔」提供解释与建议，业务操作仍由用户确认、后端校验。

麦仔支持站内商品与预算检索，回复包含可点击的商品卡片、用户协议、订单及人工帮助入口。检索只展示有库存、审核通过且卖家具备有效资格的商品；检索与规则导航不调用模型，来源与查询时价格会单独标注。自由问答仍由配置的受限 AI 网关处理。

购物车按账号在当前标签页记住一天内的勾选，重新进入时核对最新库存；库存不足可直接改为可购数量。买卖双方的订单列表按状态提示下一步操作。

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

发布商品先选择大类，再选择对应的小分类。配置图片识别后，用户可选一至三张自己的草稿照片，让麦仔补全空白的标题、分类和外观描述；已有内容和等待期间手动修改的内容会保留。价格、库存、成色和交易承诺由卖家核实填写，识图不会自动上架。识图直接调用 MiniMax 的多模态接口，配置样例见 [listing-assistant.env.example](config/listing-assistant.env.example)，默认关闭。开启后默认全站每天10次、每个账号每天3次（上海时区）；相同照片成功结果按账号缓存24小时，不自动重试失败调用。

站内比价不消耗模型额度。它筛选当前在售、有库存、卖家认证有效的商品，以折线展示最多12个样本的挂牌价，并显示最低价、中位价和最高价；点击样本可查看对应商品。横轴是不同商品样本，不是时间轴；该图不表示历史走势、成交价或全网行情。体验来源商品会单独说明，采用参考价仍需卖家点击确认。

需要演示完整发布与交易流程时，可显式设置服务端 `MAIMAI_EXPERIENCE_LISTING_ENABLED=true`。平台审核通过的卖家可以发布带模拟来源的新商品，经商品审核后进入站内模拟收银台，无需真实收款渠道；该操作不把渠道状态改成已认证，也不发生真实扣款。默认关闭此开关，真实商品继续核验收款资格。本地 `local/test` 配合 `mock` 支付时也使用模拟发布，已经存在的正式交易资料不会自动改成模拟记录。

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

### Windows / VS Code 全站启动

安装 JDK 21、Maven 3.9、Node.js 24，以及 VS Code 的 Extension Pack for Java 和 Vue - Official。在 VS Code 打开包含 `backend`、`frontend`、`scripts` 的项目根目录，等待 Java 项目导入。

可以把 [本机配置样例](config/local-environment.example.json) 复制到忽略的 `.local/environment.json`，填写自己的 JDK 和 Maven 安装目录、MySQL 端口与项目账号密码；已有配置只需编辑，不要覆盖。样例没有可用密码，不要把填好的私密配置发给其他同学或提交到 Git。

```powershell
New-Item -ItemType Directory -Force .local | Out-Null
if (-not (Test-Path .local/environment.json)) {
    Copy-Item config/local-environment.example.json .local/environment.json
}
npm.cmd ci --prefix frontend
```

先启动自己的 MySQL 服务并按前文创建两个专用数据库，再从“运行和调试”选择“麦麦全站（数据库＋后端＋前端）”，按 F5；也可以运行 [MaimaiLocalApplication.java](backend/src/main/java/com/maimai/MaimaiLocalApplication.java) 的 `main` 方法。终端显示就绪后，打开 `http://127.0.0.1:5173/`。MySQL 管理客户端窗口不能代替数据库服务。

统一启动器复用已运行的数据库，不替新电脑安装软件、初始化数据库或重置密码。它自动启动数据库的能力只适用于已经准备好的项目专用实例；源码交接包不包含该实例。停止时在启动器终端按回车，它只关闭本次启动的服务。

`MaimaiApplication.java` 仅为后端入口；要单独调试后端，请选择“麦麦后端（本地 8081）”，前端另开终端启动。单独访问后端端口不能浏览完整网站。

Maven 未传入版本参数时使用 `0.0.0-local` 作为 IDE 导入占位版本；产品版本只在 `VERSION` 维护。生成正式构建请使用项目脚本或按本文命令传入 `-Drevision`，不能把 IDE 占位版本当作发布版本。

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
| AI 交互助手 | 未配置时拒绝模型调用，可使用帮助内容与人工工单；使用独立只读 Hermes 网关或经核验的兼容端点 |
| AI 后台预审 | 服务端直接请求 MiniMax；独立开关、私密凭据、每日请求上限，默认关闭 |

后台预审从商品资料、商品讨论、评价与追评、求购及回复、主动举报、售后申请、人工工单中收集线索。业务保存成功后异步处理，先以本地规则筛选候选文本，再调用模型；没有命中规则不代表审核通过。普通咨询不会逐条请求模型，连续编辑等待 30 秒再取样，相同内容去重；模型请求串行、默认至少间隔 30 秒，每个 UTC 日最多 20 次（失败也计数）。失败后保留人工待办，不自动重试；连续三次失败自动暂停，核对配置后由超级管理员恢复。历史内容只在超级管理员点击“补查近期内容”后进入筛选。

管理员在 `/admin/governance` 查看按同一业务对象归并的线索、脱敏原文、关联账号、证据摘录、待补材料和人工核查建议，可下载 TXT 核查材料并记录跟进。运营只查看公开内容，客服只查看交易与工单，超级管理员查看两者。AI 发现的线索与用户主动举报分开记录，不能自动封号、隐藏内容、退款或扣信誉；点击“完成跟进”也不会代替原业务处理。

本地配置可将 [配置样例](config/governance.env.example) 复制到被忽略的 `.local/private/governance.env`，填写服务端 Key、模型及开关，再重新启动后端。生产环境在自己的服务环境文件中设置相同字段。只允许官方 MiniMax HTTPS 地址；不要把 Key 放进前端、截图或 Git。默认不读取私人聊天、地址或证据图片，文本按允许字段取样并做规则脱敏；脱敏不能保证识别所有自由文本中的个人信息。测试运行强制关闭真实预审请求，使用受控模型验证流程。

网页发布新版本后，每两分钟及窗口重新获得焦点时检查更新，并提示用户先保存再刷新，不强制重载正在填写的表单。部署须保留上一版静态资源，避免旧页面加载分块失败。后端生产配置启用优雅关闭；普通单实例重启仍有短暂不可用窗口，不能当作零停机发布。

麦仔使用 LLM，不是 JEPA 或独立执行交易的决策引擎。后端按用户权限组装业务上下文并对部分个人字段脱敏；客服自由文本会发送到所配置的模型服务，因此请勿在对话中填写密钥或其他敏感资料。发布、付款、退款、封号和举报裁决均不由模型自动执行。

模型密钥仅在服务端保存，不能使用 `VITE_*` 变量注入浏览器。网站网关令牌与模型供应商 API Key 应分开配置，配置方式和数据边界见 [安全说明](SECURITY.md)。

## 测试与构建

流程助手先区分选品、描述整理和具体交易问题。“不知道卖什么”等入门问题使用已审阅的平台指引，不调用模型，并明确标注来源；进一步分析通过网站只读网关完成。草稿、当前问题和同一账号上一轮问答分开传递，跨账号或跨业务的追问记录不能复用。用户输入先进入对话区，等待时显示请求状态；处理中只查询已有记录，网络结果不明时沿用原请求标识，避免重复推理。模型建议不会自动写入表单或执行交易。

买家交易页、发布页、卖家工作台和管理总览提供分角色操作指引。卖家可以识别草稿、审核和上架状态；未认证用户仍可保留云端草稿。管理员从真实业务待办进入核查，完成预审跟进前需要确认已检查原文与关联记录，并保留人工说明。

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
