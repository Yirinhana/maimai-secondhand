# 腾讯云体验版部署

## v0.15.0：网站专用 Hermes 与关联数据

本轮新增 V39，只追加 AI 请求、举报分析、评价提醒及举报原文字段，保留旧迁移与原始业务数据。管理员概览按付款凭据区分模拟支付、正式渠道、历史体验、未付款和待核查，计算费额不代表已到账收入。完整需求及 ER 图位于 `docs/course/第四组需求分析与ER图-v0.15.0-20260922/`。

`hermes-site-gateway.py` 使用服务器现有 Hermes AIAgent 代码，通过独立 `maimai-hermes.service` 运行在 127.0.0.1:8643。独立系统用户、HOME、EnvironmentFile 和 systemd 沙箱阻止网站访问个人助理的记忆、配置和工具。每次请求新建只读 agent，显式禁用工具、文件上下文、轨迹保存和共享会话；模型失败返回错误，不用占位回答冒充成功。模型供应商仍为用户已授权的 MiniMax-M3。

安装前检查 `systemd/maimai-hermes.service` 的只读运行库挂载，尤其已安装的 Python 3.11.15 路径。`sudo python3 deploy/install-hermes-site.py --source-dir deploy` 只创建网站专用服务；它不修改个人 Hermes。已有服务更新脚本后需重启 `maimai-hermes.service`。先运行 `sudo python3 deploy/verify-hermes-site.py` 验证能力标记和一次真实模型回答，再在版本切换时使用安装脚本的 `--activate` 参数：先备份 `/etc/maimai/maimai.env`，只更新网站的四个 Hermes 配置项，最后随应用发布重启 `maimai.service`。

适配器只允许本机连接和内部 bearer token，前端不接触模型密钥；业务权限、脱敏、限流、请求记录及举报人工裁决仍由 Java 服务负责。服务器低内存下限制一个同时生成请求，忙碌时页面可重试和转人工。发布失败先回退应用软链接和备份的环境配置，不自动撤销数据库迁移或删除业务记录。旧的部署章节仅作对应版本历史，不能作为当前库存、卖家状态或测试结果依据。

2026-09-16 已按用户本轮授权发布 v0.13.0：四幕品牌欢迎页、2.7秒渐变、可拖动贴边的麦仔客服及新网站人设。发布前225项后端测试通过；线上119项静态资源与候选包哈希一致，并完成一次正常账号真实AI问答。备份和具体验证边界见 [v0.13.0发布记录](../docs/testing/maizai-edge-release-v0130.md)。

网站缇娜与邮件接入的候选变更见 [039验证记录](../docs/testing/tina-support.md) 和 [人设/接口设计](../docs/design/tina-support.md)。QQ/Foxmail采用 `smtp.qq.com:465`、隐式TLS；发件地址和用户名使用专用邮箱，密码填SMTP授权码。只把已授权的邮件字段合并入 `/etc/maimai/maimai.env` 并保持0600，先备份原配置，保留数据库、地图及其他既有字段；不整份覆盖成示例，不把授权码放进命令行或Git。本地仍默认捕获邮件，避免开发测试触发外发。

目标环境为 Ubuntu 24.04、Nginx、Java 21 和独立 MySQL 8。域名为 `https://market.example.com`；根域重定向到 www。部署需要用户授权，执行前核对目标主机指纹、现有服务与端口；本轮授权和实际结果见 `docs/tasks/038-tencent-preview-deploy.md` 及 `docs/testing/tencent-preview.md`。

前端与 JAR 在本地构建，服务器不运行 npm/Maven。目录：`/opt/maimai/releases/<版本及时间>/` 保存完整交付物，`/opt/maimai/current` 指向当前版本；`/etc/maimai/maimai.env` 为 root 0600 凭据文件；`/var/lib/maimai/` 保存数据库与各类图片。保留旧交付物和配置备份，不自动清理。

MySQL 的 Ubuntu 官方 core deb 使用 `apt-get download` 获取，`dpkg-deb -x` 解包到 `/opt/maimai/runtime/mysql`，避免安装服务包时卸载既有 MariaDB 客户端。专用 `maimai-db` 用户运行 `maimai-mysql.service`，只监听 127.0.0.1:3306，使用独立配置及数据目录；升级需重新检查依赖和运行文件，并保留可回退版本。不要覆盖或复用现有数据库。

应用以 `maimai` 用户和 `prod` profile 监听 127.0.0.1:8080。初始 Java 堆 384MB、元空间上限 160MB、Hikari 5 连接；MySQL buffer pool 128MB、连接上限 30、禁用 performance_schema。资源限制是小规模体验的起始配置，不代表容量或性能承诺；Hermes 仍保留运行。

首次空库先由 Flyway 完成迁移，再停止应用，以 root 运行 `python3 -W ignore::DeprecationWarning /opt/maimai/current/deploy/bootstrap-preview.py --jar /opt/maimai/current/backend.jar`。脚本拒绝非空用户/商品表和重复创建凭据；建立随机密码管理员、不可登录的展示账号和五件零库存商品，图片从当前 JAR 提取。不会导入本地数据库、默认开发密码、支付资质或用户同意记录。管理员凭据写入 `/etc/maimai/initial-admin.json`，不打印到终端；只能经安全连接交付给用户。

Nginx 样例保留现有 `/shenlun` 代理及 ACME 文件目录，覆盖根域与 www 的证书位置已核实。实际替换前备份原配置，执行 `nginx -t` 通过后才 reload。API 和所有图片均沿现有授权路径；Nginx 不直接公开私信、售后或数据库目录。开发接口返回 404；上游转发头由 Nginx 重设；页面和固定名称图片每次校验缓存，散列资源缓存七天。

`backup.sh` 为 root 执行的数据库与上传图片本机备份，先一致性导出数据库，再打包图片；现有应用保留替换前的图片，因此不会丢失导出快照引用的旧文件。仅写 `/var/backups/maimai/<UTC时间>/`，成功后生成校验清单与 COMPLETE 标记，不包含环境凭据。定时器每天上海时间 03:30 左右运行，磁盘余量低于 2GB 时失败并保留现有备份；不自动删除历史。上线后需检查定时器失败状态和磁盘，后续与用户确定清理周期、异地备份及告警。初次部署必须实际恢复到独立验证数据库，并核对图片归档；本机备份不能代替异地副本。

版本切换前逐文件验证 `checksums.json`、VERSION、JAR 和前端。建立新软链接后原子替换 current，重启应用并核对 API/数据库；失败则指回旧交付物并恢复 Nginx 备份。只回退程序和站点配置，不自动回滚数据库迁移或删除用户数据。数据库服务恢复后须检查应用状态，必要时重新启动 maimai.service。

真实微信支付、退款和分账仍待资质、渠道实现及验收。SMTP、物流查询和专用只读 Hermes 客服未配置时保持不可用，不使用模拟结果。当前公开页面是课程体验，不是已获准真实交易运营的平台。填写 `maimai.env` 示例的凭据不等于相关能力已经验证。

## v0.4.0更新与组网连接

最新部署、邮件认证、指定账号与演示商品状态见 [v0.4.0线上验证](../docs/testing/tencent-v040.md)。公网商品保持零库存，卖家渠道状态PENDING，原数据保留。`seed-preview-demo.py`是明确预览批次的辅助工具，需root、受限私密清单及0.4.0运行目录；默认只预览，--apply先备份再事务，重复批次不写入，不得用来导入生产交易或支付资质。

Windows项目终端可运行 `./scripts/connect-server.ps1`，或附带只读命令 `./scripts/connect-server.ps1 whoami`。它仅使用忽略的.local/ssh/maimai-tailscale.conf，保持严格主机指纹与专用密钥，不使用公网代理或改全局配置。当前仍使用原临时授权，到期后须经用户明确许可续期或替换，不能通过关闭主机校验绕过。
