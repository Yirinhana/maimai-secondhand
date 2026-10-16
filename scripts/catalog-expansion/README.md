# 本地体验商品扩充

此工具只追加 `manifest.json` 中的 42 件体验商品。默认只读预览，**工具本身不请求模型，不新建账户，不创建成交、评价或付款记录**。只有 `--apply` 会写库，且连接被固定为本项目 `127.0.0.1:3307/maimai`；服务端数据目录必须正好是项目 `.local/mysql-data`。没有生产模式，也不支持传入服务器地址。新商品会写入正常治理信号队列；如果本地后台治理正在运行并且规则命中，后续预审仍受站点原有开关、限速和每日额度约束。

## 图片准备

每件商品使用自己的一张正面 JPEG，保存到：

`backend/src/main/resources/demo/catalog-v3/exp021-001-front.jpg` 至 `exp021-042-front.jpg`。

该目录不包含预置占位图。图片必须与 manifest 对应物品的颜色、形态及瑕疵一致，不能复制旧样例图。要求单张 1 MB 内、每边至少 256 像素、最长边最多 1600 像素，无 EXIF。实际解码、SHA-256 与解码像素指纹都检查；与旧 `catalog-v2` 以及其他已准备的新图片重复会拒绝。

使用现有带 Pillow 的 Python 环境，不安装新的数据库客户端。先对已完成的一批图片做离线检查：

```powershell
python scripts/catalog-expansion/import_catalog.py --image-audit --ids exp021-001,exp021-002
```

此命令不连接数据库、不修改 manifest。人工看图确认物品对应后，把输出的 `sha256` 填入对应 `images[0].sha256`，并将 `reviewed` 设为 `true`。缺少图片、hash 不符或未经确认的图片不能 `--apply`。本批 42 张独立生成的商品图片已完成检查，清单已记录对应哈希；体验来源保留在商品资料中。运行工具默认仍只预览，不会自动导入。

## 预览与分批

```powershell
python scripts/catalog-expansion/import_catalog.py --ids exp021-001,exp021-002
python scripts/catalog-expansion/import_catalog.py --limit 3
```

`--ids` 使用清单键，不是数据库 ID；`--limit 3` 固定选择清单前 3 件，不表示“接下来未导入的 3 件”。二者不能同时使用。选中批次缺任何一张图时整批拒绝。没有选中的未完成图片不阻碍该批执行。

卖家通过旧体验商品的标题、来源与三视图路径唯一定位，再读取其当前 seller_id；必须仍为 ACTIVE、具有 SELLER 角色，最新申请为 APPROVED，且本地模拟渠道状态为 QUALIFIED。不会修改用户角色、认证或渠道资格。所在地区取该卖家锚点商品的公开地区，不读取收货地址。

分类按真实 ACTIVE 父类与 ACTIVE 叶子类匹配；同名异父类不会串联。仅在确实需要新增小类并人工确认时使用 `--create-missing-categories`，它只在已有且唯一的父类下追加缺失子类，不会新建父类或启用停用分类。本批 42 件均使用现有分类，正常不需要此参数。

## 应用

```powershell
python scripts/catalog-expansion/import_catalog.py --ids exp021-001,exp021-002 --apply
```

默认新商品进入 `PENDING_REVIEW`，之后从正常管理后台审核。若执行者已经逐条核对素材与商品内容，可对**本次新插入的条目**使用显式 `--approve --apply`，工具会记录独立的审核、修订、审计和卖家通知；不自动审批之前导入的商品。若有多个超级管理员，必须通过 `--admin-id` 明确已有活跃超管。该参数不会创建管理员。

命令优先使用已有 MySQL 安装及 `.local/private/mysql-client.ini`。可通过 `--mysql` 指定已安装客户端、`--client-file` 指定本项目私有目录里的现有配置；连接地址和库仍固定。配置内容和原始 SQL 报错不会输出。

## 保留与回退

每次应用先在 `.local/private/catalog-expansion/<timestamp>/` 保存完整数据库备份、选中清单与预览，安装仅属于该批的新图片，再在单一数据库事务中追加商品。事务内取得批次锁、分类与账号锁、再次检查卖家/分类/原图关系，核验每件清单键尚未导入，失败即随连接关闭回滚。

数据库审计以 `CATALOG_EXPANSION_ITEM` 和 `maimai-experience-catalog-021:<item-key>` 保留不可覆盖的键到产品 ID 映射；成功后导出 `idmap.json`、`installed-images.json` 与 `rollback-plan.json`。本地回执丢失时仍按数据库映射跳过。重复运行不会更改已有商品；用户后续改标题、价格、库存或下架都被保留。因并发变更触发保护时重新预览，不自动放宽校验。

本工具只新增，不提供删除命令。正常回退通过后台下架本批商品，保留图片、审计、修订和订单关联。已经产生交易或用户编辑的条目应人工核查，不用旧全库备份覆盖新订单。数据库事务失败后已经写入的图片会保留在专属文件名下，便于核验重试；不会擅自删除文件。

## 测试

```powershell
python -m unittest discover -s scripts/catalog-expansion/tests -v
```

测试使用临时图片与数据库快照替身，验证归属、认证、重复执行、分类、字段边界、图片完整性、已有图片复用和事务保护；不连接数据库，不调用模型，不执行 `--apply`。实际数据库预览与隔离库演练由集成人员在素材验收后进行。
