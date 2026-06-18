package group.flyfish.dev.shop.converter;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopItem;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析商品参数中的自动交付动作。
 * <p>未配置动作的老商品继续按商品类型推导，保持线上兼容。</p>
 */
public final class ShopItemDeliveryPlan {

    private static final String KEY_DELIVERY_ACTIONS = "deliveryActions";

    private ShopItemDeliveryPlan() {
    }

    public static List<ShopDeliveryAction> actions(ShopItem item) {
        if (item == null) {
            return List.of();
        }
        List<ShopDeliveryAction> configured = parseConfiguredActions(item.getParams());
        if (!configured.isEmpty()) {
            return configured;
        }
        return ShopDeliveryAction.defaultActions(item.getType());
    }

    public static boolean hasAction(ShopItem item, ShopDeliveryAction action) {
        return actions(item).contains(action);
    }

    public static List<String> actionNames(ShopItem item) {
        return actions(item).stream().map(Enum::name).toList();
    }

    public static List<ShopDeliveryAction> normalize(List<ShopDeliveryAction> actions, ShopItem.Type fallbackType) {
        Set<ShopDeliveryAction> normalized = new LinkedHashSet<>();
        if (actions != null) {
            for (ShopDeliveryAction action : actions) {
                if (action != null) {
                    normalized.add(action);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.addAll(ShopDeliveryAction.defaultActions(fallbackType));
        }
        return new ArrayList<>(normalized);
    }

    private static List<ShopDeliveryAction> parseConfiguredActions(String params) {
        if (StringUtils.isBlank(params)) {
            return List.of();
        }
        try {
            Map<String, Object> map = readParamMap(params);
            Object raw = map.get(KEY_DELIVERY_ACTIONS);
            if (!(raw instanceof Iterable<?> values)) {
                return List.of();
            }
            Set<ShopDeliveryAction> actions = new LinkedHashSet<>();
            for (Object value : values) {
                ShopDeliveryAction action = parseAction(value);
                if (action != null) {
                    actions.add(action);
                }
            }
            return new ArrayList<>(actions);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Map<String, Object> readParamMap(String params) {
        Object parsed = JacksonUtils.readValue(params, Object.class);
        int depth = 0;
        while (parsed instanceof String text && depth < 3) {
            parsed = JacksonUtils.readValue(text, Object.class);
            depth++;
        }
        if (parsed instanceof Map<?, ?> map) {
            return JacksonUtils.readValue(JacksonUtils.toJson(map), new TypeReference<>() {
            });
        }
        return Map.of();
    }

    private static ShopDeliveryAction parseAction(Object value) {
        String name = StringUtils.upperCase(StringUtils.trimToNull(String.valueOf(value)));
        if (name == null) {
            return null;
        }
        try {
            return ShopDeliveryAction.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
