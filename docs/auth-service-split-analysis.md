# 认证服务拆分与双 Native 架构分析

本文记录当前双应用模式的构建树分析，并给出将 `flyfish-auth` 独立为认证应用的拆分方案。目标是让飞鱼小铺和低代码平台只通过轻量 API 获取登录态、用户资料和第三方绑定信息，同时避免引入服务注册、配置中心、消息总线等完整微服务复杂度。

## 当前结论

当前工程已整理为三套服务工程目录。生产运行目标是三个 native 应用：

- `flyfish-auth/flyfish-auth-app`：认证应用，端口 `10080`。
- `flyfish-lowcode/flyfish-lowcode-app`：低代码应用，端口 `10081`。
- `flyfish-shop/flyfish-shop-app`：小铺应用，端口 `10082`。

结构层面已经完成：

- `flyfish-auth` 下包含 `flyfish-auth-api`、`flyfish-auth-app`。
- `flyfish-lowcode` 下包含 `flyfish-lowcode-api`、`flyfish-lowcode-app`。
- `flyfish-shop` 下包含 `flyfish-shop-api`、`flyfish-shop-app`。

此前两应用阶段的业务边界已经拆开，但运行时依赖闭包高度重合。基于 `dependency:tree -Dscope=runtime` 和本地 native 产物观察：

- `flyfish-shop` native 约 `205M`。
- `flyfish-lowcode` native 约 `204M`。
- 两个应用共同运行时依赖坐标约 `109` 个，差异各只有约 `4` 个。
- 小铺独有主要是 `flyfish-shop`、`flyfish-git`、`mapstruct`。
- 低代码独有主要是 `flyfish-lowcode`、`freemarker`、`evo-inflector`。
- `flyfish-auth` 通过 `flyfish-platform` 同时进入两个应用，并带入 OAuth、模板、邮件和用户体系。

因此，当前 400M+ 总体积不是业务代码巨大，而是两个 native 进程各自静态包含了一整套 Spring/WebFlux/R2DBC/Netty/Jackson/认证闭包。继续拆更多 native app 不会自动共享这些 native runtime，甚至可能让磁盘总量继续增长。

## 当前超级重复项

### 必然重复的基础运行闭包

这些依赖只要每个应用都是独立 Spring WebFlux native 进程，就基本都会重复：

- Spring Boot/WebFlux/Reactor Netty。
- Spring Data/R2DBC/事务基础设施。
- Jackson 3.x。
- Hibernate Validator。
- 日志、YAML、Micrometer 基础组件。
- Netty HTTP/HTTP2/HTTP3、DNS、epoll 等网络组件。

这类重复不能通过简单 Maven 模块拆分消除。GraalVM native image 是按每个可执行文件做闭包分析并静态生成镜像，不建议把 Spring AOT 产物和业务 Java 模块做成共享动态库让多个 native app 链接，否则版本、反射提示、资源提示和 AOT 生成物会变得不可验证。

### 不应该继续重复的认证闭包

`flyfish-auth` 当前被两个业务 app 同时引入，带来的重复更值得处理：

- `spring-boot-starter-thymeleaf`、`thymeleaf`、`attoparser`、`unbescape`：只服务 OAuth 回调页和绑定确认页。
- `spring-webflux-pac4j`、`pac4j-core`、`pac4j-oauth`：只服务第三方 OAuth 登录。
- `scribejava-*`：只服务 OAuth token 和 profile 流程。
- `reflections`、`javassist`：由 pac4j 间接带入，对 native 不友好。
- `jackson-databind 2.x`：由 pac4j 带入，和 Spring Boot 4 当前 Jackson 3.x 并存。
- `spring-boot-starter-mail`：认证邮箱快捷登录需要；低代码不应该为此携带邮件能力。
- 用户表、OAuth 绑定表、Token 解析、微信快捷登录会话、OAuth 模板资源。

这些能力天然属于“认证中心”，小铺和低代码多数场景只需要结果：当前用户是谁、是否登录、有哪些第三方绑定、是否管理员或维护者。

## 推荐目标架构

保持单机三进程，不引入完整微服务治理：

```text
nginx
├── flyfish-auth-app     10080  认证、用户、OAuth、邮箱登录、微信快捷登录
├── flyfish-lowcode-app  10081  低代码业务，只通过 auth API 解析当前用户
└── flyfish-shop-app     10082  小铺业务，只通过 auth API 读取用户和绑定资料
```

仍然保持：

- 同一台服务器。
- 同一个 MySQL 实例。
- systemd 管理三个进程。
- nginx 基于路径转发。
- 无服务注册、无配置中心、无分布式事务、无消息中间件。

认证服务是内部稳定依赖，不做复杂治理。业务服务只调用 `127.0.0.1:10080`，超时短、结果可缓存、失败降级为未登录或提示重试。

## 模块拆分建议

### `flyfish-auth-api`

新增纯契约模块，不依赖 pac4j、Thymeleaf、mail、R2DBC 实现。

建议承载：

- `PortalUserVo`、`PortalUserOauthVo`、`OAuthType`、`GuestUser`。
- `CurrentUser` 注解。
- `UserAuthorizationUtils` 或更中性的 `PortalUserAuthorities`。
- 当前用户、用户批量查询、OAuth 绑定资料等 DTO。
- Auth HTTP Interface Client 的接口定义可放在此模块或单独 `flyfish-auth-client`。

目标：小铺和低代码可以使用统一用户类型，但不会引入认证实现。

### `flyfish-auth-app`

从当前 `flyfish-auth` 保留重实现能力：

- 用户表、OAuth 绑定表、用户资料维护。
- JWT 签发、退出登录、黑名单。
- OAuth 登录、绑定、换绑确认。
- Gitea/GitHub/Gitee profile 拉取。
- 邮箱快捷登录。
- 微信快捷登录码创建和兑换。
- OAuth 模板资源。

该模块是当前过渡期的认证实现模块。最终远程鉴权完成后，它应只被 `flyfish-auth-app` 引用，不再进入小铺和低代码 app。

### `flyfish-auth-client`

给小铺和低代码使用的轻量客户端模块。

建议承载：

- 基于 Spring Http Interface Client 的 `AuthSessionClient`、`AuthUserClient`。
- `RemotePrincipalExtractor`：从请求 token 调 auth 服务解析当前用户。
- `UserArgumentResolver`：继续支持 `@CurrentUser PortalUserVo`，业务 Controller 不需要大改。
- `ReactiveUserFilter`：继续做路径鉴权，但不读用户表。
- 极轻量本地缓存：按 token 或 userId 缓存 `PortalUserVo`，TTL 例如 30-60 秒；退出或绑定变更由 auth 返回新 token 或前端刷新即可。

目标：业务侧保留当前编程模型，但 runtime 不带 OAuth/pac4j/模板/用户仓储。

### `flyfish-auth-app`

新增启动模块，依赖：

- `flyfish-common`
- `flyfish-platform` 中真正通用、无 auth 实现依赖的部分
- `flyfish-auth-api`

如果 `flyfish-platform` 仍然需要当前用户上传能力，应改为依赖 `flyfish-auth-api` 和可插拔的 `PrincipalExtractor`，不能再直接依赖 auth server。

## 内部 API 设计

认证服务对业务 app 暴露内部 API，建议统一放在 `/internal/auth/**`，仅允许本机访问，公网 nginx 不暴露。

必要接口：

- `POST /internal/auth/session/resolve`：入参 `{ token }`，返回 `PortalUserVo`，包含第三方绑定摘要。
- `GET /internal/auth/users/{id}`：查询单个用户。
- `POST /internal/auth/users/batch`：批量查询用户，用于订单、工单、客服列表补齐头像昵称。
- `GET /internal/auth/users/{id}/authorizations`：查询指定用户绑定资料。
- `GET /internal/auth/oauth/{type}/openid/{openid}`：公众号、客服场景按 openid 反查用户绑定。
- `POST /internal/auth/wechat/login-codes`：公众号命中“购买/开通”关键词后创建快捷登录码。
- `POST /internal/auth/wechat/login-codes/{code}/consume`：前端点击快捷登录链接后兑换 token。

公网接口继续由 auth app 承载：

- `/portal/users/**`
- `/oauth/**`
- `/email/**`
- `/wx/login/**` 或微信快捷登录相关路径

小铺自己的公众号消息网关 `/wx` 可以继续在 `flyfish-shop-app`，因为消息规则、客服、工单、商品购买链接属于小铺业务；但它不应该直接操作认证内存登录会话，而是调用 auth app 创建快捷登录码。

## 路由调整建议

nginx 分流可调整为：

```text
/portal/users/**     -> auth 10080
/oauth/**            -> auth 10080
/email/**            -> auth 10080
/wx/quick-login/**   -> auth 10080

/integrity/**        -> lowcode 10081
/portal/workbench    -> lowcode 10081

/shops/**            -> shop 10082
/portal/tickets/**   -> shop 10082
/portal/customer-service/** -> shop 10082
/wx                  -> shop 10082
/wx/**               -> shop 10082，快捷登录路径除外
```

内部接口 `/internal/auth/**` 不走公网，业务 app 使用 `http://127.0.0.1:10080` 直连。

## 数据归属

仍使用同一个生产数据库，但按模块拥有表，避免微服务复杂度：

- auth 拥有 `portal_user`、`portal_user_oauth`、登录码、token 黑名单等认证表。
- shop 只保存 `buyer_id`、`user_id`、必要的显示快照，不直接写 auth 表。
- lowcode 只保存自己的数据源、模型、测试记录等。
- git 仓库 token 管理如果仍属于小铺自动交付，应留在 shop/git 域；如果将来变成全站能力，可再独立成 `flyfish-repository-app`。

小铺展示用户信息有两种方式：

- 订单、工单、客服列表优先使用业务表中的用户快照，保证历史可回溯。
- 需要最新资料时，调用 auth 批量查询接口补齐。

## 性能策略

为了“像微服务但不复杂”，建议只做以下轻量机制：

- 内部 HTTP 使用 Spring Http Interface Client + WebFlux。
- 调 auth 的超时控制在 `300ms-800ms`，失败返回未登录或业务错误提示。
- `PortalUserVo` 本地缓存 30-60 秒，按 token 和 userId 两层缓存即可，不引入 Redis。
- 管理列表批量查用户，禁止循环逐条请求 auth。
- JWT 验签可在业务 app 本地完成第一道校验，再调用 auth 获取用户快照；如果要支持立即退出生效，则仍以 auth session resolve 为准。
- 只在需要 `@CurrentUser PortalUserVo` 的请求解析用户，公开列表接口不触发认证调用。

## Native 体积预期

需要明确：拆出 auth app 不保证总二进制体积下降。

预期变化：

- 小铺/低代码单个 native 有机会移除 pac4j、Thymeleaf、scribejava、reflections、auth 邮件登录、用户仓储和模板资源。
- auth app 会新增一个完整 Spring native 进程，因此总磁盘体积可能从 `约 409M` 增加到 `约 550M-600M`。
- 真正收益是：认证复杂依赖不污染业务 app、业务 app 构建和 AOT 分析更单纯、OAuth/登录问题集中排查、低代码应用不再携带完全无关的认证登录实现。

如果最核心指标是总磁盘体积，最佳选择不是继续拆 native app，而是：

- 保持单 native app；或
- auth 使用 JVM jar 运行，小铺/低代码使用 native；或
- 回到一个业务 native app，按前端和 nginx 做能力门控。

如果最核心指标是边界、稳定性和单业务上线成本，auth 独立 app 是合理选择。

## 分阶段迁移计划

### 第一阶段：契约剥离

1. 新增 `flyfish-auth-api`。
2. 将 `PortalUserVo`、`PortalUserOauthVo`、`OAuthType`、`CurrentUser`、权限判断工具迁移到 auth-api。
3. 修改 shop/lowcode/platform/git，只依赖 auth-api，不再 import auth-server 包。
4. 架构测试新增规则：业务模块禁止 import `group.flyfish.dev.oauth.*`、`group.flyfish.dev.user.repository.*`、`group.flyfish.dev.user.service.impl.*`。

### 第二阶段：远程当前用户

1. 新增 `flyfish-auth-client`。
2. 在 lowcode/shop app 中使用 `RemotePrincipalExtractor` 替代本地 `PrincipalExtractorImpl`。
3. auth app 暴露 `/internal/auth/session/resolve` 和用户批量查询接口。
4. 小铺订单、工单、客服需要用户信息的地方改为 auth-client 批量读取或使用快照。

### 第三阶段：auth app 独立启动

1. 新增 `flyfish-auth-app`，端口 `10080`。
2. `flyfish-lowcode-app` 移除 `flyfish-auth.yml` 导入，改为导入 `flyfish-auth-client.yml`。
3. `flyfish-shop-app` 移除 OAuth/pac4j 用户表实现依赖，公众号快捷登录改为调用 auth 内部 API。
4. nginx 增加 auth 路由，systemd 增加 `flyfish-auth.service`。
5. 上线前备份数据库，三进程临时端口冒烟，再切正式 symlink。

### 第四阶段：瘦身和构建优化

1. 移除 app runtime 中不该出现的 Lombok。
2. 检查生产 native profile 是否完全排除 H2。
3. 评估是否禁用 HTTP/3/QUIC 相关 native runtime，若业务不需要 HTTP/3，可减少 Netty 多平台 QUIC 依赖进入 fat jar 的概率。
4. 清理 pac4j 引入的 Jackson 2.x，只留在 auth app 内；业务 app 保持 Spring Boot 4/Jackson 3.x。
5. 为 `build.sh` 增加 `auth` 目标：`FLYFISH_NATIVE_APPS=auth,shop,lowcode ./build.sh`。

## 最小验收标准

- `flyfish-lowcode-app` 的依赖树不再出现 `pac4j`、`scribejava`、`thymeleaf`、`flyfish-auth-server`。
- `flyfish-shop-app` 的依赖树不再出现 `pac4j`、`scribejava`、`thymeleaf`、`flyfish-auth-server`。
- `flyfish-auth-app` 独立承载 `/portal/users/current`、OAuth 登录、邮箱登录、微信快捷登录。
- 小铺购买、订单、工单、客服、Git/Gitea/Gitee/GitHub 自动开通仍能拿到绑定资料。
- 低代码工作台、数据建模、在线运行、集成测试只在需要登录态时请求 auth。
- 退出登录、换绑确认、OAuth 回调、微信快捷登录不再出现跨实例状态分裂。
- 生产 nginx 只有公网必要路由，`/internal/auth/**` 不对公网开放。

## 关键判断

建议做 auth 独立，但不要期待“三个 native 总大小比两个 native 更小”。这次拆分的正确目标是降低业务 app 的认证依赖污染和故障耦合，而不是压缩磁盘总量。如果要同时追求总体积和边界，可以优先把 `flyfish-auth-app` 用 jar 启动；认证请求频率低、CPU 压力小，JVM jar 的运维成本可能低于新增一个 200M 级 native。
