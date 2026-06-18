package group.flyfish.dev.common.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 项目内部 JSON 编解码工具。
 *
 * <p>Spring Boot 4 已经切换到 Jackson 3 的 {@code tools.jackson.*} 运行时，
 * 业务代码统一通过这里完成少量手写 JSON 处理，避免再散落多套 JSON 运行时或多个
 * ObjectMapper 实例。HTTP 请求响应仍然交给 Spring Boot 自动配置的 JsonMapper。</p>
 */
public final class JacksonUtils {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private JacksonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    public static String toJsonOrString(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public static <T> T readValue(String json, Class<T> type) {
        try {
            return JSON_MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON反序列化失败", e);
        }
    }

    public static <T> T readValue(String json, TypeReference<T> type) {
        try {
            return JSON_MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON反序列化失败", e);
        }
    }

    public static JsonNode readTree(String json) {
        try {
            return JSON_MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON解析失败", e);
        }
    }

    public static <T> T treeToValue(JsonNode node, Class<T> type) {
        try {
            return JSON_MAPPER.treeToValue(node, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("JSON节点转换失败", e);
        }
    }
}
