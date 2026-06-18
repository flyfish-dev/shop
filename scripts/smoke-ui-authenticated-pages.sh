#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/flyfish-ui-auth-smoke.XXXXXX")"
OUTPUT_DIR="${FLYFISH_UI_AUTH_SMOKE_OUTPUT_DIR:-$ROOT_DIR/output/playwright/ui-auth-smoke}"
BACKEND_PORT=10081
FRONTEND_PORT="${FLYFISH_UI_AUTH_SMOKE_FRONTEND_PORT:-9999}"
FRONTEND_URL="http://127.0.0.1:$FRONTEND_PORT"
PLAYWRIGHT_TIMEOUT="${FLYFISH_UI_AUTH_SMOKE_TIMEOUT:-30000}"
PLAYWRIGHT_WAIT="${FLYFISH_UI_AUTH_SMOKE_WAIT:-1000}"
PIDS=()
STARTED_PID=""

source "$ROOT_DIR/scripts/lib/auth-smoke.sh"

LOWCODE_JAR="$ROOT_DIR/flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode.jar"
SHOP_JAR="$ROOT_DIR/flyfish-shop/flyfish-shop-app/target/flyfish-shop.jar"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

cleanup() {
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      wait "$pid" >/dev/null 2>&1 || true
    fi
  done
  rm -rf "$WORK_DIR"
}

trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

assert_port_free() {
  local port="$1"
  local label="$2"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  [[ -z "$pids" ]] || fail "$label port $port is already in use by PID(s): $pids"
}

wait_for_port_free() {
  local port="$1"
  local label="$2"
  local deadline=$((SECONDS + 20))

  while (( SECONDS < deadline )); do
    if [[ -z "$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)" ]]; then
      return
    fi
    sleep 1
  done

  assert_port_free "$port" "$label"
}

assert_file() {
  [[ -f "$1" ]] || fail "missing artifact: $1"
}

build_artifacts() {
  if [[ "${FLYFISH_UI_AUTH_SMOKE_SKIP_PACKAGE:-0}" == "1" ]]; then
    return
  fi
  "$ROOT_DIR/mvnw" -q -pl flyfish-auth/flyfish-auth-app,flyfish-lowcode/flyfish-lowcode-app,flyfish-shop/flyfish-shop-app -am -DskipTests clean package
}

curl_get() {
  local url="$1"
  local body_file="$2"
  curl -s --max-time 10 -o "$body_file" -w "%{http_code}" "$url" || true
}

assert_capabilities_body() {
  local body="$1"
  local expected_codes="$2"
  local label="$3"
  BODY="$body" EXPECTED="$expected_codes" LABEL="$label" node <<'NODE'
const expected = process.env.EXPECTED.split(',').filter(Boolean).sort();
const label = process.env.LABEL;
let payload;
try {
  payload = JSON.parse(process.env.BODY || '');
} catch (error) {
  console.error(`ERROR: ${label} capabilities did not return JSON`);
  process.exit(1);
}
const actual = Array.isArray(payload.result)
  ? payload.result.map(item => item && item.code).filter(Boolean).sort()
  : [];
if (payload.success !== true || JSON.stringify(actual) !== JSON.stringify(expected)) {
  console.error(`ERROR: ${label} capabilities expected ${expected.join(',')}, got ${actual.join(',')}`);
  process.exit(1);
}
NODE
}

wait_for_capabilities() {
  local name="$1"
  local pid="$2"
  local expected_codes="$3"
  local log_file="$WORK_DIR/$name.log"
  local body_file="$WORK_DIR/$name-capabilities.json"
  local deadline=$((SECONDS + 90))

  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      tail -n 120 "$log_file" >&2 || true
      fail "$name exited before readiness check passed"
    fi
    local http_code
    http_code="$(curl_get "http://127.0.0.1:$BACKEND_PORT/portal/capabilities" "$body_file")"
    if [[ "$http_code" == "200" ]]; then
      assert_capabilities_body "$(cat "$body_file")" "$expected_codes" "$name"
      return
    fi
    sleep 1
  done

  tail -n 160 "$log_file" >&2 || true
  fail "$name did not become ready in time"
}

wait_for_frontend() {
  local body_file="$WORK_DIR/frontend.html"
  local deadline=$((SECONDS + 60))

  while (( SECONDS < deadline )); do
    local http_code
    http_code="$(curl_get "$FRONTEND_URL/" "$body_file")"
    if [[ "$http_code" == "200" ]]; then
      return
    fi
    sleep 1
  done

  tail -n 120 "$WORK_DIR/vite.log" >&2 || true
  fail "frontend did not become ready in time"
}

start_frontend() {
  (
    cd "$ROOT_DIR/web"
    npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT" >"$WORK_DIR/vite.log" 2>&1
  ) &
  STARTED_PID=$!
  PIDS+=("$STARTED_PID")
  wait_for_frontend
}

start_app() {
  local name="$1"
  local jar_file="$2"
  local expected_codes="$3"
  local app_dir="$WORK_DIR/$name"
  local log_file="$WORK_DIR/$name.log"
  mkdir -p "$app_dir/db"
  (
    cd "$app_dir"
    exec java -jar "$jar_file" \
      --server.port="$BACKEND_PORT" \
      --spring.profiles.active=local \
      --spring.r2dbc.username=sa \
      --spring.r2dbc.password= \
      "--spring.r2dbc.url=r2dbc:h2:file:///./db/$name;MODE=MySQL;AUTO_SERVER=TRUE" \
      >"$log_file" 2>&1
  ) &
  STARTED_PID=$!
  PIDS+=("$STARTED_PID")
  wait_for_capabilities "$name" "$STARTED_PID" "$expected_codes"
}

stop_pid() {
  local pid="$1"
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true
  fi
  wait_for_port_free "$BACKEND_PORT" "backend"
}

seed_app_users() {
  local h2_jar="$1"
  local name="$2"
  auth_smoke_seed_users "$h2_jar" "$WORK_DIR/$name/db/$name"
}

screenshot_page() {
  local storage_file="$1"
  local path="$2"
  local selector="$3"
  local file="$4"
  npx --yes playwright screenshot \
    --timeout "$PLAYWRIGHT_TIMEOUT" \
    --load-storage "$storage_file" \
    --wait-for-selector "$selector" \
    --wait-for-timeout "$PLAYWRIGHT_WAIT" \
    --viewport-size 1440,1000 \
    "$FRONTEND_URL$path" \
    "$OUTPUT_DIR/$file"
}

smoke_main_pages() {
  screenshot_page "$USER_STORAGE" "/account/profile" ".profile-page" "main-user-profile.png"
  screenshot_page "$USER_STORAGE" "/account/orders" ".orders-page" "main-user-orders.png"
  screenshot_page "$USER_STORAGE" "/account/tickets" ".tickets-page" "main-user-tickets.png"
  screenshot_page "$USER_STORAGE" "/shop/manage/items" ".shop-auth-state" "main-user-manage-forbidden.png"
  screenshot_page "$ADMIN_STORAGE" "/shop/manage/users" ".user-manage" "main-admin-manage-users.png"
  screenshot_page "$ADMIN_STORAGE" "/shop/manage/items" ".item-manage" "main-admin-manage-items.png"
}

smoke_lowcode_pages() {
  screenshot_page "$USER_STORAGE" "/account/profile" ".profile-page" "lowcode-user-profile.png"
  screenshot_page "$USER_STORAGE" "/account/orders" "text=您访问的页面不存在" "lowcode-user-orders-notfound.png"
  screenshot_page "$ADMIN_STORAGE" "/shop/manage/items" "text=您访问的页面不存在" "lowcode-admin-manage-notfound.png"
}

smoke_shop_pages() {
  screenshot_page "$USER_STORAGE" "/account/profile" ".profile-page" "shop-user-profile.png"
  screenshot_page "$USER_STORAGE" "/account/orders" ".orders-page" "shop-user-orders.png"
  screenshot_page "$USER_STORAGE" "/account/tickets" ".tickets-page" "shop-user-tickets.png"
  screenshot_page "$USER_STORAGE" "/shop/manage/items" ".shop-auth-state" "shop-user-manage-forbidden.png"
  screenshot_page "$ADMIN_STORAGE" "/shop/manage/users" ".user-manage" "shop-admin-manage-users.png"
  screenshot_page "$ADMIN_STORAGE" "/shop/manage/items" ".item-manage" "shop-admin-manage-items.png"
  screenshot_page "$USER_STORAGE" "/model-design" "text=您访问的页面不存在" "shop-user-lowcode-notfound.png"
}

run_app_ui_smoke() {
  local name="$1"
  local jar_file="$2"
  local expected_codes="$3"
  local smoke_function="$4"
  local h2_jar="$5"
  local pid

  start_app "$name" "$jar_file" "$expected_codes"
  pid="$STARTED_PID"
  seed_app_users "$h2_jar" "$name"
  "$smoke_function"
  stop_pid "$pid"
  echo "$name authenticated UI smoke passed"
}

require_command java
require_command javac
require_command curl
require_command node
require_command npm
require_command npx
require_command lsof
require_command find

assert_port_free "$BACKEND_PORT" "backend"
assert_port_free "$FRONTEND_PORT" "frontend"

build_artifacts
assert_file "$LOWCODE_JAR"
assert_file "$SHOP_JAR"

H2_JAR="$(auth_smoke_find_h2_jar)"
auth_smoke_compile_token_helper
USER_TOKEN="$(auth_smoke_create_token "$SMOKE_USER_ID")"
ADMIN_TOKEN="$(auth_smoke_create_token "$SMOKE_ADMIN_ID")"
USER_STORAGE="$WORK_DIR/user-storage.json"
ADMIN_STORAGE="$WORK_DIR/admin-storage.json"
auth_smoke_write_storage_state "$USER_TOKEN" "$FRONTEND_URL" "$USER_STORAGE"
auth_smoke_write_storage_state "$ADMIN_TOKEN" "$FRONTEND_URL" "$ADMIN_STORAGE"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

start_frontend
run_app_ui_smoke "flyfish-lowcode" "$LOWCODE_JAR" "lowcode" smoke_lowcode_pages "$H2_JAR"
run_app_ui_smoke "flyfish-shop" "$SHOP_JAR" "shop" smoke_shop_pages "$H2_JAR"

echo "Authenticated UI page smoke passed. Screenshots: $OUTPUT_DIR"
