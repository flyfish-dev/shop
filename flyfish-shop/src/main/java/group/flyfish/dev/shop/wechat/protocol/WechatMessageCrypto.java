package group.flyfish.dev.shop.wechat.protocol;

import group.flyfish.dev.shop.wechat.config.WechatMpProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 微信公众号消息签名和 AES 安全模式实现。
 *
 * <p>实现范围严格限定在公众号服务器回调所需的 SHA1 参数排序签名、
 * AES-CBC-NoPadding 解密/加密和 PKCS#7 补位。这样既保持协议正确，
 * 也避免在 native 下引入大量反射成本。</p>
 */
@Component
public class WechatMessageCrypto {

    private static final int AES_BLOCK_SIZE = 32;

    private final WechatMpProperties properties;

    private final WechatXmlCodec xmlCodec;

    private final SecureRandom secureRandom = new SecureRandom();

    public WechatMessageCrypto(WechatMpProperties properties, WechatXmlCodec xmlCodec) {
        this.properties = properties;
        this.xmlCodec = xmlCodec;
    }

    public boolean checkSignature(String timestamp, String nonce, String signature) {
        return secureEquals(sha1(properties.getToken(), timestamp, nonce), signature);
    }

    public String decryptXml(String encryptedXml, String msgSignature, String timestamp, String nonce) {
        String encryptedContent = xmlCodec.extractEncrypt(encryptedXml);
        String signature = sha1(properties.getToken(), timestamp, nonce, encryptedContent);
        if (!secureEquals(signature, msgSignature)) {
            throw new IllegalArgumentException("微信加密消息签名校验失败");
        }
        return decryptContent(encryptedContent);
    }

    public String encryptXml(String plainXml) {
        String encryptedContent = encryptContent(plainXml);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = randomNonce();
        String signature = sha1(properties.getToken(), timestamp, nonce, encryptedContent);
        return """
                <xml>
                  <Encrypt><![CDATA[%s]]></Encrypt>
                  <MsgSignature><![CDATA[%s]]></MsgSignature>
                  <TimeStamp>%s</TimeStamp>
                  <Nonce><![CDATA[%s]]></Nonce>
                </xml>
                """.formatted(encryptedContent, signature, timestamp, nonce);
    }

    private String decryptContent(String encryptedContent) {
        try {
            byte[] decrypted = cipher(Cipher.DECRYPT_MODE).doFinal(Base64.getDecoder().decode(encryptedContent));
            byte[] unpadded = removePkcs7Padding(decrypted);
            int xmlLength = ByteBuffer.wrap(unpadded, 16, 4).getInt();
            int xmlStart = 20;
            int xmlEnd = xmlStart + xmlLength;
            if (xmlLength < 0 || xmlEnd > unpadded.length) {
                throw new IllegalArgumentException("微信加密消息长度非法");
            }
            String appId = new String(unpadded, xmlEnd, unpadded.length - xmlEnd, StandardCharsets.UTF_8);
            if (StringUtils.isNotBlank(appId) && !StringUtils.equals(appId, properties.getAppId())) {
                throw new IllegalArgumentException("微信加密消息 AppId 不匹配");
            }
            return new String(unpadded, xmlStart, xmlLength, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("微信加密消息解密失败", e);
        }
    }

    private String encryptContent(String plainXml) {
        try {
            byte[] random = new byte[16];
            secureRandom.nextBytes(random);
            byte[] plain = plainXml.getBytes(StandardCharsets.UTF_8);
            byte[] appId = StringUtils.defaultString(properties.getAppId()).getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + plain.length + appId.length);
            buffer.put(random);
            buffer.putInt(plain.length);
            buffer.put(plain);
            buffer.put(appId);
            byte[] padded = addPkcs7Padding(buffer.array());
            return Base64.getEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE).doFinal(padded));
        } catch (Exception e) {
            throw new IllegalArgumentException("微信回复消息加密失败", e);
        }
    }

    private Cipher cipher(int mode) throws Exception {
        byte[] aesKey = aesKey();
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(mode, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(aesKey, 0, 16));
        return cipher;
    }

    private byte[] aesKey() {
        String aesKey = StringUtils.deleteWhitespace(StringUtils.trimToEmpty(properties.getAesKey()));
        if (aesKey.length() != 43) {
            throw new IllegalArgumentException("微信 EncodingAESKey 长度必须为 43 位");
        }
        return Base64.getDecoder().decode(aesKey + "=");
    }

    private byte[] addPkcs7Padding(byte[] content) {
        int amountToPad = AES_BLOCK_SIZE - content.length % AES_BLOCK_SIZE;
        byte[] padded = Arrays.copyOf(content, content.length + amountToPad);
        Arrays.fill(padded, content.length, padded.length, (byte) amountToPad);
        return padded;
    }

    private byte[] removePkcs7Padding(byte[] content) {
        if (content.length == 0) {
            throw new IllegalArgumentException("微信加密消息为空");
        }
        int pad = content[content.length - 1] & 0xff;
        if (pad < 1 || pad > AES_BLOCK_SIZE || pad > content.length) {
            throw new IllegalArgumentException("微信加密消息补位非法");
        }
        return Arrays.copyOf(content, content.length - pad);
    }

    private String sha1(String... values) {
        try {
            String raw = Arrays.stream(values)
                    .map(StringUtils::defaultString)
                    .sorted()
                    .reduce("", String::concat);
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA1 签名生成失败", e);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                StringUtils.defaultString(expected).getBytes(StandardCharsets.UTF_8),
                StringUtils.defaultString(actual).getBytes(StandardCharsets.UTF_8));
    }

    private String randomNonce() {
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
