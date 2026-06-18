import { computed, watch } from 'vue';
import { normalizeCouponCode } from './useShopCoupon.js';
import { resolveShopItemPromotion } from './useShopItemPromotion.js';

export function useShopDefaultCoupon({ item, couponCode, appliedCoupon, couponError }) {
  const defaultPromotion = computed(() => resolveShopItemPromotion(item.value));

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
      item.value?.defaultCouponPreview?.couponCode,
      item.value?.defaultCouponPreview?.payableAmount
    ],
    () => applyDefaultCoupon({ force: true }),
    { immediate: true }
  );

  return {
    defaultPromotion,
    applyDefaultCoupon
  };
}
