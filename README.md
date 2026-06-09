# 飞鱼小铺 / Flyfish Shop

飞鱼小铺是飞鱼体系中的轻量数字商品与服务交付平台，包含商品展示、下单支付、订单管理、优惠券、Git 仓库交付、客服工单、公众号快捷登录、邮箱 magic link 登录等能力。当前仓库同时保留飞鱼低代码平台的最小运行实例，方便在同一套共享认证体系下独立运行低代码平台、小铺或完整集成版。

本仓库是源码交付版：已移除本地数据库、构建产物、测试截图和真实第三方密钥。所有敏感配置均通过环境变量注入，请勿把 `.env`、数据库文件、证书或生产日志提交到仓库。

## 架构概览

```text
flyfish-common       通用基础设施、异常、JSON、R2DBC、缓存控制
flyfish-auth         共享认证、JWT、OAuth、微信快捷登录、邮箱 magic link
flyfish-platform     门户能力发现、公共工作台、上传等平台能力
flyfish-git          Git 平台集成、访问 Token 与仓库元数据
flyfish-lowcode      飞鱼低代码平台业务模块
flyfish-shop         飞鱼小铺业务模块
flyfish-lowcode-app  仅低代码平台最小运行实例
flyfish-shop-app     仅飞鱼小铺最小运行实例
flyfish-main         低代码平台 + 小铺完整集成实例
web                  Vue 3 单前端，按 capability 动态展示低代码/小铺页面
```

前端只有一套登录态，认证能力集中在 `flyfish-auth`。低代码平台和小铺业务模块不直接互相依赖，应用实例通过 Maven 模块组合决定启用哪些能力。

## 本地启动

### 环境要求

- JDK 21+
- Maven Wrapper，项目已内置 `./mvnw`
- Node.js 20+ 与 npm
- 本地开发默认使用 H2 文件库；生产推荐 MySQL 8+

### 后端

完整集成版：

```bash
./mvnw -pl flyfish-main -am -DskipTests package
java -jar flyfish-main/target/flyfish-dev.jar --spring.profiles.active=local
```

仅小铺实例：

```bash
./mvnw -pl flyfish-shop-app -am -DskipTests package
java -jar flyfish-shop-app/target/flyfish-shop.jar --spring.profiles.active=local
```

仅低代码实例：

```bash
./mvnw -pl flyfish-lowcode-app -am -DskipTests package
java -jar flyfish-lowcode-app/target/flyfish-lowcode.jar --spring.profiles.active=local
```

### 前端

```bash
cd web
npm ci
npm run dev
```

默认前端地址为 `http://127.0.0.1:9999`，开发代理默认指向后端 `http://localhost:10081`。如果只启动小铺实例，请将 `web/vite.config.js` 中代理目标改到小铺后端端口，或启动时统一指定后端端口。

## 生产上线指引

1. 准备 MySQL 数据库，创建业务库和独立账号。
2. 复制 `.env.example` 中需要的环境变量到部署平台，填入真实值。
3. 设置强随机 `USER_JWT_SECRET`，所有实例必须一致，否则共享登录态会失效。
4. 配置 `OAUTH_CALLBACK_URL`、邮箱 magic link base URL、公众号网页入口、支付回调地址，确保域名和 HTTPS 证书已生效。
5. 构建后端 jar：

   ```bash
   ./mvnw -pl flyfish-shop-app -am -DskipTests clean package
   ```

   或构建完整集成版：

   ```bash
   ./mvnw -pl flyfish-main -am -DskipTests clean package
   ```

6. 构建前端静态资源：

   ```bash
   cd web
   npm ci
   npm run build
   ```

7. 使用 Nginx、CDN 或对象存储托管 `web/dist`，并将 `/portal`、`/oauth`、`/email`、`/wx`、`/shops`、`/integrity` 等 API 路径反向代理到后端。
8. 在第三方平台后台配置回调：
   - OAuth 回调：`https://你的域名/oauth/callback`
   - 邮箱 magic link：`EMAIL_MAGIC_LINK_BASE_URL=https://你的域名`
   - 微信公众号服务器地址与网页授权域名
   - 支付异步通知：`https://你的域名/shops/payments/h5zhifu/notify`
9. 上线后执行冒烟测试，确认能力发现、登录态、商品列表、订单、工单与管理端权限正常。

Native 构建与生产部署细节可参考 [docs/native-build-deploy.md](docs/native-build-deploy.md)，模块边界说明可参考 [docs/module-architecture.md](docs/module-architecture.md)。

## 上线物料清单

上线前请准备并妥善保管以下物料：

- 域名、HTTPS 证书、DNS 解析和反向代理配置
- MySQL 8+ 数据库地址、库名、账号、密码
- `USER_JWT_SECRET`，建议使用 32 字符以上随机值
- OAuth 应用：
  - Gitea client id / secret
  - Gitee client id / secret
  - GitHub client id / secret
- SMTP 邮件服务：
  - host、port、username、password、from
  - magic link 发信域名和 SPF/DKIM/DMARC 配置
- 微信公众号：
  - AppID、AppSecret
  - 消息 Token、EncodingAESKey
  - 客服二维码素材 media_id
  - 服务器地址、网页授权域名、JS 接口安全域名
- 支付渠道：
  - H5 支付 app id、通信 key
  - 支付回调公网 HTTPS 地址
  - 回调 IP 白名单或防火墙策略
- Git 交付能力：
  - Gitea/GitHub 管理 Token
  - 可交付仓库、组织、权限策略
- 运维：
  - 日志采集、监控告警、备份策略
  - 数据库备份和恢复演练
  - 前端静态资源发布路径

## 关键环境变量

完整模板见 [.env.example](.env.example)。

| 类别 | 变量 |
| --- | --- |
| 数据库 | `SPRING_R2DBC_URL`, `SPRING_R2DBC_USERNAME`, `SPRING_R2DBC_PASSWORD` |
| 认证 | `USER_JWT_SECRET`, `OAUTH_CALLBACK_URL` |
| OAuth | `OAUTH_GITEA_CLIENT_ID`, `OAUTH_GITEA_CLIENT_SECRET`, `OAUTH_GITEE_CLIENT_ID`, `OAUTH_GITEE_CLIENT_SECRET`, `OAUTH_GITHUB_CLIENT_ID`, `OAUTH_GITHUB_CLIENT_SECRET` |
| 邮件登录 | `EMAIL_MAGIC_LINK_BASE_URL`, `EMAIL_MAGIC_LINK_FROM`, `SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` |
| 微信 | `WX_MP_APP_ID`, `WX_MP_SECRET`, `WX_MP_TOKEN`, `WX_MP_AES_KEY`, `WX_MP_QUICK_LOGIN_BASE_URL` |
| 支付 | `H5ZHIFU_APP_ID`, `H5ZHIFU_KEY`, `H5ZHIFU_NOTIFY_URL` |
| 通知 | `SUPPORT_NOTIFICATION_MAIL_ADMIN_RECIPIENTS`, `SUPPORT_NOTIFICATION_WECHAT_ADMIN_OPENIDS` |

## 验证命令

后端测试：

```bash
./mvnw test
```

前端构建与架构检查：

```bash
cd web
npm ci
npm run build
npm run test:architecture
npm run test:build-artifacts
npm run test:route-smoke
```

应用冒烟：

```bash
scripts/check-app-artifacts.sh
scripts/smoke-minimal-apps.sh
scripts/smoke-authenticated-apps.sh
```

UI 截图冒烟需要本机可执行 Playwright：

```bash
scripts/smoke-ui-pages.sh
scripts/smoke-ui-authenticated-pages.sh
```

## 安全注意事项

- 不要提交 `.env`、数据库文件、日志、证书、密钥或第三方平台真实凭据。
- 生产环境必须覆盖默认 `USER_JWT_SECRET`。
- 邮箱 magic link 默认只在当前服务进程内记录已使用 nonce；多实例部署建议将一次性 token 状态迁移到 Redis 或数据库。
- 管理端接口依赖共享认证和维护者授权，请上线前用普通用户与维护者用户分别验证权限。
- 支付回调必须开启 HTTPS，并校验签名。

## 开源许可

请在正式公开前补充适合项目的 `LICENSE` 文件。未提供许可证时，默认不授予复制、修改、分发或商用权利。
