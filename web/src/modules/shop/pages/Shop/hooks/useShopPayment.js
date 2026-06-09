import { computed, ref } from 'vue';
import { useLocalStorage } from '@vueuse/core';
import { message } from 'ant-design-vue';
import { createOrder, getOrder } from '../apis/api.js';
import { deliveryStatusText } from '@/modules/shop/utils/shopDelivery.js';
import { isGitRepositoryAccessType, isGitRepositoryDonationAccessType } from '@/modules/shop/utils/shopCovers.js';

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

export function useShopPayment({
  item,
  user,
  store,
  router,
  itemDeliveryMode,
  loadOrders,
  validateCheckout,
  coupon,
  orderAmountPayload
}) {
  const pendingPaymentOrderNo = useLocalStorage('shop-pending-payment-order', '');
  const pendingPaymentScene = useLocalStorage('shop-pending-payment-scene', '');
  const payModalVisible = ref(false);
  const payLoading = ref(false);
  const currentOrder = ref(null);
  const currentPayment = ref(null);
  const paymentScene = ref('native');
  const poller = ref(null);

  const qrCode = computed(() => {
    const text = currentPayment.value?.qrcodeText;
    return text ? `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(text)}` : '';
  });
  const payLink = computed(() => currentPayment.value?.jumpUrl || currentPayment.value?.qrcodeText || '');
  const currentTradeType = computed(() => (currentPayment.value?.tradeType || paymentScene.value || 'native').toLowerCase());
  const redirectPayment = computed(() => ['h5', 'jsapi'].includes(currentTradeType.value));
  const checkoutTip = computed(() => {
    if (isGitRepositoryDonationAccessType(item.value?.type)) {
      return '确认打赏金额';
    }
    return isGitRepositoryAccessType(item.value?.type)
      ? '确认绑定账号'
      : (paymentScene.value === 'native' ? '确认订单信息' : '发起支付');
  });
  const redirectPayButtonText = computed(() => {
    if (currentTradeType.value === 'jsapi') {
      return '前往微信支付';
    }
    if (currentTradeType.value === 'h5') {
      return '前往 H5 支付';
    }
    return '继续支付';
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
    if (paymentFinished.value) {
      return ['PAID', 'DELIVERED'].includes(currentOrder.value?.status) ? '支付已完成' : '支付已结束';
    }
    const titles = {
      native: '微信扫码支付',
      h5: '手机 H5 支付',
      jsapi: '微信 JSAPI 支付'
    };
    return titles[currentTradeType.value] || '完成支付';
  });
  const paymentInstruction = computed(() => {
    if (currentOrder.value?.status === 'FAILED') {
      return '订单未能完成，请在我的订单中核实';
    }
    if (currentOrder.value?.status === 'DELIVERED') {
      return '服务已开通，可在我的订单查看详情';
    }
    if (currentOrder.value?.status === 'PAID') {
      const deliveryMode = currentOrder.value.deliveryMode || itemDeliveryMode.value;
      if (currentOrder.value.deliveryStatus === 'WAITING' && deliveryMode === 'MANUAL') {
        return '支付已完成，等待人工交付';
      }
      return deliveryStatusText(currentOrder.value.deliveryStatus, deliveryMode);
    }
    if (paymentFinished.value) {
      return '订单已更新，可在我的订单查看详情';
    }
    if (currentTradeType.value === 'native') {
      return '请使用微信扫码完成支付';
    }
    if (currentTradeType.value === 'jsapi') {
      return '将打开微信支付页，支付后回到本页自动刷新状态';
    }
    return '将打开手机支付页，支付后回到本页自动刷新状态';
  });
  const paymentOrderNoText = computed(() => currentOrder.value?.orderNo ? `订单号 ${currentOrder.value.orderNo}` : '');

  const stopPolling = () => {
    if (poller.value) {
      clearInterval(poller.value);
      poller.value = null;
    }
  };

  const refreshOrder = async orderNo => {
    const latest = await getOrder(orderNo);
    currentOrder.value = latest;
    if (!isPaymentFlowFinished(latest)) {
      return;
    }

    stopPolling();
    pendingPaymentOrderNo.value = '';
    pendingPaymentScene.value = '';
    await loadOrders();
    if (latest.status === 'DELIVERED') {
      message.success('支付完成，服务已开通');
    } else if (latest.status === 'PAID') {
      message.success('支付完成，待人工交付');
    } else if (latest.status === 'FAILED') {
      message.error(latest.deliveryMessage || '支付或交付失败');
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
      if (isPaymentFlowFinished(latest)) {
        pendingPaymentOrderNo.value = '';
        pendingPaymentScene.value = '';
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

  const checkout = async () => {
    if (!user.value?.id) {
      store.rememberRedirect(location.pathname + location.search);
      router.push('/login');
      return;
    }
    if (validateCheckout && !validateCheckout()) {
      return;
    }

    paymentScene.value = detectPaymentScene();

    const couponResult = await coupon.resolveCouponCodeForOrder();
    if (!couponResult.ok) {
      return;
    }

    payLoading.value = true;
    try {
      const res = await createOrder({
        itemId: item.value.id,
        count: 1,
        couponCode: couponResult.couponCode,
        properties: {},
        ...(orderAmountPayload ? orderAmountPayload() : {}),
        payType: 'wechat',
        tradeType: paymentScene.value
      });
      currentOrder.value = res.order;
      currentPayment.value = res.payment;
      pendingPaymentOrderNo.value = res.order.orderNo;
      pendingPaymentScene.value = res.payment?.tradeType || paymentScene.value;
      payModalVisible.value = true;
      startPolling(res.order.orderNo);
    } catch (e) {
      if (coupon.isCouponFailure(e)) {
        coupon.removeInvalidCoupon(e.message || '优惠券核销失败，已移除');
        return;
      }
      message.error(e.message || '发起支付失败');
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
    stopPolling();
    loadOrders();
  };

  const handlePayModalClose = () => {
    clearPaymentState();
  };

  const openPayLink = () => {
    if (payLink.value) {
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

  return {
    payModalVisible,
    payLoading,
    currentOrder,
    currentPayment,
    paymentScene,
    qrCode,
    payLink,
    redirectPayment,
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
