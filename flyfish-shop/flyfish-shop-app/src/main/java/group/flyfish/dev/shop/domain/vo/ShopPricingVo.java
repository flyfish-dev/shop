package group.flyfish.dev.shop.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ShopPricingVo {

    private BigDecimal cnyPerUsd;
}
