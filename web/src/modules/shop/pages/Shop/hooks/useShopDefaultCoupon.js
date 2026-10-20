import { computed, watch } from 'vue';
import { normalizeCouponCode } from './useShopCoupon.js';
import { resolveShopItemPromotion } from './useShopItemPromotion.js';

export function useShopDefaultCoupon({ item, couponCode, appliedCoupon, couponError, enabled }) {
  const defaultPromotion = computed(() => enabled?.value === false
    ? resolveShopItemPromotion({})
    : resolveShopItemPromotion(item.value));

  const applyDefaultCoupon = ({ force = false } = {}) => {
    const promotion = defaultPromotion.value;
    if (!promotion.active || !promotion.couponCode) {
      return false;
    }

    const currentCode = normalizeCouponCode(couponCode.value);
    const appliedCode = normalizeCouponCode(appliedCoupon.value?.couponCode);
    const shouldKeepUserInput = currentCode && currentCode !== appliedCode && currentCode !== promotion.couponCode;
    if (!force && shouldKeepUserInput) {
      return false;
    }

    couponCode.value = promotion.couponCode;
    appliedCoupon.value = {
      couponCode: promotion.couponCode,
      originalAmount: promotion.originalAmount,
      discountAmount: promotion.discountAmount,
      payableAmount: promotion.payableAmount
    };
    couponError.value = '';
    return true;
  };

  watch(
    () => [
      item.value?.id,
      item.value?.price,
      item.value?.defaultCouponPreview?.couponCode,
      item.value?.defaultCouponPreview?.payableAmount,
      enabled?.value
    ],
    () => applyDefaultCoupon({ force: true }),
    // 金额变化会先让通用优惠券钩子清理旧试算，默认优惠应在同一轮更新末尾重新落值。
    { immediate: true, flush: 'post' }
  );

  return {
    defaultPromotion,
    applyDefaultCoupon
  };
}
