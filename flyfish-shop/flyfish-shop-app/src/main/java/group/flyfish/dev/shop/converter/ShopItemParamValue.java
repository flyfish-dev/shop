package group.flyfish.dev.shop.converter;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 订单项参数
 *
 * @author wangyu
 */
public interface ShopItemParamValue {

    /**
     * 从商品生成实例
     *
     * @param item 商品
     * @return 结果
     */
    static ShopItemParamValue from(ShopItem item) {
        return switch (item.getType()) {
            case GIT_REPOSITORY_ACCESS, GIT_REPOSITORY_DONATION_ACCESS -> gitRepositoryAccess(item.getParams());
            case DIGITAL_DOWNLOAD -> digitalDelivery(item.getParams());
            case LICENSE -> licenseDelivery(item.getParams(), item.getName());
            default -> throw new ServiceException("商品信息异常！");
        };
    }

    static GitRepositoryAccessParamValue gitRepositoryAccess(String params) {
        return parse(params, GitRepositoryAccessParamValue.class, GitRepositoryAccessParamValue::new,
                GitRepositoryAccessParamValue::normalize);
    }

    static DigitalDeliveryParamValue digitalDelivery(String params) {
        return parse(params, DigitalDeliveryParamValue.class, DigitalDeliveryParamValue::new,
                DigitalDeliveryParamValue::normalize);
    }

    static LicenseDeliveryParamValue licenseDelivery(String params, String fallbackName) {
        LicenseDeliveryParamValue value = parse(extractNestedParams(params, "licenseDelivery"),
                LicenseDeliveryParamValue.class, LicenseDeliveryParamValue::new, ignored -> {
                });
        value.normalize(fallbackName);
        return value;
    }

    static String extractNestedParams(String params, String key) {
        if (StringUtils.isBlank(params)) {
            return params;
        }
        try {
            JsonNode parsed = JacksonUtils.readTree(params);
            int depth = 0;
            while (parsed != null && parsed.isTextual() && depth < 3) {
                parsed = JacksonUtils.readTree(parsed.asText());
                depth++;
            }
            JsonNode nested = parsed == null ? null : parsed.get(key);
            if (nested == null || nested.isNull()) {
                return params;
            }
            return JacksonUtils.toJson(JacksonUtils.treeToValue(nested, Object.class));
        } catch (Exception e) {
            return params;
        }
    }

    private static <T> T parse(String params, Class<T> type, java.util.function.Supplier<T> fallback,
                               java.util.function.Consumer<T> normalizer) {
        if (StringUtils.isBlank(params)) {
            T value = fallback.get();
            normalizer.accept(value);
            return value;
        }
        try {
            JsonNode parsed = JacksonUtils.readTree(params);
            int depth = 0;
            while (parsed != null && parsed.isTextual() && depth < 3) {
                parsed = JacksonUtils.readTree(parsed.asText());
                depth++;
            }
            T value = JacksonUtils.treeToValue(parsed, type);
            normalizer.accept(value);
            return value;
        } catch (Exception e) {
            throw new ServiceException("商品参数异常：" + e.getMessage());
        }
    }

    /**
     * 转换为json
     *
     * @return 结果
     */
    default String toJSON() {
        return JacksonUtils.toJson(this);
    }
}
