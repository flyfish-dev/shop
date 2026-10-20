package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.license.LicenseOriginPolicy;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Purchase-time form configured on a shop item.
 */
@Data
public class ShopOrderFormParamValue {

    public static final String PARAM_KEY = "orderForm";
    public static final String TARGET_LICENSE_ALLOWED_ORIGINS = "license.allowedOrigins";
    public static final String TARGET_LICENSE_DEPLOYMENT_MODE = "license.deploymentMode";
    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final int DEFAULT_MAX_LENGTH = 512;

    private Boolean enabled;

    private String title;

    private String description;

    private List<Field> fields = new ArrayList<>();

    public static ShopOrderFormParamValue fromItem(ShopItem item) {
        if (item == null || StringUtils.isBlank(item.getParams())) {
            return disabled();
        }
        String nested = ShopItemParamValue.extractNestedParams(item.getParams(), PARAM_KEY);
        if (StringUtils.equals(nested, item.getParams())) {
            return disabled();
        }
        try {
            ShopOrderFormParamValue value = JacksonUtils.readValue(nested, ShopOrderFormParamValue.class);
            value.normalize();
            return value;
        } catch (Exception e) {
            throw new BusinessException("ORDER_FORM_CONFIG_INVALID", "商品下单表单配置异常：" + e.getMessage());
        }
    }

    public static Map<String, Object> readOrderFormValues(ShopOrder order) {
        if (order == null || StringUtils.isBlank(order.getProperties())) {
            return Map.of();
        }
        try {
            Map<String, Object> properties = JacksonUtils.readValue(order.getProperties(), new TypeReference<>() {
            });
            return asMap(properties.get(PARAM_KEY));
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static Map<String, Object> readProperties(ShopOrder order) {
        if (order == null || StringUtils.isBlank(order.getProperties())) {
            return Map.of();
        }
        try {
            return JacksonUtils.readValue(order.getProperties(), new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static void applyLicenseOverrides(LicenseDeliveryParamValue param, ShopItem item, ShopOrder order) {
        if (param == null) {
            return;
        }
        ShopOrderFormParamValue form = fromItem(item);
        Map<String, Object> values = readOrderFormValues(order);
        form.applyLicenseOverrides(param, values);
    }

    public Map<String, Object> validateSubmitted(ShopOrderDto dto) {
        normalize();
        if (!isEnabled()) {
            return Map.of();
        }
        Map<String, Object> rawValues = asMap(dto == null || dto.getProperties() == null
                ? null
                : dto.getProperties().get(PARAM_KEY));
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Field field : fields) {
            if (!field.isVisible(rawValues)) {
                continue;
            }
            Object normalizedValue = normalizeSubmittedValue(field, rawValues.get(field.getKey()));
            if (normalizedValue != null) {
                normalized.put(field.getKey(), normalizedValue);
            }
        }
        return normalized;
    }

    public void applyLicenseOverrides(LicenseDeliveryParamValue param, Map<String, Object> values) {
        normalize();
        if (!isEnabled() || values == null || values.isEmpty()) {
            return;
        }
        Set<String> allowedOrigins = new LinkedHashSet<>();
        for (Field field : fields) {
            if (!field.isVisible(values)) {
                continue;
            }
            if (TARGET_LICENSE_DEPLOYMENT_MODE.equals(field.getTarget())) {
                Object value = values.get(field.getKey());
                param.setDeploymentMode(value == null
                        ? null
                        : StringUtils.trimToNull(String.valueOf(value)));
                continue;
            }
            if (!TARGET_LICENSE_ALLOWED_ORIGINS.equals(field.getTarget())) {
                continue;
            }
            Object value = values.get(field.getKey());
            allowedOrigins.addAll(toOrigins(value));
        }
        if (!allowedOrigins.isEmpty()) {
            int maxDeployments = "commercial".equalsIgnoreCase(param.getEdition())
                    ? LicenseOriginPolicy.COMMERCIAL_MAX_SPECIFIC_ORIGINS
                    : param.getMaxDeployments() == null || param.getMaxDeployments() <= 0
                            ? 1
                            : param.getMaxDeployments();
            if (allowedOrigins.size() > maxDeployments) {
                throw new BusinessException("ORDER_FORM_TOO_MANY_ORIGINS",
                        "授权域名数量不能超过 " + maxDeployments + " 个");
            }
            param.setAllowedOrigins(new ArrayList<>(allowedOrigins));
        }
        param.normalize(null);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled) && fields != null && !fields.isEmpty();
    }

    /**
     * 判断下单表单是否会覆盖指定交付参数。
     *
     * <p>商品保存时允许授权域名使用占位值或暂不填写，真正签发前再由已校验的下单表单覆盖。
     * 该方法用于区分“商品配置阶段”和“许可证签发阶段”，避免过早拒绝正常商品配置。</p>
     */
    public boolean overridesTarget(String target) {
        normalize();
        return isEnabled() && StringUtils.isNotBlank(target)
                && fields.stream().anyMatch(field -> StringUtils.equals(target, field.getTarget()));
    }

    private void normalize() {
        enabled = Boolean.TRUE.equals(enabled);
        title = StringUtils.trimToNull(title);
        description = StringUtils.trimToNull(description);
        List<Field> normalizedFields = new ArrayList<>();
        if (fields != null) {
            Set<String> keys = new LinkedHashSet<>();
            for (Field field : fields) {
                Field normalized = normalizeField(field);
                if (normalized == null || !keys.add(normalized.getKey())) {
                    continue;
                }
                normalizedFields.add(normalized);
            }
        }
        fields = normalizedFields;
    }

    private Field normalizeField(Field field) {
        if (field == null) {
            return null;
        }
        String key = StringUtils.trimToNull(field.getKey());
        if (key == null || !FIELD_KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException("ORDER_FORM_FIELD_INVALID", "商品下单表单字段配置异常");
        }
        Field normalized = new Field();
        normalized.setKey(key);
        normalized.setLabel(StringUtils.defaultIfBlank(StringUtils.trimToNull(field.getLabel()), key));
        normalized.setType(normalizeType(field.getType()));
        normalized.setRequired(Boolean.TRUE.equals(field.getRequired()));
        normalized.setRequiredValue(field.getRequiredValue());
        normalized.setTarget(StringUtils.trimToNull(field.getTarget()));
        normalized.setNormalize(StringUtils.lowerCase(StringUtils.trimToNull(field.getNormalize()), Locale.ROOT));
        normalized.setPlaceholder(StringUtils.trimToNull(field.getPlaceholder()));
        normalized.setHelp(StringUtils.trimToNull(field.getHelp()));
        normalized.setMaxLength(normalizeMaxLength(field.getMaxLength()));
        normalized.setMaxItems(normalizeMaxItems(field.getMaxItems()));
        normalized.setVisibleWhen(normalizeVisibleWhen(field.getVisibleWhen()));
        normalized.setOptions(normalizeOptions(field.getOptions()));
        return normalized;
    }

    private String normalizeType(String value) {
        String type = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
        if (Set.of("text", "textarea", "url", "checkbox", "select").contains(type)) {
            return type;
        }
        return "text";
    }

    private Integer normalizeMaxLength(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_MAX_LENGTH;
        }
        return Math.min(value, 2048);
    }

    private Integer normalizeMaxItems(Integer value) {
        return value == null || value <= 0 ? null : Math.min(value, 100);
    }

    private VisibleWhen normalizeVisibleWhen(VisibleWhen value) {
        if (value == null || StringUtils.isBlank(value.getKey()) || value.getValue() == null) {
            return null;
        }
        VisibleWhen normalized = new VisibleWhen();
        normalized.setKey(StringUtils.trim(value.getKey()));
        normalized.setValue(value.getValue());
        return normalized;
    }

    private List<Option> normalizeOptions(List<Option> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<Option> normalized = new ArrayList<>();
        for (Option option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            Option copy = new Option();
            copy.setValue(option.getValue());
            copy.setLabel(StringUtils.defaultIfBlank(StringUtils.trimToNull(option.getLabel()),
                    String.valueOf(option.getValue())));
            normalized.add(copy);
        }
        return normalized;
    }

    private Object normalizeSubmittedValue(Field field, Object raw) {
        if ("checkbox".equals(field.getType())) {
            Boolean value = toBoolean(raw);
            if (field.isRequired() && !Objects.equals(value, requiredCheckboxValue(field))) {
                throw new BusinessException("ORDER_FORM_REQUIRED", field.getLabel() + "不能为空");
            }
            return value == null ? null : value;
        }
        String value = StringUtils.trimToNull(raw == null ? null : String.valueOf(raw));
        if (value == null) {
            if (field.isRequired()) {
                throw new BusinessException("ORDER_FORM_REQUIRED", field.getLabel() + "不能为空");
            }
            return null;
        }
        if (value.length() > field.getMaxLength()) {
            throw new BusinessException("ORDER_FORM_TOO_LONG", field.getLabel() + "长度过长");
        }
        if ("url".equals(field.getType())) {
            return shouldNormalizeOrigin(field) ? normalizeOrigin(value, field.getLabel()) : validateUrl(value, field.getLabel());
        }
        if ("textarea".equals(field.getType()) && shouldNormalizeOrigin(field)) {
            List<String> origins = toOrigins(raw);
            if (origins.isEmpty()) {
                if (field.isRequired()) {
                    throw new BusinessException("ORDER_FORM_REQUIRED", field.getLabel() + "不能为空");
                }
                return null;
            }
            if (field.getMaxItems() != null && origins.size() > field.getMaxItems()) {
                throw new BusinessException("ORDER_FORM_TOO_MANY_ORIGINS",
                        field.getLabel() + "不能超过 " + field.getMaxItems() + " 个");
            }
            return origins;
        }
        if ("select".equals(field.getType()) && !field.getOptions().isEmpty()) {
            boolean matched = field.getOptions().stream()
                    .anyMatch(option -> StringUtils.equals(String.valueOf(option.getValue()), value));
            if (!matched) {
                throw new BusinessException("ORDER_FORM_INVALID", field.getLabel() + "不在可选范围内");
            }
        }
        return value;
    }

    private Boolean requiredCheckboxValue(Field field) {
        Object required = field.getRequiredValue();
        Boolean value = toBoolean(required);
        return value == null ? Boolean.TRUE : value;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = StringUtils.lowerCase(StringUtils.trimToNull(String.valueOf(value)), Locale.ROOT);
        if (text == null) {
            return null;
        }
        if (Set.of("true", "1", "yes", "y", "on").contains(text)) {
            return Boolean.TRUE;
        }
        if (Set.of("false", "0", "no", "n", "off").contains(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean shouldNormalizeOrigin(Field field) {
        return "origin".equals(field.getNormalize()) || TARGET_LICENSE_ALLOWED_ORIGINS.equals(field.getTarget());
    }

    private String validateUrl(String value, String label) {
        URI uri = parseHttpUri(value, label);
        return uri.toString();
    }

    private String normalizeOrigin(String value, String label) {
        return LicenseOriginPolicy.normalizeFormOrigin(value, label);
    }

    private URI parseHttpUri(String value, String label) {
        try {
            URI uri = URI.create(value);
            String scheme = StringUtils.lowerCase(uri.getScheme(), Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || StringUtils.isBlank(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception e) {
            throw new BusinessException("ORDER_FORM_URL_INVALID", label + "必须是 http 或 https 地址");
        }
    }

    private List<String> toOrigins(Object value) {
        if (value == null) {
            return List.of();
        }
        List<?> rawValues = value instanceof Collection<?> collection
                ? new ArrayList<>(collection)
                : splitOriginText(value);
        Set<String> origins = new LinkedHashSet<>();
        for (Object raw : rawValues) {
            String text = StringUtils.trimToNull(raw == null ? null : String.valueOf(raw));
            if (text != null) {
                origins.add(normalizeOrigin(text, "授权域名"));
            }
        }
        return new ArrayList<>(origins);
    }

    private List<String> splitOriginText(Object value) {
        String text = StringUtils.trimToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            return List.of();
        }
        return List.of(text.split("[\\s,，]+"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    out.put(String.valueOf(key), mapValue);
                }
            });
            return out;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return JacksonUtils.readValue(text, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static ShopOrderFormParamValue disabled() {
        ShopOrderFormParamValue value = new ShopOrderFormParamValue();
        value.setEnabled(false);
        value.setFields(List.of());
        return value;
    }

    @Data
    public static class Field {

        private String key;

        private String label;

        private String type;

        private Boolean required;

        private Object requiredValue;

        private String target;

        private String normalize;

        private String placeholder;

        private String help;

        private Integer maxLength;

        private Integer maxItems;

        private VisibleWhen visibleWhen;

        private List<Option> options = new ArrayList<>();

        public boolean isRequired() {
            return Boolean.TRUE.equals(required);
        }

        public boolean isVisible(Map<String, Object> values) {
            return visibleWhen == null || Objects.equals(String.valueOf(visibleWhen.getValue()),
                    String.valueOf(values == null ? null : values.get(visibleWhen.getKey())));
        }
    }

    @Data
    public static class VisibleWhen {

        private String key;

        private Object value;
    }

    @Data
    public static class Option {

        private String label;

        private Object value;
    }
}
