#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/flyfish-ui-smoke.XXXXXX")"
OUTPUT_DIR="${FLYFISH_UI_SMOKE_OUTPUT_DIR:-$ROOT_DIR/output/playwright/ui-smoke}"
LOWCODE_PORT="${FLYFISH_UI_SMOKE_LOWCODE_PORT:-10081}"
SHOP_PORT="${FLYFISH_UI_SMOKE_SHOP_PORT:-10082}"
FRONTEND_PORT="${FLYFISH_UI_SMOKE_FRONTEND_PORT:-9999}"
FRONTEND_URL="http://127.0.0.1:$FRONTEND_PORT"
PLAYWRIGHT_TIMEOUT="${FLYFISH_UI_SMOKE_TIMEOUT:-30000}"
PLAYWRIGHT_WAIT="${FLYFISH_UI_SMOKE_WAIT:-1000}"
PIDS=()
STARTED_PID=""

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
  if [[ "${FLYFISH_UI_SMOKE_SKIP_PACKAGE:-0}" == "1" ]]; then
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
  local port="$3"
  local expected_codes="$4"
  local log_file="$WORK_DIR/$name.log"
  local body_file="$WORK_DIR/$name-capabilities.json"
  local deadline=$((SECONDS + 90))

  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      tail -n 120 "$log_file" >&2 || true
      fail "$name exited before readiness check passed"
    fi
    local http_code
    http_code="$(curl_get "http://127.0.0.1:$port/portal/capabilities" "$body_file")"
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
  local port="$3"
  local expected_codes="$4"
  local app_dir="$WORK_DIR/$name"
  local log_file="$WORK_DIR/$name.log"
  mkdir -p "$app_dir/db"
  (
    cd "$app_dir"
    exec java -jar "$jar_file" \
      --server.port="$port" \
      --spring.profiles.active=local \
      "--spring.r2dbc.url=r2dbc:h2:file:///./db/$name;MODE=MySQL" \
      >"$log_file" 2>&1
  ) &
  STARTED_PID=$!
  PIDS+=("$STARTED_PID")
  wait_for_capabilities "$name" "$STARTED_PID" "$port" "$expected_codes"
}

stop_pid() {
  local pid="$1"
  local port="$2"
  local label="$3"
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true
  fi
  wait_for_port_free "$port" "$label"
}

screenshot_page() {
  local path="$1"
  local selector="$2"
  local file="$3"
  npx --yes playwright screenshot \
    --timeout "$PLAYWRIGHT_TIMEOUT" \
    --wait-for-selector "$selector" \
    --wait-for-timeout "$PLAYWRIGHT_WAIT" \
    --viewport-size 1440,1000 \
    "$FRONTEND_URL$path" \
    "$OUTPUT_DIR/$file"
}

smoke_main_pages() {
  screenshot_page "/" ".home-wrapper" "main-home.png"
  screenshot_page "/login" ".container" "main-login.png"
  screenshot_page "/model-design" ".model-design" "main-model-design.png"
  screenshot_page "/model-design/select-data-source" ".select-data-source" "main-lowcode-select-data-source.png"
  screenshot_page "/model-design/select-data-table" ".select-data-table" "main-lowcode-select-data-table.png"
  screenshot_page "/model-design/select-model-design" ".select-model-design" "main-lowcode-select-model-design.png"
  screenshot_page "/model-design/select-save-model" ".select-model-design" "main-lowcode-select-save-model-redirect.png"
  screenshot_page "/code-generate" ".screen-card" "main-lowcode-code-generate.png"
  screenshot_page "/online-launch" ".online-page" "main-lowcode-online-launch.png"
  screenshot_page "/integrate-test" ".integration-page" "main-lowcode-integration-test.png"
  screenshot_page "/shop/item-list" ".items-container" "main-shop-item-list.png"
  screenshot_page "/account/orders" ".container" "main-shop-account-orders-login.png"
  screenshot_page "/account/tickets" ".container" "main-shop-account-tickets-login.png"
  screenshot_page "/shop/manage/items" ".container" "main-shop-manage-items-login.png"
}

smoke_lowcode_pages() {
  screenshot_page "/" ".home-wrapper" "lowcode-home.png"
  screenshot_page "/model-design" ".model-design" "lowcode-model-design.png"
  screenshot_page "/model-design/select-data-source" ".select-data-source" "lowcode-select-data-source.png"
  screenshot_page "/model-design/select-data-table" ".select-data-table" "lowcode-select-data-table.png"
  screenshot_page "/model-design/select-model-design" ".select-model-design" "lowcode-select-model-design.png"
  screenshot_page "/model-design/select-save-model" ".select-model-design" "lowcode-select-save-model-redirect.png"
  screenshot_page "/code-generate" ".screen-card" "lowcode-code-generate.png"
  screenshot_page "/online-launch" ".online-page" "lowcode-online-launch.png"
  screenshot_page "/integrate-test" ".integration-page" "lowcode-integration-test.png"
  screenshot_page "/shop/item-list" "text=您访问的页面不存在" "lowcode-shop-notfound.png"
  screenshot_page "/account/orders" "text=您访问的页面不存在" "lowcode-account-orders-notfound.png"
  screenshot_page "/shop/manage/items" "text=您访问的页面不存在" "lowcode-shop-manage-notfound.png"
}

smoke_shop_pages() {
  screenshot_page "/" ".home-wrapper" "shop-home.png"
  screenshot_page "/login" ".container" "shop-login.png"
  screenshot_page "/shop/item-list" ".items-container" "shop-item-list.png"
  screenshot_page "/account/orders" ".container" "shop-account-orders-login.png"
  screenshot_page "/account/tickets" ".container" "shop-account-tickets-login.png"
  screenshot_page "/shop/manage/items" ".container" "shop-manage-items-login.png"
  screenshot_page "/model-design" "text=您访问的页面不存在" "shop-model-design-notfound.png"
  screenshot_page "/code-generate" "text=您访问的页面不存在" "shop-code-generate-notfound.png"
  screenshot_page "/integrate-test" "text=您访问的页面不存在" "shop-integrate-test-notfound.png"
}

run_app_ui_smoke() {
  local name="$1"
  local jar_file="$2"
  local port="$3"
  local expected_codes="$4"
  local smoke_function="$5"
  local pid

  start_app "$name" "$jar_file" "$port" "$expected_codes"
  pid="$STARTED_PID"
  "$smoke_function"
  stop_pid "$pid" "$port" "$name"
  echo "$name UI smoke passed"
}

require_command java
require_command curl
require_command node
require_command npm
require_command npx
require_command lsof

assert_port_free "$LOWCODE_PORT" "lowcode"
assert_port_free "$SHOP_PORT" "shop"
assert_port_free "$FRONTEND_PORT" "frontend"

build_artifacts
assert_file "$LOWCODE_JAR"
assert_file "$SHOP_JAR"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

start_frontend
run_app_ui_smoke "flyfish-lowcode" "$LOWCODE_JAR" "$LOWCODE_PORT" "lowcode" smoke_lowcode_pages
run_app_ui_smoke "flyfish-shop" "$SHOP_JAR" "$SHOP_PORT" "shop" smoke_shop_pages

echo "UI page smoke passed. Screenshots: $OUTPUT_DIR"
