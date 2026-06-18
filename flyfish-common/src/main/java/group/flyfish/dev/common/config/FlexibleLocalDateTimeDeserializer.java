package group.flyfish.dev.common.config;

import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * LocalDateTime 反序列化器。
 *
 * <p>服务端对外统一输出 {@code yyyy-MM-dd HH:mm:ss}，便于页面展示和日志排查。
 * 但浏览器组件、第三方调用方经常会提交 ISO-8601 的 {@code yyyy-MM-dd'T'HH:mm:ss}。
 * 这里在入口处集中兼容两类格式，避免各个 DTO 或 Controller 分散写格式转换逻辑。</p>
 */
final class FlexibleLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    static final FlexibleLocalDateTimeDeserializer INSTANCE = new FlexibleLocalDateTimeDeserializer();

    private static final List<DateTimeFormatter> SUPPORTED_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    private FlexibleLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        }
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
        }

        String rawValue = StringUtils.trimToNull(parser.getString());
        if (rawValue == null) {
            return null;
        }

        for (DateTimeFormatter formatter : SUPPORTED_FORMATTERS) {
            try {
                return LocalDateTime.parse(rawValue, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个受支持格式，最后统一抛出可读错误。
            }
        }

        throw context.weirdStringException(rawValue, LocalDateTime.class,
                "时间格式必须为 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd'T'HH:mm:ss");
    }
}
