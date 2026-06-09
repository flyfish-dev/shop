# Docker Native 一键部署

本文档用于本地快速体验 Flyfish Shop 的 native 镜像部署方式。该模式会通过 Docker Compose 拉起三类服务：

- `mysql`：MySQL 8.4，保存业务数据。
- `app`：基于 GraalVM native-image 构建的 Flyfish 后端，默认启用完整集成版 `flyfish-main`。
- `web`：Nginx 托管前端静态资源，并反向代理后端 API。

## 快速启动

确保本机已安装 Docker 和 Docker Compose v2，然后在项目根目录执行：

```bash
./scripts/docker-native-up.sh
```

首次启动会构建 linux/amd64 native 镜像，耗时较长，取决于机器性能和 Docker 构建缓存。构建完成后访问：

```text
http://127.0.0.1:9999
```

停止服务：

```bash
./scripts/docker-native-down.sh
```

如果要同时删除数据库和上传文件卷：

```bash
docker compose -f deploy/docker/docker-compose.native.yml down -v
```

## 手动 Compose 命令

```bash
docker compose -f deploy/docker/docker-compose.native.yml up --build -d
docker compose -f deploy/docker/docker-compose.native.yml logs -f app
docker compose -f deploy/docker/docker-compose.native.yml ps
```

## 默认端口

| 服务 | 宿主机端口 | 容器端口 |
| --- | --- | --- |
| 前端入口 | `9999` | `web:80` |
| 后端 API | `10081` | `app:10081` |
| MySQL | 不暴露 | `mysql:3306` |

可以通过环境变量覆盖端口：

```bash
FLYFISH_HTTP_PORT=8080 FLYFISH_API_PORT=18081 ./scripts/docker-native-up.sh
```

通过脚本启动时，`OAUTH_CALLBACK_URL`、`EMAIL_MAGIC_LINK_BASE_URL`、`WX_MP_QUICK_LOGIN_BASE_URL` 和支付回调默认会跟随 `FLYFISH_PUBLIC_BASE_URL`。未设置 `FLYFISH_PUBLIC_BASE_URL` 时，脚本会按 `FLYFISH_HTTP_PORT` 推导本机访问地址。

## 常用环境变量

Docker Compose 已提供本地体验默认值。生产或公网体验时至少应覆盖以下变量：

| 变量 | 说明 |
| --- | --- |
| `USER_JWT_SECRET` | 共享登录态 JWT 密钥，必须使用强随机值 |
| `MYSQL_PASSWORD` | MySQL 普通用户密码 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `FLYFISH_PUBLIC_BASE_URL` | 对外访问地址，本地体验默认为 `http://127.0.0.1:9999` |
| `OAUTH_CALLBACK_URL` | OAuth 回调地址 |
| `EMAIL_MAGIC_LINK_BASE_URL` | 邮箱 magic link 的站点地址 |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | 邮箱快速登录发信配置 |
| `WX_MP_*` | 微信公众号配置 |
| `H5ZHIFU_*` | 支付渠道配置 |

示例：

```bash
USER_JWT_SECRET="$(openssl rand -hex 32)" \
MYSQL_PASSWORD="replace-me" \
MYSQL_ROOT_PASSWORD="replace-root-me" \
OAUTH_CALLBACK_URL="https://shop.example.com/oauth/callback" \
EMAIL_MAGIC_LINK_BASE_URL="https://shop.example.com" \
FLYFISH_HTTP_PORT=80 \
./scripts/docker-native-up.sh
```

完整变量模板见项目根目录 [.env.example](../.env.example)。

## Native 构建参数

后端镜像默认使用 Alibaba Cloud Linux 3 + GraalVM 25 构建 linux/amd64 native 二进制。可以覆盖以下参数：

| 变量 | 默认值 |
| --- | --- |
| `FLYFISH_DOCKER_PLATFORM` | `linux/amd64` |
| `GRAAL_IMAGE` | `container-registry.oracle.com/graalvm/native-image:25` |
| `ALINUX_IMAGE` | `registry.cn-hangzhou.aliyuncs.com/alinux/alinux3:latest` |
| `NATIVE_MARCH` | `x86-64-v3` |
| `NATIVE_OPTIMIZATION_LEVEL` | `-O3` |
| `NATIVE_BUILD_PARALLELISM` | `2` |
| `NATIVE_BUILD_XMX` | `8g` |

内存较小的机器可以降低并行度或堆大小：

```bash
NATIVE_BUILD_PARALLELISM=1 NATIVE_BUILD_XMX=6g ./scripts/docker-native-up.sh
```

Apple Silicon 机器会通过 Docker 运行 linux/amd64 构建，首次构建会明显更慢。若要自行探索 arm64 native 构建，需要同时调整 `FLYFISH_DOCKER_PLATFORM` 和 `NATIVE_MARCH`，并自行验证运行期兼容性。

## 访问与验证

前端页面：

```bash
curl -fsS http://127.0.0.1:9999/shop/item-list >/dev/null
```

后端匿名用户接口：

```bash
curl -fsS http://127.0.0.1:9999/portal/users/current
```

能力发现接口：

```bash
curl -fsS http://127.0.0.1:9999/portal/capabilities
```

## 数据持久化

Compose 使用两个命名卷：

- `flyfish-shop-native_flyfish_mysql`：MySQL 数据。
- `flyfish-shop-native_flyfish_uploads`：上传图片等本地文件。

普通停止不会删除数据。需要完全重置体验环境时执行：

```bash
docker compose -f deploy/docker/docker-compose.native.yml down -v
```

## 生产提示

该 Compose 文件主要面向快速体验和小规模自托管。公网生产环境请至少补齐：

- HTTPS 证书和正式域名。
- 强随机 `USER_JWT_SECRET`。
- 独立 MySQL 账号、备份和监控。
- SMTP、OAuth、微信公众号、支付回调等真实第三方配置。
- Nginx/CDN 的访问日志、限流和上传大小策略。

更完整的 native 裸机发布流程见 [native-build-deploy.md](native-build-deploy.md)。
