#!/bin/sh

set -eu

# 生产环境是 Alibaba Cloud Linux 3，glibc 为 2.32。
# 本脚本固定使用 alinux3 + GraalVM 25 的 linux/amd64 容器构建，避免在 macOS arm64
# 直接产出错误架构，或在高版本 glibc 镜像中产出生产机无法加载的二进制。

BUILDER_IMAGE="${BUILDER_IMAGE:-flyfish-native-builder:alinux3-graal2503}"
GRAAL_IMAGE="${GRAAL_IMAGE:-container-registry.oracle.com/graalvm/native-image:25}"
ALINUX_IMAGE="${ALINUX_IMAGE:-registry.cn-hangzhou.aliyuncs.com/alinux/alinux3:latest}"
NATIVE_MARCH="${NATIVE_MARCH:-x86-64-v3}"
NATIVE_OPTIMIZATION_LEVEL="${NATIVE_OPTIMIZATION_LEVEL:--O3}"
NATIVE_BUILD_PARALLELISM="${NATIVE_BUILD_PARALLELISM:-2}"
NATIVE_BUILD_XMX="${NATIVE_BUILD_XMX:-8g}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker 未安装，无法构建 linux/amd64 native 产物。" >&2
  exit 1
fi

docker info >/dev/null

rm -f flyfish-main/target/flyfish-main flyfish-main/target/lib*.so

docker build --platform linux/amd64 -t "${BUILDER_IMAGE}" - <<EOF
FROM ${GRAAL_IMAGE} AS graal
FROM ${ALINUX_IMAGE}
RUN dnf install -y gcc gcc-c++ glibc-devel zlib-devel findutils tar gzip which make && dnf clean all
COPY --from=graal /usr/lib64/graalvm/graalvm-java25 /opt/graalvm
ENV JAVA_HOME=/opt/graalvm
ENV PATH=/opt/graalvm/bin:/opt/graalvm/lib/svm/bin:\${PATH}
RUN java -version && native-image --version && ldd --version | head -1 && gcc --version | head -1
EOF

docker run --platform linux/amd64 --rm \
  -v "$PWD":/workspace \
  -v "$HOME/.m2":/root/.m2 \
  -w /workspace \
  "${BUILDER_IMAGE}" \
  bash -lc "
    ./mvnw -q -N -DskipTests install &&
    ./mvnw -q -DskipTests -pl flyfish-ddl clean install &&
    ./mvnw -q -DskipTests -Pnative,!local \
      -Dnative.build.parallelism=${NATIVE_BUILD_PARALLELISM} \
      -Dnative.build.xmx=${NATIVE_BUILD_XMX} \
      -Dnative.optimization.level=${NATIVE_OPTIMIZATION_LEVEL} \
      -Dnative.march=${NATIVE_MARCH} \
      -pl flyfish-main native:compile
  "

file flyfish-main/target/flyfish-main
ls -lh flyfish-main/target/flyfish-main flyfish-main/target/lib*.so
