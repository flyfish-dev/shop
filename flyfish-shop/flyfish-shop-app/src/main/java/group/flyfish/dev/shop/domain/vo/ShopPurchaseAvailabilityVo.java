package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品购买可用性。
 */
@Data
public class ShopPurchaseAvailabilityVo {

    private boolean purchasable;

    private String reasonCode;

    private String message;

    private String provider;

    private List<String> repositories = List.of();

    private String conflictOrderNo;

    private List<String> conflictRepositories = List.of();

    private boolean remoteChecked;

    private String boundUsername;

    private List<String> openedRepositories = List.of();

    private List<String> pendingRepositories = List.of();

    private List<String> insufficientRepositories = List.of();

    public static ShopPurchaseAvailabilityVo available(String provider, List<String> repositories) {
        ShopPurchaseAvailabilityVo vo = new ShopPurchaseAvailabilityVo();
        vo.setPurchasable(true);
        vo.setProvider(provider);
        vo.setRepositories(repositories == null ? List.of() : repositories);
        return vo;
    }

    public static ShopPurchaseAvailabilityVo unavailable(String reasonCode, String message) {
        ShopPurchaseAvailabilityVo vo = new ShopPurchaseAvailabilityVo();
        vo.setPurchasable(false);
        vo.setReasonCode(reasonCode);
        vo.setMessage(message);
        return vo;
    }
}
