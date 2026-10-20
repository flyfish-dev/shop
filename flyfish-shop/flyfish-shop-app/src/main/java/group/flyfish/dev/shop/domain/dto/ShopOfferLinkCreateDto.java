package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 专享购买链接创建数据。
 */
@Data
public class ShopOfferLinkCreateDto {

    @Size(max = 64, message = "专享链接名称不能超过64个字符")
    private String name;

    private Long skuId;

    private ShopOfferLink.Purpose purpose = ShopOfferLink.Purpose.PROMOTION;

    private ShopOfferLink.Type type;

    @DecimalMin(value = "0.01", message = "优惠值必须大于0")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.00", message = "最高减免不能小于0")
    private BigDecimal maxDiscountAmount;

    @Min(value = 1, message = "有效天数不能小于1天")
    @Max(value = 30, message = "有效天数不能超过30天")
    private Integer validDays = 5;

    private Long recipientUserId;

    @Size(max = 128, message = "客户名称不能超过128个字符")
    private String recipientName;

    @Size(max = 128, message = "合同确认编号不能超过128个字符")
    private String contractReference;

    @Size(max = 128, message = "付款确认编号不能超过128个字符")
    private String paymentReference;

    @Size(max = 500, message = "确认备注不能超过500个字符")
    private String confirmationNote;

    @DecimalMin(value = "0.01", message = "已确认付款金额必须大于0")
    private BigDecimal settlementAmount;

    @Size(max = 3, message = "结算币种格式不正确")
    private String settlementCurrency = "CNY";
}
