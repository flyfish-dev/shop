package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.dto.ShopCouponCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopCouponUpdateDto;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import group.flyfish.dev.shop.domain.vo.ShopCouponVo;
import group.flyfish.dev.shop.repository.ShopCouponRepository;
import group.flyfish.dev.shop.service.CouponDiscount;
import group.flyfish.dev.shop.service.OfferLinkDiscount;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopOfferLinkService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShopCouponServiceImpl implements ShopCouponService {

    private final ShopCouponRepository shopCouponRepository;
    private final ShopOfferLinkService shopOfferLinkService;

    @Override
    public Flux<ShopCouponVo> listCoupons() {
        return shopCouponRepository.findAllOrderByCreateTimeDesc().map(ShopCouponVo::new);
    }

    @Override
    @Transactional
    public Mono<ShopCouponVo> createCoupon(ShopCouponCreateDto dto) {
        return resolveUniqueCode(dto.getCode(), null)
                .flatMap(code -> {
                    ShopCoupon coupon = new ShopCoupon();
                    coupon.setCode(code);
                    coupon.setName(StringUtils.trim(dto.getName()));
                    coupon.setSource(ShopCoupon.Source.MANUAL);
                    coupon.setItemId(dto.getItemId());
                    coupon.setSkuId(dto.getSkuId());
                    coupon.setType(dto.getType());
                    coupon.setDiscountValue(money(dto.getDiscountValue()));
                    coupon.setThresholdAmount(money(defaultMoney(dto.getThresholdAmount())));
                    coupon.setMaxDiscountAmount(money(defaultMoney(dto.getMaxDiscountAmount())));
                    coupon.setTotalCount(dto.getTotalCount());
                    coupon.setUsedCount(0);
                    coupon.setEnabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()));
                    coupon.setStartTime(dto.getStartTime());
                    coupon.setEndTime(dto.getEndTime());
                    if (coupon.getType() == ShopCoupon.Type.REDUCTION) {
                        coupon.setMaxDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    }
                    validateCoupon(coupon);
                    return shopCouponRepository.save(coupon);
                })
                .map(ShopCouponVo::new);
    }

    @Override
    @Transactional
    public Mono<ShopCouponVo> updateCoupon(Long id, ShopCouponUpdateDto dto) {
        return shopCouponRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("COUPON_NOT_FOUND", "优惠券不存在")))
                .map(this::requireManualCoupon)
                .flatMap(coupon -> resolveCodeForUpdate(dto.getCode(), coupon)
                        .map(code -> applyUpdate(coupon, dto, code)))
                .flatMap(coupon -> {
                    validateCoupon(coupon);
                    return shopCouponRepository.save(coupon);
                })
                .map(ShopCouponVo::new);
    }

    @Override
    public Mono<Void> deleteCoupon(Long id) {
        return shopCouponRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("COUPON_NOT_FOUND", "优惠券不存在")))
                .map(this::requireManualCoupon)
                .flatMap(shopCouponRepository::delete)
                .then();
    }

    @Override
    public Mono<CouponDiscount> applyCoupon(String code, BigDecimal originalAmount) {
        return applyCoupon(code, originalAmount, null, null);
    }

    @Override
    public Mono<CouponDiscount> applyCoupon(String code, BigDecimal originalAmount, Long itemId, Long skuId) {
        BigDecimal normalizedOriginal = money(originalAmount);
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return Mono.just(noDiscount(normalizedOriginal));
        }
        return shopCouponRepository.findByCode(normalizedCode)
                .switchIfEmpty(Mono.error(new BusinessException("COUPON_NOT_FOUND", "优惠券不存在")))
                .map(this::requireManualCoupon)
                .map(coupon -> calculateDiscount(coupon, normalizedOriginal, itemId, skuId));
    }

    /**
     * 专享链接背后的优惠券只是核销凭据，必须通过高强度链接令牌使用。
     * 管理员普通维护、删除和客户输入优惠码都不能绕过这个边界。
     */
    private ShopCoupon requireManualCoupon(ShopCoupon coupon) {
        if (coupon.getSource() == ShopCoupon.Source.OFFER_LINK) {
            throw new BusinessException("COUPON_NOT_FOUND", "优惠券不存在");
        }
        return coupon;
    }

    @Override
    public Mono<Void> markUsed(String code) {
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return Mono.empty();
        }
        return shopCouponRepository.increaseUsedCount(normalizedCode).then();
    }

    @Override
    public Mono<Void> claimForZeroAmountOrder(String code) {
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return Mono.error(new BusinessException("COUPON_CODE_REQUIRED", "零元订单必须使用有效优惠券"));
        }
        return shopCouponRepository.claimAvailableUse(normalizedCode)
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new BusinessException("COUPON_UNAVAILABLE", "优惠券状态已变化，请重新确认")));
    }

    @Override
    public Mono<OfferLinkDiscount> applyOfferLink(String token, BigDecimal originalAmount, Long itemId, Long skuId,
                                                  String currency, Long buyerId) {
        return shopOfferLinkService.apply(token, originalAmount, itemId, skuId, currency, buyerId);
    }

    @Override
    public Mono<Void> reserveOfferLink(Long offerLinkId, String orderNo, Long buyerId) {
        return shopOfferLinkService.reserve(offerLinkId, orderNo, buyerId);
    }

    @Override
    public Mono<Void> consumeOfferLink(Long offerLinkId, String orderNo, Long buyerId) {
        return shopOfferLinkService.consume(offerLinkId, orderNo, buyerId);
    }

    @Override
    public Mono<Void> releaseOfferLink(String orderNo) {
        return shopOfferLinkService.release(orderNo);
    }

    @Override
    public Mono<Void> releaseClosedOfferLinkReservations() {
        return shopOfferLinkService.releaseClosedOrderReservations();
    }

    private ShopCoupon applyUpdate(ShopCoupon coupon, ShopCouponUpdateDto dto, String code) {
        coupon.setCode(code);
        coupon.setItemId(dto.getItemId());
        coupon.setSkuId(dto.getSkuId());
        if (StringUtils.isNotBlank(dto.getName())) {
            coupon.setName(StringUtils.trim(dto.getName()));
        }
        if (dto.getType() != null) {
            coupon.setType(dto.getType());
        }
        if (dto.getDiscountValue() != null) {
            coupon.setDiscountValue(money(dto.getDiscountValue()));
        }
        if (dto.getThresholdAmount() != null) {
            coupon.setThresholdAmount(money(dto.getThresholdAmount()));
        }
        if (dto.getMaxDiscountAmount() != null) {
            coupon.setMaxDiscountAmount(money(dto.getMaxDiscountAmount()));
        }
        if (dto.getTotalCount() != null) {
            coupon.setTotalCount(dto.getTotalCount());
        }
        if (dto.getEnabled() != null) {
            coupon.setEnabled(dto.getEnabled());
        }
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        if (coupon.getType() == ShopCoupon.Type.REDUCTION) {
            coupon.setMaxDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        return coupon;
    }

    private CouponDiscount calculateDiscount(ShopCoupon coupon, BigDecimal originalAmount, Long itemId, Long skuId) {
        ensureCouponUsable(coupon, originalAmount, itemId, skuId);
        BigDecimal discountAmount;
        if (coupon.getType() == ShopCoupon.Type.DISCOUNT) {
            BigDecimal rate = coupon.getDiscountValue().divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
            BigDecimal payableAmount = money(originalAmount.multiply(rate));
            discountAmount = originalAmount.subtract(payableAmount);
            discountAmount = applyDiscountCouponCap(coupon, discountAmount);
        } else {
            discountAmount = coupon.getDiscountValue();
        }
        discountAmount = capDiscount(originalAmount, money(discountAmount));
        BigDecimal payableAmount = money(originalAmount.subtract(discountAmount));
        return new CouponDiscount(coupon.getCode(), originalAmount, discountAmount, payableAmount);
    }

    private CouponDiscount noDiscount(BigDecimal originalAmount) {
        return new CouponDiscount(null, originalAmount, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                money(originalAmount));
    }

    private void ensureCouponUsable(ShopCoupon coupon, BigDecimal originalAmount, Long itemId, Long skuId) {
        validateCoupon(coupon);
        if (coupon.getItemId() != null && !coupon.getItemId().equals(itemId)) {
            throw new BusinessException("COUPON_ITEM_MISMATCH", "优惠券不适用于当前商品");
        }
        if (coupon.getSkuId() != null && !coupon.getSkuId().equals(skuId)) {
            throw new BusinessException("COUPON_SKU_MISMATCH", "优惠券不适用于当前商品规格");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(coupon.getEnabled())) {
            throw new BusinessException("COUPON_DISABLED", "优惠券不可用");
        }
        if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())) {
            throw new BusinessException("COUPON_NOT_STARTED", "优惠券尚未生效");
        }
        if (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("COUPON_EXPIRED", "优惠券已过期");
        }
        if (coupon.getTotalCount() != null && coupon.getTotalCount() > 0
                && (coupon.getUsedCount() == null ? 0 : coupon.getUsedCount()) >= coupon.getTotalCount()) {
            throw new BusinessException("COUPON_EMPTY", "优惠券已用完");
        }
        if (originalAmount.compareTo(defaultMoney(coupon.getThresholdAmount())) < 0) {
            throw new BusinessException("COUPON_THRESHOLD_NOT_MET", "订单金额未达到优惠券门槛");
        }
    }

    private void validateCoupon(ShopCoupon coupon) {
        if (StringUtils.isBlank(coupon.getCode())) {
            throw new BusinessException("COUPON_CODE_REQUIRED", "优惠券编码不能为空");
        }
        if (StringUtils.isBlank(coupon.getName())) {
            throw new BusinessException("COUPON_NAME_REQUIRED", "优惠券名称不能为空");
        }
        if (coupon.getType() == null) {
            throw new BusinessException("COUPON_TYPE_REQUIRED", "优惠类型不能为空");
        }
        if (coupon.getSkuId() != null && coupon.getItemId() == null) {
            throw new BusinessException("COUPON_SCOPE_INVALID", "限定商品规格时必须同时限定商品");
        }
        if (coupon.getDiscountValue() == null || coupon.getDiscountValue().signum() <= 0) {
            throw new BusinessException("COUPON_VALUE_INVALID", "优惠值必须大于0");
        }
        if (coupon.getType() == ShopCoupon.Type.DISCOUNT
                && coupon.getDiscountValue().compareTo(BigDecimal.TEN) >= 0) {
            throw new BusinessException("COUPON_DISCOUNT_INVALID", "折扣值必须小于10");
        }
        if (coupon.getMaxDiscountAmount() == null) {
            coupon.setMaxDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (coupon.getMaxDiscountAmount().signum() < 0) {
            throw new BusinessException("COUPON_MAX_DISCOUNT_INVALID", "最高减免不能小于0");
        }
        if (coupon.getEndTime() != null && coupon.getStartTime() != null
                && coupon.getEndTime().isBefore(coupon.getStartTime())) {
            throw new BusinessException("COUPON_TIME_INVALID", "结束时间不能早于开始时间");
        }
    }

    private Mono<String> resolveUniqueCode(String rawCode, Long currentId) {
        String normalizedCode = normalizeCode(rawCode);
        if (StringUtils.isBlank(normalizedCode)) {
            normalizedCode = "FF" + RandomStringUtils.secure().nextAlphanumeric(8).toUpperCase();
        }
        String code = normalizedCode;
        return shopCouponRepository.findByCode(code)
                .filter(existing -> currentId == null || !currentId.equals(existing.getId()))
                .hasElement()
                .flatMap(exists -> {
                    if (exists && StringUtils.isNotBlank(rawCode)) {
                        return Mono.error(new BusinessException("COUPON_CODE_EXISTS", "优惠券编码已存在"));
                    }
                    if (exists) {
                        return resolveUniqueCode(null, currentId);
                    }
                    return Mono.just(code);
                });
    }

    private Mono<String> resolveCodeForUpdate(String rawCode, ShopCoupon coupon) {
        if (StringUtils.isBlank(rawCode)) {
            return Mono.just(coupon.getCode());
        }
        return resolveUniqueCode(rawCode, coupon.getId());
    }

    private String normalizeCode(String code) {
        return StringUtils.upperCase(StringUtils.trimToNull(code));
    }

    private BigDecimal capDiscount(BigDecimal originalAmount, BigDecimal discountAmount) {
        if (originalAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return discountAmount.min(originalAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyDiscountCouponCap(ShopCoupon coupon, BigDecimal discountAmount) {
        BigDecimal maxDiscount = defaultMoney(coupon.getMaxDiscountAmount());
        if (maxDiscount.signum() <= 0) {
            return discountAmount;
        }
        return discountAmount.min(maxDiscount);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP);
    }
}
