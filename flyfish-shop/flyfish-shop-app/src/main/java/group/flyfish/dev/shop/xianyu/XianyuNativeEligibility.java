package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.converter.impl.ShopOrderFormParamValue;

public final class XianyuNativeEligibility {
    private XianyuNativeEligibility() { }
    public static void require(ShopItem item) {
        if (item == null || ShopItemDeliveryPlan.hasAction(item, ShopDeliveryAction.GIT_REPOSITORY_ACCESS)
                || ShopItemDeliveryPlan.hasAction(item, ShopDeliveryAction.LICENSE)
                || ShopOrderFormParamValue.fromItem(item).isEnabled()) {
            throw new BusinessException("XIANYU_MINIAPP_REQUIRED", "该商品需完成购买信息确认");
        }
    }
}
