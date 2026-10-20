# 小铺主站与独立低代码前端

## 应用边界

- `web/` 是小铺主站，`/` 与 `/shop` 都展示公开首页，管理员从顶部管理菜单进入工作台。
- `web/lowcode/` 是独立 npm 包 `@flyfish/lowcode-web`，包含自己的 HTML、入口、路由、布局、业务页面、API、状态与 Surely 表格组件。
- 登录、用户资料、网络、基础路由和 i18n 由共享源码提供，不复制认证逻辑。
- 小铺不导入低代码实现；低代码不导入小铺实现。Vite 构建插件检查真实模块图，违反边界会直接失败。
- `/model-design`、`/code-generate`、`/online-launch`、`/integrate-test` 旧链接进行整页跳转，保留深层路径、查询参数。跨站链接不携带 token。
- 小铺继续采用 history 路由和双静态 release；低代码采用 hash 路由，可以部署在 `/lowcode/` 或独立域名，刷新深层页面不依赖 SPA rewrite。

## 本地运行

```bash
cd web
npm ci
npm run dev
# 另一终端
npm run dev:lowcode
```

小铺默认 `http://127.0.0.1:9999/`，低代码默认 `http://127.0.0.1:9998/`。
真实账号、建模和交易联调需要原本的本地后端服务。端口不同的 localStorage 不共享，不把开发端口之间的跳转当作 SSO。

`npm run preview:storefront` 是替代小铺 dev server 的只读设计预览：仅代理公开商品 GET 和公开商品图，不转发认证头、不代理账号写入、订单、支付或授权下载。
不要把这个预览脚本用于正式发布或真实支付验收。

## 构建配置

| 变量 | 默认开发值 | 默认生产值 | 生效位置 |
| --- | --- | --- | --- |
| `VITE_LOWCODE_URL` | `http://127.0.0.1:9998/` | `/lowcode/` | 小铺顶部、footer、旧链接跳转 |
| `VITE_SHOP_URL` | `http://127.0.0.1:9999/` | `/` | 低代码返回小铺 |

低代码配置固定 `VITE_APP_TARGET=lowcode`，主站不设置该标记。两套应用只请求自身的 capability 入口。
低代码配置从 `web/lowcode/.env.production.local` 或构建进程环境读取；小铺配置从 `web/.env.production.local` 或构建进程环境读取。
独立域名部署时，必须把两个 URL 都设置为各自完整 HTTPS 地址。

```bash
cd web
npm run build:lowcode
npm run build
npm run test:architecture
npm run test:storefront
npm run test:build-artifacts
npm run test:route-smoke
```

小铺的现有产物及部署方式不变。低代码产物仅在 `web/lowcode/dist/`，不包含合同查看器、商品、订单和客服。
浏览器回归脚本为 `web/scripts/smoke-storefront-browser.mjs`，使用隔离上下文与 API fixture 检查两种身份、中英文、320–1920px、分组筛选、搜索、一次性登录回跳、空态、失败重试与旧低代码深链接。它不进行真实支付或 OAuth 授权。
执行前启动两个开发入口，提供已安装的 Playwright：

```bash
FLYFISH_PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs node scripts/smoke-storefront-browser.mjs
```

## 发布顺序

1. 先执行测试并构建 `npm run build:lowcode` 与 `npm run build`。
2. 将低代码哈希资源和 HTML 发布到 `/lowcode/` 或独立 HTTPS 域名，保留旧哈希资源以兼容已打开页面。
3. 发布小铺 shell 与静态 release，确认 shell 和静态 manifest 的 release id 一致。
4. 原子切换入口后，验证 `/lowcode/` 的 hash 刷新、共享登录、小铺首页和静态资源 MIME。
5. 任一步失败时回滚入口 HTML 和 release 指针，不删除仍被旧页面引用的哈希资源。

## 图片发布

自有 PNG/JPEG/WebP/AVIF 素材使用 Tinify API 压缩，覆盖 `src/`、`lowcode/src/` 和 `public/images/`。SVG、ICO、第三方 viewer 资源、验证码及联系二维码不发送到第三方压缩接口。
密钥通过 `TINIFY_API_KEY` 环境变量或 Git 忽略的 `.local/tinify.env` 提供，不能进入客户端或 Git。

```bash
cd web
npm run images:compress
npm run test:images
```

`image-optimization.json` 记录输出哈希、尺寸和压缩前后字节数；已验证素材不重复调用 API。超过免费额度时停止，不自动购买额度。原图保留在忽略的 `target/tinify-originals/`，不会上传到生产。
主站构建先校验图片记录。随代码发布的公共图片使用不可变 release 地址，用户上传图片仍走原有存储，附件、合同、授权文件不受影响。

独立域名部署可以直接托管 `lowcode/dist/`，同域反代认证与低代码 API，并配置相应 OAuth callback/邮箱回跳允许域名。
现有认证回调只接受同源返回路径：迁移独立域名不是仅改 DNS，必须分别验证每个 OAuth 提供商。不同域名的 localStorage 不共享；不能在 URL 里转发 token 绕过这一边界。

## 首页体验

首页采用品牌封面、搜索、商品组导航、精选商品和支持入口，保留原有字体、留白、商品卡片和页脚。商品图、价格、优惠与交付能力仍来自原有 API 与 hooks，不维护第二套商品数据。
宽屏卡片最大 310px，手机为定宽横滑货架；图片固定比例、卡片等高。入场、滚动显现、分组切换和悬停动效尊重 `prefers-reduced-motion`，观察器在卸载时释放。
