package group.flyfish.dev.common.bridge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class BridgeSignature {
    private BridgeSignature() { }

    public static BridgeRequest sign(String path, String body, String secret) {
        long timestamp = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString();
        return new BridgeRequest(path, timestamp, nonce, body, digest(path, timestamp, nonce, body, secret));
    }

    public static void verify(BridgeRequest request, String path, String secret, Instant now) {
        if (request == null || !path.equals(request.path()) || request.body() == null
                || request.body().length() > 8_000_000 || request.nonce() == null
                || !request.nonce().matches("[a-zA-Z0-9-]{16,80}")
                || request.timestamp() < now.getEpochSecond() - 60
                || request.timestamp() > now.getEpochSecond() + 60) {
            throw new IllegalArgumentException("Invalid bridge request");
        }
        String expected = digest(path, request.timestamp(), request.nonce(), request.body(), secret);
        if (request.signature() == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                request.signature().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Invalid bridge signature");
        }
    }

    private static String digest(String path, long timestamp, String nonce, String body, String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Bridge key must contain at least 32 characters");
        }
        try {
            String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("POST\n" + path + "\n" + timestamp + "\n"
                    + nonce + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Bridge signing unavailable", error);
        }
    }
}
