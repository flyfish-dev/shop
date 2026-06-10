# Native 构建与生产上线记录

本文记录 Flyfish Dev 后端 native 二进制的标准构建、验证、上线和回滚流程。目标是避免重复踩坑：生产机不负责编译，必须在本地使用与生产 glibc 兼容的 linux/amd64 容器构建好后再上传。

## 2026-06-09 三服务 native app 生产架构

生产环境不再使用全量 `flyfish-main` 作为默认后端，而是拆成三个独立 native app：

- `flyfish-auth/flyfish-auth-app` -> `flyfish-auth`：端口 `10080`，承载用户、OAuth、邮箱登录和微信快捷登录。
- `flyfish-lowcode/flyfish-lowcode-app` -> `flyfish-lowcode`：端口 `10081`，承载低代码工作台、数据建模、在线运行、集成测试和代码生成。
- `flyfish-shop/flyfish-shop-app` -> `flyfish-shop`：端口 `10082`，承载商城、支付、工单、客服、公众号消息网关和 Git 仓库开通。

nginx 负责按路径分发：

- `/portal/users/`、`/oauth/`、`/email/`、`/wx/quick-login`、`/wx/qr-codes` -> auth。
- `/integrity/`、`/portal/workbench`、`/portal/capabilities` -> lowcode。
- `/shops/`、`/portal/customer-service/`、`/portal/tickets`、`/portal/files`、`/wx`、`/images/` -> shop。
- `/__auth/`、`/__lowcode/` 与 `/__shop/` 是前端能力探测和验证专用内部前缀，分别反向代理到对应实例并去掉该前缀。

前端仍是一套 `web/dist`，通过 `/__lowcode/portal/capabilities` 与 `/__shop/portal/capabilities` 聚合能力；认证状态统一走 auth 实例。

### 公共库复用策略

Spring Boot native image 会把 Java 依赖按可达性分析纳入每个可执行文件。生产级 Spring native 应用不建议把 `flyfish-common`、`flyfish-auth` 这类 Java 模块做成单独动态库再由多个应用链接：这会破坏 Spring AOT 生成物与 GraalVM reachability metadata 的闭包假设，也会让发布版本、反射提示和资源提示难以校验。

本项目采用更稳定的复用方式：

- 公共模块和服务子模块以 Maven artifact 进入本地 `.m2` 和 Docker 构建缓存。
- native 编译只针对被选择的 app 模块执行，例如 `FLYFISH_NATIVE_APPS=shop ./build.sh`。
- 公共模块未变更时，只做普通 Java 编译/缓存命中，不会触发另一个业务 app 的 native 编译。
- GraalVM 生成的 JDK 配套 `lib*.so` 按 app 目录随二进制发布，通过各自的 `LD_LIBRARY_PATH` 加载，避免两个 app 的不同构建批次误用彼此的配套库。

### 常用命令

同时构建三个生产 app：

```bash
./build.sh
```

只构建小铺：

```bash
FLYFISH_NATIVE_APPS=shop ./build.sh
```

只构建低代码：

```bash
FLYFISH_NATIVE_APPS=lowcode ./build.sh
```

只构建认证：

```bash
FLYFISH_NATIVE_APPS=auth ./build.sh
```

上线最新三实例 native：

```bash
./scripts/deploy-split-native.sh
```

上线脚本会上传三个 native app、同步前端、安装 systemd/nginx 配置、切换 symlink、重启服务并执行远程冒烟验证。

### 2026-06-09 三服务本地构建验证记录

- 本地回归：`./mvnw test` 通过。
- 架构守卫：`./mvnw -q -pl flyfish-common -Dtest=ModuleBoundaryTest test` 通过。
- 产物边界：`FLYFISH_ARTIFACT_PROFILE=native bash scripts/check-app-artifacts.sh` 通过。
- 构建命令：`./build.sh`。
- 构建耗时：auth `20m 13s`，lowcode `12m 58s`，shop `25m 8s`；macOS arm64 通过 linux/amd64 容器跨架构构建，实际耗时会明显长于 amd64 Linux。
- 认证产物：`flyfish-auth/flyfish-auth-app/target/flyfish-auth`，`ls -lh` 约 `195M`，native-image 报告 `204.38MB`。
- 低代码产物：`flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode`，`ls -lh` 约 `178M`，native-image 报告 `187.14MB`。
- 小铺产物：`flyfish-shop/flyfish-shop-app/target/flyfish-shop`，`ls -lh` 约 `184M`，native-image 报告 `193.30MB`。
- glibc 需求：最高 `GLIBC_2.32`，匹配生产 Alibaba Cloud Linux 3。
- AOT 校验：三个二进制均能找到各自的 `Flyfish*Application__ApplicationContextInitializer`。
- 体积观察：三个 app 总和大于旧单体是 native image 的正常结果；收益在于每次只需对变更 app 做 native 编译，且 lowcode/shop 不再携带 auth 的 OAuth/Pac4j/Thymeleaf 实现闭包。
- 后续瘦身点：auth 的 Jackson 2 databind 来自 `pac4j-core`，Jackson 2 annotations 仍由 `flyfish-ddl` 间接带入；这些不是业务 app 互相污染，但如果后续继续追求极限体积，可以优先替换 Pac4j 登录实现和清理旧 Jackson 注解。

## 2026-06-08 构建记录

以下为历史单体 native 构建记录，仅用于回滚和问题对照；新生产发布优先使用上面的三服务 native app 流程。

- 本地回归：`./mvnw test` 通过，103 个测试全部成功。
- 前端回归：`cd web && npm run build` 通过。
- native 构建命令：`./build.sh`。
- 构建耗时：29m 4s。
- 构建环境：alinux3 + GraalVM 25.0.3，`--platform linux/amd64`，`-O3`，`x86-64-v3`，并行度 2。
- 主二进制：`flyfish-main/target/flyfish-main`，213M，`ELF 64-bit LSB executable, x86-64`。
- glibc 需求：最高 `GLIBC_2.32`，匹配生产 Alibaba Cloud Linux 3。
- native 配套库：`libawt.so`、`libawt_headless.so`、`libawt_xawt.so`、`libjava.so`、`libjvm.so`、`libmanagement_ext.so`。
- 体积观察：GraalVM 输出总镜像 231.79M，其中 code area 128.73M、image heap 94.44M。后续瘦身优先排查重复进入镜像的 Jackson 2.x/3.x、FreeMarker/Thymeleaf 和不必要模板路径。

## 环境约束

- 生产服务器：Alibaba Cloud Linux 3，`x86_64`，glibc `2.32`。
- 本地开发机可能是 macOS arm64，不能直接运行本机 `native-image` 给生产构建，否则会产出 arm64 二进制。
- 当前项目是 Spring Boot 4.x，native 构建要使用 GraalVM 25.x，才能正确消费 Spring Boot 4 的 reachability metadata。
- 生产机内存较小，不适合编译 native。曾在 1.8GB 内存机器上尝试，即使加 swap 也会出现 OOM/exit 137。

## 正确构建方式

上线前先做基础回归：

```bash
./mvnw test

cd web
npm run build
cd -
```

前端构建产物在 `web/dist`。后端 native 构建通过后，再一起发布前端和后端。

优先使用项目根目录脚本：

```bash
./build.sh
```

脚本会做三件事：

1. 构建 linux/amd64 的 alinux3 + GraalVM 25 native 构建镜像。
2. 在容器内安装本项目父 POM。
3. 使用 `-Pnative,!local native:compile` 构建 `FLYFISH_NATIVE_APPS` 指定的 app native 产物，默认 `auth,lowcode,shop`。

`!local` 不能省略。本项目 local profile 会引入 H2 驱动，本地开发和测试需要它；生产 native
使用 MySQL，不应把 H2 驱动和元数据一起打进二进制。

关键默认参数：

```bash
BUILDER_IMAGE=flyfish-native-builder:alinux3-graal2503
GRAAL_IMAGE=container-registry.oracle.com/graalvm/native-image:25
ALINUX_IMAGE=registry.cn-hangzhou.aliyuncs.com/alinux/alinux3:latest
NATIVE_MARCH=x86-64-v3
NATIVE_OPTIMIZATION_LEVEL=-O3
NATIVE_BUILD_PARALLELISM=2
NATIVE_BUILD_XMX=8g
```

构建成功后必须至少存在这些文件：

```bash
flyfish-auth/flyfish-auth-app/target/flyfish-auth
flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode
flyfish-shop/flyfish-shop-app/target/flyfish-shop
```

GraalVM 25 会按实际可达代码生成配套 JDK native 库。若对应 app 的 `target` 下出现
`lib*.so`，上线时必须和该 app 主二进制放在同一个 release 子目录中。脚本会在构建前清理旧
`lib*.so`，避免把上一次遗留的库误认为本次产物。

## 前端静态资源发布

前端由 nginx 直接托管，构建成功后同步 `web/dist`：

```bash
rsync -av --delete web/dist/ root@your-server.example.com:/opt/flyfish-dev/web/
```

发布后验证：

```bash
curl -k -sS -D - https://shop.example.com/shop/item-list -o /tmp/flyfish-shop.html
head -20 /tmp/flyfish-shop.html
```

响应应为 `200`，HTML 中应包含最新构建出的 `/assets/index-*.js` 和 `/assets/index-*.css`。

## 本地校验

构建完成后先确认三个服务二进制都是 linux/amd64：

```bash
file \
  flyfish-auth/flyfish-auth-app/target/flyfish-auth \
  flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode \
  flyfish-shop/flyfish-shop-app/target/flyfish-shop
```

期望输出包含：

```text
ELF 64-bit LSB executable, x86-64
```

确认每个 app 的 glibc 版本需求不能高于生产机：

```bash
docker run --platform linux/amd64 --rm \
  -v "$PWD":/workspace \
  flyfish-native-builder:alinux3-graal2503 \
  bash -lc '
    for app in \
      flyfish-auth/flyfish-auth-app/target/flyfish-auth \
      flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode \
      flyfish-shop/flyfish-shop-app/target/flyfish-shop; do
      echo "== $app =="
      readelf --version-info "/workspace/$app" | grep -o "GLIBC_[0-9.]*" | sort -Vu | tail -10
    done
  '
```

生产机是 glibc `2.32`，因此最高版本应为 `GLIBC_2.32` 或更低。

确认 Spring AOT initializer 已进入各自二进制：

```bash
docker run --platform linux/amd64 --rm \
  -v "$PWD":/workspace \
  flyfish-native-builder:alinux3-graal2503 \
  bash -lc '
    strings /workspace/flyfish-auth/flyfish-auth-app/target/flyfish-auth | grep -F "FlyfishAuthApplication__ApplicationContextInitializer" | head
    strings /workspace/flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode | grep -F "FlyfishLowcodeApplication__ApplicationContextInitializer" | head
    strings /workspace/flyfish-shop/flyfish-shop-app/target/flyfish-shop | grep -F "FlyfishShopApplication__ApplicationContextInitializer" | head
  '
```

如果没有输出，不要上线。

## 上传 release

当前三服务架构统一使用脚本上传、切换和验证，避免手工漏传某个 app 的 `lib*.so`：

```bash
./scripts/deploy-split-native.sh
```

脚本会创建形如 `/opt/flyfish-dev/releases/<时间>-split-native-<提交>` 的 release，并分别上传：

- `auth/flyfish-auth`
- `lowcode/flyfish-lowcode`
- `shop/flyfish-shop`
- 各 app 目录下本次构建生成的 `lib*.so`

## 临时端口冒烟

三服务切换后必须验证内部端口和公网路由。`deploy-split-native.sh` 已内置以下检查：

```bash
ssh root@your-server.example.com 'curl -fsS http://127.0.0.1:10080/portal/users/current'
ssh root@your-server.example.com 'curl -fsS http://127.0.0.1:10081/portal/capabilities'
ssh root@your-server.example.com 'curl -fsS http://127.0.0.1:10082/portal/capabilities'
ssh root@your-server.example.com 'curl -fsS "http://127.0.0.1:10082/shops/items?page=1&size=3"'
curl -k -sS -D - https://api.example.com/__lowcode/portal/capabilities
curl -k -sS -D - https://api.example.com/__shop/portal/capabilities
curl -k -sS -D - 'https://api.example.com/shops/items?page=1&size=3'
curl -k -sS -D - https://shop.example.com/shop/item-list
```

如果失败，先看对应服务日志：

```bash
ssh root@your-server.example.com "journalctl -u flyfish-auth -u flyfish-lowcode -u flyfish-shop --no-pager -l | tail -300"
```

## 切换生产服务

三服务的生产入口由独立 symlink 和 systemd 管理：

```bash
release="/opt/flyfish-dev/releases/替换为本次release"

ssh root@your-server.example.com "
  ln -sfn ${release}/auth /opt/flyfish-dev/app/auth-native
  ln -sfn ${release}/lowcode /opt/flyfish-dev/app/lowcode-native
  ln -sfn ${release}/shop /opt/flyfish-dev/app/shop-native
  systemctl daemon-reload
  systemctl restart flyfish-auth flyfish-lowcode flyfish-shop
"
```

生产服务文件应直接执行 native：

```ini
ExecStart=/opt/flyfish-dev/app/auth-native/flyfish-auth --server.port=10080
ExecStart=/opt/flyfish-dev/app/lowcode-native/flyfish-lowcode --server.port=10081
ExecStart=/opt/flyfish-dev/app/shop-native/flyfish-shop --server.port=10082
```

切换后验证：

```bash
ssh root@your-server.example.com '
  systemctl show flyfish-auth flyfish-lowcode flyfish-shop -p ActiveState -p SubState -p MainPID --no-pager
  ps -o pid,comm,rss,args -p $(systemctl show -p MainPID --value flyfish-auth),$(systemctl show -p MainPID --value flyfish-lowcode),$(systemctl show -p MainPID --value flyfish-shop)
  curl -fsS http://127.0.0.1:10080/portal/users/current
  curl -fsS http://127.0.0.1:10081/portal/capabilities
  curl -fsS "http://127.0.0.1:10082/shops/items?page=1&size=3" | head -c 1000
'
```

`ps` 中进程名应分别为 `flyfish-auth`、`flyfish-lowcode`、`flyfish-shop`，不是 `java`。

## 回滚

优先回滚到上一个三服务 native release：

```bash
previous="/opt/flyfish-dev/releases/替换为上一个可用split-native"

ssh root@your-server.example.com "
  ln -sfn ${previous}/auth /opt/flyfish-dev/app/auth-native
  ln -sfn ${previous}/lowcode /opt/flyfish-dev/app/lowcode-native
  ln -sfn ${previous}/shop /opt/flyfish-dev/app/shop-native
  systemctl restart flyfish-auth flyfish-lowcode flyfish-shop
"
```

如果需要临时回滚到旧单体 jar，必须同步恢复 nginx 路由和 `flyfish-dev.service`，只作为故障应急路径使用：

```bash
ssh root@your-server.example.com "
  systemctl stop flyfish-auth flyfish-lowcode flyfish-shop
  systemctl start flyfish-dev
"
```

## 已验证的失败路径

以下路径不要再尝试：

- 不要在生产机上编译 native：内存不足会 OOM/exit 137，也会影响线上稳定性。
- 不要在 macOS arm64 直接 native 构建生产包：产物架构不对。
- 不要使用 Oracle Linux 10 / glibc 2.39 这类新系统镜像构建生产包：会出现 `GLIBC_2.34 not found` 一类错误。
- 不要用 GraalVM 21 构建当前 Spring Boot 4 项目：会出现 `AotInitializerNotFoundException`，原因是 Spring Boot 4 的 reachability metadata 没被完整纳入。
- 不要用 `native:compile-no-fork` 作为标准构建目标：它容易绕开 Spring Boot AOT 与 native plugin 的集成链路。
- 不要只上传主二进制：GraalVM 25 会生成 `lib*.so` 配套库，必须一起上传并设置 `LD_LIBRARY_PATH`。

## 2026-06-06 趣味昵称与 native 减重上线记录

- 本次上线先执行 `./mvnw test`、`web` 目录下 `npm run build`，随后执行 `./build.sh` 在本地 linux/amd64 容器内完成 native 构建。
- 本次生产库备份文件：`/opt/flyfish-dev/backups/flyfish-dev-db-20260606100223-pre-fun-nickname.sql.gz`。
- 本次 native release：`/opt/flyfish-dev/releases/202606061001-native-cc24b7c-fun-nickname-slim/native`。
- 构建产物：主二进制 `210M`，native-image 报告总文件大小 `219.72MB`；上一次同类构建主二进制约 `232M`，本次减少约 `22M`。
- 减重关键点：`build.sh` 改为 `-Pnative,!local`，确保生产 native 不再把本地 H2 驱动与元数据打进二进制；构建前清理旧 `lib*.so`，避免误上传历史配套库。
- 剩余主要体积来源集中在 JDK/SVM、Spring、FreeMarker、Thymeleaf、Jackson、pac4j 等真实可达依赖；后续如继续减重，应优先评估模板引擎、pac4j 的 Jackson 2 传递依赖和不必要的运行期功能。
- 上线前用 `flyfish-native-smoke-100241.service` 在 `10082` 端口完成冒烟，确认 `/portal/users/current` 与 `/shops/items?page=1&size=3` 均返回 `200`，且烟测服务无 warning/error。
- 正式切换后 `flyfish-dev` 主服务进程为 native 二进制，`api.example.com` 与 `shop.example.com` 均返回 `200`；内置浏览器检查商城页可见飞鱼小铺、推荐商品、工单入口和商品卡片，控制台无错误。

## 2026-06-01 商品仓库参数迁移上线记录

- 本次上线先执行 `./mvnw test`、`web` 目录下 `npm run build`，随后执行 `./build.sh` 在本地 linux/amd64 容器内完成 native 构建。
- 本次生产库备份文件：`/opt/flyfish-dev/backups/20260601110844-pre-gitrepo-param-migration.sql.gz`。
- 本次 native release：`/opt/flyfish-dev/releases/202606011143-native-gitrepo-provider-a82b070-graal25/native`。
- 上线前用 `flyfish-native-smoke-gitrepo.service` 在 `10082` 端口完成冒烟，确认 `/portal/users/current` 可访问，启动期迁移能正确执行。
- 本次迁移重点验证商品仓库参数：所有商品的 `repositoryIds` 都能关联到 `git_repository`，商品参数快照中的 `provider`、`owner`、`repo` 与仓库表一致；`rtsp本地浏览器播放` 已补全为 GitHub `flyfish-dev/rtsp-source`。
- 正式切换后 `flyfish-dev` 主服务进程为 native 二进制，`api.example.com` 与 `shop.example.com` 均返回 `200`，浏览器检查商品列表和商品详情页无新的控制台错误。

## 2026-05-25 成功上线记录

- Git commit：`32e2286`
- 本地构建镜像：`flyfish-native-builder:alinux3-graal2503`
- GraalVM：`25.0.3`
- 构建系统：Alibaba Cloud Linux 3 容器，glibc `2.32`
- native release：`/opt/flyfish-dev/releases/202605250143-native-32e2286-graal25/native`
- 二进制 SHA256：`1e166606026cfbd3e7256a2b22d080f71cc29c31ee77fa0033702c3834ddd844`
- 冒烟验证：
  - `http://127.0.0.1:10082/portal/users/current` 返回 200
  - `http://127.0.0.1:10082/shops/items?page=1&size=3` 返回 200
- 生产验证：
  - `https://api.example.com/portal/users/current` 返回 200
  - `https://api.example.com/shops/items?page=1&size=3` 返回 200
  - `https://shop.example.com/shop/item-list` 返回 200

## 2026-05-31 仓库同步功能 native 上线记录

- Git commit：`700b801`
- 本地构建镜像：`flyfish-native-builder:alinux3-graal2503`
- GraalVM：`25.0.3`
- 构建系统：Alibaba Cloud Linux 3 容器，glibc `2.32`
- native release：`/opt/flyfish-dev/releases/202605312301-native-700b801-graal25/native`
- 二进制 SHA256：`5a09263a53e4caa0a6c5fa6c7619f49899a961aadc0ec9548a0279d89be9392f`
- 构建验证：
  - `./mvnw test`：85 个测试通过
  - `npm run build`：通过
  - `file flyfish-main/target/flyfish-main`：`ELF 64-bit LSB executable, x86-64`
  - `readelf --version-info`：最高 `GLIBC_2.32`
  - `strings`：包含 `FlyfishDevApplication__ApplicationContextInitializer`
- 冒烟验证：
  - `http://127.0.0.1:10082/portal/users/current` 返回 200
  - `http://127.0.0.1:10082/shops/items?page=1&size=3` 返回 200
  - `http://127.0.0.1:10082/shops/managements/git/repository-options` 未登录返回 401
- 生产验证：
  - 主服务符号链接指向 `/opt/flyfish-dev/releases/202605312301-native-700b801-graal25/native`
  - `systemctl status flyfish-dev`：`active (running)`
  - `ps` 进程名：`flyfish-dev`，RSS 约 `69MB`
  - `https://api.example.com/portal/users/current` 返回 200
  - `https://api.example.com/shops/items?page=1&size=3` 返回 200
  - `https://api.example.com/shops/managements/git/repositories/sync` 未登录返回 401
  - `https://shop.example.com/shop/manage/repositories` 返回 200
