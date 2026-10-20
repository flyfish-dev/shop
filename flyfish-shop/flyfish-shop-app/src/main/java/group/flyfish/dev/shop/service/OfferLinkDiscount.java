package group.flyfish.dev.shop.service;

import group.flyfish.dev.shop.domain.po.ShopOfferLink;

/**
 * 专享链接在下单时解析出的结算选择。
 *
 * <p>促销链接只使用优惠结果；已签约已付款链接还携带管理员确认依据，供订单形成不可变快照。</p>
 */
public record OfferLinkDiscount(Long offerLinkId,
                                CouponDiscount discount,
                                ShopOfferLink.Purpose purpose,
                                String currency,
                                String contractReference,
                                String paymentReference,
                                String confirmationNote,
                                String confirmedBy) {

    public boolean externallySettled() {
        return purpose == ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT;
    }
}
