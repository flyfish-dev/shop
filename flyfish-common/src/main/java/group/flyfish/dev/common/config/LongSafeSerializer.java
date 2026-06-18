package group.flyfish.dev.common.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 面向前端的 Long 序列化器。
 *
 * <p>JavaScript 只能安全表示 {@code Number.MAX_SAFE_INTEGER} 范围内的整数。
 * 超出范围的 Long 统一序列化为字符串，避免订单号、用户 ID 等大整数在浏览器端丢精度。</p>
 */
final class LongSafeSerializer extends StdSerializer<Long> {

    static final LongSafeSerializer INSTANCE = new LongSafeSerializer();

    private static final long MAX_SAFE_JS_INTEGER = 9_007_199_254_740_991L;

    private LongSafeSerializer() {
        super(Long.class);
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext context) throws JacksonException {
        if (value > MAX_SAFE_JS_INTEGER || value < -MAX_SAFE_JS_INTEGER) {
            gen.writeString(String.valueOf(value));
            return;
        }
        gen.writeNumber(value);
    }
}
