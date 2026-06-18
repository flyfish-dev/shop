import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { applyCoupon } from '../apis/api.js';

const COUPON_FAILURE_PATTERN = /优惠券|订单金额未达到|门槛/;

export const normalizeCouponCode = value => (value || '').trim().toUpperCase();

export function useShopCoupon({ item, user, store, router, count = 1, baseAmount, orderAmountPayload }) {
  const couponApplying = ref(false);
  const couponCode = ref('');
  const appliedCoupon = ref(null);
  const couponError = ref('');

  const hasAppliedCoupon = computed(() => Boolean(appliedCoupon.value?.couponCode));
  const currentPayableAmount = computed(() => appliedCoupon.value?.payableAmount || baseAmount?.value || item.value?.price);

  const resetCoupon = () => {
    couponCode.value = '';
    appliedCoupon.value = null;
    couponError.value = '';
  };

  const removeInvalidCoupon = text => {
    const tip = text || '优惠券不可用，已移除';
    couponCode.value = '';
    appliedCoupon.value = null;
    couponError.value = tip;
    message.warning(tip);
  };

  const isCouponFailure = error => COUPON_FAILURE_PATTERN.test(error?.message || '');

  const applyCouponCode = async ({ silentSuccess = false } = {}) => {
    const code = normalizeCouponCode(couponCode.value);
    if (!code) {
      couponError.value = '请输入优惠券编码';
      return false;
    }
    if (!user.value?.id) {
      store.rememberRedirect(location.pathname + location.search);
      message.warning('请先登录后使用优惠券');
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
        count,
        couponCode: code,
        ...(orderAmountPayload ? orderAmountPayload() : {})
      });
      appliedCoupon.value = result;
      couponCode.value = result.couponCode || code;
      if (!silentSuccess) {
        message.success(`优惠券已应用，已优惠 ¥${result.discountAmount}`);
      }
      return true;
    } catch (e) {
      removeInvalidCoupon(e.message || '优惠券不可用，已移除');
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

  return {
    couponApplying,
    couponCode,
    appliedCoupon,
    couponError,
    currentPayableAmount,
    hasAppliedCoupon,
    applyCouponCode,
    isCouponFailure,
    removeInvalidCoupon,
    resetCoupon,
    resolveCouponCodeForOrder
  };
}
