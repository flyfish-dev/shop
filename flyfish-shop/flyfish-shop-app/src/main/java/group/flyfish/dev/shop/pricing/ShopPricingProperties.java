package group.flyfish.dev.shop.pricing;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Data
@Validated
@ConfigurationProperties(prefix = "shop.pricing")
public class ShopPricingProperties {

    @DecimalMin(value = "0.01")
    private BigDecimal cnyPerUsd = new BigDecimal("6.78");
}
