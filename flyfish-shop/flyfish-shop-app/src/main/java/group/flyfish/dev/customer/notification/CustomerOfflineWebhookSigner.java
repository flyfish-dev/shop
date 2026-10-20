package group.flyfish.dev.customer.notification;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 客服离线 Webhook HMAC-SHA256 签名器。
 */
public class CustomerOfflineWebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    public String sign(String secret, String timestamp, byte[] body) {
        if (StringUtils.isBlank(secret)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception error) {
            throw new IllegalStateException("生成客服离线 Webhook 签名失败", error);
        }
    }
}
