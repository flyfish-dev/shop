package group.flyfish.dev.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 3 全局配置。
 *
 * <p>Spring Boot 4 默认使用 Jackson 3 的 {@code tools.jackson.*} 运行时。
 * 这里通过 Boot 推荐的 {@link JsonMapperBuilderCustomizer} 扩展自动配置，
 * 让 WebFlux HTTP codec、WebClient codec 和注入的 JsonMapper 使用同一套规则，
 * 避免再手写旧版 codec 或散落多个 ObjectMapper。</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public JsonMapperBuilderCustomizer flyfishJsonMapperBuilderCustomizer() {
        SimpleModule flyfishModule = new SimpleModule("flyfish-json");
        flyfishModule.addSerializer(Long.class, LongSafeSerializer.INSTANCE);
        flyfishModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(LOCAL_DATE_TIME_FORMATTER));
        flyfishModule.addDeserializer(LocalDateTime.class, FlexibleLocalDateTimeDeserializer.INSTANCE);

        return builder -> builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .addModule(flyfishModule);
    }
}
