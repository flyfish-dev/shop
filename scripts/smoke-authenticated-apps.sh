#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/flyfish-auth-smoke.XXXXXX")"
PIDS=()
STARTED_PID=""

source "$ROOT_DIR/scripts/lib/auth-smoke.sh"

LOWCODE_JAR="$ROOT_DIR/flyfish-lowcode-app/target/flyfish-lowcode.jar"
SHOP_JAR="$ROOT_DIR/flyfish-shop-app/target/flyfish-shop.jar"
MAIN_JAR="$ROOT_DIR/flyfish-main/target/flyfish-dev.jar"

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

pick_port() {
  node -e "const net=require('net');const server=net.createServer();server.listen(0,'127.0.0.1',()=>{console.log(server.address().port);server.close();});"
}

build_artifacts() {
  if [[ "${FLYFISH_AUTH_SMOKE_SKIP_PACKAGE:-0}" == "1" ]]; then
    return
  fi
  "$ROOT_DIR/mvnw" -q -pl flyfish-main,flyfish-lowcode-app,flyfish-shop-app -am -DskipTests clean package
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
    java -jar "$jar_file" \
      --server.port="$port" \
      --spring.profiles.active=local \
      --spring.r2dbc.username=sa \
      --spring.r2dbc.password= \
      "--spring.r2dbc.url=r2dbc:h2:file:///./db/$name;MODE=MySQL;AUTO_SERVER=TRUE" \
      >"$log_file" 2>&1
  ) &
  STARTED_PID=$!
  PIDS+=("$STARTED_PID")
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
  local headers_file="$3"
  curl -s --max-time 10 -D "$headers_file" -o "$body_file" -w "%{http_code}" "$url" || true
}

curl_get_auth() {
  local url="$1"
  local token="$2"
  local body_file="$3"
  local headers_file="$4"
  curl -s --max-time 10 -D "$headers_file" -H "Authorization: Bearer $token" \
    -o "$body_file" -w "%{http_code}" "$url" || true
}

wait_for_app() {
  local name="$1"
  local pid="$2"
  local base_url="$3"
  local log_file="$WORK_DIR/$name.log"
  local body_file="$WORK_DIR/$name-ready.json"
  local headers_file="$WORK_DIR/$name-ready.headers"
  local deadline=$((SECONDS + 90))

  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      tail -n 120 "$log_file" >&2 || true
      fail "$name exited before readiness check passed"
    fi
    local status
    status="$(curl_get "$base_url/portal/capabilities" "$body_file" "$headers_file")"
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
  console.error(`ERROR: ${label} returned success=${payload.success}, message=${payload.message}`);
  process.exit(1);
}
NODE
}

assert_json_not_success() {
  local body="$1"
  local label="$2"
  BODY="$body" LABEL="$label" node <<'NODE'
const label = process.env.LABEL;
let payload;
try {
  payload = JSON.parse(process.env.BODY || '');
} catch (error) {
  process.exit(0);
}
if (payload.success === true) {
  console.error(`ERROR: ${label} unexpectedly returned success=true`);
  process.exit(1);
}
NODE
}

assert_no_store() {
  local headers_file="$1"
  local label="$2"
  if ! tr '[:upper:]' '[:lower:]' < "$headers_file" | grep -q 'cache-control:.*no-store'; then
    cat "$headers_file" >&2
    fail "$label must return Cache-Control no-store"
  fi
}

assert_success_get() {
  local base_url="$1"
  local path="$2"
  local token="$3"
  local label="$4"
  local body_file="$WORK_DIR/http-body.json"
  local headers_file="$WORK_DIR/http-headers.txt"
  local status
  status="$(curl_get_auth "$base_url$path" "$token" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  assert_json_success "$body" "$label"
  assert_no_store "$headers_file" "$label"
}

assert_workbench_shop_action() {
  local base_url="$1"
  local token="$2"
  local expected="$3"
  local label="$4"
  local body_file="$WORK_DIR/workbench-body.json"
  local headers_file="$WORK_DIR/workbench-headers.txt"
  local status
  status="$(curl_get_auth "$base_url/portal/workbench" "$token" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  BODY="$body" EXPECTED="$expected" LABEL="$label" node <<'NODE'
const payload = JSON.parse(process.env.BODY || '{}');
const label = process.env.LABEL;
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
const expected = process.env.EXPECTED === 'true';
if (payload.success !== true
  || hasShopEntryAction !== expected
  || hasShopManageAction !== expected
  || (expected ? (!validShopSummary || !validShopMetrics)
    : hasShopSummary || leakedShopActions.length > 0 || shopMetrics.length > 0)) {
  console.error(`ERROR: ${label} expected shop extension ${expected}, got summary=${JSON.stringify(result.shop)} actions=${JSON.stringify(actions)} metrics=${JSON.stringify(extensionMetrics)}`);
  process.exit(1);
}
NODE
  assert_no_store "$headers_file" "$label"
}

assert_not_success_get() {
  local base_url="$1"
  local path="$2"
  local token="$3"
  local label="$4"
  local body_file="$WORK_DIR/http-body.json"
  local headers_file="$WORK_DIR/http-headers.txt"
  local status
  if [[ -n "$token" ]]; then
    status="$(curl_get_auth "$base_url$path" "$token" "$body_file" "$headers_file")"
  else
    status="$(curl_get "$base_url$path" "$body_file" "$headers_file")"
  fi
  if [[ "$status" == "000" ]]; then
    fail "$label could not be reached while app was otherwise ready"
  fi
  if [[ "$status" == "200" ]]; then
    assert_json_not_success "$(cat "$body_file")" "$label"
  fi
}

assert_status_get() {
  local base_url="$1"
  local path="$2"
  local expected="$3"
  local label="$4"
  local body_file="$WORK_DIR/http-body.json"
  local headers_file="$WORK_DIR/http-headers.txt"
  local status
  status="$(curl_get "$base_url$path" "$body_file" "$headers_file")"
  if [[ "$status" != "$expected" ]]; then
    cat "$body_file" >&2 || true
    fail "$label expected HTTP $expected, got $status"
  fi
}

assert_current_user() {
  local base_url="$1"
  local token="$2"
  local expected_id="$3"
  local expected_username="$4"
  local label="$5"
  local body_file="$WORK_DIR/current-user.json"
  local headers_file="$WORK_DIR/current-user.headers"
  local status
  status="$(curl_get_auth "$base_url/portal/users/current" "$token" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  BODY="$body" EXPECTED_ID="$expected_id" EXPECTED_USERNAME="$expected_username" LABEL="$label" node <<'NODE'
const label = process.env.LABEL;
let payload;
try {
  payload = JSON.parse(process.env.BODY || '');
} catch (error) {
  console.error(`ERROR: ${label} did not return JSON`);
  process.exit(1);
}
const result = payload.result || {};
if (payload.success !== true || String(result.id) !== process.env.EXPECTED_ID ||
    result.username !== process.env.EXPECTED_USERNAME) {
  console.error(`ERROR: ${label} returned unexpected user ${JSON.stringify(result)}`);
  process.exit(1);
}
const gitea = result.authorizations && result.authorizations.gitea;
if (!gitea || String(gitea.openid) !== process.env.EXPECTED_ID) {
  console.error(`ERROR: ${label} did not return the seeded gitea authorization`);
  process.exit(1);
}
NODE
  assert_no_store "$headers_file" "$label"
}

assert_query_token_current_user() {
  local base_url="$1"
  local token="$2"
  local expected_id="$3"
  local label="$4"
  local body_file="$WORK_DIR/current-user-query.json"
  local headers_file="$WORK_DIR/current-user-query.headers"
  local status
  status="$(curl_get "$base_url/portal/users/current?access_token=$token" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label expected HTTP 200, got $status"
  fi
  BODY="$body" EXPECTED_ID="$expected_id" LABEL="$label" node <<'NODE'
const label = process.env.LABEL;
const payload = JSON.parse(process.env.BODY || '{}');
if (payload.success !== true || String(payload.result && payload.result.id) !== process.env.EXPECTED_ID) {
  console.error(`ERROR: ${label} did not authenticate with access_token query param`);
  process.exit(1);
}
NODE
  assert_no_store "$headers_file" "$label"
}

assert_capabilities() {
  local base_url="$1"
  local expected_codes="$2"
  local label="$3"
  local body_file="$WORK_DIR/capabilities.json"
  local headers_file="$WORK_DIR/capabilities.headers"
  local status
  status="$(curl_get "$base_url/portal/capabilities" "$body_file" "$headers_file")"
  local body
  body="$(cat "$body_file")"
  if [[ "$status" != "200" ]]; then
    echo "$body" >&2
    fail "$label capabilities expected HTTP 200, got $status"
  fi
  BODY="$body" EXPECTED="$expected_codes" LABEL="$label" node <<'NODE'
const expected = process.env.EXPECTED.split(',').filter(Boolean).sort();
const label = process.env.LABEL;
const payload = JSON.parse(process.env.BODY || '{}');
const actual = Array.isArray(payload.result)
  ? payload.result.map(item => item && item.code).filter(Boolean).sort()
  : [];
if (payload.success !== true || JSON.stringify(actual) !== JSON.stringify(expected)) {
  console.error(`ERROR: ${label} capabilities expected ${expected.join(',')}, got ${actual.join(',')}`);
  process.exit(1);
}
NODE
}

seed_users() {
  local h2_jar="$1"
  local db_file="$2"
  auth_smoke_seed_users "$h2_jar" "$db_file"
}

smoke_shared_auth() {
  local base_url="$1"
  local user_token="$2"
  local admin_token="$3"
  local label="$4"
  assert_current_user "$base_url" "$user_token" "$SMOKE_USER_ID" "smoke-user" "$label shared user token"
  assert_current_user "$base_url" "$admin_token" "$SMOKE_ADMIN_ID" "smoke-maintainer" "$label shared maintainer token"
  assert_query_token_current_user "$base_url" "$user_token" "$SMOKE_USER_ID" "$label query access_token"
}

smoke_lowcode_app() {
  local base_url="$1"
  local user_token="$2"
  local admin_token="$3"
  assert_capabilities "$base_url" "lowcode" "lowcode app"
  smoke_shared_auth "$base_url" "$user_token" "$admin_token" "lowcode app"
  assert_success_get "$base_url" "/portal/workbench" "$user_token" "lowcode authenticated workbench"
  assert_workbench_shop_action "$base_url" "$user_token" "false" "lowcode workbench excludes shop action"
  assert_not_success_get "$base_url" "/shops/orders/mine" "$user_token" "shop orders endpoint in lowcode app"
  assert_not_success_get "$base_url" "/portal/customer-service/summary" "$user_token" "customer service endpoint in lowcode app"
}

smoke_shop_app() {
  local base_url="$1"
  local user_token="$2"
  local admin_token="$3"
  assert_capabilities "$base_url" "shop" "shop app"
  smoke_shared_auth "$base_url" "$user_token" "$admin_token" "shop app"
  assert_not_success_get "$base_url" "/shops/orders/mine" "" "shop orders require login"
  assert_success_get "$base_url" "/shops/orders/mine" "$user_token" "shop authenticated orders"
  assert_success_get "$base_url" "/portal/tickets" "$user_token" "shop authenticated tickets"
  assert_success_get "$base_url" "/portal/customer-service/summary" "$user_token" "shop authenticated customer service summary"
  assert_status_get "$base_url" "/shops/managements/users" "401" "shop management requires token"
  assert_not_success_get "$base_url" "/shops/managements/users" "$user_token" "regular user cannot manage shop"
  assert_success_get "$base_url" "/shops/managements/users" "$admin_token" "shop maintainer can list users"
  assert_not_success_get "$base_url" "/portal/workbench" "$user_token" "lowcode workbench endpoint in shop app"
}

smoke_main_app() {
  local base_url="$1"
  local user_token="$2"
  local admin_token="$3"
  assert_capabilities "$base_url" "lowcode,shop" "main app"
  smoke_shared_auth "$base_url" "$user_token" "$admin_token" "main app"
  assert_success_get "$base_url" "/portal/workbench" "$user_token" "main authenticated workbench"
  assert_workbench_shop_action "$base_url" "$user_token" "true" "main workbench includes shop action"
  assert_success_get "$base_url" "/shops/orders/mine" "$user_token" "main authenticated shop orders"
  assert_success_get "$base_url" "/portal/tickets" "$user_token" "main authenticated tickets"
  assert_success_get "$base_url" "/portal/customer-service/summary" "$user_token" "main authenticated customer service summary"
  assert_status_get "$base_url" "/shops/managements/users" "401" "main shop management requires token"
  assert_not_success_get "$base_url" "/shops/managements/users" "$user_token" "main regular user cannot manage shop"
  assert_success_get "$base_url" "/shops/managements/users" "$admin_token" "main maintainer can list users"
}

run_app_smoke() {
  local name="$1"
  local jar_file="$2"
  local smoke_function="$3"
  local h2_jar="$4"
  local user_token="$5"
  local admin_token="$6"
  local port
  port="$(pick_port)"
  local pid
  start_app "$name" "$jar_file" "$port"
  pid="$STARTED_PID"
  local base_url="http://127.0.0.1:$port"
  wait_for_app "$name" "$pid" "$base_url"
  seed_users "$h2_jar" "$WORK_DIR/$name/db/$name"
  "$smoke_function" "$base_url" "$user_token" "$admin_token"
  stop_app "$pid"
  echo "$name authenticated smoke passed on $base_url"
}

require_command java
require_command javac
require_command curl
require_command node
require_command find

build_artifacts
assert_file "$LOWCODE_JAR"
assert_file "$SHOP_JAR"
assert_file "$MAIN_JAR"

H2_JAR="$(auth_smoke_find_h2_jar)"
auth_smoke_compile_token_helper
USER_TOKEN="$(auth_smoke_create_token "$SMOKE_USER_ID")"
ADMIN_TOKEN="$(auth_smoke_create_token "$SMOKE_ADMIN_ID")"

run_app_smoke "flyfish-lowcode" "$LOWCODE_JAR" smoke_lowcode_app "$H2_JAR" "$USER_TOKEN" "$ADMIN_TOKEN"
run_app_smoke "flyfish-shop" "$SHOP_JAR" smoke_shop_app "$H2_JAR" "$USER_TOKEN" "$ADMIN_TOKEN"
run_app_smoke "flyfish-main" "$MAIN_JAR" smoke_main_app "$H2_JAR" "$USER_TOKEN" "$ADMIN_TOKEN"

echo "Authenticated app runtime smoke passed."
