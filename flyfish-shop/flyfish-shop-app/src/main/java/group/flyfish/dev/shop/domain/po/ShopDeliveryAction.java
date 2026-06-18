package group.flyfish.dev.shop.domain.po;

import lombok.Getter;

import java.util.List;

/**
 * 商品自动交付动作。
 * <p>商品类型描述售卖对象，交付动作描述支付完成后系统需要执行的自动化步骤。</p>
 */
@Getter
public enum ShopDeliveryAction {

    GIT_REPOSITORY_ACCESS("代码仓库开通"),
    DIGITAL_DOWNLOAD("数字提货"),
    LICENSE("授权发放");

    private final String title;

    ShopDeliveryAction(String title) {
        this.title = title;
    }

    public static List<ShopDeliveryAction> defaultActions(ShopItem.Type type) {
        if (type == null) {
            return List.of();
        }
        return switch (type) {
            case GIT_REPOSITORY_ACCESS, GIT_REPOSITORY_DONATION_ACCESS -> List.of(GIT_REPOSITORY_ACCESS);
            case DIGITAL_DOWNLOAD -> List.of(DIGITAL_DOWNLOAD);
            case LICENSE -> List.of(LICENSE);
            case SERVICE_PACKAGE -> List.of();
        };
    }
}
