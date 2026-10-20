package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.shop.domain.po.ShopOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ShopOrderVo {

    private group.flyfish.dev.xianyu.api.SalesChannel salesChannel;
    private String refundState;
    private java.math.BigDecimal refundAmount;

    private String orderNo;

    private Long itemId;

    private String itemName;

    private String itemType;

    private String itemTypeName;

    private Long skuId;

    private String skuCode;

    private String skuName;

    private String displayName;

    private Long buyerId;

    private String buyerName;

    private String buyerAvatar;

    private String buyerPhone;

    private String buyerEmail;

    private Integer count;

    private BigDecimal amount;

    private String currency;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private String couponCode;

    private ShopOrder.SettlementSource settlementSource;

    private String externalContractReference;

    private String externalPaymentReference;

    private String externalConfirmationNote;

    private String externalConfirmedBy;

    private Map<String, Object> properties;

    private Map<String, Object> itemSnapshot;

    private Map<String, Object> skuSnapshot;

    private ShopOrder.Status status;

    private ShopOrder.DeliveryStatus deliveryStatus;

    private String deliveryMode;

    private String deliveryModeName;

    private String deliveryMessage;

    private Boolean deliveryRetryable;

    private String deliveryFailureTask;

    private String deliveryFailureTaskName;

    private Boolean extractable;

    private Boolean permanentLicenseReissueable;

    private String paymentProvider;

    private String outerNo;

    private String transactionCode;

    private LocalDateTime createTime;

    private LocalDateTime paidTime;

    private LocalDateTime expireTime;
}
