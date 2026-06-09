# 飞鱼模块化架构

本文档约束飞鱼低代码平台、飞鱼小铺与共享认证能力的模块边界。

## 后端模块

```text
flyfish-dev
├── flyfish-ddl           # DDL/建模基础类型与工具
├── flyfish-portal-api    # 门户能力与工作台扩展摘要/动作的轻量契约，不承载实现
├── flyfish-common        # 无业务感知的基础设施、响应、异常、R2DBC 仓储、上传基础服务
├── flyfish-auth          # 用户、OAuth、Token、登录会话与 CurrentUser 解析
├── flyfish-platform      # 需要共享登录态的门户 Web 能力，如文件上传入口
├── flyfish-git           # Git 平台客户端、Token、仓库管理服务与仓库查询
├── flyfish-lowcode       # 低代码平台：数据源、模型、SQL 运行、集成测试、工作台
├── flyfish-shop          # 飞鱼小铺：商品、订单、支付、交付、客服、工单、公众号能力
├── flyfish-lowcode-app   # 低代码最小启动实例
├── flyfish-shop-app      # 小铺最小启动实例
└── flyfish-main          # 全量组合启动实例
```

## 依赖方向

允许的依赖方向：

```text
app -> business -> integration/shared -> common -> ddl
```

具体约束：

- `flyfish-common` 不依赖任何业务模块。
- `flyfish-portal-api` 不依赖业务模块，只提供门户能力声明、工作台扩展摘要和扩展动作契约。
- `flyfish-auth` 只依赖 `flyfish-common`，为低代码和小铺提供同一套用户、OAuth、Token 与登录态。
- `flyfish-platform` 依赖 `flyfish-portal-api` 与 `flyfish-auth`，承载低代码与小铺都需要、但必须感知当前用户的共享门户入口。
- `flyfish-git` 依赖 `flyfish-auth`，但不依赖 `flyfish-shop`；小铺的 Git 管理 HTTP 入口和 Git 商品交付解析都留在 `flyfish-shop`。
- `flyfish-lowcode` 依赖 `flyfish-portal-api` 与 `flyfish-auth`，不依赖 `flyfish-shop`。
- `flyfish-shop` 依赖 `flyfish-portal-api`、`flyfish-git` 与 `flyfish-auth`，不依赖 `flyfish-lowcode`。
- app 模块允许直接依赖 `flyfish-common`，仅用于启动类引用通用仓储工厂、调度或运行时基础设施；不能放入业务实现。
- `flyfish-lowcode-app` 与 `flyfish-shop-app` 分别组合 `flyfish-platform` 和自身业务模块，提供最小可运行实例。
- `flyfish-main` 组合 `flyfish-platform`、低代码和小铺模块做全量启动，不承载业务实现。

`flyfish-main/src/test/java/group/flyfish/dev/architecture/ModuleBoundaryTest.java` 会在 Maven 测试中校验上述内部模块依赖和源码 import 边界，防止后续改动重新引入反向业务依赖。
该测试还会校验三个 app 模块只保留 Spring Boot 启动类，不承载业务 Java 源码、业务 schema 或 dialect 脚本。

测试归属跟随主代码归属：

- common/auth/git/lowcode/shop 的单元测试放在对应模块 `src/test/java` 下。
- `flyfish-main` 只保留组合启动、全量集成和架构边界测试。
- 父 POM 统一提供 JUnit/Mockito/AssertJ/Reactor Test 等 test scope 依赖，不进入运行产物。

## 共享门户能力

`flyfish-platform` 用于承载必须感知当前用户、但又不属于具体业务的共享 Web 入口。

- `/portal/files` 位于 `flyfish-platform`，复用 `flyfish-common` 的上传基础服务与 `flyfish-auth` 的当前用户解析。
- `/portal/capabilities` 位于 `flyfish-platform`，聚合当前实例 classpath 中的 `PortalCapabilityProvider`；该契约由 `flyfish-portal-api` 提供，前端据此判断当前实例可展示的业务入口。
- 低代码实例和小铺实例都显式引入 `flyfish-platform`，因此共享同一套上传入口、登录态和鉴权上下文。
- `flyfish-common` 继续保持纯基础设施定位，不直接依赖 `PortalUserVo` 或认证实现。
- `banner.txt` 与后端静态兜底页 `static/index.html`、`static/favicon.ico` 位于 `flyfish-platform`，OAuth 模板位于 `flyfish-auth`，三套后端实例不再重复打包同一份兜底静态资源或模板资源。完整前端仍由 `web` 单独构建成一套 `dist`，按部署文档由 nginx 托管。

能力提供者归属业务模块：

- `flyfish-lowcode` 提供 `lowcode` 能力。
- `flyfish-shop` 提供 `shop` 能力。
- `flyfish-main` 同时引入两个业务模块，因此返回两个能力；最小实例只返回自身能力。

## 数据库初始化

数据库初始化由 `flyfish-common` 的模块化初始化器统一执行：

- 各模块只携带自己的 `schema/*.sql` 和可选的 `dialect/{database}/*.sql`。
- 初始化器按脚本文件名排序加载当前 classpath 中存在的模块脚本。
- `flyfish-lowcode-app` 只加载 common/auth/lowcode 表结构，不加载小铺或 Git 管理表。
- `flyfish-shop-app` 加载 common/auth/git/shop 表结构，不加载低代码数据源表。
- `flyfish-main` 同时引入所有业务模块，因此加载全量表结构。

新增表结构必须放入拥有该表实体或业务能力的模块，不能再复制全量 `schema.sql` 到 app 模块。
架构测试会校验 `CREATE TABLE` 不能重复声明，并校验 schema/dialect 中的 `ALTER TABLE`、`COMMENT ON TABLE`、`CREATE INDEX ... ON`、`UPDATE` 只能引用本模块拥有的表。

## 配置归属

应用实例只保留实例级配置，例如端口、激活 profile、本地或生产数据库连接。公共配置按模块存放并由 app 显式导入：

- `flyfish-common`：`config/flyfish-common.yml`，基础 Spring/Jackson/HTTP 配置。
- `flyfish-platform`：`config/flyfish-platform.yml`，统一前端静态资源映射。
- `flyfish-auth`：`config/flyfish-auth.yml`，OAuth、pac4j 与微信快捷登录的业务中性默认配置。
- `flyfish-git`：`config/flyfish-git.yml`，Git 平台 API 配置。
- `flyfish-lowcode`：`config/flyfish-lowcode.yml`，低代码生成器配置，以及低代码 OAuth 展示名覆盖。
- `flyfish-shop`：`config/flyfish-shop.yml`，小铺支付、公众号、客服通知、小铺鉴权白名单、微信快捷登录跳转与小铺 OAuth 展示名覆盖。

`flyfish-lowcode-app` 不导入小铺、公众号、支付、Git 管理或客服配置；`flyfish-shop-app` 不导入低代码生成器配置。

共享认证模块的默认值不能再带具体业务语义：

- `flyfish-auth` 的 OAuth `app-name` 默认是“飞鱼”，供共享认证流程兜底。
- `flyfish-auth` 的 `wx.quick-login.default-redirect` 默认是 `/`，不指向小铺页面。
- `flyfish-lowcode` 将 OAuth 展示名覆盖为“飞鱼低代码平台”。
- `flyfish-shop` 将 OAuth 展示名覆盖为“飞鱼小铺”，并把微信快捷登录默认跳转覆盖到 `/shop/item-list`。

数据库驱动按拥有者加载：

- `flyfish-common` 不携带具体数据库驱动，只提供 R2DBC 基础设施。
- `flyfish-lowcode` 显式携带 `r2dbc-mysql`，用于低代码连接外部 MySQL 数据源做元数据读取和代码生成。
- app 模块的 `local` profile 提供本地应用库需要的 `r2dbc-h2`。
- app 模块的 `prod`/`native` profile 提供生产应用库需要的 `r2dbc-mysql`，并排除本地 H2 驱动。

## 运行时基础设施归属

动态 API 缓存控制由 `flyfish-platform` 的 `ApiCacheControlConfig` 统一注册，避免浏览器缓存游客态、旧 token 或 OAuth 回调后的过期响应。各模块只通过 `ApiNoStorePathProvider` 声明自己拥有的动态接口前缀：

- `flyfish-platform` 声明 `/portal/`。
- `flyfish-auth` 声明 `/oauth/` 与 `/wx/`。
- `flyfish-lowcode` 声明 `/integrity/`。
- `flyfish-shop` 声明 `/shops/`。

Native Runtime Hints 按模块拆分：

- `flyfish-common` 只注册 common 基础设施、通用绑定类型和 classpath 资源。
- `flyfish-platform` 注册门户 API 返回类型，`flyfish-auth`、`flyfish-git`、`flyfish-lowcode`、`flyfish-shop` 分别注册自己的反射类型和资源。
- 共享模块不能用字符串或反射配置引用具体业务包，避免 native 构建时把未引入业务模块重新拉进来。

## 工作台解耦

低代码工作台不直接读取小铺表，也不硬编码小铺入口。工作台壳、低代码基础动作和 `/portal/workbench` 接口归属 `flyfish-lowcode`；小铺摘要和小铺工作台动作通过 `flyfish-portal-api` 的 `PortalWorkbenchSummaryProvider` 扩展契约由 `flyfish-shop` 自己注入：

- 低代码实例未引入小铺时，工作台不展示小铺摘要和小铺入口动作，低代码仍可运行。
- 全量实例同时引入小铺时，小铺模块提供真实商品/订单摘要，并通过 `actions()` 暴露自己的工作台入口；`actionName`、`actionPath`、`actionStatus` 作为兼容默认实现继续可用，保持原有 `workbench.shop` JSON 结构。
- `WorkbenchVo` 只保留通用扩展摘要容器，通过 capability 动态输出顶层扩展属性；扩展指标统一放入 `extensionMetrics`，指标文案由业务 provider 提供，低代码 Java 类型中不出现小铺 VO、常量或路由。
- 前端低代码工作台只消费 `workbench.actions`、`extensionMetrics` 与 capability 动态 summary，不直接读取 `workbench.shop`，也不写死小铺管理路由或小铺指标文案。
- 业务模块源码边界测试会禁止低代码模块出现小铺文案、`Shop/shop` 标识或 `/shop` 路由，也禁止小铺模块出现低代码平台文案、`Lowcode/lowcode` 标识或低代码路由。
- `flyfish-common` 不承载工作台或小铺语义，继续保持纯基础设施定位。

## 共享认证

后端共享认证在 `flyfish-auth`：

- `group.flyfish.dev.user`：门户用户、Token、当前用户上下文、审计用户。
- `group.flyfish.dev.oauth`：OAuth 提供商、回调、绑定与登录。
- `group.flyfish.dev.wechat.bean`：微信登录会话 DTO，供认证流程和小铺公众号入口复用。
- `group.flyfish.dev.wechat.service`：微信扫码登录的共享登录服务、存储和提供者接口；小铺公众号 API、消息路由和规则实现位于 `group.flyfish.dev.shop.wechat`，不与 auth 形成 split package。
- `UserAuthorizationUtils` 只暴露登录、管理员、通用维护者和第三方授权资料读取；小铺维护者语义由 `flyfish-shop` 的 `ShopAuthorizationUtils` 包装。

前端共享认证在 `web/src/modules/auth`：

- `api.js`：`PortalUsers`、`PortalOauth`、`PortalFiles`。
- `store/client.js`：共享登录态、token、本地跳转、路由鉴权。
- `authority.js`：授权账号读取、通用维护者判断和展示格式化，不写死小铺角色。
- `routes.js`：登录页与个人资料路由。
- `pages/Login` 与 `pages/Account/Profile.vue`：登录和用户资料页面。

低代码与小铺前端都消费同一个 `useClientStore`，共享 `access_token` 与登录态。
小铺前端的 `isShopMaintainer` 包装位于 `web/src/modules/shop/authority.js`，共享认证模块不承载小铺角色名或路由。

## 前端模块

前端仍是一套 Vite/Vue 应用，根路由只组合模块；生产环境构建为一套 `web/dist`，不按后端实例重复构建：

- `web/src/network/request.js`：底层 HTTP request 封装，不做业务 API 聚合。
- `web/src/modules/portal/api.js`：共享门户能力接口。
- `web/src/modules/portal/usePortalCapabilities.js`：共享门户能力状态，缓存 `/portal/capabilities` 结果。
- `web/src/modules/auth/routes.js`：登录、个人资料。
- `web/src/modules/auth/pages`：认证和用户资料页面。
- `web/src/modules/lowcode/manifest.js`：低代码模块轻量清单，不包含页面实现和图片资源。
- `web/src/modules/lowcode/routes.js`：数据建模、代码生成、在线运行、集成测试路由，只绑定异步页面组件。
- `web/src/modules/lowcode/nav.js`：低代码首页卡片图片资源扩展，仅在 `hasLowcode` 为真后由首页动态加载。
- `web/src/modules/lowcode/api/workbench.js`：低代码工作台接口封装。
- `web/src/modules/lowcode/components/LowcodeWorkbench.vue`：低代码工作台 UI，只在 `hasLowcode` 为真后由首页异步组件加载。
- `web/src/modules/lowcode/pages`：低代码平台页面。
- `web/src/modules/lowcode/store`：低代码平台数据源状态。
- `web/src/modules/shop/routes.js`：飞鱼小铺公开页与管理页。
- `web/src/modules/shop/pages`：飞鱼小铺、订单、工单、商品管理等页面。
- `web/src/modules/shop/assets/contact.js`：小铺客服二维码等小铺归属资源的运行时 URL 扩展。
- `web/src/modules/shop/components/MarketEntry.vue`：小铺首页入口条，只在 `hasShop` 为真后由首页异步组件加载。
- `web/src/modules/shop/components/CustomerService`：小铺客服浮窗、会话、工单提醒与客服联系方式组件。
- `web/src/modules/shop/utils`：小铺订单、工单、商品封面、交付、Git 仓库开通等领域工具；公共 `src/utils` 只保留跨业务通用工具。

`/account/orders` 与 `/account/tickets` 仍保留原 URL，但由 `shopRoutes` 注册，因为它们属于小铺订单与工单能力，不放入认证模块。

首页、顶栏、页脚、登录标题和浏览器标题都消费 `usePortalCapabilities`：

- 公共壳层的实例标题、默认开始入口、小铺入口名称和入口路径都从 `/portal/capabilities` 返回的 capability 元数据派生，避免在多个共享组件里重复维护业务名称和默认路由；`web/src/modules/portal` 不硬编码业务名称或业务默认路由。
- 低代码最小实例只展示低代码入口，不展示小铺入口、商品管理、客服微信。
- 小铺最小实例只展示小铺入口，标题切换为“飞鱼小铺”，不展示低代码入口。
- 全量实例同时展示低代码与小铺入口。
- 公共用户菜单只在 `hasShop` 为真时展示“提交工单”和“我的订单”，避免低代码最小实例在共享登录态下泄漏小铺账户入口。
- 公共页脚只在 `hasShop` 为真后动态加载小铺提供的联系方式资源扩展，不直接引用小铺二维码资产，避免低代码最小实例首屏入口携带小铺资源名或预加载依赖。
- 首页不再从根路由表读取低代码卡片，也不直接请求工作台数据；低代码导航图片和低代码工作台组件在能力确认后按需加载，小铺市场入口组件在 `hasShop` 为真后按需加载。

路由组件带有 `meta.capability`，`RouterView` 会在渲染异步业务页面前校验当前实例能力：

- 能力未加载完成时，先渲染轻量占位，不触发业务页面动态 import。
- 能力不存在时，渲染 NotFound，不实例化另一个业务模块页面。
- 鉴权跳转在能力校验之后执行，避免低代码最小实例手动访问小铺管理页时被误导到登录页。

客服浮窗归属小铺模块，由小铺页面布局和小铺账号布局挂载；公共 `App.vue` 不直接依赖小铺客服组件，避免低代码首屏加载小铺客服代码。

`web/scripts/check-architecture.mjs` 会校验前端 import 边界：

- 低代码模块不能 import 小铺模块。
- 小铺模块不能 import 低代码模块。
- 认证和门户能力模块不能 import 业务模块。
- 公共壳层不能直接 import 业务页面，只能通过路由能力门控组合模块。
- 旧的 `@/store`、`@/pages/Shop`、`@/pages/ModelDesign` 等迁移前路径不能再被引用。
- 旧的 `@/utils/shopDelivery`、`@/utils/orderSort` 等小铺领域工具路径不能再被引用，也不能回到公共 `src/utils`。
- 旧的 `@/network/apis` 全局 API 聚合入口不能再被引用，业务 API wrapper 必须留在所属模块或页面附近。
- 公共壳层若出现 `/account/orders`、`/account/tickets` 等小铺账户入口，必须声明 `capability: 'shop'` 并通过 `hasShop` 门控。
- 公共壳层不能直接 import 小铺归属图片资源；需要通过小铺模块暴露的可选扩展并在能力门控后懒加载。
- 首页不能直接 import 根路由表来读取业务入口；低代码导航图片、低代码工作台和小铺市场入口都必须通过 capability 门控后的可选扩展懒加载。

运行方式：

```sh
cd web
npm run test:architecture
```

`web/scripts/check-build-artifacts.mjs` 会在 `npm run build` 后校验构建产物加载边界：

- `dist/index.html` 只引用一个主入口脚本。
- `modulepreload` 只预加载 `vendor-vue` 与 `vendor-ant-design` 共享依赖块。
- 商品详情、客服浮窗、Markdown 编辑器、低代码设计器等业务重页面不进入首屏 HTML 引用。
- 小铺客服二维码等小铺归属资源不能出现在主入口脚本或首屏 HTML 引用中。
- 低代码首页卡片图标不能出现在主入口脚本或首屏 HTML 引用中，必须随低代码导航扩展按需加载。
- 低代码工作台和小铺市场入口必须作为独立懒加载 chunk 输出，不能回流到主入口脚本。
- 关键业务页面仍作为独立懒加载 chunk 输出。
- Markdown 预览可以作为小铺详情和客服消息的按需能力加载，但不能拉起 Markdown 编辑器或 CodeMirror 语言包；编辑器只允许在商品管理弹窗链路中懒加载。

运行方式：

```sh
cd web
npm run build
npm run test:build-artifacts
```

`web/scripts/smoke-route-capabilities.mjs` 会在 `npm run build` 后校验前端路由能力门控和关键懒加载块：

- 低代码路由只动态引用低代码模块，小铺路由只动态引用小铺模块。
- `RouterView` 在渲染异步业务组件前先完成能力加载和能力判断。
- `App.vue` 只负责共享认证同步，不直接依赖小铺客服组件。
- 小铺账号布局挂载小铺客服浮窗，且构建后的客服 chunk 不进入低代码业务块。
- 关键小铺 chunk 不混入低代码实现标识，关键低代码 chunk 不混入小铺实现标识。

运行方式：

```sh
cd web
npm run build
npm run test:route-smoke
```

## 最小运行

全量组合实例：

```sh
./mvnw -pl flyfish-main -am -DskipTests clean package
java -jar flyfish-main/target/flyfish-dev.jar
```

低代码最小实例：

```sh
./mvnw -pl flyfish-lowcode-app -am -DskipTests clean package
java -jar flyfish-lowcode-app/target/flyfish-lowcode.jar
```

小铺最小实例：

```sh
./mvnw -pl flyfish-shop-app -am -DskipTests clean package
java -jar flyfish-shop-app/target/flyfish-shop.jar
```

本地默认端口：

- `flyfish-lowcode-app`：`10081`，可通过 `FLYFISH_LOWCODE_PORT` 覆盖。
- `flyfish-shop-app`：`10082`，可通过 `FLYFISH_SHOP_PORT` 覆盖。

本地 H2 数据库文件已分开：

- 低代码：`db/lowcode-db`
- 小铺：`db/shop-db`
- 全量实例保留原来的 `db/dev-db`

前端：

```sh
cd web
npm run dev
```

生产构建：

```sh
cd web
npm run test:architecture
npm run build
npm run test:build-artifacts
npm run test:route-smoke
```

后端本地产物边界检查：

```sh
./mvnw -q -pl flyfish-main,flyfish-lowcode-app,flyfish-shop-app -am -DskipTests clean package
./scripts/check-app-artifacts.sh
```

该脚本会校验：

- 低代码 JAR 只包含 portal-api/common/platform/auth/lowcode，不包含 shop/git。
- 小铺 JAR 只包含 portal-api/common/platform/auth/shop/git，不包含 lowcode。
- 全量 JAR 同时包含 lowcode/shop/git。
- 三个 JAR 中每个 `flyfish-*` 模块依赖只能出现一次，包括共享契约 `flyfish-portal-api`，避免共享模块被重复打包。
- 三个 app 不在自身 `BOOT-INF/classes/static` 重复打包静态兜底页，兜底静态资源由 `flyfish-platform` 提供。
- 三个 app 不在自身 `BOOT-INF/classes/templates` 重复打包模板，OAuth 模板由 `flyfish-auth` 提供。
- 本地驱动归属符合预期：小铺本地不带 MySQL 应用库驱动，低代码保留外部 MySQL 数据源读取能力。
- 小铺模块 JAR 不包含迁移前遗留的 `group.flyfish.dev.wechat` 实现包，避免旧 target class 被重新打入发布产物。

后端最小实例运行冒烟：

```sh
./scripts/smoke-minimal-apps.sh
```

该脚本会重新打包三种应用，在临时目录和随机端口中依次启动低代码最小实例、小铺最小实例和全量实例，并校验：

- 三个实例都共享 `/portal/users/current` 与 `/oauth/providers`。
- 低代码最小实例只返回 `lowcode` 能力，低代码工作台与数据源接口可用，小铺公开接口不能返回成功业务结果。
- 小铺最小实例只返回 `shop` 能力，小铺店铺、分组、商品列表接口可用，低代码工作台和数据源接口不能返回成功业务结果。
- 全量实例同时返回 `lowcode` 与 `shop` 能力，低代码和小铺基础接口都可用。
- 每次运行使用临时 H2 文件，结束后自动清理进程和临时数据库。

如果已经完成 clean 打包，可使用 `FLYFISH_SMOKE_SKIP_PACKAGE=1 ./scripts/smoke-minimal-apps.sh` 只复跑运行冒烟。

后端认证态运行冒烟：

```sh
./scripts/smoke-authenticated-apps.sh
```

该脚本使用临时 H2 文件库注入 smoke 用户和小铺维护者用户，并用与生产 `JwtCodec` 相同的 ES256 密钥派生方式生成真实 JWT。它会依次启动低代码最小实例、小铺最小实例和全量实例，并校验：

- 三个实例都能通过共享 `/portal/users/current` 识别 Bearer token 和 `access_token` query token。
- 低代码最小实例在认证态下只暴露低代码工作台，小铺订单和客服接口不能返回成功业务结果。
- 小铺最小实例在认证态下可访问订单、工单和客服摘要，低代码工作台不能返回成功业务结果。
- 小铺管理接口无 token 时返回 401，普通用户不能管理小铺，维护者用户可以访问管理用户列表。
- 全量实例在同一套认证态下同时验证低代码和小铺认证接口。

如果已经完成 clean 打包，可使用 `FLYFISH_AUTH_SMOKE_SKIP_PACKAGE=1 ./scripts/smoke-authenticated-apps.sh` 只复跑认证态冒烟。

前端页面级能力冒烟：

```sh
./scripts/smoke-ui-pages.sh
```

该脚本会启动同一套 Vite 前端，固定通过本地 `10081` 代理端口依次连接全量实例、低代码最小实例和小铺最小实例，并用 Playwright CLI 等待页面 selector 后截图：

- 全量实例：首页、登录页、低代码建模主页面、建模子页、代码生成、在线运行、集成测试、小铺列表页都可加载；小铺账号和管理入口在未登录时跳转登录页。
- 低代码最小实例：首页、低代码建模主页面、建模子页、代码生成、在线运行、集成测试都可加载；小铺列表、账号和管理路由渲染 NotFound。
- 小铺最小实例：首页、登录页和小铺列表页可加载；小铺账号和管理入口在未登录时跳转登录页；低代码建模、代码生成、集成测试路由渲染 NotFound。
- 直接访问低代码保存模型步骤会按既有建模流程回退到模型设计步骤，脚本会截图验证该行为。

截图默认输出到 `output/playwright/ui-smoke`。如果已经完成 clean 打包，可使用 `FLYFISH_UI_SMOKE_SKIP_PACKAGE=1 ./scripts/smoke-ui-pages.sh` 只复跑页面冒烟。

前端认证态页面冒烟：

```sh
./scripts/smoke-ui-authenticated-pages.sh
```

该脚本复用后端认证态冒烟的临时用户和真实 JWT，通过 Playwright `--load-storage` 将 `access_token` 写入同一套前端登录态，然后在三种应用形态下截图验证：

- 全量实例：普通用户可访问个人资料、我的订单、我的工单；普通用户访问小铺管理页渲染无权访问；维护者用户可访问用户管理和商品管理。
- 低代码最小实例：认证态个人资料可访问；小铺账号和小铺管理路由仍渲染 NotFound。
- 小铺最小实例：认证态个人资料、我的订单、我的工单可访问；普通用户不能访问管理页；维护者用户可访问用户管理和商品管理；低代码路由仍渲染 NotFound。

截图默认输出到 `output/playwright/ui-auth-smoke`。如果已经完成 clean 打包，可使用 `FLYFISH_UI_AUTH_SMOKE_SKIP_PACKAGE=1 ./scripts/smoke-ui-authenticated-pages.sh` 只复跑认证态页面冒烟。
