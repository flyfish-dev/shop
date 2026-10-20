package group.flyfish.dev.shop.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 专享购买链接试算数据。
 */
@Data
public class ShopOfferLinkPreviewDto {

    @NotBlank(message = "专享购买链接令牌不能为空")
    @Size(max = 128, message = "专享购买链接令牌格式无效")
    private String token;

    @NotNull(message = "商品id不能为空")
    private Long itemId;

    private Long skuId;

    private BigDecimal donationAmount;

    private String paymentCurrency;
}
