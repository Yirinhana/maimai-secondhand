# 腾讯云体验版部署

目标环境为 Ubuntu 24.04、Nginx、Java 21 和独立 MySQL 8。域名为 `https://market.example.com`；根域重定向到 www。部署需要用户授权，执行前核对目标主机指纹、现有服务与端口；本轮授权和实际结果见 `docs/tasks/038-tencent-preview-deploy.md` 及 `docs/testing/tencent-preview.md`。

前端与 JAR 在本地构建，服务器不运行 npm/Maven。目录：`/opt/maimai/releases/<版本及时间>/` 保存完整交付物，`/opt/maimai/current` 指向当前版本；`/etc/maimai/maimai.env` 为 root 0600 凭据文件；`/var/lib/maimai/` 保存数据库与各类图片。保留旧交付物和配置备份，不自动清理。

MySQL 的 Ubuntu 官方 core deb 使用 `apt-get download` 获取，`dpkg-deb -x` 解包到 `/opt/maimai/runtime/mysql`，避免安装服务包时卸载既有 MariaDB 客户端。专用 `maimai-db` 用户运行 `maimai-mysql.service`，只监听 127.0.0.1:3306，使用独立配置及数据目录；升级需重新检查依赖和运行文件，并保留可回退版本。不要覆盖或复用现有数据库。

应用以 `maimai` 用户和 `prod` profile 监听 127.0.0.1:8080。初始 Java 堆 384MB、元空间上限 160MB、Hikari 5 连接；MySQL buffer pool 128MB、连接上限 30、禁用 performance_schema。资源限制是小规模体验的起始配置，不代表容量或性能承诺；Hermes 仍保留运行。

首次空库先由 Flyway 完成迁移，再停止应用，以 root 运行 `python3 -W ignore::DeprecationWarning /opt/maimai/current/deploy/bootstrap-preview.py --jar /opt/maimai/current/backend.jar`。脚本拒绝非空用户/商品表和重复创建凭据；建立随机密码管理员、不可登录的展示账号和五件零库存商品，图片从当前 JAR 提取。不会导入本地数据库、默认开发密码、支付资质或用户同意记录。管理员凭据写入 `/etc/maimai/initial-admin.json`，不打印到终端；只能经安全连接交付给用户。

Nginx 样例保留现有 `/shenlun` 代理及 ACME 文件目录，覆盖根域与 www 的证书位置已核实。实际替换前备份原配置，执行 `nginx -t` 通过后才 reload。API 和所有图片均沿现有授权路径；Nginx 不直接公开私信、售后或数据库目录。开发接口返回 404；上游转发头由 Nginx 重设；页面和固定名称图片每次校验缓存，散列资源缓存七天。

`backup.sh` 为 root 执行的数据库与上传图片本机备份，先一致性导出数据库，再打包图片；现有应用保留替换前的图片，因此不会丢失导出快照引用的旧文件。仅写 `/var/backups/maimai/<UTC时间>/`，成功后生成校验清单与 COMPLETE 标记，不包含环境凭据。定时器每天上海时间 03:30 左右运行，磁盘余量低于 2GB 时失败并保留现有备份；不自动删除历史。上线后需检查定时器失败状态和磁盘，后续与用户确定清理周期、异地备份及告警。初次部署必须实际恢复到独立验证数据库，并核对图片归档；本机备份不能代替异地副本。

版本切换前逐文件验证 `checksums.json`、VERSION、JAR 和前端。建立新软链接后原子替换 current，重启应用并核对 API/数据库；失败则指回旧交付物并恢复 Nginx 备份。只回退程序和站点配置，不自动回滚数据库迁移或删除用户数据。数据库服务恢复后须检查应用状态，必要时重新启动 maimai.service。

真实微信支付、退款和分账仍待资质、渠道实现及验收。SMTP、物流查询和专用只读 Hermes 客服未配置时保持不可用，不使用模拟结果。当前公开页面是课程体验，不是已获准真实交易运营的平台。填写 `maimai.env` 示例的凭据不等于相关能力已经验证。
