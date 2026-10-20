import { computed, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useLocalStorage } from '@vueuse/core';
import { message } from 'ant-design-vue';
import { createOrder, getOrder, getPaymentMethods } from '../apis/api.js';
import { deliveryStatusText } from '@/modules/shop/utils/shopDelivery.js';
import { isXianyuRuntime, storageKey, authorizeXianyu, payXianyu } from '@/modules/xianyu/runtime.js';
import { get } from '@/network/request.js';
import {
  isGitRepositoryAccessType,
  isGitRepositoryDonationAccessType,
  isPureDonationType
} from '@/modules/shop/utils/shopCovers.js';

export const detectPaymentScene = () => {
  const ua = navigator.userAgent || '';
  // 微信内置浏览器对应 JSAPI，移动浏览器对应 H5，桌面端使用扫码支付。
  if (/MicroMessenger/i.test(ua)) {
    return 'jsapi';
  }
  if (/Android|iPhone|iPad|iPod|Windows Phone|Mobile/i.test(ua)) {
    return 'h5';
  }
  return 'native';
};

const normalizeLocale = locale => String(locale || '').toLowerCase();

const KNOWN_PAYMENT_METHODS = new Set(['h5zhifu-wechat', 'h5zhifu-alipay', 'stripe']);
const FALLBACK_PAYMENT_METHODS = ['h5zhifu-wechat', 'stripe'];

const defaultPaymentMethodForLocale = (locale, methods = FALLBACK_PAYMENT_METHODS) => {
  const preferred = normalizeLocale(locale).startsWith('en') ? 'stripe' : 'h5zhifu-wechat';
  return methods.includes(preferred) ? preferred : (methods[0] || '');
};

const parsePaymentMethod = value => {
  if (value === 'xianyu') return { method: 'xianyu', provider: 'xianyu', payType: 'xianyu' };
  if (value === 'stripe') {
    return {
      method: 'stripe',
      provider: 'stripe',
      payType: 'stripe'
    };
  }
  if (value === 'h5zhifu-alipay') {
    return {
      method: 'h5zhifu-alipay',
      provider: 'h5zhifu',
      payType: 'alipay'
    };
  }
  return {
    method: 'h5zhifu-wechat',
    provider: 'h5zhifu',
    payType: 'wechat'
  };
};

export function useShopPayment({
  item,
  user,
  store,
  router,
  itemDeliveryMode,
  loadOrders,
  validateCheckout,
  coupon,
  orderAmountPayload,
  paymentCurrency,
  externalSettlementCheckout
}) {
  const { locale, t } = useI18n();
  const pendingPaymentOrderNo = useLocalStorage(storageKey('shop-pending-payment-order'), '');
  const pendingPaymentScene = useLocalStorage(storageKey('shop-pending-payment-scene'), '');
  const pendingPaymentMethod = useLocalStorage(storageKey('shop-pending-payment-method'), '');
  const payModalVisible = ref(false);
  const payLoading = ref(false);
  const currentOrder = ref(null);
  const currentPayment = ref(null);
  const availablePaymentMethods = ref(isXianyuRuntime ? ['xianyu'] : [...FALLBACK_PAYMENT_METHODS]);
  const paymentMethod = ref(isXianyuRuntime ? 'xianyu' : defaultPaymentMethodForLocale(locale.value));
  const paymentScene = ref(parsePaymentMethod(paymentMethod.value).provider === 'stripe' ? 'checkout' : 'native');
  const poller = ref(null);

  const paymentMethodOptions = computed(() => isXianyuRuntime ? [
    { value: 'xianyu', label: locale.value.startsWith('en') ? 'Xianyu Pay' : '闲鱼支付', description: '', badge: '' }
  ] : [
    {
      value: 'h5zhifu-wechat',
      label: t('shop.paymentMethods.wechat.title'),
      description: t('shop.paymentMethods.wechat.description'),
      badge: normalizeLocale(locale.value).startsWith('zh') ? t('shop.paymentMethods.recommended') : ''
    },
    {
      value: 'h5zhifu-alipay',
      label: t('shop.paymentMethods.alipay.title'),
      description: t('shop.paymentMethods.alipay.description'),
      badge: ''
    },
    {
      value: 'stripe',
      label: t('shop.paymentMethods.stripe.title'),
      description: t('shop.paymentMethods.stripe.description'),
      badge: normalizeLocale(locale.value).startsWith('en') ? t('shop.paymentMethods.recommended') : ''
    }
  ].filter(option => availablePaymentMethods.value.includes(option.value))
    .filter(option => String(paymentCurrency?.value || 'CNY').toUpperCase() !== 'USD'
      || option.value === 'stripe'));
  const selectedPaymentMethod = computed(() => (
    paymentMethodOptions.value.find(option => option.value === paymentMethod.value)
    || paymentMethodOptions.value[0]
    || {
      value: '',
      label: t('shop.paymentMethods.unavailable'),
      description: ''
    }
  ));
  const paymentProvider = computed(() => parsePaymentMethod(paymentMethod.value).provider);
  const currentPaymentProvider = computed(() => currentPayment.value?.provider || paymentProvider.value);
  const qrCode = computed(() => {
    const text = currentPayment.value?.qrcodeText;
    return text ? `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(text)}` : '';
  });
  const payLink = computed(() => currentPayment.value?.jumpUrl || currentPayment.value?.qrcodeText || '');
  const paymentActionReady = computed(() => currentPaymentProvider.value === 'xianyu'
    ? currentPayment.value?.action === 'XIANYU_TRADE_PAY'
      && typeof currentPayment.value?.platformOrderId === 'string'
      && /^[0-9]+$/.test(currentPayment.value.platformOrderId)
    : Boolean(payLink.value));
  const currentTradeType = computed(() => (currentPayment.value?.tradeType || paymentScene.value || 'native').toLowerCase());
  const redirectPayment = computed(() => currentPaymentProvider.value === 'xianyu' || currentPaymentProvider.value === 'stripe'
    || ['h5', 'jsapi', 'checkout'].includes(currentTradeType.value));
  const zeroAmountCheckout = computed(() => coupon.hasActiveDiscount?.value
    && Number(coupon.effectiveDiscount?.value?.payableAmount) === 0);
  const offerLinkCheckout = computed(() => Boolean(coupon.effectiveDiscount?.value)
    && !coupon.effectiveDiscount.value.couponCode);
  const zeroAmountOrder = computed(() => Boolean(currentOrder.value)
    && Number(currentOrder.value.amount) === 0);
  const externalSettlementOrder = computed(() => currentOrder.value?.settlementSource === 'EXTERNAL_CONFIRMED');
  const checkoutTip = computed(() => {
    if (externalSettlementCheckout?.value) {
      return t('shop.offerLink.directCheckoutTip');
    }
    if (zeroAmountCheckout.value) {
      return offerLinkCheckout.value
        ? t('shop.offerLink.freeCheckoutTip')
        : t('shop.paymentFlow.freeCheckoutTip');
    }
    if (isGitRepositoryDonationAccessType(item.value?.type)) {
      return t('shop.paymentFlow.confirmContribution');
    }
    if (paymentProvider.value === 'stripe') {
      return t('shop.paymentMethods.stripe.checkoutTip');
    }
    return isGitRepositoryAccessType(item.value?.type)
      ? t('shop.paymentFlow.confirmAccount')
      : (paymentScene.value === 'native'
          ? t('shop.paymentFlow.confirmOrder')
          : t('shop.paymentFlow.startPayment'));
  });
  const redirectPayButtonText = computed(() => {
    if (currentPaymentProvider.value === 'stripe' || currentTradeType.value === 'checkout') {
      return t('shop.paymentMethods.stripe.redirectButton');
    }
    if (currentTradeType.value === 'jsapi') {
      return t('shop.paymentFlow.openWechat');
    }
    if (currentTradeType.value === 'h5') {
      return t('shop.paymentFlow.openH5');
    }
    return t('shop.paymentFlow.continuePayment');
  });

  const isPaymentFlowFinished = order => {
    if (!order?.status) {
      return false;
    }
    if (['DELIVERED', 'FAILED', 'CLOSED'].includes(order.status)) {
      return true;
    }
    const deliveryMode = order.deliveryMode || itemDeliveryMode.value;
    return order.status === 'PAID' && order.deliveryStatus === 'WAITING' && deliveryMode === 'MANUAL';
  };

  const paymentFinished = computed(() => isPaymentFlowFinished(currentOrder.value));
  const paymentModalTitle = computed(() => {
    if (externalSettlementOrder.value) {
      return t('shop.offerLink.directOrderTitle');
    }
    if (zeroAmountOrder.value && paymentFinished.value) {
      return t('shop.paymentFlow.freeOrderComplete');
    }
    if (paymentFinished.value) {
      return ['PAID', 'DELIVERED'].includes(currentOrder.value?.status)
        ? t('shop.paymentFlow.paymentComplete')
        : t('shop.paymentFlow.paymentEnded');
    }
    const titles = {
      native: t('shop.paymentFlow.scanWechat'),
      h5: t('shop.paymentFlow.mobileH5'),
      jsapi: t('shop.paymentFlow.wechatJsapi'),
      checkout: t('shop.paymentMethods.stripe.modalTitle')
    };
    return titles[currentTradeType.value] || t('shop.paymentFlow.completePayment');
  });
  const paymentInstruction = computed(() => {
    if (currentOrder.value?.status === 'FAILED') {
      return t('shop.paymentFlow.orderFailed');
    }
    if (externalSettlementOrder.value && currentOrder.value?.status === 'DELIVERED') {
      return t('shop.offerLink.directComplete');
    }
    if (externalSettlementOrder.value && currentOrder.value?.status === 'PAID') {
      return t('shop.offerLink.directAwaiting');
    }
    if (zeroAmountOrder.value && currentOrder.value?.status === 'DELIVERED') {
      return t('shop.paymentFlow.freeServiceReady');
    }
    if (zeroAmountOrder.value && currentOrder.value?.status === 'PAID') {
      return t('shop.paymentFlow.freeAwaitingManual');
    }
    if (currentOrder.value?.status === 'DELIVERED') {
      if (isPureDonationType(item.value?.type)) {
        return t('shop.paymentFlow.contributionComplete');
      }
      return t('shop.paymentFlow.serviceReady');
    }
    if (currentOrder.value?.status === 'PAID') {
      const deliveryMode = currentOrder.value.deliveryMode || itemDeliveryMode.value;
      if (currentOrder.value.deliveryStatus === 'WAITING' && deliveryMode === 'MANUAL') {
        return t('shop.paymentFlow.awaitingManual');
      }
      return deliveryStatusText(currentOrder.value.deliveryStatus, deliveryMode, t);
    }
    if (paymentFinished.value) {
      return t('shop.paymentFlow.orderUpdated');
    }
    if (currentPaymentProvider.value === 'xianyu') {
      return normalizeLocale(locale.value).startsWith('en')
        ? (paymentActionReady.value ? 'Continue with Xianyu payment.' : 'Preparing your payment. Keep this order open.')
        : (paymentActionReady.value ? '请继续完成闲鱼付款。' : '支付订单正在准备，请稍候。');
    }
    if (currentPaymentProvider.value === 'stripe' || currentTradeType.value === 'checkout') {
      return t('shop.paymentMethods.stripe.instruction');
    }
    if (currentTradeType.value === 'native') {
      return t('shop.paymentFlow.scanInstruction');
    }
    if (currentTradeType.value === 'jsapi') {
      return t('shop.paymentFlow.wechatInstruction');
    }
    return t('shop.paymentFlow.mobileInstruction');
  });
  const paymentOrderNoText = computed(() => currentOrder.value?.orderNo
    ? t('shop.paymentFlow.orderNumber', { orderNo: currentOrder.value.orderNo })
    : '');

  const ensureAvailablePaymentMethod = value => {
    const methods = paymentMethodOptions.value.map(option => option.value);
    paymentMethod.value = methods.includes(value)
      ? value
      : defaultPaymentMethodForLocale(locale.value, methods);
  };

  const loadAvailablePaymentMethods = async () => {
    if (isXianyuRuntime) return;
    try {
      const result = await getPaymentMethods();
      const methods = Array.isArray(result)
        ? [...new Set(result.filter(method => KNOWN_PAYMENT_METHODS.has(method)))]
        : [];
      availablePaymentMethods.value = methods;
      ensureAvailablePaymentMethod(paymentMethod.value);
    } catch {
      availablePaymentMethods.value = [...FALLBACK_PAYMENT_METHODS];
      ensureAvailablePaymentMethod(paymentMethod.value);
    }
  };

  const stopPolling = () => {
    if (poller.value) {
      clearInterval(poller.value);
      poller.value = null;
    }
  };

  const refreshOrder = async orderNo => {
    const latest = await getOrder(orderNo);
    currentOrder.value = latest;
    if (isXianyuRuntime && ['PENDING', 'PAYING'].includes(latest.status)) {
      currentPayment.value = await get(`/xianyu/orders/${orderNo}/payment`, { credential: true });
    }
    if (!isPaymentFlowFinished(latest)) {
      return;
    }

    stopPolling();
    pendingPaymentOrderNo.value = '';
    pendingPaymentScene.value = '';
    await loadOrders();
    if (latest.status === 'DELIVERED') {
      const successMessage = Number(latest.amount) === 0 && latest.couponCode
        ? t('shop.paymentFlow.freeOrderComplete')
        : (isPureDonationType(item.value?.type)
            ? t('shop.paymentFlow.contributionComplete')
            : t('shop.paymentFlow.serviceActivated'));
      message.success(successMessage);
    } else if (latest.status === 'PAID') {
      message.success(t('shop.paymentFlow.paymentAwaitingManual'));
    } else if (latest.status === 'FAILED') {
      message.error(latest.deliveryMessage || t('shop.paymentFlow.paymentOrDeliveryFailed'));
    }
  };

  const startPolling = orderNo => {
    stopPolling();
    poller.value = setInterval(() => refreshOrder(orderNo).catch(() => null), 3000);
  };

  const restorePendingPayment = async () => {
    if (!pendingPaymentOrderNo.value || !user.value?.id) {
      return;
    }
    try {
      const latest = await getOrder(pendingPaymentOrderNo.value);
      currentOrder.value = latest;
      paymentScene.value = pendingPaymentScene.value || paymentScene.value;
      ensureAvailablePaymentMethod(pendingPaymentMethod.value || paymentMethod.value);
      if (isPaymentFlowFinished(latest)) {
        pendingPaymentOrderNo.value = '';
        pendingPaymentScene.value = '';
        pendingPaymentMethod.value = '';
        await loadOrders();
        return;
      }
      payModalVisible.value = true;
      startPolling(latest.orderNo);
    } catch {
      pendingPaymentOrderNo.value = '';
      pendingPaymentScene.value = '';
    }
  };

  const handlePageShow = async () => {
    if (currentOrder.value?.orderNo) {
      await refreshOrder(currentOrder.value.orderNo).catch(() => null);
      if (!isPaymentFlowFinished(currentOrder.value)) {
        startPolling(currentOrder.value.orderNo);
      }
      return;
    }
    restorePendingPayment();
  };

  const checkout = async (extraPayload = {}) => {
    if (!user.value?.id) {
      store.rememberRedirect(location.pathname + location.search);
      if (isXianyuRuntime) {
        await authorizeXianyu(token => store.setToken(token));
        await store.refreshUser();
        return;
      }
      router.push('/login');
      return;
    }
    if (validateCheckout && !validateCheckout()) {
      return;
    }

    const discountResult = await coupon.resolveDiscountForOrder({
      allowCoupon: String(paymentCurrency?.value || 'CNY').toUpperCase() !== 'USD'
    });
    if (!discountResult.ok) {
      return;
    }

    const fullyDiscounted = Boolean(discountResult.couponCode || discountResult.offerToken)
      && zeroAmountCheckout.value;
    const externallySettled = Boolean(discountResult.offerToken && externalSettlementCheckout?.value);
    if (isXianyuRuntime && (fullyDiscounted || externallySettled || discountResult.offerToken)) {
      message.error('当前渠道暂不支持此结算方式');
      return;
    }
    if (!fullyDiscounted && !externallySettled
      && !paymentMethodOptions.value.some(option => option.value === paymentMethod.value)) {
      message.error(t('shop.paymentMethods.unavailable'));
      return;
    }

    const method = externallySettled
      ? { provider: 'external-confirmed', payType: 'external-confirmed' }
      : (fullyDiscounted
          ? { provider: 'coupon', payType: 'coupon' }
          : parsePaymentMethod(paymentMethod.value));
    paymentScene.value = externallySettled
      ? 'external'
      : (fullyDiscounted ? 'coupon' : resolvePaymentTradeType(method));

    payLoading.value = true;
    try {
      const res = await createOrder({
        itemId: item.value.id,
        skuId: item.value.skuId,
        count: 1,
        couponCode: discountResult.couponCode,
        offerToken: discountResult.offerToken,
        properties: {},
        ...(orderAmountPayload ? orderAmountPayload() : {}),
        paymentProvider: method.provider,
        payType: method.payType,
        tradeType: paymentScene.value,
        paymentLocale: locale.value,
        paymentCurrency: paymentCurrency?.value,
        paymentReturnPath: `${location.pathname}${location.search}`,
        ...extraPayload
      });
      currentOrder.value = res.order;
      currentPayment.value = res.payment;
      payModalVisible.value = true;
      if (isPaymentFlowFinished(res.order)) {
        pendingPaymentOrderNo.value = '';
        pendingPaymentScene.value = '';
        pendingPaymentMethod.value = '';
        await loadOrders();
        if (res.order.status === 'FAILED') {
          message.error(res.order.deliveryMessage || t('shop.paymentFlow.paymentOrDeliveryFailed'));
        }
      } else {
        pendingPaymentOrderNo.value = res.order.orderNo;
        pendingPaymentScene.value = res.payment?.tradeType || paymentScene.value;
        pendingPaymentMethod.value = paymentMethod.value;
        startPolling(res.order.orderNo);
      }
      if (res.payment?.provider === 'stripe' && res.payment?.jumpUrl) {
        window.location.href = res.payment.jumpUrl;
      }
      if (isXianyuRuntime && res.payment?.action === 'XIANYU_TRADE_PAY') {
        await payXianyu(res.payment);
        await refreshOrder(res.order.orderNo);
      }
    } catch (e) {
      if (coupon.isDiscountFailure(e)) {
        coupon.handleDiscountFailure(e);
        return;
      }
      message.error(e.message || t('shop.paymentFlow.paymentStartFailed'));
    } finally {
      payLoading.value = false;
    }
  };

  const clearPaymentState = () => {
    payModalVisible.value = false;
    currentOrder.value = null;
    currentPayment.value = null;
    pendingPaymentOrderNo.value = '';
    pendingPaymentScene.value = '';
    pendingPaymentMethod.value = '';
    stopPolling();
    loadOrders();
  };

  const handlePayModalClose = () => {
    clearPaymentState();
  };

  const openPayLink = async () => {
    if (isXianyuRuntime) {
      try {
        await refreshOrder(currentOrder.value.orderNo);
        await payXianyu(currentPayment.value);
        await refreshOrder(currentOrder.value.orderNo);
      } catch (error) { message.error(error.message); }
      return;
    }
    if (payLink.value) {
      if (currentPaymentProvider.value === 'stripe') {
        window.location.href = payLink.value;
        return;
      }
      const opened = redirectPayment.value ? window.open(payLink.value, '_blank') : null;
      if (!opened) {
        window.location.href = payLink.value;
      }
    }
  };

  const goMyOrders = () => {
    payModalVisible.value = false;
    stopPolling();
    pendingPaymentOrderNo.value = '';
    pendingPaymentScene.value = '';
    pendingPaymentMethod.value = '';
    router.push('/account/orders');
  };

  const goSubmitTicket = () => {
    payModalVisible.value = false;
    router.push('/account/tickets', { create: '1' });
  };

  const continueShopping = () => {
    clearPaymentState();
    router.push('/shop/item-list');
  };

  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      handlePageShow().catch(() => null);
    }
  };

  const resolvePaymentTradeType = method => {
    if (method.provider === 'stripe') {
      return 'checkout';
    }
    const detected = detectPaymentScene();
    if (method.payType === 'alipay' && detected === 'jsapi') {
      return 'h5';
    }
    return detected;
  };

  watch(() => locale.value, value => {
    if (!pendingPaymentOrderNo.value) {
      paymentMethod.value = defaultPaymentMethodForLocale(
        value,
        paymentMethodOptions.value.map(option => option.value)
      );
      const method = parsePaymentMethod(paymentMethod.value);
      paymentScene.value = method.provider === 'stripe' ? 'checkout' : detectPaymentScene();
    }
  });

  watch(() => paymentCurrency?.value, () => {
    ensureAvailablePaymentMethod(paymentMethod.value);
  });

  onMounted(() => {
    loadAvailablePaymentMethods();
  });

  return {
    payModalVisible,
    payLoading,
    currentOrder,
    currentPayment,
    paymentMethod,
    paymentMethodOptions,
    selectedPaymentMethod,
    paymentProvider,
    paymentScene,
    qrCode,
    payLink,
    paymentActionReady,
    redirectPayment,
    zeroAmountCheckout,
    zeroAmountOrder,
    externalSettlementOrder,
    checkoutTip,
    paymentFinished,
    paymentModalTitle,
    paymentInstruction,
    paymentOrderNoText,
    redirectPayButtonText,
    checkout,
    detectPaymentScene,
    handlePageShow,
    handleVisibilityChange,
    handlePayModalClose,
    openPayLink,
    goMyOrders,
    goSubmitTicket,
    continueShopping,
    restorePendingPayment,
    stopPolling
  };
}
