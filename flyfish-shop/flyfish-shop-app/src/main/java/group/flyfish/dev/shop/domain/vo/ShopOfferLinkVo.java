package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专享购买链接管理展示数据。
 */
@Data
public class ShopOfferLinkVo {

    private Long id;
    private String name;
    private String tokenHint;
    private Long itemId;
    private String itemName;
    private Long skuId;
    private String skuName;
    private ShopOfferLink.Purpose purpose;
    private ShopOfferLink.Type type;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private String status;
    private LocalDateTime expiresAt;
    private String reservedOrderNo;
    private String usedOrderNo;
    private Long recipientUserId;
    private String recipientName;
    private String contractReference;
    private String paymentReference;
    private String confirmationNote;
    private BigDecimal settlementAmount;
    private String settlementCurrency;
    private LocalDateTime createTime;

    public ShopOfferLinkVo(ShopOfferLink link) {
        this.id = link.getId();
        this.name = link.getName();
        this.tokenHint = link.getTokenHint();
        this.itemId = link.getItemId();
        this.itemName = link.getItemName();
        this.skuId = link.getSkuId();
        this.skuName = link.getSkuName();
        this.purpose = link.getPurpose() == null
                ? ShopOfferLink.Purpose.PROMOTION : link.getPurpose();
        this.type = link.getType();
        this.discountValue = link.getDiscountValue();
        this.maxDiscountAmount = link.getMaxDiscountAmount();
        this.status = currentStatus(link);
        this.expiresAt = link.getExpiresAt();
        this.reservedOrderNo = link.getReservedOrderNo();
        this.usedOrderNo = link.getUsedOrderNo();
        this.recipientUserId = link.getRecipientUserId();
        this.recipientName = link.getRecipientName();
        this.contractReference = link.getContractReference();
        this.paymentReference = link.getPaymentReference();
        this.confirmationNote = link.getConfirmationNote();
        this.settlementAmount = link.getSettlementAmount();
        this.settlementCurrency = link.getSettlementCurrency();
        this.createTime = link.getCreateTime();
    }

    private String currentStatus(ShopOfferLink link) {
        if (link.getStatus() == ShopOfferLink.Status.ACTIVE
                && link.getExpiresAt() != null && LocalDateTime.now().isAfter(link.getExpiresAt())) {
            return "EXPIRED";
        }
        return link.getStatus() == null ? "UNKNOWN" : link.getStatus().name();
    }
}
