package group.flyfish.dev.user.support;

import group.flyfish.dev.user.domain.ParsedToken;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtCodecTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };
    private static final String SECRET = "test-secret-for-token-provider";

    /**
     * 这个 fixture 由旧版 JJWT 使用同一个 secret 派生出的 ES256 私钥签发。
     * 保留它是为了证明去掉 JJWT 依赖后，生产上已签发的标准 ES256 token 仍可解析。
     */
    private static final String LEGACY_JJWT_TOKEN = "eyJhbGciOiJFUzI1NiJ9."
            + "eyJzdWIiOiIyMDAyIiwianRpIjoibGVnYWN5LXRva2VuLWlkIiwiZXhwIjoxODkzNDU2MDAwfQ."
            + "01Nh8vsZ5HXqg-yjA7gyFvw5wZZcYCGezMU8XzOq0uus6Kcz6gtEj98BuQe5uCLE7VcpFAMytC7ytBuB89oKUA";

    @Test
    void createsStandardEs256Jwt() throws Exception {
        JwtCodec codec = JwtCodec.fromSecret(SECRET);

        String token = codec.encode("1001", "token-id", new Date(1_700_000_000_000L),
                new Date(1_893_456_000_000L));

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        assertEquals("ES256", readJson(parts[0]).get("alg"));
        assertEquals("JWT", readJson(parts[0]).get("typ"));
        assertEquals("1001", readJson(parts[1]).get("sub"));
        assertEquals(64, BASE64_URL_DECODER.decode(parts[2]).length);
        assertTrue(codec.decode(token).isPresent());
    }

    @Test
    void parsesLegacyJjwtIssuedToken() {
        JwtCodec codec = JwtCodec.fromSecret(SECRET);

        Optional<ParsedToken> parsed = codec.decode(LEGACY_JJWT_TOKEN);

        assertTrue(parsed.isPresent());
        assertEquals("2002", parsed.get().subject());
        assertEquals("legacy-token-id", parsed.get().id());
        assertEquals(1_893_456_000L, parsed.get().expiration().getTime() / 1000);
    }

    @Test
    void rejectsExpiredToken() {
        JwtCodec codec = JwtCodec.fromSecret(SECRET);
        String token = codec.encode("1001", "token-id", new Date(1_000L), new Date(2_000L));

        assertTrue(codec.decode(token).isEmpty());
    }

    @Test
    void rejectsMalformedAndTamperedToken() {
        JwtCodec codec = JwtCodec.fromSecret(SECRET);
        String token = codec.encode("1001", "token-id", new Date(), new Date(System.currentTimeMillis() + 60_000L));
        String[] parts = token.split("\\.");
        String brokenPayload = parts[1].substring(0, parts[1].length() - 1) + "A";

        assertTrue(codec.decode("not-a-jwt").isEmpty());
        assertTrue(codec.decode(parts[0] + "." + brokenPayload + "." + parts[2]).isEmpty());
    }

    private Map<String, Object> readJson(String part) throws Exception {
        return JSON_MAPPER.readValue(BASE64_URL_DECODER.decode(part), JSON_MAP);
    }
}
