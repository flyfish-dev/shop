package group.flyfish.dev.shop.service.support.h5zhifu;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * H5支付签名工具。
 *
 * <p>官方签名规则是：去掉 sign 和空值，按参数名 ASCII 升序拼接 key=value，
 * 最后追加 &key=通信密钥，并对整个字符串做 MD5 后转大写。该工具同时支持 Map
 * 和 Java Bean，Bean 模式会优先读取 Jackson 注解里的真实 JSON 字段名，
 * 保证下单签名字段名和接口出参一致。</p>
 */
public final class H5ZhiFuSigner {

    private H5ZhiFuSigner() {
    }

    public static String sign(Map<String, ?> data, String key) {
        // TreeMap 使用 String 自然序，等价于文档要求的参数名 ASCII 字典序。
        String source = data.entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> StringUtils.isNotBlank(String.valueOf(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue()),
                        (left, right) -> right, TreeMap::new))
                .entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return DigestUtils.md5DigestAsHex((source + "&key=" + key).getBytes()).toUpperCase();
    }

    public static String sign(Object bean, String key) {
        Map<String, Object> data = new TreeMap<>();
        Class<?> type = bean.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    JsonName jsonName = JsonName.from(field);
                    data.put(jsonName.value(), field.get(bean));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            type = type.getSuperclass();
        }
        return sign(data, key);
    }

    private record JsonName(String value) {
        private static JsonName from(Field field) {
            JsonProperty property = field.getAnnotation(JsonProperty.class);
            if (property != null && StringUtils.isNotBlank(property.value())) {
                return new JsonName(property.value());
            }
            return new JsonName(toSnakeCase(field.getName()));
        }

        private static String toSnakeCase(String value) {
            return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        }
    }
}
