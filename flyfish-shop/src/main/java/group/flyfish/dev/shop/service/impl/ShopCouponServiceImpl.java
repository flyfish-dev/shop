package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.dto.ShopCouponCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopCouponUpdateDto;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import group.flyfish.dev.shop.domain.vo.ShopCouponVo;
import group.flyfish.dev.shop.repository.ShopCouponRepository;
import group.flyfish.dev.shop.service.CouponDiscount;
import group.flyfish.dev.shop.service.ShopCouponService;
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

    private static final BigDecimal MIN_PAYABLE_AMOUNT = new BigDecimal("0.01");

    private final ShopCouponRepository shopCouponRepository;

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
                    coupon.setType(dto.getType());
                    coupon.setDiscountValue(money(dto.getDiscountValue()));
                    coupon.setThresholdAmount(money(defaultMoney(dto.getThresholdAmount())));
                    coupon.setTotalCount(dto.getTotalCount());
                    coupon.setUsedCount(0);
                    coupon.setEnabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()));
                    coupon.setStartTime(dto.getStartTime());
                    coupon.setEndTime(dto.getEndTime());
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
        return shopCouponRepository.deleteById(id);
    }

    @Override
    public Mono<CouponDiscount> applyCoupon(String code, BigDecimal originalAmount) {
        BigDecimal normalizedOriginal = money(originalAmount);
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return Mono.just(noDiscount(normalizedOriginal));
        }
        return shopCouponRepository.findByCode(normalizedCode)
                .switchIfEmpty(Mono.error(new BusinessException("COUPON_NOT_FOUND", "优惠券不存在")))
                .map(coupon -> calculateDiscount(coupon, normalizedOriginal));
    }

    @Override
    public Mono<Void> markUsed(String code) {
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return Mono.empty();
        }
        return shopCouponRepository.increaseUsedCount(normalizedCode).then();
    }

    private ShopCoupon applyUpdate(ShopCoupon coupon, ShopCouponUpdateDto dto, String code) {
        coupon.setCode(code);
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
        if (dto.getTotalCount() != null) {
            coupon.setTotalCount(dto.getTotalCount());
        }
        if (dto.getEnabled() != null) {
            coupon.setEnabled(dto.getEnabled());
        }
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        return coupon;
    }

    private CouponDiscount calculateDiscount(ShopCoupon coupon, BigDecimal originalAmount) {
        ensureCouponUsable(coupon, originalAmount);
        BigDecimal discountAmount;
        if (coupon.getType() == ShopCoupon.Type.DISCOUNT) {
            BigDecimal rate = coupon.getDiscountValue().divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
            BigDecimal payableAmount = money(originalAmount.multiply(rate));
            discountAmount = originalAmount.subtract(payableAmount);
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

    private void ensureCouponUsable(ShopCoupon coupon, BigDecimal originalAmount) {
        validateCoupon(coupon);
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
        if (coupon.getDiscountValue() == null || coupon.getDiscountValue().signum() <= 0) {
            throw new BusinessException("COUPON_VALUE_INVALID", "优惠值必须大于0");
        }
        if (coupon.getType() == ShopCoupon.Type.DISCOUNT
                && coupon.getDiscountValue().compareTo(BigDecimal.TEN) >= 0) {
            throw new BusinessException("COUPON_DISCOUNT_INVALID", "折扣值必须小于10");
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
        BigDecimal maxDiscount = originalAmount.subtract(MIN_PAYABLE_AMOUNT);
        if (maxDiscount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return discountAmount.min(maxDiscount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP);
    }
}
