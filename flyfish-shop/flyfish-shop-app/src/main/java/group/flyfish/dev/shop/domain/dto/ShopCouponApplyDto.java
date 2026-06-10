package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.annotations.data.Property;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 优惠券试算数据。
 */
@Data
public class ShopCouponApplyDto {

    @NotNull(message = "商品不能为空")
    @Property("商品id")
    private String itemId;

    @Property("商品数量")
    private Integer count;

    @NotBlank(message = "优惠券编码不能为空")
    @Property("优惠券编码")
    private String couponCode;

    @Property("打赏金额")
    private BigDecimal donationAmount;
}
