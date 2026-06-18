import { computed, unref } from 'vue';

const toNumber = value => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

export const formatShopMoney = value => toNumber(value).toFixed(2);

export const resolveShopItemPromotion = item => {
  const preview = item?.defaultCouponPreview || {};
  const originalAmount = toNumber(preview.originalAmount ?? item?.price);
  const payableAmount = toNumber(preview.payableAmount ?? item?.price);
  const discountAmount = toNumber(preview.discountAmount);
  const couponCode = (preview.couponCode || item?.defaultCouponCode || '').trim().toUpperCase();
  const active = Boolean(couponCode && discountAmount > 0 && payableAmount > 0 && payableAmount < originalAmount);

  return {
    active,
    couponCode,
    originalAmount: formatShopMoney(originalAmount),
    payableAmount: formatShopMoney(active ? payableAmount : originalAmount),
    discountAmount: formatShopMoney(active ? discountAmount : 0)
  };
};

export function useShopItemPromotion(item) {
  const promotion = computed(() => resolveShopItemPromotion(unref(item)));

  return {
    promotion
  };
}
