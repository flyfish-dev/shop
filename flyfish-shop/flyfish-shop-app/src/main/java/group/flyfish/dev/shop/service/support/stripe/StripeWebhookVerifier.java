package group.flyfish.dev.shop.service.support.stripe;

import group.flyfish.dev.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class StripeWebhookVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long DEFAULT_TOLERANCE_SECONDS = 300;

    public void verify(String payload, String signatureHeader, String endpointSecret) {
        if (StringUtils.isBlank(endpointSecret)) {
            throw new BusinessException("STRIPE_WEBHOOK_SECRET_MISSING", "Stripe Webhook 密钥未配置");
        }
        Map<String, String> signature = parseSignature(signatureHeader);
        String timestamp = signature.get("t");
        String expectedSignature = signature.get("v1");
        if (StringUtils.isBlank(timestamp) || StringUtils.isBlank(expectedSignature)) {
            throw new BusinessException("STRIPE_WEBHOOK_SIGNATURE_MISSING", "Stripe Webhook 签名缺失");
        }
        verifyTimestamp(timestamp);
        String signedPayload = timestamp + "." + StringUtils.defaultString(payload);
        String actualSignature = hmacHex(endpointSecret, signedPayload);
        if (!MessageDigest.isEqual(actualSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("STRIPE_WEBHOOK_SIGNATURE_INVALID", "Stripe Webhook 签名错误");
        }
    }

    private Map<String, String> parseSignature(String header) {
        Map<String, String> result = new LinkedHashMap<>();
        if (StringUtils.isBlank(header)) {
            return result;
        }
        for (String item : header.split(",")) {
            String[] parts = item.split("=", 2);
            if (parts.length == 2) {
                result.put(parts[0], parts[1]);
            }
        }
        return result;
    }

    private void verifyTimestamp(String timestamp) {
        try {
            long eventTime = Long.parseLong(timestamp);
            long age = Math.abs(Instant.now().getEpochSecond() - eventTime);
            if (age > DEFAULT_TOLERANCE_SECONDS) {
                throw new BusinessException("STRIPE_WEBHOOK_SIGNATURE_EXPIRED", "Stripe Webhook 签名已过期");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("STRIPE_WEBHOOK_SIGNATURE_INVALID", "Stripe Webhook 签名时间无效");
        }
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new BusinessException("STRIPE_WEBHOOK_SIGNATURE_INVALID", "Stripe Webhook 签名校验失败");
        }
    }
}
