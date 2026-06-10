package group.flyfish.dev.user.support;

import group.flyfish.dev.user.domain.ParsedToken;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 轻量 JWT 编解码器，当前只支持本系统实际使用的 ES256 签名 JWT。
 *
 * <p>这里不再依赖第三方 JWT 运行时，原因有两个：</p>
 * <p>1. 系统只需要签发和校验三段式 JWS，完整 JWT 框架会引入不必要的动态加载链路。</p>
 * <p>2. GraalVM Native Image 对运行时反射和 ServiceLoader 更敏感，使用 JDK 原生密码学 API
 * 可以让行为更稳定，也更容易审计。</p>
 *
 * <p>实现严格遵循 JWS 对 ES256 的要求：JCA 的 ECDSA 签名结果是 DER 编码，
 * 但 JWT 第三段要求的是固定 64 字节的 r || s 原始签名。因此本类显式完成
 * DER 和 JOSE 两种签名格式的转换。</p>
 */
public final class JwtCodec {

    private static final String ALG_ES256 = "ES256";
    private static final String JWT_TYPE = "JWT";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String EC_ALGORITHM = "EC";
    private static final String EC_CURVE = "secp256r1";
    private static final String KEY_RANDOM_ALGORITHM = "SHA1PRNG";
    private static final int ES256_INTEGER_BYTES = 32;
    private static final int ES256_SIGNATURE_BYTES = ES256_INTEGER_BYTES * 2;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final KeyPair keyPair;

    private JwtCodec(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * 根据配置密钥派生固定的 P-256 密钥对。
     *
     * <p>历史实现也是从 {@code user.jwt.secret} 派生 ES256 密钥对。这里保留同样的
     * 派生方式，确保已经签发的旧 token 仍然能被校验，不因为去掉 JJWT 依赖而让用户掉线。</p>
     */
    public static JwtCodec fromSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] seed = digest.digest(requireText(secret, "JWT密钥不能为空").getBytes(StandardCharsets.UTF_8));

            SecureRandom secureRandom = SecureRandom.getInstance(KEY_RANDOM_ALGORITHM);
            secureRandom.setSeed(seed);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM);
            keyPairGenerator.initialize(new ECGenParameterSpec(EC_CURVE), secureRandom);
            return new JwtCodec(keyPairGenerator.generateKeyPair());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("初始化JWT密钥对失败", e);
        }
    }

    /**
     * 签发标准三段式 JWT。
     *
     * <p>payload 只放认证链路需要的最小字段：sub 表示用户、jti 用于退出登录后的黑名单、
     * iat 便于排查签发时间、exp 用于过期判断。避免把昵称、权限等易变信息写进 token，
     * 可以减少用户资料变更后的同步成本。</p>
     */
    public String encode(String subject, String tokenId, Date issuedAt, Date expiration) {
        try {
            Date safeIssuedAt = requireDate(issuedAt, "JWT签发时间不能为空");
            Date safeExpiration = requireDate(expiration, "JWT过期时间不能为空");
            if (!safeExpiration.after(safeIssuedAt)) {
                throw new IllegalArgumentException("JWT过期时间必须晚于签发时间");
            }

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", ALG_ES256);
            header.put("typ", JWT_TYPE);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", requireText(subject, "JWT主题不能为空"));
            payload.put("jti", requireText(tokenId, "JWT编号不能为空"));
            payload.put("iat", safeIssuedAt.getTime() / 1000);
            payload.put("exp", safeExpiration.getTime() / 1000);

            String signingInput = base64UrlJson(header) + "." + base64UrlJson(payload);
            byte[] derSignature = sign(signingInput);
            return signingInput + "." + BASE64_URL_ENCODER.encodeToString(derToJoseSignature(derSignature));
        } catch (JacksonException | GeneralSecurityException e) {
            throw new IllegalStateException("创建JWT失败", e);
        }
    }

    /**
     * 解析并校验 JWT。
     *
     * <p>校验顺序刻意保持保守：先检查三段式结构和 header 算法，再验签，验签通过后才信任
     * payload 内容。任何格式异常、签名错误或过期都会返回空 Optional，调用方不需要感知底层细节。</p>
     */
    public Optional<ParsedToken> decode(String token) {
        try {
            String[] parts = splitToken(token);
            Map<String, Object> header = readJsonPart(parts[0]);
            if (!ALG_ES256.equals(valueAsString(header.get("alg")))) {
                return Optional.empty();
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] joseSignature = BASE64_URL_DECODER.decode(parts[2]);
            if (!verify(signingInput, joseSignature)) {
                return Optional.empty();
            }

            Map<String, Object> payload = readJsonPart(parts[1]);
            String subject = valueAsString(payload.get("sub"));
            String tokenId = valueAsString(payload.get("jti"));
            Long expirationSeconds = valueAsLong(payload.get("exp"));
            if (isBlank(subject) || isBlank(tokenId) || expirationSeconds == null) {
                return Optional.empty();
            }

            Date expiration = new Date(expirationSeconds * 1000);
            if (!expiration.after(new Date())) {
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(subject, tokenId, expiration));
        } catch (IllegalArgumentException | JacksonException | GeneralSecurityException e) {
            return Optional.empty();
        }
    }

    private String[] splitToken(String token) {
        if (isBlank(token)) {
            throw new IllegalArgumentException("JWT不能为空");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || isBlank(parts[0]) || isBlank(parts[1]) || isBlank(parts[2])) {
            throw new IllegalArgumentException("JWT必须是header.payload.signature三段式结构");
        }
        return parts;
    }

    private String base64UrlJson(Map<String, Object> value) {
        return BASE64_URL_ENCODER.encodeToString(JSON_MAPPER.writeValueAsBytes(value));
    }

    private Map<String, Object> readJsonPart(String part) {
        return JSON_MAPPER.readValue(BASE64_URL_DECODER.decode(part), JSON_MAP);
    }

    private byte[] sign(String signingInput) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signature.sign();
    }

    private boolean verify(String signingInput, byte[] joseSignature) throws GeneralSecurityException {
        if (joseSignature.length != ES256_SIGNATURE_BYTES) {
            return false;
        }
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(keyPair.getPublic());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signature.verify(joseToDerSignature(joseSignature));
    }

    /**
     * JCA 输出的 ECDSA 签名是 ASN.1 DER 序列，JWT 规范要求改成固定长度 r || s。
     */
    private byte[] derToJoseSignature(byte[] derSignature) {
        DerReader reader = new DerReader(derSignature);
        reader.readSequenceStart();
        byte[] r = reader.readInteger();
        byte[] s = reader.readInteger();
        reader.assertFullyRead();

        byte[] jose = new byte[ES256_SIGNATURE_BYTES];
        writeFixedInteger(r, jose, 0);
        writeFixedInteger(s, jose, ES256_INTEGER_BYTES);
        return jose;
    }

    /**
     * JWT 收到的是 r || s 原始签名，JCA 验签前需要还原成 ASN.1 DER 序列。
     */
    private byte[] joseToDerSignature(byte[] joseSignature) {
        byte[] r = unsignedDerInteger(Arrays.copyOfRange(joseSignature, 0, ES256_INTEGER_BYTES));
        byte[] s = unsignedDerInteger(Arrays.copyOfRange(joseSignature, ES256_INTEGER_BYTES, ES256_SIGNATURE_BYTES));

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeDerInteger(body, r);
        writeDerInteger(body, s);

        ByteArrayOutputStream der = new ByteArrayOutputStream();
        der.write(0x30);
        writeDerLength(der, body.size());
        der.writeBytes(body.toByteArray());
        return der.toByteArray();
    }

    private void writeFixedInteger(byte[] derInteger, byte[] target, int targetOffset) {
        byte[] unsigned = stripLeadingZeros(derInteger);
        if (unsigned.length > ES256_INTEGER_BYTES) {
            throw new IllegalArgumentException("ES256签名整数长度异常");
        }
        System.arraycopy(unsigned, 0, target, targetOffset + ES256_INTEGER_BYTES - unsigned.length, unsigned.length);
    }

    private byte[] unsignedDerInteger(byte[] fixedInteger) {
        byte[] unsigned = stripLeadingZeros(fixedInteger);
        if (unsigned.length == 0) {
            return new byte[]{0};
        }
        if ((unsigned[0] & 0x80) == 0) {
            return unsigned;
        }
        byte[] positive = new byte[unsigned.length + 1];
        System.arraycopy(unsigned, 0, positive, 1, unsigned.length);
        return positive;
    }

    private void writeDerInteger(ByteArrayOutputStream out, byte[] integer) {
        out.write(0x02);
        writeDerLength(out, integer.length);
        out.writeBytes(integer);
    }

    private void writeDerLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
            return;
        }
        byte[] bytes = BigInteger.valueOf(length).toByteArray();
        out.write(0x80 | bytes.length);
        out.writeBytes(bytes);
    }

    private byte[] stripLeadingZeros(byte[] bytes) {
        int offset = 0;
        while (offset < bytes.length && bytes[offset] == 0) {
            offset++;
        }
        return Arrays.copyOfRange(bytes, offset, bytes.length);
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long valueAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !isBlank(text)) {
            return Long.parseLong(text);
        }
        return null;
    }

    private static String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Date requireDate(Date value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 极小 DER 读取器，只实现 ES256 签名需要的 SEQUENCE + INTEGER + INTEGER。
     */
    private static final class DerReader {

        private final byte[] source;
        private int offset;
        private int sequenceEnd;

        private DerReader(byte[] source) {
            this.source = source;
        }

        private void readSequenceStart() {
            requireTag(0x30);
            int length = readLength();
            sequenceEnd = offset + length;
            if (sequenceEnd != source.length) {
                throw new IllegalArgumentException("DER签名长度异常");
            }
        }

        private byte[] readInteger() {
            requireTag(0x02);
            int length = readLength();
            if (length <= 0 || offset + length > sequenceEnd) {
                throw new IllegalArgumentException("DER整数长度异常");
            }
            byte[] value = Arrays.copyOfRange(source, offset, offset + length);
            offset += length;
            return value;
        }

        private void assertFullyRead() {
            if (offset != sequenceEnd) {
                throw new IllegalArgumentException("DER签名存在多余字段");
            }
        }

        private void requireTag(int expected) {
            if (offset >= source.length || (source[offset++] & 0xff) != expected) {
                throw new IllegalArgumentException("DER签名结构异常");
            }
        }

        private int readLength() {
            if (offset >= source.length) {
                throw new IllegalArgumentException("DER长度缺失");
            }
            int first = source[offset++] & 0xff;
            if ((first & 0x80) == 0) {
                return first;
            }
            int byteCount = first & 0x7f;
            if (byteCount == 0 || byteCount > 4 || offset + byteCount > source.length) {
                throw new IllegalArgumentException("DER长度异常");
            }
            int length = 0;
            for (int i = 0; i < byteCount; i++) {
                length = (length << 8) | (source[offset++] & 0xff);
            }
            return length;
        }
    }
}
