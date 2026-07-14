package group.flyfish.dev.shop.pricing;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.vo.ShopPricingVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class ShopPricingService {

    private static final BigDecimal MINIMUM_USD_PAYMENT = new BigDecimal("0.50");
    private static final BigDecimal MINIMUM_USD_DONATION = new BigDecimal("1.00");

    private final ShopPricingProperties properties;

    public BigDecimal effectiveUsdPrice(ShopItem item) {
        if (item == null) {
            return null;
        }
        BigDecimal effective = effectiveUsdPrice(item.getPrice(), item.getUsdPrice());
        return isDonationItem(item) && effective != null ? effective.max(MINIMUM_USD_DONATION) : effective;
    }

    public BigDecimal effectiveUsdPrice(BigDecimal cnyPrice, BigDecimal manualUsdPrice) {
        if (manualUsdPrice != null) {
            if (manualUsdPrice.signum() <= 0) {
                throw new BusinessException("USD_PRICE_INVALID", "美元价格必须大于0");
            }
            return manualUsdPrice.setScale(2, RoundingMode.HALF_UP);
        }
        if (cnyPrice == null || cnyPrice.signum() <= 0) {
            return null;
        }
        BigDecimal converted = cnyPrice.divide(properties.getCnyPerUsd(), 2, RoundingMode.HALF_UP);
        return converted.max(MINIMUM_USD_PAYMENT);
    }

    public BigDecimal minimumDonationUsd(ShopItem item) {
        BigDecimal effective = effectiveUsdPrice(item);
        return effective == null ? MINIMUM_USD_DONATION : effective.max(MINIMUM_USD_DONATION);
    }

    public BigDecimal cnyPerUsd() {
        return properties.getCnyPerUsd().setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public ShopPricingVo pricing() {
        return new ShopPricingVo(cnyPerUsd());
    }

    private boolean isDonationItem(ShopItem item) {
        return item.getType() == ShopItem.Type.DONATION
                || item.getType() == ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS;
    }
}
