package group.flyfish.dev.shop.pricing;

import group.flyfish.dev.shop.domain.po.ShopItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopPricingServiceTest {

    private final ShopPricingProperties properties = new ShopPricingProperties();
    private final ShopPricingService service = new ShopPricingService(properties);

    @Test
    void prefersManualUsdPrice() {
        assertEquals(new BigDecimal("18.88"),
                service.effectiveUsdPrice(new BigDecimal("99.00"), new BigDecimal("18.88")));
    }

    @Test
    void convertsCnyUsingConfiguredSnapshot() {
        properties.setCnyPerUsd(new BigDecimal("6.78"));

        assertEquals(new BigDecimal("58.85"),
                service.effectiveUsdPrice(new BigDecimal("399.00"), null));
    }

    @Test
    void keepsEnglishDonationMinimumAtOneDollar() {
        ShopItem item = new ShopItem();
        item.setPrice(new BigDecimal("5.00"));

        assertEquals(new BigDecimal("1.00"), service.minimumDonationUsd(item));
    }
}
