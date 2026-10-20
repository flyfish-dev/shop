package group.flyfish.dev.xianyu.top;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class TopSigner {
    private TopSigner() { }
    public static String sign(Map<String, String> parameters, String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("TOP credential missing");
        StringBuilder input = new StringBuilder(secret);
        new TreeMap<>(parameters).forEach((key, value) -> {
            if (!"sign".equals(key) && value != null && !value.isEmpty()) input.append(key).append(value);
        });
        input.append(secret);
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5")
                    .digest(input.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }
}
