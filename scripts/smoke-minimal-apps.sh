#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/flyfish-smoke.XXXXXX")"
PIDS=()
STARTED_PID=""

LOWCODE_JAR="$ROOT_DIR/flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode.jar"
SHOP_JAR="$ROOT_DIR/flyfish-shop/flyfish-shop-app/target/flyfish-shop.jar"
AUTH_JAR="$ROOT_DIR/flyfish-auth/flyfish-auth-app/target/flyfish-auth.jar"
AUTH_BASE_URL=""

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

pick_port() {
  node -e "const net=require('net');const server=net.createServer();server.listen(0,'127.0.0.1',()=>{console.log(server.address().port);server.close();});"
}

build_artifacts() {
  if [[ "${FLYFISH_SMOKE_SKIP_PACKAGE:-0}" == "1" ]]; then
    return
  fi
  "$ROOT_DIR/mvnw" -q -pl flyfish-auth/flyfish-auth-app,flyfish-lowcode/flyfish-lowcode-app,flyfish-shop/flyfish-shop-app -am -DskipTests clean package
}

assert_file() {
  [[ -f "$1" ]] || fail "missing artifact: $1"
}

start_app() {
  local name="$1"
  local jar_file="$2"
  local port="$3"
  local app_dir="$WORK_DIR/$name"
  local log_file="$WORK_DIR/$name.log"
  mkdir -p "$app_dir/db"
  (
    cd "$app_dir"
    if [[ -n "$AUTH_BASE_URL" ]]; then
      FLYFISH_AUTH_BASE_URL="$AUTH_BASE_URL" java -jar "$jar_file" \
        --server.port="$port" \
        --spring.profiles.active=local \
        "--spring.r2dbc.url=r2dbc:h2:file:///./db/$name;MODE=MySQL" \
        >"$log_file" 2>&1
    else
      java -jar "$jar_file" \
        --server.port="$port" \
        --spring.profiles.active=local \
        "--spring.r2dbc.url=r2dbc:h2:file:///./db/$name;MODE=MySQL" \
        >"$log_file" 2>&1
    fi
  ) &
  STARTED_PID=$!
  PIDS+=("$STARTED_PID")
}

wait_for_auth() {
  local name="$1"
  local pid="$2"
  local base_url="$3"
  local log_file="$WORK_DIR/$name.log"
  local body_file="$WORK_DIR/$name-ready.json"
  local deadline=$((SECONDS + 90))

  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      tail -n 120 "$log_file" >&2 || true
      fail "$name exited before readiness check passed"
    fi
    local status
    status="$(curl_get "$base_url/oauth/providers" "$body_file")"
    if [[ "$status" == "200" ]]; then
      return
    fi
    sleep 1
  done

  tail -n 160 "$log_file" >&2 || true
  fail "$name did not become ready in time"
}

stop_app() {
  local pid="$1"
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true
  fi
}

curl_get() {
  local url="$1"
  local body_file="$2"
  curl -s --max-time 10 -o "$body_file" -w "%{http_code}" "$url" || true
}

curl_get_with_headers() {
  local url="$1"
  local body_file="$2"
  local headers_file="$3"
  curl -s --max-time 10 -D "$headers_file" -o "$body_file" -w "%{http_code}" "$url" || true
}

wait_for_app() {
  local name="$1"
  local pid="$2"
  local base_url="$3"
  local log_file="$WORK_DIR/$name.log"
  local body_file="$WORK_DIR/$name-ready.json"
  local deadline=$((SECONDS + 90))

  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      tail -n 120 "$log_file" >&2 || true
      fail "$name exited before readiness check passed"
    fi
    local status
    status="$(curl_get "$base_url/portal/capabilities" "$body_file")"
    if [[ "$status" == "200" ]]; then
      return
    fi
    sleep 1
  done

  tail -n 160 "$log_file" >&2 || true
  fail "$name did not become ready in time"
}

assert_json_success() {
  local body="$1"
  local label="$2"
  BODY="$body" LABEL="$label" node <<'NODE'
const label = process.env.LABEL;
let payload;
try {
  payload = JSON.parse(process.env.BODY || '');
} catch (error) {
  console.error(`ERROR: ${label} did not return JSON`);
  process.exit(1);
}
if (payload.success !== true) {
  console.error(`ERROR: ${label} returned success=${payload.success}`);
  process.exit(1);
}
NODE
}

assert_http_json_success() {
  local base_url="$1"
  local path="$2"
  local label="$3"
  local body_file="$WORK_DIR/http-body.json"
  local status
  status="$(curl_get "$base_url$path" "$body_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  assert_json_success "$body" "$label"
}

assert_http_json_success_no_store() {
  local base_url="$1"
  local path="$2"
  local label="$3"
  local body_file="$WORK_DIR/http-body.json"
  local headers_file="$WORK_DIR/http-headers.txt"
  local status
  status="$(curl_get_with_headers "$base_url$path" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  assert_json_success "$body" "$label"
  if ! tr '[:upper:]' '[:lower:]' < "$headers_file" | grep -q 'cache-control:.*no-store'; then
    cat "$headers_file" >&2
    fail "$label must return Cache-Control no-store"
  fi
}

assert_not_json_success() {
  local base_url="$1"
  local path="$2"
  local label="$3"
  local body_file="$WORK_DIR/http-body.json"
  local status
  status="$(curl_get "$base_url$path" "$body_file")"
  if [[ "$status" == "000" ]]; then
    fail "$label could not be reached while app was otherwise ready"
  fi
  if [[ "$status" == "200" ]]; then
    local body
    body="$(cat "$body_file")"
    BODY="$body" LABEL="$label" node <<'NODE'
const label = process.env.LABEL;
let payload;
try {
  payload = JSON.parse(process.env.BODY || '');
} catch (error) {
  console.error(`ERROR: ${label} returned HTTP 200 with non-JSON content`);
  process.exit(1);
}
if (payload.success === true) {
  console.error(`ERROR: ${label} unexpectedly returned success=true`);
  process.exit(1);
}
NODE
  fi
}

assert_capabilities() {
  local base_url="$1"
  local expected_codes="$2"
  local label="$3"
  local body_file="$WORK_DIR/capabilities.json"
  local status
  status="$(curl_get "$base_url/portal/capabilities" "$body_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label capabilities expected HTTP 200, got $status"
  fi
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

assert_workbench_shop_extension() {
  local base_url="$1"
  local expected="$2"
  local label="$3"
  local body_file="$WORK_DIR/workbench-body.json"
  local headers_file="$WORK_DIR/workbench-headers.txt"
  local status
  status="$(curl_get_with_headers "$base_url/portal/workbench" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  BODY="$body" EXPECTED="$expected" LABEL="$label" node <<'NODE'
const payload = JSON.parse(process.env.BODY || '{}');
const label = process.env.LABEL;
const expected = process.env.EXPECTED === 'true';
const result = payload.result || {};
const actions = Array.isArray(result.actions) ? result.actions : [];
const extensionMetrics = Array.isArray(result.extensionMetrics) ? result.extensionMetrics : [];
const shopEntryAction = actions.find(item => item && item.name === '飞鱼小铺');
const shopManageAction = actions.find(item => item && item.name === '商品管理');
const hasShopEntryAction = Boolean(shopEntryAction && shopEntryAction.path === '/shop/item-list');
const hasShopManageAction = Boolean(shopManageAction && shopManageAction.path === '/shop/manage/items');
const leakedShopActions = actions.filter(item => item
  && (String(item.path || '').startsWith('/shop') || /小铺|商品/.test(String(item.name || ''))));
const shopMetrics = extensionMetrics.filter(item => item && item.capability === 'shop');
const validShopMetrics = ['商品', '订单', '待处理订单']
  .every(label => shopMetrics.some(item => item.label === label && typeof item.value === 'number'));
const hasShopSummary = Object.prototype.hasOwnProperty.call(result, 'shop');
const validShopSummary = hasShopSummary
  && result.shop
  && typeof result.shop.items === 'number'
  && typeof result.shop.orders === 'number'
  && typeof result.shop.pendingOrders === 'number';
if (payload.success !== true
  || hasShopEntryAction !== expected
  || hasShopManageAction !== expected
  || (expected ? (!validShopSummary || !validShopMetrics)
    : hasShopSummary || leakedShopActions.length > 0 || shopMetrics.length > 0)) {
  console.error(`ERROR: ${label} expected shop extension ${expected}, got summary=${JSON.stringify(result.shop)} actions=${JSON.stringify(actions)} metrics=${JSON.stringify(extensionMetrics)}`);
  process.exit(1);
}
NODE
  if ! tr '[:upper:]' '[:lower:]' < "$headers_file" | grep -q 'cache-control:.*no-store'; then
    cat "$headers_file" >&2
    fail "$label must return Cache-Control no-store"
  fi
}

smoke_common_auth() {
  local base_url="$1"
  assert_http_json_success_no_store "$base_url" "/portal/users/current" "shared current user"
  assert_http_json_success_no_store "$base_url" "/oauth/providers" "shared OAuth provider config"
}

smoke_auth_app() {
  local base_url="$1"
  smoke_common_auth "$base_url"
}

smoke_lowcode_app() {
  local base_url="$1"
  assert_capabilities "$base_url" "lowcode" "lowcode app"
  assert_workbench_shop_extension "$base_url" "false" "lowcode workbench"
  assert_http_json_success_no_store "$base_url" "/integrity/sources" "lowcode data sources"
  assert_not_json_success "$base_url" "/portal/users/current" "auth current endpoint in lowcode app"
  assert_not_json_success "$base_url" "/oauth/providers" "OAuth provider endpoint in lowcode app"
  assert_not_json_success "$base_url" "/shops/current" "shop current endpoint in lowcode app"
  assert_not_json_success "$base_url" "/shops/items?page=0&size=10" "shop item list endpoint in lowcode app"
}

smoke_shop_app() {
  local base_url="$1"
  assert_capabilities "$base_url" "shop" "shop app"
  assert_not_json_success "$base_url" "/portal/users/current" "auth current endpoint in shop app"
  assert_not_json_success "$base_url" "/oauth/providers" "OAuth provider endpoint in shop app"
  assert_http_json_success_no_store "$base_url" "/shops/current" "shop current endpoint"
  assert_http_json_success_no_store "$base_url" "/shops/item-groups" "shop item groups"
  assert_http_json_success_no_store "$base_url" "/shops/items?page=0&size=10" "shop item list"
  assert_not_json_success "$base_url" "/portal/workbench" "lowcode workbench endpoint in shop app"
  assert_not_json_success "$base_url" "/integrity/sources" "lowcode data sources endpoint in shop app"
}

smoke_main_app() {
  local base_url="$1"
  assert_capabilities "$base_url" "lowcode,shop" "main app"
  smoke_common_auth "$base_url"
  assert_workbench_shop_extension "$base_url" "true" "main workbench"
  assert_http_json_success_no_store "$base_url" "/integrity/sources" "main lowcode data sources"
  assert_http_json_success_no_store "$base_url" "/shops/current" "main shop current endpoint"
  assert_http_json_success_no_store "$base_url" "/shops/items?page=0&size=10" "main shop item list"
}

run_app_smoke() {
  local name="$1"
  local jar_file="$2"
  local smoke_function="$3"
  local port
  port="$(pick_port)"
  local pid
  start_app "$name" "$jar_file" "$port"
  pid="$STARTED_PID"
  local base_url="http://127.0.0.1:$port"
  wait_for_app "$name" "$pid" "$base_url"
  "$smoke_function" "$base_url"
  stop_app "$pid"
  echo "$name smoke passed on $base_url"
}

build_artifacts
assert_file "$AUTH_JAR"
assert_file "$LOWCODE_JAR"
assert_file "$SHOP_JAR"

auth_port="$(pick_port)"
start_app "flyfish-auth" "$AUTH_JAR" "$auth_port"
auth_pid="$STARTED_PID"
AUTH_BASE_URL="http://127.0.0.1:$auth_port"
wait_for_auth "flyfish-auth" "$auth_pid" "$AUTH_BASE_URL"
smoke_auth_app "$AUTH_BASE_URL"
echo "flyfish-auth smoke passed on $AUTH_BASE_URL"

run_app_smoke "flyfish-lowcode" "$LOWCODE_JAR" smoke_lowcode_app
run_app_smoke "flyfish-shop" "$SHOP_JAR" smoke_shop_app

echo "Minimal app runtime smoke passed."
