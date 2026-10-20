package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.annotations.data.Property;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 优惠券试算数据。
 */
@Data
public class ShopCouponApplyDto {

    @NotNull(message = "商品不能为空")
    @Property("商品id")
    private String itemId;

    @Property("SKU id")
    private String skuId;

    @Property("商品数量")
    private Integer count;

    @NotBlank(message = "优惠券编码不能为空")
    @Property("优惠券编码")
    private String couponCode;

    @Property("打赏金额")
    private BigDecimal donationAmount;

    @Property(value = "支付币种", description = "纯打赏支持CNY/USD")
    private String paymentCurrency;

    @Property("是否确认年度维护协议")
    private Boolean maintenanceAgreement;

    @Property("续费来源许可证号")
    private String sourceLicenseNo;

    @Property("续费生效日期")
    private LocalDate renewalStartDate;

    @Property("续费年数")
    private Integer renewalYears;
}
