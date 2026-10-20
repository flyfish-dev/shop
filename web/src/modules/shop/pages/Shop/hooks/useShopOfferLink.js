import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { previewOfferLink } from '../apis/api.js';

const PREVIEW_DEBOUNCE_MS = 180;

const queryToken = route => {
  const value = route?.query?.offer;
  return Array.isArray(value) ? String(value[0] || '').trim() : String(value || '').trim();
};

/**
 * 解析并试算一次性专享购买链接。页面预览不会占用链接，真正的原子预占发生在创建订单时。
 */
export function useShopOfferLink({ item, route, orderAmountPayload, user }) {
  const preview = ref(null);
  const loading = ref(false);
  const error = ref('');
  let requestSequence = 0;
  let previewTimer;

  const token = computed(() => queryToken(route));
  const present = computed(() => Boolean(token.value));
  const active = computed(() => present.value && Boolean(preview.value) && !error.value);
  const externalSettlement = computed(() => active.value
    && preview.value?.purpose === 'SIGNED_PAID_FULFILLMENT');
  const blocksCheckout = computed(() => present.value && (loading.value || !active.value));

  const reset = () => {
    requestSequence += 1;
    preview.value = null;
    error.value = '';
    loading.value = false;
  };

  const loadPreview = async () => {
    if (!token.value || !item.value?.id) {
      reset();
      return false;
    }
    const sequence = ++requestSequence;
    const payload = orderAmountPayload ? orderAmountPayload() : {};
    loading.value = true;
    error.value = '';
    try {
      const result = await previewOfferLink({
        token: token.value,
        itemId: item.value.id,
        skuId: item.value.skuId,
        donationAmount: payload.donationAmount,
        paymentCurrency: payload.paymentCurrency
      });
      if (sequence !== requestSequence) {
        return false;
      }
      preview.value = result;
      return true;
    } catch (e) {
      if (sequence === requestSequence) {
        preview.value = null;
        error.value = e.message || '专享购买链接不可用';
      }
      return false;
    } finally {
      if (sequence === requestSequence) {
        loading.value = false;
      }
    }
  };

  const schedulePreview = () => {
    clearTimeout(previewTimer);
    if (!token.value || !item.value?.id) {
      reset();
      return;
    }
    requestSequence += 1;
    preview.value = null;
    error.value = '';
    loading.value = true;
    previewTimer = setTimeout(loadPreview, PREVIEW_DEBOUNCE_MS);
  };

  const resolveForOrder = async () => {
    if (!present.value) {
      return { ok: true, offerToken: undefined };
    }
    if (!active.value && !(await loadPreview())) {
      return { ok: false, offerToken: undefined };
    }
    return { ok: true, offerToken: token.value };
  };

  const markInvalid = text => {
    preview.value = null;
    error.value = text || '专享购买链接不可用';
  };

  watch(() => [
    token.value,
    item.value?.id,
    item.value?.skuId,
    user?.value?.id,
    orderAmountPayload?.().donationAmount,
    orderAmountPayload?.().paymentCurrency
  ], schedulePreview, { immediate: true });

  onBeforeUnmount(() => clearTimeout(previewTimer));

  return {
    offerToken: token,
    offerLinkPresent: present,
    offerLinkActive: active,
    offerLinkExternalSettlement: externalSettlement,
    offerLinkLoading: loading,
    offerLinkError: error,
    offerLinkPreview: preview,
    offerLinkBlocksCheckout: blocksCheckout,
    loadOfferLinkPreview: loadPreview,
    resolveOfferLinkForOrder: resolveForOrder,
    markOfferLinkInvalid: markInvalid
  };
}
