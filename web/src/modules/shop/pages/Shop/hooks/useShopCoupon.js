import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { applyCoupon } from '../apis/api.js';
import { useI18n } from 'vue-i18n';

const COUPON_FAILURE_PATTERN = /优惠券|订单金额未达到|门槛/;
const OFFER_LINK_FAILURE_PATTERN = /专享|购买链接|OFFER_LINK/;

export const normalizeCouponCode = value => (value || '').trim().toUpperCase();

export function useShopCoupon({ item, user, store, router, count = 1, baseAmount, orderAmountPayload, offerLink }) {
  const { t } = useI18n();
  const couponApplying = ref(false);
  const couponCode = ref('');
  const appliedCoupon = ref(null);
  const couponError = ref('');

  const hasAppliedCoupon = computed(() => Boolean(appliedCoupon.value?.couponCode));
  const effectiveDiscount = computed(() => offerLink?.offerLinkActive?.value
    ? offerLink.offerLinkPreview.value
    : appliedCoupon.value);
  const hasActiveDiscount = computed(() => Boolean(effectiveDiscount.value));
  const currentPayableAmount = computed(() => effectiveDiscount.value?.payableAmount
    ?? baseAmount?.value
    ?? item.value?.price);

  const resetCoupon = () => {
    couponCode.value = '';
    appliedCoupon.value = null;
    couponError.value = '';
  };

  const removeInvalidCoupon = text => {
    const tip = text || t('shop.couponMessages.unavailableRemoved');
    couponCode.value = '';
    appliedCoupon.value = null;
    couponError.value = tip;
    message.warning(tip);
  };

  const isCouponFailure = error => COUPON_FAILURE_PATTERN.test(error?.message || '');
  const isDiscountFailure = error => isCouponFailure(error)
    || OFFER_LINK_FAILURE_PATTERN.test(error?.message || '');

  const handleDiscountFailure = error => {
    const text = error?.message || t('shop.couponMessages.redemptionFailed');
    if (offerLink?.offerLinkPresent?.value && OFFER_LINK_FAILURE_PATTERN.test(text)) {
      offerLink.markOfferLinkInvalid(text);
      message.warning(text);
      return;
    }
    removeInvalidCoupon(text);
  };

  const applyCouponCode = async ({ silentSuccess = false } = {}) => {
    const code = normalizeCouponCode(couponCode.value);
    if (!code) {
      couponError.value = t('shop.couponMessages.enterCode');
      return false;
    }
    if (!user.value?.id) {
      store.rememberRedirect(location.pathname + location.search);
      message.warning(t('shop.couponMessages.signInRequired'));
      router.push('/login');
      return false;
    }
    if (!item.value?.id) {
      return false;
    }

    couponApplying.value = true;
    couponError.value = '';
    try {
      const result = await applyCoupon({
        itemId: item.value.id,
        skuId: item.value.skuId,
        count,
        couponCode: code,
        ...(orderAmountPayload ? orderAmountPayload() : {})
      });
      appliedCoupon.value = result;
      couponCode.value = result.couponCode || code;
      if (!silentSuccess) {
        message.success(t('shop.couponMessages.applied', { amount: result.discountAmount }));
      }
      return true;
    } catch (e) {
      removeInvalidCoupon(e.message || t('shop.couponMessages.unavailableRemoved'));
      return false;
    } finally {
      couponApplying.value = false;
    }
  };

  const resolveCouponCodeForOrder = async () => {
    if (appliedCoupon.value?.couponCode) {
      return { ok: true, couponCode: appliedCoupon.value.couponCode };
    }
    if (!normalizeCouponCode(couponCode.value)) {
      return { ok: true, couponCode: undefined };
    }
    const ok = await applyCouponCode({ silentSuccess: true });
    return { ok, couponCode: ok ? appliedCoupon.value?.couponCode : undefined };
  };

  const resolveDiscountForOrder = async ({ allowCoupon = true } = {}) => {
    if (offerLink?.offerLinkPresent?.value) {
      return offerLink.resolveOfferLinkForOrder();
    }
    if (!allowCoupon) {
      return { ok: true, couponCode: undefined, offerToken: undefined };
    }
    return resolveCouponCodeForOrder();
  };

  watch(couponCode, value => {
    const code = normalizeCouponCode(value);
    if (hasAppliedCoupon.value && code !== appliedCoupon.value.couponCode) {
      appliedCoupon.value = null;
    }
    if (code) {
      couponError.value = '';
    }
  });

  watch(() => item.value?.id, resetCoupon);

  watch(() => baseAmount?.value, () => {
    if (hasAppliedCoupon.value) {
      appliedCoupon.value = null;
    }
  });

  watch(() => offerLink?.offerLinkActive?.value, active => {
    if (active) {
      couponCode.value = '';
      appliedCoupon.value = null;
      couponError.value = '';
    }
  });

  return {
    couponApplying,
    couponCode,
    appliedCoupon,
    couponError,
    effectiveDiscount,
    currentPayableAmount,
    hasAppliedCoupon,
    hasActiveDiscount,
    applyCouponCode,
    isCouponFailure,
    isDiscountFailure,
    handleDiscountFailure,
    removeInvalidCoupon,
    resetCoupon,
    resolveCouponCodeForOrder,
    resolveDiscountForOrder
  };
}
