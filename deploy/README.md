# 部署麦麦二手

本指南使用占位域名 market.example.com，不包含维护者的服务器地址、SSH 用户或管理网络。部署前先核对目标主机、现有服务和备份；示例配置不能直接覆盖一台已运行其他应用的服务器。

## 1. 构建交付物

在准备好开发与测试环境的机器上运行 scripts/build.ps1。通过验证后，交付物位于 .local/releases/，包含 backend.jar、frontend/、VERSION、deploy/ 和 checksums.json。服务器运行构建产物，不需要运行 Vite 开发服务器或在服务器安装 npm/Maven。

通过自己的受保护传输通道上传到 /opt/maimai/releases/<版本及时间>/。逐文件核对校验清单，不传入 .local/private、测试数据库、运行日志或整个开发目录。SSH 私钥和主机指纹只保存在本机受限配置中，不提交到仓库。

## 2. 准备独立运行环境

参考环境为 Ubuntu 24.04、Java 21、MySQL 8.4、Nginx。创建 maimai 应用用户及独立 maimai 数据库，使用仅有项目库权限的数据库账号。服务样例在 systemd/；其中 MySQL 的独立运行目录需要部署者事先准备。它们不是安装器，不应覆盖或停止其他数据库。

配置样例 maimai.env.example 复制到服务器 /etc/maimai/maimai.env 后填写。文件应仅 root 和实际服务所需组可读；不能将填写后的文件放回公开源码。生产使用 prod profile，后端和数据库仅监听回环地址，不能启用 local/test 或导入公开开发账号。

可参考的目录约定：

| 路径 | 用途 |
| --- | --- |
| /opt/maimai/releases/ | 按版本保存不可变交付物 |
| /opt/maimai/current | 指向当前发布版本的软链接 |
| /etc/maimai/ | 私密环境与数据库连接配置 |
| /var/lib/maimai/ | 上传图片等业务文件 |
| /var/backups/maimai/ | 本机备份，仍需独立的异地副本 |

先在可回退的环境中运行 Flyway 迁移并检查服务状态。历史 bootstrap-preview.py 仅适合空库课程展示初始化，要求特定 MySQL 路径和系统 crypt 支持，不是通用的生产管理员开户工具；不要对已有数据运行历史种子脚本。

## 3. 域名、HTTPS 与反向代理

将自己的域名解析到服务器，配置有效 TLS 证书，然后按 nginx/maimai.conf.example 设置静态目录与 API/WebSocket 代理。样例中的 market.example.com、example.com 和证书路径必须按自己的域名修改；不需要根域跳转时可去掉对应 server 段。

MAIMAI_FRONTEND_ORIGIN 要与浏览器使用的 HTTPS 域名完全一致。Nginx 只公开网站和必要接口，不直接公开数据库、环境文件、私信图片目录或 AI 网关。检查配置通过后再 reload，原配置先备份。后端启动成功不代表域名、TLS、代理和 Cookie 已配置正确。

## 4. 外部服务与麦仔

SMTP、地图、物流按 maimai.env.example 逐项配置并验证。体验支付只产生站内状态记录，不发生真实资金变化；真实渠道仍需另外接入。

AI 配置与防泄露措施见 [安全说明](../SECURITY.md)。网站专用 Hermes 服务采用独立用户、HOME、令牌与 systemd 沙箱，禁止个人记忆和工具执行。

安装样例假定已审查的 Hermes 运行库在 /opt/hermes/runtime，其虚拟环境为 venv/，对应 Python 3.11 运行时在 /opt/hermes/python。两处路径是部署约定，需自行准备并核对 systemd 的只读挂载，不能指向未审查的个人配置目录。Python 版本或运行库结构不同，需要先调整样例并执行适配器测试。

install-hermes-site.py 是历史环境的受控辅助脚本，要求已有 /etc/maimai/maimai.env；首次转换仅支持其校验的供应商端点。默认安装不切换网站，--activate 才更新网站环境。运行前阅读脚本、备份配置并核对前置条件，不能把它当作任意机器的一键安装器。通过 verify-hermes-site.py 验证会产生一次真实模型调用，需要部署者自己的配置与费用授权。

## 5. 发布与恢复

1. 保存应用、数据库与上传文件备份，并在独立环境验证恢复。
2. 上传新交付物，核对版本、校验清单、数据库迁移和私密配置。
3. 将 current 切换到新版本，重启 maimai 服务，检查健康、登录、商品、图片、私信和订单。
4. 在手机与桌面浏览器验证 HTTPS、Cookie、代理与资源路径；检查开发端点不能访问。
5. 若应用验收失败，回退 current 与必要配置。不要自动回滚数据库迁移或删除业务数据。

backup.sh 和对应定时器只提供本机备份样例，不包含自动清理与异地容灾。需配置存储权限、磁盘监控、保留周期、失败提醒及异地副本。源码仓库不是业务数据备份。

## 验证记录

源码构建结果见对应提交的 [GitHub Actions](https://github.com/Yirinhana/maimai-secondhand/actions)。部署者仍需单独核验服务器服务状态、HTTPS、角色权限、业务流程和备份恢复；CI 通过不代表线上部署已经通过验收。包含实际主机信息的检查记录只保存在自己的受限目录中。
