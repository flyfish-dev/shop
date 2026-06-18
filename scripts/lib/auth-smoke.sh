#!/usr/bin/env bash

JWT_SECRET="${FLYFISH_AUTH_SMOKE_JWT_SECRET:-change-me-flyfish-dev-jwt-secret}"
SMOKE_USER_ID="${FLYFISH_AUTH_SMOKE_USER_ID:-9001}"
SMOKE_ADMIN_ID="${FLYFISH_AUTH_SMOKE_ADMIN_ID:-1}"
TOKEN_TTL_SECONDS="${FLYFISH_AUTH_SMOKE_TOKEN_TTL_SECONDS:-86400}"

auth_smoke_find_h2_jar() {
  local h2_jar
  h2_jar="$(find "$HOME/.m2/repository/com/h2database/h2" -name 'h2-*.jar' | sort | tail -1)"
  [[ -n "$h2_jar" && -f "$h2_jar" ]] || fail "missing H2 jar in ~/.m2/repository"
  echo "$h2_jar"
}

auth_smoke_compile_token_helper() {
  local source_file="$WORK_DIR/SmokeJwt.java"
  local classes_dir="$WORK_DIR/classes"
  mkdir -p "$classes_dir"
  cat >"$source_file" <<'JAVA'
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

public final class SmokeJwt {
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final int ES256_INTEGER_BYTES = 32;
    private static final int ES256_SIGNATURE_BYTES = ES256_INTEGER_BYTES * 2;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: SmokeJwt <secret> <subject> <ttlSeconds>");
        }
        String secret = args[0];
        String subject = args[1];
        long ttlSeconds = Long.parseLong(args[2]);
        long issuedAt = System.currentTimeMillis() / 1000;
        long expiresAt = issuedAt + ttlSeconds;
        String tokenId = UUID.randomUUID().toString().replace("-", "");

        String header = base64Url("{\"alg\":\"ES256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + escape(subject) + "\",\"jti\":\"" + escape(tokenId)
                + "\",\"iat\":" + issuedAt + ",\"exp\":" + expiresAt + "}");
        String signingInput = header + "." + payload;

        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(keyPair(secret).getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String joseSignature = BASE64_URL_ENCODER.encodeToString(derToJoseSignature(signature.sign()));
        System.out.println(signingInput + "." + joseSignature);
    }

    private static KeyPair keyPair(String secret) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] seed = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(seed);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
        return generator.generateKeyPair();
    }

    private static String base64Url(String text) {
        return BASE64_URL_ENCODER.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static byte[] derToJoseSignature(byte[] derSignature) {
        int[] offset = {0};
        expect(derSignature, offset, 0x30);
        readLength(derSignature, offset);
        byte[] r = readInteger(derSignature, offset);
        byte[] s = readInteger(derSignature, offset);
        if (offset[0] != derSignature.length) {
            throw new IllegalArgumentException("Unexpected DER signature tail");
        }

        byte[] jose = new byte[ES256_SIGNATURE_BYTES];
        writeFixedInteger(r, jose, 0);
        writeFixedInteger(s, jose, ES256_INTEGER_BYTES);
        return jose;
    }

    private static byte[] readInteger(byte[] bytes, int[] offset) {
        expect(bytes, offset, 0x02);
        int length = readLength(bytes, offset);
        if (length < 0 || offset[0] + length > bytes.length) {
            throw new IllegalArgumentException("Invalid DER integer length");
        }
        byte[] integer = Arrays.copyOfRange(bytes, offset[0], offset[0] + length);
        offset[0] += length;
        return integer;
    }

    private static void expect(byte[] bytes, int[] offset, int expected) {
        if (offset[0] >= bytes.length || (bytes[offset[0]] & 0xff) != expected) {
            throw new IllegalArgumentException("Invalid DER signature");
        }
        offset[0]++;
    }

    private static int readLength(byte[] bytes, int[] offset) {
        if (offset[0] >= bytes.length) {
            throw new IllegalArgumentException("Missing DER length");
        }
        int first = bytes[offset[0]++] & 0xff;
        if (first < 0x80) {
            return first;
        }
        int count = first & 0x7f;
        if (count == 0 || count > 4 || offset[0] + count > bytes.length) {
            throw new IllegalArgumentException("Invalid DER length");
        }
        int value = 0;
        for (int i = 0; i < count; i++) {
            value = (value << 8) | (bytes[offset[0]++] & 0xff);
        }
        return value;
    }

    private static void writeFixedInteger(byte[] derInteger, byte[] target, int targetOffset) {
        byte[] unsigned = stripLeadingZeros(derInteger);
        if (unsigned.length > ES256_INTEGER_BYTES) {
            throw new IllegalArgumentException("ES256 signature integer is too long");
        }
        System.arraycopy(unsigned, 0, target, targetOffset + ES256_INTEGER_BYTES - unsigned.length, unsigned.length);
    }

    private static byte[] stripLeadingZeros(byte[] bytes) {
        int offset = 0;
        while (offset < bytes.length && bytes[offset] == 0) {
            offset++;
        }
        byte[] result = Arrays.copyOfRange(bytes, offset, bytes.length);
        return result.length == 0 ? BigInteger.ZERO.toByteArray() : result;
    }
}
JAVA
  javac -d "$classes_dir" "$source_file"
}

auth_smoke_create_token() {
  local subject="$1"
  java -cp "$WORK_DIR/classes" SmokeJwt "$JWT_SECRET" "$subject" "$TOKEN_TTL_SECONDS"
}

auth_smoke_write_seed_sql() {
  local seed_file="$1"
  cat >"$seed_file" <<SQL
MERGE INTO user_portal (id, username, password, avatar, phone, email, bio, create_time, update_time)
KEY(id)
VALUES (${SMOKE_ADMIN_ID}, 'smoke-maintainer', '', NULL, NULL, 'maintainer-smoke@example.com', 'Smoke maintainer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO user_portal (id, username, password, avatar, phone, email, bio, create_time, update_time)
KEY(id)
VALUES (${SMOKE_USER_ID}, 'smoke-user', '', NULL, NULL, 'user-smoke@example.com', 'Smoke user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO user_portal_oauth (user_id, type, user_info, openid, login_name, display_name, nickname, avatar_url, email, profile_url, union_id, auth_time)
KEY(user_id, type, openid)
VALUES (${SMOKE_ADMIN_ID}, 'GITEA', '{"id":"1","login":"smoke-maintainer","is_admin":true}', '1', 'smoke-maintainer', 'Smoke Maintainer', 'Smoke Maintainer', NULL, 'maintainer-smoke@example.com', NULL, NULL, CURRENT_TIMESTAMP);

MERGE INTO user_portal_oauth (user_id, type, user_info, openid, login_name, display_name, nickname, avatar_url, email, profile_url, union_id, auth_time)
KEY(user_id, type, openid)
VALUES (${SMOKE_USER_ID}, 'GITEA', '{"id":"${SMOKE_USER_ID}","login":"smoke-user","is_admin":false}', '${SMOKE_USER_ID}', 'smoke-user', 'Smoke User', 'Smoke User', NULL, 'user-smoke@example.com', NULL, NULL, CURRENT_TIMESTAMP);
SQL
}

auth_smoke_run_sql_script() {
  local h2_jar="$1"
  local db_file="$2"
  local script_file="$3"
  java -cp "$h2_jar" org.h2.tools.RunScript \
    -url "jdbc:h2:file:$db_file;MODE=MySQL;AUTO_SERVER=TRUE" \
    -user sa \
    -password "" \
    -script "$script_file"
}

auth_smoke_seed_users() {
  local h2_jar="$1"
  local db_file="$2"
  local seed_file="$WORK_DIR/seed-users.sql"
  auth_smoke_write_seed_sql "$seed_file"
  auth_smoke_run_sql_script "$h2_jar" "$db_file" "$seed_file"
}

auth_smoke_write_storage_state() {
  local token="$1"
  local origin="$2"
  local storage_file="$3"
  TOKEN="$token" ORIGIN="$origin" STORAGE_FILE="$storage_file" node <<'NODE'
const fs = require('node:fs');
const storage = {
  cookies: [],
  origins: [
    {
      origin: process.env.ORIGIN,
      localStorage: [
        { name: 'access_token', value: process.env.TOKEN },
        { name: 'auth_changed_at', value: String(Date.now()) }
      ]
    }
  ]
};
fs.writeFileSync(process.env.STORAGE_FILE, JSON.stringify(storage, null, 2));
NODE
}
