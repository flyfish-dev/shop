#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

LOWCODE_JAR="$ROOT_DIR/flyfish-lowcode-app/target/flyfish-lowcode.jar"
SHOP_JAR="$ROOT_DIR/flyfish-shop-app/target/flyfish-shop.jar"
MAIN_JAR="$ROOT_DIR/flyfish-main/target/flyfish-dev.jar"
PORTAL_API_JAR="$ROOT_DIR/flyfish-portal-api/target/flyfish-portal-api-0.0.2-SNAPSHOT.jar"
COMMON_JAR="$ROOT_DIR/flyfish-common/target/flyfish-common-0.0.2-SNAPSHOT.jar"
PLATFORM_JAR="$ROOT_DIR/flyfish-platform/target/flyfish-platform-0.0.2-SNAPSHOT.jar"
AUTH_JAR="$ROOT_DIR/flyfish-auth/target/flyfish-auth-0.0.2-SNAPSHOT.jar"
GIT_JAR="$ROOT_DIR/flyfish-git/target/flyfish-git-0.0.2-SNAPSHOT.jar"
LOWCODE_MODULE_JAR="$ROOT_DIR/flyfish-lowcode/target/flyfish-lowcode-0.0.2-SNAPSHOT.jar"
SHOP_MODULE_JAR="$ROOT_DIR/flyfish-shop/target/flyfish-shop-0.0.2-SNAPSHOT.jar"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

assert_file() {
  [[ -f "$1" ]] || fail "missing artifact: $1"
}

jar_contains() {
  local jar_file="$1"
  local pattern="$2"
  jar tf "$jar_file" | grep -Eq "$pattern"
}

jar_count() {
  local jar_file="$1"
  local pattern="$2"
  jar tf "$jar_file" | awk -v pattern="$pattern" '$0 ~ pattern { count++ } END { print count + 0 }'
}

assert_contains() {
  local jar_file="$1"
  local pattern="$2"
  local label="$3"
  jar_contains "$jar_file" "$pattern" || fail "$label missing from $(basename "$jar_file")"
}

assert_count() {
  local jar_file="$1"
  local pattern="$2"
  local expected="$3"
  local label="$4"
  local actual
  actual="$(jar_count "$jar_file" "$pattern")"
  [[ "$actual" == "$expected" ]] || fail "$label expected $expected occurrence(s) in $(basename "$jar_file"), got $actual"
}

assert_single_lib() {
  local jar_file="$1"
  local artifact_id="$2"
  local label="$3"
  assert_count "$jar_file" "^BOOT-INF/lib/$artifact_id-.*\\.jar$" "1" "$label"
}

assert_not_contains() {
  local jar_file="$1"
  local pattern="$2"
  local label="$3"
  if jar_contains "$jar_file" "$pattern"; then
    fail "$label unexpectedly present in $(basename "$jar_file")"
  fi
}

assert_no_duplicate_flyfish_libs() {
  local jar_file="$1"
  local duplicates
  duplicates="$(jar tf "$jar_file" \
    | grep -E '^BOOT-INF/lib/flyfish-.*\.jar$' \
    | sed 's#.*/##' \
    | sort \
    | uniq -d \
    || true)"
  [[ -z "$duplicates" ]] || fail "duplicate flyfish libraries in $(basename "$jar_file"): $duplicates"
}

for artifact in "$LOWCODE_JAR" "$SHOP_JAR" "$MAIN_JAR" "$PORTAL_API_JAR" "$COMMON_JAR" "$PLATFORM_JAR" "$AUTH_JAR" "$GIT_JAR" "$LOWCODE_MODULE_JAR" "$SHOP_MODULE_JAR"; do
  assert_file "$artifact"
done

assert_no_duplicate_flyfish_libs "$LOWCODE_JAR"
assert_single_lib "$LOWCODE_JAR" 'flyfish-ddl' 'ddl module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-portal-api' 'portal API module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-common' 'common module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-auth' 'auth module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-platform' 'platform module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-lowcode' 'lowcode module'
assert_contains "$LOWCODE_JAR" 'BOOT-INF/lib/r2dbc-h2-' 'local H2 driver'
assert_contains "$LOWCODE_JAR" 'BOOT-INF/lib/r2dbc-mysql-' 'lowcode external MySQL driver'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/flyfish-shop-' 'shop module'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/flyfish-git-' 'git module'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/classes/static/' 'app-level static resources'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/classes/templates/' 'app-level template resources'

assert_no_duplicate_flyfish_libs "$SHOP_JAR"
assert_single_lib "$SHOP_JAR" 'flyfish-ddl' 'ddl module'
assert_single_lib "$SHOP_JAR" 'flyfish-portal-api' 'portal API module'
assert_single_lib "$SHOP_JAR" 'flyfish-common' 'common module'
assert_single_lib "$SHOP_JAR" 'flyfish-auth' 'auth module'
assert_single_lib "$SHOP_JAR" 'flyfish-platform' 'platform module'
assert_single_lib "$SHOP_JAR" 'flyfish-shop' 'shop module'
assert_single_lib "$SHOP_JAR" 'flyfish-git' 'git module'
assert_contains "$SHOP_JAR" 'BOOT-INF/lib/r2dbc-h2-' 'local H2 driver'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/lib/flyfish-lowcode-' 'lowcode module'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/lib/r2dbc-mysql-' 'local MySQL application driver'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/classes/static/' 'app-level static resources'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/classes/templates/' 'app-level template resources'

assert_no_duplicate_flyfish_libs "$MAIN_JAR"
assert_single_lib "$MAIN_JAR" 'flyfish-ddl' 'ddl module'
assert_single_lib "$MAIN_JAR" 'flyfish-portal-api' 'portal API module'
assert_single_lib "$MAIN_JAR" 'flyfish-common' 'common module'
assert_single_lib "$MAIN_JAR" 'flyfish-auth' 'auth module'
assert_single_lib "$MAIN_JAR" 'flyfish-platform' 'platform module'
assert_single_lib "$MAIN_JAR" 'flyfish-lowcode' 'lowcode module'
assert_single_lib "$MAIN_JAR" 'flyfish-shop' 'shop module'
assert_single_lib "$MAIN_JAR" 'flyfish-git' 'git module'
assert_contains "$MAIN_JAR" 'BOOT-INF/lib/r2dbc-h2-' 'local H2 driver'
assert_contains "$MAIN_JAR" 'BOOT-INF/lib/r2dbc-mysql-' 'lowcode external MySQL driver'
assert_not_contains "$MAIN_JAR" 'BOOT-INF/classes/static/' 'app-level static resources'
assert_not_contains "$MAIN_JAR" 'BOOT-INF/classes/templates/' 'app-level template resources'

assert_contains "$PLATFORM_JAR" '^static/index.html$' 'shared static fallback entry'
assert_contains "$PLATFORM_JAR" '^static/favicon.ico$' 'shared favicon'

assert_contains "$AUTH_JAR" '^templates/oauth/redirect.html$' 'shared OAuth redirect template'
assert_contains "$AUTH_JAR" '^templates/oauth/bind-confirm.html$' 'shared OAuth bind confirmation template'

assert_not_contains "$PORTAL_API_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git|oauth|user)/' 'business implementation package'
assert_not_contains "$COMMON_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git|oauth|user|portal)/' 'business implementation package'
assert_not_contains "$PLATFORM_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git|oauth|user)/' 'business implementation package'
assert_not_contains "$AUTH_JAR" 'group/flyfish/dev/(shop|generator|customer|support|git|portal)/' 'business implementation package'
assert_not_contains "$GIT_JAR" 'group/flyfish/dev/(shop|generator|customer|support|portal)/' 'business implementation package'
assert_not_contains "$LOWCODE_MODULE_JAR" 'group/flyfish/dev/(shop|customer|support|wechat|git)/' 'shop implementation package'
assert_not_contains "$SHOP_MODULE_JAR" 'group/flyfish/dev/(generator|lowcode)/' 'lowcode implementation package'
assert_not_contains "$SHOP_MODULE_JAR" '^group/flyfish/dev/wechat/' 'legacy shared WeChat implementation package'

echo "Application artifact boundaries passed."
