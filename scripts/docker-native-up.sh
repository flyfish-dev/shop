#!/bin/sh

set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/deploy/docker/docker-compose.native.yml}"
HTTP_PORT="${FLYFISH_HTTP_PORT:-9999}"
LOCAL_BASE_URL="http://127.0.0.1:${HTTP_PORT}"
PUBLIC_BASE_URL="${FLYFISH_PUBLIC_BASE_URL:-${LOCAL_BASE_URL}}"

export FLYFISH_PUBLIC_BASE_URL="${PUBLIC_BASE_URL}"

if [ -z "${OAUTH_CALLBACK_URL:-}" ]; then
  export OAUTH_CALLBACK_URL="${PUBLIC_BASE_URL}/oauth/callback"
fi

if [ -z "${EMAIL_MAGIC_LINK_BASE_URL:-}" ]; then
  export EMAIL_MAGIC_LINK_BASE_URL="${PUBLIC_BASE_URL}"
fi

if [ -z "${WX_MP_QUICK_LOGIN_BASE_URL:-}" ]; then
  export WX_MP_QUICK_LOGIN_BASE_URL="${PUBLIC_BASE_URL}"
fi

if [ -z "${H5ZHIFU_NOTIFY_URL:-}" ]; then
  export H5ZHIFU_NOTIFY_URL="${PUBLIC_BASE_URL}/shops/payments/h5zhifu/notify"
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker 未安装，无法启动 native 一键体验环境。" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "当前 Docker 未提供 compose 子命令，请安装 Docker Compose v2。" >&2
  exit 1
fi

cd "${ROOT_DIR}"

docker compose -f "${COMPOSE_FILE}" up --build -d

if ! command -v curl >/dev/null 2>&1; then
  echo "服务已启动，访问 ${PUBLIC_BASE_URL}"
  exit 0
fi

echo "等待 Flyfish native 体验环境就绪..."
for i in $(seq 1 90); do
  if curl -fsS "${LOCAL_BASE_URL}/portal/users/current" >/dev/null 2>&1 \
    && curl -fsS "${LOCAL_BASE_URL}/shop/item-list" >/dev/null 2>&1; then
    echo "Flyfish native 体验环境已就绪：${PUBLIC_BASE_URL}"
    exit 0
  fi
  sleep 2
done

echo "容器已启动，但本机访问检查超时。请查看日志："
echo "docker compose -f ${COMPOSE_FILE} logs -f"
exit 1
