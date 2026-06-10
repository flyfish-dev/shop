#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER="${FLYFISH_DEPLOY_SERVER:-root@your-server.example.com}"
BASE_DIR="${FLYFISH_DEPLOY_BASE_DIR:-/opt/flyfish-dev}"
COMMIT="$(git -C "$ROOT_DIR" rev-parse --short HEAD)"
STAMP="$(date +%Y%m%d%H%M%S)"
RELEASE="${FLYFISH_DEPLOY_RELEASE:-$BASE_DIR/releases/${STAMP}-split-native-${COMMIT}}"

AUTH_TARGET="$ROOT_DIR/flyfish-auth/flyfish-auth-app/target"
LOWCODE_TARGET="$ROOT_DIR/flyfish-lowcode/flyfish-lowcode-app/target"
SHOP_TARGET="$ROOT_DIR/flyfish-shop/flyfish-shop-app/target"
AUTH_BIN="$AUTH_TARGET/flyfish-auth"
LOWCODE_BIN="$LOWCODE_TARGET/flyfish-lowcode"
SHOP_BIN="$SHOP_TARGET/flyfish-shop"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "missing file: $1"
}

upload_native_app() {
  local name="$1"
  local target="$2"
  local binary="$3"
  local remote_dir="$RELEASE/$name"

  require_file "$binary"
  ssh "$SERVER" "mkdir -p '$remote_dir'"
  ssh "$SERVER" "
    if [ -d '$BASE_DIR/app/${name}-native' ]; then
      cp -a '$BASE_DIR/app/${name}-native/.' '$remote_dir/'
    fi
  "
  rsync -az --partial --inplace --progress "$binary" "$SERVER:$remote_dir/"
  if compgen -G "$target/lib*.so" >/dev/null; then
    rsync -az --partial --inplace --progress "$target"/lib*.so "$SERVER:$remote_dir/"
  fi
}

upload_frontend() {
  require_file "$ROOT_DIR/web/dist/index.html"
  rsync -az --partial --delete --progress "$ROOT_DIR/web/dist/" "$SERVER:$BASE_DIR/web/"
}

install_runtime_files() {
  rsync -az --partial "$ROOT_DIR/deploy/systemd/flyfish-auth.service" "$SERVER:/etc/systemd/system/flyfish-auth.service"
  rsync -az --partial "$ROOT_DIR/deploy/systemd/flyfish-lowcode.service" "$SERVER:/etc/systemd/system/flyfish-lowcode.service"
  rsync -az --partial "$ROOT_DIR/deploy/systemd/flyfish-shop.service" "$SERVER:/etc/systemd/system/flyfish-shop.service"
  rsync -az --partial "$ROOT_DIR/deploy/nginx/nginx-split-native-locations.conf" \
    "$SERVER:$BASE_DIR/config/nginx-split-native-locations.conf"
  rsync -az --partial "$ROOT_DIR/deploy/nginx/flyfish-dev.split-native.conf" \
    "$SERVER:/www/server/panel/vhost/nginx/flyfish-dev.conf"
}

switch_services() {
  ssh "$SERVER" "
    set -e
    mkdir -p '$BASE_DIR/app' '$BASE_DIR/logs'
    ln -sfn '$RELEASE/auth' '$BASE_DIR/app/auth-native'
    ln -sfn '$RELEASE/lowcode' '$BASE_DIR/app/lowcode-native'
    ln -sfn '$RELEASE/shop' '$BASE_DIR/app/shop-native'
    chown -R flyfish:flyfish '$RELEASE' '$BASE_DIR/app'
    chmod 755 '$RELEASE/auth/flyfish-auth'
    chmod 755 '$RELEASE/lowcode/flyfish-lowcode' '$RELEASE/shop/flyfish-shop'
    find '$RELEASE' -name 'lib*.so' -exec chmod 644 {} +
    NGINX_BIN=\$(command -v nginx || true)
    if [ -z \"\$NGINX_BIN\" ]; then
      NGINX_BIN=/www/server/nginx/sbin/nginx
    fi
    if [ ! -x \"\$NGINX_BIN\" ]; then
      echo \"找不到可执行的 nginx：\$NGINX_BIN\" >&2
      exit 1
    fi
    systemctl daemon-reload
    \"\$NGINX_BIN\" -t
    systemctl enable flyfish-auth flyfish-lowcode flyfish-shop
    systemctl restart flyfish-auth
    systemctl restart flyfish-lowcode
    systemctl restart flyfish-shop
    systemctl disable --now flyfish-dev.service 2>/dev/null || true
    \"\$NGINX_BIN\" -s reload
  "
}

verify_remote() {
  ssh "$SERVER" "
    set -e
    for i in \$(seq 1 45); do
      if systemctl is-active --quiet flyfish-lowcode &&
         systemctl is-active --quiet flyfish-auth &&
         systemctl is-active --quiet flyfish-shop &&
         curl -fsS http://127.0.0.1:10080/portal/users/current >/tmp/flyfish-auth-current-user.json &&
         curl -fsS http://127.0.0.1:10081/portal/capabilities >/tmp/flyfish-lowcode-capabilities.json &&
         curl -fsS http://127.0.0.1:10082/portal/capabilities >/tmp/flyfish-shop-capabilities.json; then
        break
      fi
      if [ \"\$i\" -eq 45 ]; then
        systemctl --no-pager --full status flyfish-auth flyfish-lowcode flyfish-shop || true
        exit 1
      fi
      sleep 2
    done
    curl -fsS http://127.0.0.1:10080/portal/users/current >/tmp/flyfish-auth-current-user.json
    curl -fsS http://127.0.0.1:10081/portal/capabilities >/tmp/flyfish-lowcode-capabilities.json
    curl -fsS http://127.0.0.1:10082/portal/capabilities >/tmp/flyfish-shop-capabilities.json
    curl -fsS 'http://127.0.0.1:10082/shops/items?page=1&size=3' >/tmp/flyfish-shop-items.json
    curl -fsS http://127.0.0.1:10080/portal/users/current >/tmp/flyfish-current-user.json
    curl -fsS https://api.example.com/__lowcode/portal/capabilities >/tmp/flyfish-api-lowcode.json
    curl -fsS https://api.example.com/__shop/portal/capabilities >/tmp/flyfish-api-shop.json
    curl -fsS 'https://api.example.com/shops/items?page=1&size=3' >/tmp/flyfish-api-shop-items.json
    curl -fsS https://shop.example.com/shop/item-list >/tmp/flyfish-dev-shop.html
  "
}

echo "Release: $RELEASE"
upload_native_app auth "$AUTH_TARGET" "$AUTH_BIN"
upload_native_app lowcode "$LOWCODE_TARGET" "$LOWCODE_BIN"
upload_native_app shop "$SHOP_TARGET" "$SHOP_BIN"
upload_frontend
install_runtime_files
switch_services
verify_remote

echo "Split native deployment finished: $RELEASE"
