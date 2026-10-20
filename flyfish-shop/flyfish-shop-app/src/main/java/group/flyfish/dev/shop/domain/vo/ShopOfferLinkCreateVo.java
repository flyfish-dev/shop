package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import lombok.Getter;
import lombok.Setter;

/**
 * 专享购买链接创建结果。完整链接仅在本次响应中返回。
 */
@Getter
@Setter
public class ShopOfferLinkCreateVo extends ShopOfferLinkVo {

    private String shareUrl;

    public ShopOfferLinkCreateVo(ShopOfferLink link, String shareUrl) {
        super(link);
        this.shareUrl = shareUrl;
    }
}
