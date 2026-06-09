# Native 构建与生产上线记录

本文记录 Flyfish Dev 后端 native 二进制的标准构建、验证、上线和回滚流程。目标是避免重复踩坑：生产机不负责编译，必须在本地使用与生产 glibc 兼容的 linux/amd64 容器构建好后再上传。

## 2026-06-08 构建记录

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
2. 在容器内安装本项目父 POM 和 `flyfish-ddl`。
3. 使用 `-Pnative,!local native:compile` 构建 `flyfish-main` 的 native 产物。

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

构建成功后必须存在这些文件：

```bash
flyfish-main/target/flyfish-main
flyfish-main/target/libawt.so
flyfish-main/target/libawt_headless.so
flyfish-main/target/libawt_xawt.so
flyfish-main/target/libjava.so
flyfish-main/target/libjvm.so
flyfish-main/target/libmanagement_ext.so
```

GraalVM 25 会按实际可达代码生成配套 JDK native 库，上线时必须以构建日志
`Build artifacts` 中列出的 `lib*.so` 为准，和主二进制一起上传。脚本会在构建前清理旧
`flyfish-main/target/lib*.so`，避免把上一次遗留的库误认为本次产物。

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

构建完成后先确认架构：

```bash
file flyfish-main/target/flyfish-main
```

期望输出包含：

```text
ELF 64-bit LSB executable, x86-64
```

确认 glibc 版本需求不能高于生产机：

```bash
docker run --platform linux/amd64 --rm \
  -v "$PWD":/workspace \
  flyfish-native-builder:alinux3-graal2503 \
  bash -lc 'readelf --version-info /workspace/flyfish-main/target/flyfish-main | grep -o "GLIBC_[0-9.]*" | sort -Vu | tail -30'
```

生产机是 glibc `2.32`，因此最高版本应为 `GLIBC_2.32` 或更低。

确认 Spring AOT initializer 已进入二进制：

```bash
docker run --platform linux/amd64 --rm \
  -v "$PWD":/workspace \
  flyfish-native-builder:alinux3-graal2503 \
  bash -lc 'strings /workspace/flyfish-main/target/flyfish-main | grep -F "FlyfishDevApplication__ApplicationContextInitializer" | head'
```

如果没有输出，不要上线。

## 上传 release

示例以当前提交短 hash 为 release 名的一部分：

```bash
commit="$(git rev-parse --short HEAD)"
release="/opt/flyfish-dev/releases/$(date +%Y%m%d%H%M)-native-${commit}-graal25"

ssh root@your-server.example.com "mkdir -p ${release}/native"

rsync -av \
  flyfish-main/target/flyfish-main \
  flyfish-main/target/lib*.so \
  root@your-server.example.com:${release}/native/

ssh root@your-server.example.com "
  cd ${release}/native &&
  mv -f flyfish-main flyfish-dev &&
  chmod 755 flyfish-dev libjava.so libjvm.so &&
  chmod 644 libawt*.so libfontmanager.so libmanagement_ext.so &&
  chown -R flyfish:flyfish . &&
  file flyfish-dev &&
  sha256sum flyfish-dev &&
  ldd flyfish-dev
"
```

`ldd flyfish-dev` 必须能正常解析，不能出现 `GLIBC_x.xx not found`。

## 临时端口冒烟

先使用临时 systemd unit 在 `10082` 端口启动，不要直接切主服务：

```bash
release="/opt/flyfish-dev/releases/替换为本次release"
unit="flyfish-native-smoke-$(date +%H%M)"

ssh root@your-server.example.com "
  systemctl stop ${unit}.service 2>/dev/null || true
  systemd-run --unit=${unit} \
    --property=User=flyfish \
    --property=Group=flyfish \
    --property=WorkingDirectory=/opt/flyfish-dev \
    --property=EnvironmentFile=/opt/flyfish-dev/config/flyfish-dev.env \
    --property=Environment=LD_LIBRARY_PATH=${release}/native \
    --property=Environment=TZ=Asia/Shanghai \
    --collect \
    ${release}/native/flyfish-dev --server.port=10082
"
```

冒烟接口：

```bash
ssh root@your-server.example.com 'curl -fsS http://127.0.0.1:10082/portal/users/current'
ssh root@your-server.example.com 'curl -fsS "http://127.0.0.1:10082/shops/items?page=1&size=3"'
```

通过后停止临时 unit：

```bash
ssh root@your-server.example.com "systemctl stop ${unit}.service"
```

如果失败，查看日志：

```bash
ssh root@your-server.example.com "journalctl -u ${unit} --no-pager -l | tail -200"
```

## 切换生产服务

冒烟通过后再切换主服务：

```bash
release="/opt/flyfish-dev/releases/替换为本次release"

ssh root@your-server.example.com "
  ln -sfn ${release}/native /opt/flyfish-dev/app/native
  rm -f /etc/systemd/system/flyfish-dev.service.d/override.conf
  rmdir /etc/systemd/system/flyfish-dev.service.d 2>/dev/null || true
  systemctl daemon-reload
  systemctl restart flyfish-dev
"
```

生产服务文件应直接执行 native：

```ini
ExecStart=/opt/flyfish-dev/app/native/flyfish-dev --server.port=10081
Environment=LD_LIBRARY_PATH=/opt/flyfish-dev/app/native
```

切换后验证：

```bash
ssh root@your-server.example.com '
  systemctl show flyfish-dev -p ActiveState -p SubState -p MainPID --no-pager
  ps -o pid,comm,rss,args -p $(systemctl show -p MainPID --value flyfish-dev)
  curl -fsS http://127.0.0.1:10081/portal/users/current
  curl -fsS "http://127.0.0.1:10081/shops/items?page=1&size=3" | head -c 1000
'

curl -k -sS -D - https://api.example.com/portal/users/current
curl -k -sS -D - 'https://api.example.com/shops/items?page=1&size=3'
curl -k -sS -D - https://shop.example.com/shop/item-list
```

`ps` 中进程名应为 `flyfish-dev`，不是 `java`。

## 回滚

优先回滚到上一个 native release：

```bash
previous="/opt/flyfish-dev/releases/替换为上一个可用native/native"

ssh root@your-server.example.com "
  ln -sfn ${previous} /opt/flyfish-dev/app/native
  systemctl restart flyfish-dev
"
```

如果需要临时回滚到 jar：

```bash
ssh root@your-server.example.com "
  mkdir -p /etc/systemd/system/flyfish-dev.service.d
  cat >/etc/systemd/system/flyfish-dev.service.d/override.conf <<'EOF'
[Service]
ExecStart=
ExecStart=/usr/bin/java -jar /opt/flyfish-dev/app/flyfish-dev.jar --server.port=10081
EOF
  systemctl daemon-reload
  systemctl restart flyfish-dev
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
