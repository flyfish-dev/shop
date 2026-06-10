package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.domain.po.ShopOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShopOrderVo {

    private String orderNo;

    private Long itemId;

    private String itemName;

    private String itemType;

    private String itemTypeName;

    private Long buyerId;

    private String buyerName;

    private String buyerAvatar;

    private String buyerPhone;

    private String buyerEmail;

    private Integer count;

    private BigDecimal amount;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private String couponCode;

    private ShopOrder.Status status;

    private ShopOrder.DeliveryStatus deliveryStatus;

    private String deliveryMode;

    private String deliveryModeName;

    private String deliveryMessage;

    private Boolean extractable;

    private String paymentProvider;

    private String transactionCode;

    private LocalDateTime createTime;

    private LocalDateTime paidTime;

    private LocalDateTime expireTime;
}
