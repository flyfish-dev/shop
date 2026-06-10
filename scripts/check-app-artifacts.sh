#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

AUTH_JAR="$ROOT_DIR/flyfish-auth/flyfish-auth-app/target/flyfish-auth.jar"
LOWCODE_JAR="$ROOT_DIR/flyfish-lowcode/flyfish-lowcode-app/target/flyfish-lowcode.jar"
SHOP_JAR="$ROOT_DIR/flyfish-shop/flyfish-shop-app/target/flyfish-shop.jar"
PORTAL_API_JAR="$ROOT_DIR/flyfish-portal-api/target/flyfish-portal-api-0.0.2-SNAPSHOT.jar"
COMMON_JAR="$ROOT_DIR/flyfish-common/target/flyfish-common-0.0.2-SNAPSHOT.jar"
PLATFORM_JAR="$ROOT_DIR/flyfish-platform/target/flyfish-platform-0.0.2-SNAPSHOT.jar"
AUTH_API_JAR="$ROOT_DIR/flyfish-auth/flyfish-auth-api/target/flyfish-auth-api-0.0.2-SNAPSHOT.jar"
LOWCODE_API_JAR="$ROOT_DIR/flyfish-lowcode/flyfish-lowcode-api/target/flyfish-lowcode-api-0.0.2-SNAPSHOT.jar"
SHOP_API_JAR="$ROOT_DIR/flyfish-shop/flyfish-shop-api/target/flyfish-shop-api-0.0.2-SNAPSHOT.jar"
GIT_JAR="$ROOT_DIR/flyfish-git/target/flyfish-git-0.0.2-SNAPSHOT.jar"
ARTIFACT_PROFILE="${FLYFISH_ARTIFACT_PROFILE:-auto}"

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

assert_r2dbc_drivers() {
  local jar_file="$1"
  local label="$2"
  assert_contains "$jar_file" 'BOOT-INF/lib/r2dbc-mysql-' "$label production MySQL driver"
  case "$ARTIFACT_PROFILE" in
    local)
      assert_contains "$jar_file" 'BOOT-INF/lib/r2dbc-h2-' "$label local H2 driver"
      ;;
    prod|native)
      assert_not_contains "$jar_file" 'BOOT-INF/lib/r2dbc-h2-' "$label local H2 driver"
      ;;
    auto)
      ;;
    *)
      fail "unknown FLYFISH_ARTIFACT_PROFILE=$ARTIFACT_PROFILE, expected auto/local/prod/native"
      ;;
  esac
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

for artifact in "$AUTH_JAR" "$LOWCODE_JAR" "$SHOP_JAR" "$PORTAL_API_JAR" "$COMMON_JAR" "$PLATFORM_JAR" \
  "$AUTH_API_JAR" "$LOWCODE_API_JAR" "$SHOP_API_JAR" "$GIT_JAR"; do
  assert_file "$artifact"
done

assert_no_duplicate_flyfish_libs "$AUTH_JAR"
assert_single_lib "$AUTH_JAR" 'flyfish-ddl' 'ddl module'
assert_single_lib "$AUTH_JAR" 'flyfish-common' 'common module'
assert_single_lib "$AUTH_JAR" 'flyfish-auth-api' 'auth API module'
assert_contains "$AUTH_JAR" '^BOOT-INF/classes/templates/oauth/redirect.html$' 'OAuth redirect template'
assert_contains "$AUTH_JAR" '^BOOT-INF/classes/templates/oauth/bind-confirm.html$' 'OAuth bind confirmation template'
assert_not_contains "$AUTH_JAR" 'BOOT-INF/lib/flyfish-lowcode-app-' 'lowcode app module'
assert_not_contains "$AUTH_JAR" 'BOOT-INF/lib/flyfish-shop-app-' 'shop app module'
assert_not_contains "$AUTH_JAR" 'BOOT-INF/lib/flyfish-git-' 'git module'

assert_no_duplicate_flyfish_libs "$LOWCODE_JAR"
assert_single_lib "$LOWCODE_JAR" 'flyfish-ddl' 'ddl module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-portal-api' 'portal API module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-common' 'common module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-auth-api' 'auth API module'
assert_single_lib "$LOWCODE_JAR" 'flyfish-platform' 'platform module'
assert_r2dbc_drivers "$LOWCODE_JAR" 'lowcode'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/flyfish-auth-core-' 'auth implementation module'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/pac4j-' 'OAuth implementation dependency'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/thymeleaf-' 'template implementation dependency'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/flyfish-shop-app-' 'shop app module'
assert_not_contains "$LOWCODE_JAR" 'BOOT-INF/lib/flyfish-git-' 'git module'

assert_no_duplicate_flyfish_libs "$SHOP_JAR"
assert_single_lib "$SHOP_JAR" 'flyfish-ddl' 'ddl module'
assert_single_lib "$SHOP_JAR" 'flyfish-portal-api' 'portal API module'
assert_single_lib "$SHOP_JAR" 'flyfish-common' 'common module'
assert_single_lib "$SHOP_JAR" 'flyfish-auth-api' 'auth API module'
assert_single_lib "$SHOP_JAR" 'flyfish-platform' 'platform module'
assert_single_lib "$SHOP_JAR" 'flyfish-git' 'git module'
assert_r2dbc_drivers "$SHOP_JAR" 'shop'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/lib/flyfish-auth-core-' 'auth implementation module'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/lib/pac4j-' 'OAuth implementation dependency'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/lib/thymeleaf-' 'template implementation dependency'
assert_not_contains "$SHOP_JAR" 'BOOT-INF/lib/flyfish-lowcode-app-' 'lowcode app module'

assert_contains "$PLATFORM_JAR" '^static/index.html$' 'shared static fallback entry'
assert_contains "$PLATFORM_JAR" '^static/favicon.ico$' 'shared favicon'

assert_not_contains "$PORTAL_API_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git|oauth|user)/' 'business implementation package'
assert_not_contains "$COMMON_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git|oauth|user|portal)/' 'business implementation package'
assert_not_contains "$PLATFORM_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git)/' 'business implementation package'
assert_not_contains "$AUTH_API_JAR" 'group/flyfish/dev/(shop|generator|customer|support|wechat|git|oauth/(controller|service|vender)|user/(controller|repository|service))/' 'auth API implementation package'
assert_not_contains "$LOWCODE_API_JAR" 'group/flyfish/dev/generator/' 'lowcode API implementation package'
assert_not_contains "$SHOP_API_JAR" 'group/flyfish/dev/(shop/(controller|service|repository|domain|wechat)|customer|support)/' 'shop API implementation package'
assert_not_contains "$GIT_JAR" 'group/flyfish/dev/(shop|generator|customer|support|portal)/' 'business implementation package'
assert_not_contains "$SHOP_JAR" '^BOOT-INF/classes/group/flyfish/dev/wechat/' 'legacy shared WeChat implementation package'

echo "Application artifact boundaries passed."
