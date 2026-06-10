# 飞鱼服务工程架构

本文档约束飞鱼认证、低代码平台和飞鱼小铺的模块边界。当前目标是把根目录从扁平业务模块整理为“服务工程 + 子模块”的结构：每个服务有自己的聚合目录、API 契约模块和启动应用模块。

## 根目录结构

```text
flyfish-dev-next
├── flyfish-ddl                 # DDL/建模基础注解与通用类型
├── flyfish-portal-api          # 门户能力、工作台扩展等轻量契约
├── flyfish-common              # 无业务感知的基础设施、响应、异常、R2DBC 仓储、上传基础能力
├── flyfish-platform            # 门户共享 Web 能力，例如文件上传、能力聚合和静态兜底资源
├── flyfish-git                 # Git/Gitea/Gitee/GitHub 仓库管理与远程 API 对接
├── flyfish-auth
│   ├── flyfish-auth-api        # 认证服务契约，后续服务间调用共享 DTO/接口约定
│   └── flyfish-auth-app        # 认证服务启动实例和业务实现，端口 10080
├── flyfish-lowcode
│   ├── flyfish-lowcode-api     # 低代码服务契约，后续服务间调用共享 DTO/接口约定
│   └── flyfish-lowcode-app     # 低代码启动实例和业务实现，端口 10081
└── flyfish-shop
    ├── flyfish-shop-api        # 小铺服务契约，后续服务间调用共享 DTO/接口约定
    └── flyfish-shop-app        # 小铺启动实例和业务实现，端口 10082
```

`flyfish-main` 单体组合启动模块已退出主工程。生产和本地服务化验证以三个启动应用为准：认证、低代码、小铺。

## 依赖方向

允许的依赖方向：

```text
app -> service implementation -> integration/shared -> common -> ddl
api -> common/none
```

具体约束：

- `flyfish-common` 不依赖任何业务模块。
- `flyfish-portal-api` 不依赖业务模块，只提供门户能力和工作台扩展契约。
- `flyfish-auth-api` 放认证服务的跨服务契约、用户 DTO、授权 DTO 和内部 HTTP Interface Client 约定，不放数据库、OAuth 登录实现、模板或 Controller。
- `flyfish-lowcode-api`、`flyfish-shop-api` 只放跨服务契约，不放 controller、repository、service 实现、schema、模板或具体配置。
- `flyfish-auth-app` 组合 `flyfish-auth-api` 和 `flyfish-common`，承载用户、OAuth、Token、邮箱登录、微信快捷登录等认证实现。
- `flyfish-platform` 依赖 `flyfish-portal-api`、`flyfish-common` 和 `flyfish-auth-api`，通过内部 HTTP Interface Client 解析当前用户和审计上下文。
- `flyfish-git` 只依赖 `flyfish-common`，不感知认证、低代码和小铺业务。
- `flyfish-lowcode-app` 组合 `flyfish-platform`、`flyfish-auth-api` 和自身低代码业务实现，不依赖小铺和认证实现。
- `flyfish-shop-app` 组合 `flyfish-platform`、`flyfish-auth-api`、`flyfish-git` 和自身小铺业务实现，不依赖低代码和认证实现。

## 运行实例

生产以三个 native 服务启动：

```text
flyfish-auth-app     -> flyfish-auth     :10080
flyfish-lowcode-app  -> flyfish-lowcode  :10081
flyfish-shop-app     -> flyfish-shop     :10082
```

nginx 按路径分发：

- `/portal/users/**`、`/oauth/**`、`/email/**`、`/wx/quick-login/**`、`/wx/qr-codes/**` -> auth。
- `/portal/workbench`、`/portal/capabilities`、`/integrity/**` -> lowcode。
- `/shops/**`、`/portal/customer-service/**`、`/portal/tickets/**`、`/portal/files/**`、`/wx`、`/images/**` -> shop。
- `/__auth/`、`/__lowcode/`、`/__shop/` 为前端能力探测和内部验证前缀。

## 配置归属

- `flyfish-common`：`config/flyfish-common.yml`。
- `flyfish-platform`：`config/flyfish-platform.yml`。
- `flyfish-auth-app`：`config/flyfish-auth.yml` 与认证实例级 `application-*.yml`。
- `flyfish-platform`：`config/flyfish-auth-client.yml` 用于业务服务访问认证服务。
- `flyfish-git`：`config/flyfish-git.yml`。
- `flyfish-lowcode-app`：`config/flyfish-lowcode.yml` 与 `application-*.yml`。
- `flyfish-shop-app`：`config/flyfish-shop.yml` 与 `application-*.yml`。

启动应用只保留实例级配置，例如端口、profile 和应用库连接。业务配置随业务模块走，不能复制到多个 app。

## 数据库脚本归属

数据库初始化由 `flyfish-common` 的模块化初始化器加载当前 classpath 中的脚本：

- `flyfish-auth-app` 拥有认证用户、OAuth 绑定、登录相关表。
- `flyfish-lowcode-app` 拥有低代码数据源、建模、运行和测试相关表。
- `flyfish-shop-app` 拥有商品、订单、支付、交付、工单、客服、公众号活动相关表。
- `flyfish-git` 拥有仓库管理和 API token 表。

schema/dialect 脚本不能跨模块引用其他模块拥有的表。需要跨模块展示用户信息时，应保存必要快照或通过后续 auth API 批量查询。

## 架构测试

`flyfish-common/src/test/java/group/flyfish/dev/architecture/ModuleBoundaryTest.java` 会校验：

- 服务目录必须包含清晰的 api/app 子模块。
- 内部 Maven 依赖必须符合依赖方向。
- API 模块不能携带实现资源和实现包引用。
- 低代码和小铺不能互相 import 或泄漏对方路由/文案。
- schema 表只能由一个模块声明，脚本只能引用本模块拥有的表。

运行：

```sh
./mvnw -pl flyfish-common -Dtest=group.flyfish.dev.architecture.ModuleBoundaryTest test
```

完整回归：

```sh
./mvnw test
cd web && npm run build
```

## 构建

构建三个 native 服务：

```sh
./build.sh
```

只构建某个服务：

```sh
FLYFISH_NATIVE_APPS=auth ./build.sh
FLYFISH_NATIVE_APPS=lowcode ./build.sh
FLYFISH_NATIVE_APPS=shop ./build.sh
```

对应产物：

```text
flyfish-auth/flyfish-auth-app/target/flyfish-auth
flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode
flyfish-shop/flyfish-shop-app/target/flyfish-shop
```
