#!/usr/bin/env bash

set -euo pipefail

# 生产环境是 Alibaba Cloud Linux 3，glibc 为 2.32。
# 本脚本固定使用 alinux3 + GraalVM 25 的 linux/amd64 容器构建，避免在 macOS arm64
# 直接产出错误架构，或在高版本 glibc 镜像中产出生产机无法加载的二进制。
#
# 默认构建生产拆分后的三个服务 native app：
#   FLYFISH_NATIVE_APPS=auth,lowcode,shop ./build.sh
# 如只改小铺，可执行：
#   FLYFISH_NATIVE_APPS=shop ./build.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILDER_IMAGE="${BUILDER_IMAGE:-flyfish-native-builder:alinux3-graal2503}"
GRAAL_IMAGE="${GRAAL_IMAGE:-container-registry.oracle.com/graalvm/native-image:25}"
ALINUX_IMAGE="${ALINUX_IMAGE:-registry.cn-hangzhou.aliyuncs.com/alinux/alinux3:latest}"
NATIVE_MARCH="${NATIVE_MARCH:-x86-64-v3}"
NATIVE_OPTIMIZATION_LEVEL="${NATIVE_OPTIMIZATION_LEVEL:--O3}"
NATIVE_BUILD_PARALLELISM="${NATIVE_BUILD_PARALLELISM:-2}"
NATIVE_BUILD_XMX="${NATIVE_BUILD_XMX:-8g}"
FLYFISH_NATIVE_APPS="${FLYFISH_NATIVE_APPS:-auth,lowcode,shop}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker 未安装，无法构建 linux/amd64 native 产物。" >&2
  exit 1
fi

docker info >/dev/null

module_for_app() {
  case "$1" in
    auth) echo "flyfish-auth/flyfish-auth-app" ;;
    lowcode) echo "flyfish-lowcode/flyfish-lowcode-app" ;;
    shop) echo "flyfish-shop/flyfish-shop-app" ;;
    *)
      echo "未知 native app：$1。可选值：auth, lowcode, shop。" >&2
      exit 1
      ;;
  esac
}

binary_for_app() {
  case "$1" in
    auth) echo "flyfish-auth" ;;
    lowcode) echo "flyfish-lowcode" ;;
    shop) echo "flyfish-shop" ;;
  esac
}

target_for_app() {
  local module
  module="$(module_for_app "$1")"
  echo "$ROOT_DIR/$module/target"
}

rm_native_artifacts() {
  local app="$1"
  local target
  target="$(target_for_app "$app")"
  rm -f "$target/$(binary_for_app "$app")" "$target"/lib*.so
}

IFS=',' read -r -a apps <<<"$FLYFISH_NATIVE_APPS"
for app in "${apps[@]}"; do
  app="${app//[[:space:]]/}"
  [[ -n "$app" ]] || continue
  module_for_app "$app" >/dev/null
  rm_native_artifacts "$app"
done

docker build --platform linux/amd64 -t "${BUILDER_IMAGE}" - <<EOF
FROM ${GRAAL_IMAGE} AS graal
FROM ${ALINUX_IMAGE}
RUN dnf install -y gcc gcc-c++ glibc-devel zlib-devel findutils tar gzip which make binutils && dnf clean all
COPY --from=graal /usr/lib64/graalvm/graalvm-java25 /opt/graalvm
ENV JAVA_HOME=/opt/graalvm
ENV PATH=/opt/graalvm/bin:/opt/graalvm/lib/svm/bin:\${PATH}
RUN java -version && native-image --version && ldd --version | head -1 && gcc --version | head -1
EOF

app_modules=""
for app in "${apps[@]}"; do
  app="${app//[[:space:]]/}"
  [[ -n "$app" ]] || continue
  module="$(module_for_app "$app")"
  if [[ -z "$app_modules" ]]; then
    app_modules="$module"
  else
    app_modules="$app_modules,$module"
  fi
done

docker run --platform linux/amd64 --rm \
  -v "$ROOT_DIR":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  "${BUILDER_IMAGE}" \
  bash -lc "
    ./mvnw -q -N -DskipTests install &&
    ./mvnw -q -DskipTests -Pnative,!local -pl ${app_modules} -am install &&
    ./mvnw -q -DskipTests -Pnative,!local \
      -Dnative.build.parallelism=${NATIVE_BUILD_PARALLELISM} \
      -Dnative.build.xmx=${NATIVE_BUILD_XMX} \
      -Dnative.optimization.level=${NATIVE_OPTIMIZATION_LEVEL} \
      -Dnative.march=${NATIVE_MARCH} \
      -pl ${app_modules} native:compile
  "

for app in "${apps[@]}"; do
  app="${app//[[:space:]]/}"
  [[ -n "$app" ]] || continue
  target="$(target_for_app "$app")"
  binary="$target/$(binary_for_app "$app")"
  file "$binary"
  if compgen -G "$target/lib*.so" >/dev/null; then
    ls -lh "$binary" "$target"/lib*.so
  else
    ls -lh "$binary"
  fi
done
