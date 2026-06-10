package group.flyfish.dev.user.email;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.user.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EmailMagicLinkTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final JwtProperties jwtProperties;
    private final EmailMagicLinkProperties properties;
    private final Map<String, Instant> consumedNonces = new ConcurrentHashMap<>();

    public String issue(String email, Long bindUserId, String redirect) {
        Instant expiresAt = Instant.now().plus(properties.getExpiresIn());
        Map<String, Object> payload = Map.of(
                "email", email,
                "nonce", IdGenerators.uuid32(),
                "bindUserId", bindUserId == null ? "" : bindUserId,
                "redirect", StringUtils.defaultIfBlank(redirect, "/"),
                "exp", expiresAt.getEpochSecond()
        );
        String body = BASE64_URL_ENCODER.encodeToString(JacksonUtils.toJson(payload).getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    public EmailMagicLinkPayload consume(String token) {
        cleanupConsumedNonces();
        EmailMagicLinkPayload payload = parse(token);
        Instant previous = consumedNonces.putIfAbsent(payload.nonce(), payload.expiresAt());
        if (previous != null) {
            throw new BusinessException("EMAIL_MAGIC_LINK_USED", "登录链接已使用，请重新获取");
        }
        return payload;
    }

    private EmailMagicLinkPayload parse(String token) {
        try {
            String[] parts = StringUtils.defaultString(token).split("\\.", -1);
            if (parts.length != 2 || StringUtils.isAnyBlank(parts[0], parts[1])) {
                throw new IllegalArgumentException("invalid token");
            }
            if (!MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("bad signature");
            }
            Map<String, Object> payload = JacksonUtils.readValue(
                    new String(BASE64_URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8), JSON_MAP);
            String email = valueAsString(payload.get("email"));
            String nonce = valueAsString(payload.get("nonce"));
            String redirect = valueAsString(payload.get("redirect"));
            Long expiresAt = valueAsLong(payload.get("exp"));
            if (StringUtils.isAnyBlank(email, nonce) || expiresAt == null) {
                throw new IllegalArgumentException("missing fields");
            }
            Instant expiration = Instant.ofEpochSecond(expiresAt);
            if (!expiration.isAfter(Instant.now())) {
                throw new BusinessException("EMAIL_MAGIC_LINK_EXPIRED", "登录链接已过期，请重新获取");
            }
            return new EmailMagicLinkPayload(email, nonce, valueAsLong(payload.get("bindUserId")),
                    StringUtils.defaultIfBlank(redirect, "/"), expiration);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("EMAIL_MAGIC_LINK_INVALID", "登录链接无效，请重新获取");
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("邮箱登录链接签名失败", e);
        }
    }

    private void cleanupConsumedNonces() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = consumedNonces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (!entry.getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long valueAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            return Long.parseLong(text);
        }
        return null;
    }
}
