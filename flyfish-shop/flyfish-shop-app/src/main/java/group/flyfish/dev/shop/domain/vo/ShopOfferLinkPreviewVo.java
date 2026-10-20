package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import group.flyfish.dev.shop.service.CouponDiscount;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专享购买链接公开试算结果，不包含内部优惠券编码。
 */
@Data
public class ShopOfferLinkPreviewVo {

    private Long itemId;
    private Long skuId;
    private ShopOfferLink.Purpose purpose;
    private ShopOfferLink.Type type;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private String currency;
    private LocalDateTime expiresAt;

    public ShopOfferLinkPreviewVo(ShopOfferLink link, CouponDiscount discount, String currency) {
        this.itemId = link.getItemId();
        this.skuId = link.getSkuId();
        this.purpose = link.getPurpose() == null
                ? ShopOfferLink.Purpose.PROMOTION : link.getPurpose();
        this.type = link.getType();
        this.originalAmount = discount.originalAmount();
        this.discountAmount = discount.discountAmount();
        this.payableAmount = discount.payableAmount();
        this.currency = currency;
        this.expiresAt = link.getExpiresAt();
    }
}
