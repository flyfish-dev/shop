package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.shop.domain.po.ShopCoupon;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券更新数据。
 */
@Data
public class ShopCouponUpdateDto {

    private String code;

    private String name;

    /** 为空表示全店可用。 */
    private Long itemId;

    /** 为空表示商品下全部SKU可用；设置时必须同时设置商品。 */
    private Long skuId;

    private ShopCoupon.Type type;

    @DecimalMin(value = "0.01", message = "优惠值必须大于0")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.00", message = "使用门槛不能小于0")
    private BigDecimal thresholdAmount;

    @DecimalMin(value = "0.00", message = "最高减免不能小于0")
    private BigDecimal maxDiscountAmount;

    @Min(value = 0, message = "发放总量不能小于0")
    private Integer totalCount;

    private Boolean enabled;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
