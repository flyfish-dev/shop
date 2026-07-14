import { computed, unref } from 'vue';

const toNumber = value => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

export const formatShopMoney = value => toNumber(value).toFixed(2);

export const resolveShopItemPromotion = item => {
  const preview = item?.defaultCouponPreview || {};
  const baseAmount = item?.multiSku
    ? (item?.minPrice ?? item?.defaultSku?.price ?? item?.price)
    : item?.price;
  const maxAmount = item?.multiSku
    ? toNumber(item?.maxPrice ?? baseAmount)
    : toNumber(baseAmount);
  const originalAmount = toNumber(preview.originalAmount ?? baseAmount);
  const payableAmount = toNumber(preview.payableAmount ?? baseAmount);
  const discountAmount = toNumber(preview.discountAmount);
  const couponCode = (preview.couponCode || item?.defaultCouponCode || '').trim().toUpperCase();
  const active = Boolean(couponCode && discountAmount > 0 && payableAmount > 0 && payableAmount < originalAmount);
  const rangeSuffix = item?.multiSku && maxAmount > originalAmount ? ' 起' : '';

  return {
    active,
    couponCode,
    originalAmount: formatShopMoney(originalAmount),
    payableAmount: formatShopMoney(active ? payableAmount : originalAmount),
    discountAmount: formatShopMoney(active ? discountAmount : 0),
    rangeSuffix
  };
};

export function useShopItemPromotion(item) {
  const promotion = computed(() => resolveShopItemPromotion(unref(item)));

  return {
    promotion
  };
}
