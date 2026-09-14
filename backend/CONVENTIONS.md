# 后端开发约定（所有模块必须遵守）

技术基线：Spring Boot 4.0.8 / Java 21 / Spring Data JPA（`ddl-auto: validate`）/ Flyway 为 schema 唯一来源 / MySQL 8.0。

## 包结构

- `com.maimai.<module>.domain`：JPA 实体；`repo`：Spring Data Repository；`service`：业务服务；`controller`：REST 控制器；`dto`：请求/响应 DTO。
- 模块：`identity`（账号/验证码/卖家申请/地址）、`catalog`（分类/商品/图片/审核/库存流水）、`trade`（议价/购物车/结算/订单/快递/面交）、`payment`（支付/退款/流水/通知事件）、`aftersales`（售后）、`admin`（后台）、`notification`（站内通知）、`common`（通用）。
- 依赖方向：`common ← identity ← catalog ← trade ← payment ← aftersales`，`admin` 可用全部。禁止反向依赖。

## 硬性规则

1. 金额一律 `long` 整数分，字段/参数名以 `Cents` 结尾。平台服务费只允许调用 `com.maimai.common.FeeCalculator`，禁止自行写费率公式。
2. 时间一律 `java.time.Instant`（UTC）。业务时限用 `Instant.now().plus(...)`。
3. 异常一律抛 `com.maimai.common.BizException`（带业务码），不要 try-catch 后吞掉或返回 null。控制器不写错误响应逻辑。
4. 当前用户一律 `SecurityUtils.current()` / `currentUserId()`；操作他人资源必须校验归属（`requireOwner` 或显式比对 seller_id/buyer_id），后台操作校验角色。
5. 单号用 `NoGenerator.next("前缀")`：订单 `MM`、批次 `MB`、支付 `MP`、退款 `MR`、售后 `MA`。
6. 实体与 `src/main/resources/db/migration/*.sql` 完全一致：表名经 `@Table` 指定，列名经 `@Column(name=...)` 指定，`createdAt/updatedAt` 为 `Instant` 并给默认值 `Instant.now()`；枚举用嵌套 `enum` + `@Enumerated(EnumType.STRING)` + `length` 匹配；不使用 Lombok；风格照抄 `identity/domain/User.java` 样例。
7. Repository 为 `JpaRepository<T, Long>`，派生查询优先；库存等并发更新用 `@Modifying @Query` 条件更新并检查影响行数。
8. REST 路径前缀 `/api/v1`。请求 DTO 用 Bean Validation 注解（`@NotNull` 等）。响应 DTO 用 Java record。金额出参仍是分（前端负责格式化）。
9. 新 HTTP 端点若涉及修改，默认走 Spring Security 配置里 `anyRequest().authenticated()`；公开端点必须在 `SecurityConfig` 中显式列出（改动 SecurityConfig 需在完成说明中注明）。
10. 站内通知通过 `com.maimai.notification.NotificationService.notify(userId, type, title, content)` 发送。
11. 开发专用端点放 `/api/v1/dev/**` 且类上标注 `@Profile("local")`。
12. 日志不打印密码、验证码明文、完整凭据。

## 测试

- 集成测试：`@SpringBootTest` + `@ActiveProfiles("test")`，连真实 MySQL `maimai_test` 库（Flyway 自动建表）。不要用 H2、不要 mock 数据库。
- 每个测试类自建数据、自负盈亏；用 `@Transactional` 回滚或显式清理。
- 测试请求用 `TestRestTemplate` 或 MockMvc + `@AutoConfigureMockMvc`，注意 CSRF：测试中用 `SecurityMockMvcRequestPostProcessors.csrf()` 与自定义用户。
