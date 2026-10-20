package group.flyfish.dev.shop.converter;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.DeliveryExperienceParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.ShopOrderFormParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
            case DONATION, SERVICE_PACKAGE -> throw new ServiceException("该商品类型无需交付参数！");
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

    static DeliveryExperienceParamValue deliveryExperience(String params) {
        String nestedParams = extractNestedParams(params, DeliveryExperienceParamValue.PARAM_KEY);
        if (StringUtils.equals(nestedParams, params)) {
            return new DeliveryExperienceParamValue();
        }
        return parse(nestedParams, DeliveryExperienceParamValue.class, DeliveryExperienceParamValue::new,
                DeliveryExperienceParamValue::normalize);
    }

    /**
     * 仅判断是否启用了交付体验。该方法不做完整校验，确保交付策略筛选阶段不会抛出配置异常。
     */
    static boolean deliveryExperienceEnabled(String params) {
        if (StringUtils.isBlank(params)) {
            return false;
        }
        try {
            JsonNode parsed = JacksonUtils.readTree(params);
            int depth = 0;
            while (parsed != null && parsed.isTextual() && depth < 3) {
                parsed = JacksonUtils.readTree(parsed.asText());
                depth++;
            }
            JsonNode experience = parsed == null ? null : parsed.get(DeliveryExperienceParamValue.PARAM_KEY);
            return experience != null && experience.path("enabled").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    static LicenseDeliveryParamValue licenseDelivery(String params, String fallbackName) {
        LicenseDeliveryParamValue value = licenseDeliveryConfiguration(params);
        if (value.getOrderForm() == null) {
            String nestedParams = extractNestedParams(params, "licenseDelivery");
            if (!StringUtils.equals(nestedParams, params)) {
                String orderFormParams = extractNestedParams(params, ShopOrderFormParamValue.PARAM_KEY);
                if (!StringUtils.equals(orderFormParams, params)) {
                    value.setOrderForm(parse(orderFormParams, ShopOrderFormParamValue.class,
                            ShopOrderFormParamValue::new, ignored -> {
                            }));
                }
            }
        }
        value.normalize(fallbackName);
        return value;
    }

    /**
     * 解析一个订单内的全部授权组件。老商品仍返回单个 default 组件，组合商品通过
     * licenseDeliveries 数组显式声明每个产品，禁止同一订单重复产品编码。
     */
    static List<LicenseDeliveryParamValue> licenseDeliveries(String params, String fallbackName) {
        if (StringUtils.isBlank(params)) {
            return List.of(licenseDelivery(params, fallbackName));
        }
        try {
            JsonNode root = unwrap(params);
            JsonNode components = root == null ? null : root.get("licenseDeliveries");
            if (components == null || !components.isArray() || components.isEmpty()) {
                return List.of(licenseDelivery(params, fallbackName));
            }
            ShopOrderFormParamValue sharedOrderForm = parseOrderForm(root);
            Set<String> products = new LinkedHashSet<>();
            List<LicenseDeliveryParamValue> values = new ArrayList<>();
            for (JsonNode component : components) {
                LicenseDeliveryParamValue value = JacksonUtils.treeToValue(component, LicenseDeliveryParamValue.class);
                if (value.getOrderForm() == null) {
                    value.setOrderForm(sharedOrderForm);
                }
                value.normalize(fallbackName);
                if (!products.add(value.getProduct())) {
                    throw new ServiceException("组合授权包含重复产品：" + value.getProduct());
                }
                values.add(value);
            }
            return List.copyOf(values);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("组合授权参数异常：" + e.getMessage());
        }
    }

    /**
     * 只解析授权商品的购买策略配置，不执行部署域名等交付参数校验。
     *
     * <p>维护协议和授权续费 SKU 在下单前就需要读取，但续费 SKU 本身并不是一张待签发许可证，
     * 因而不应被要求重复配置来源许可证已有的域名、功能和部署限制。</p>
     */
    static LicenseDeliveryParamValue licenseDeliveryConfiguration(String params) {
        String nestedParams = extractNestedParams(params, "licenseDelivery");
        LicenseDeliveryParamValue value = parse(nestedParams,
                LicenseDeliveryParamValue.class, LicenseDeliveryParamValue::new, ignored -> {
                });
        value.normalizePurchaseConfiguration();
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

    private static JsonNode unwrap(String params) {
        JsonNode parsed = JacksonUtils.readTree(params);
        int depth = 0;
        while (parsed != null && parsed.isTextual() && depth < 3) {
            parsed = JacksonUtils.readTree(parsed.asText());
            depth++;
        }
        return parsed;
    }

    private static ShopOrderFormParamValue parseOrderForm(JsonNode root) {
        JsonNode node = root == null ? null : root.get(ShopOrderFormParamValue.PARAM_KEY);
        if (node == null || node.isNull()) {
            return null;
        }
        return JacksonUtils.treeToValue(node, ShopOrderFormParamValue.class);
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
