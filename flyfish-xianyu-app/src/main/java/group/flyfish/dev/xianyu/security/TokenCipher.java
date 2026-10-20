package group.flyfish.dev.xianyu.security;

import group.flyfish.dev.xianyu.XianyuProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class TokenCipher {
    private final XianyuProperties properties;
    private final SecureRandom random = new SecureRandom();
    public String encrypt(String text) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            byte[] ciphertext = cipher(Cipher.ENCRYPT_MODE, nonce).doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(ciphertext);
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Token encryption failed", error);
        }
    }
    public String decrypt(String text) {
        try {
            String[] parts = text.split("\\.", 2);
            return new String(cipher(Cipher.DECRYPT_MODE, Base64.getDecoder().decode(parts[0]))
                    .doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("Stored token cannot be decrypted");
        }
    }
    private Cipher cipher(int mode, byte[] nonce) throws java.security.GeneralSecurityException {
        byte[] key = Base64.getDecoder().decode(properties.getEncryptionKey());
        if (key.length != 32) throw new IllegalStateException("A 256-bit encryption key is required");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }
}
