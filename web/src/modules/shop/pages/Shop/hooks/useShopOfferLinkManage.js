import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { getShopItemDetail } from '../apis/api.js';
import {
  createItemOfferLink,
  getManageUsers,
  getItemOfferLinks,
  revokeItemOfferLink
} from '../apis/manage.js';
import {
  buildOfferLinkPayload,
  isSignedPaidFulfillment
} from '@/modules/shop/utils/shopOfferLink.js';

const defaultForm = () => ({
  name: '',
  skuId: undefined,
  purpose: 'PROMOTION',
  type: 'DISCOUNT',
  discountValue: 8,
  maxDiscountAmount: 0,
  validDays: 5,
  recipientUserId: undefined,
  recipientName: '',
  contractReference: '',
  paymentReference: '',
  confirmationNote: '',
  settlementAmount: undefined,
  settlementCurrency: 'CNY'
});

const copyText = async text => {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const input = document.createElement('textarea');
  input.value = text;
  input.style.position = 'fixed';
  input.style.opacity = '0';
  document.body.appendChild(input);
  input.select();
  document.execCommand('copy');
  input.remove();
};

export function useShopOfferLinkManage({ item, open }) {
  const detail = ref({});
  const links = ref([]);
  const form = ref(defaultForm());
  const loading = ref(false);
  const submitting = ref(false);
  const generatedUrl = ref('');
  const generatedPurpose = ref('');
  const recipientLoading = ref(false);
  const recipientOptions = ref([]);
  let recipientSearchTimer;

  const skuOptions = computed(() => (detail.value.skus || [])
    .filter(sku => sku.enabled !== false)
    .map(sku => ({
      label: `${sku.name} · ¥${Number(sku.price || 0).toFixed(2)}`,
      value: sku.id
    })));
  // 只要有可选套餐就必须选择；多规格商品即使 skus 缺失（如序列化异常）也要拦截，避免漏传 skuId
  const isMultiSku = computed(() =>
    detail.value.skuMode === 'MULTI' || detail.value.multiSku === true || Number(detail.value.skuCount || 0) > 1);
  const skuRequired = computed(() => skuOptions.value.length > 0 || isMultiSku.value);

  const syncSettlementAmount = () => {
    if (!isSignedPaidFulfillment(form.value)) {
      return;
    }
    const sku = (detail.value.skus || []).find(value => String(value.id) === String(form.value.skuId));
    const product = sku || detail.value;
    const amount = form.value.settlementCurrency === 'USD'
      ? (product.effectiveUsdPrice ?? product.usdPrice)
      : product.price;
    form.value.settlementAmount = Number.isFinite(Number(amount)) ? Number(amount) : undefined;
  };

  const resetForm = () => {
    form.value = defaultForm();
    if (skuRequired.value && skuOptions.value.length === 1) {
      form.value.skuId = skuOptions.value[0].value;
    }
  };

  const recipientLabel = user => {
    const identity = user.username || user.email || `用户 ${user.id}`;
    const contact = user.email || user.phone;
    return `${identity} · ID ${user.id}${contact ? ` · ${contact}` : ''}`;
  };

  const loadRecipients = async keyword => {
    recipientLoading.value = true;
    try {
      const users = await getManageUsers({ keyword: String(keyword || '').trim() || undefined });
      recipientOptions.value = (Array.isArray(users) ? users : []).slice(0, 50).map(user => ({
        label: recipientLabel(user),
        value: user.id
      }));
    } catch (e) {
      message.error(e.message || '客户列表加载失败');
    } finally {
      recipientLoading.value = false;
    }
  };

  const searchRecipients = keyword => {
    clearTimeout(recipientSearchTimer);
    recipientSearchTimer = setTimeout(() => loadRecipients(keyword), 180);
  };

  const load = async () => {
    if (!item.value?.id) {
      return;
    }
    loading.value = true;
    // 商品详情决定套餐是否可选，必须独立加载；历史链接失败不应拖累表单渲染。
    const [productResult, recordsResult] = await Promise.allSettled([
      getShopItemDetail(item.value.id),
      getItemOfferLinks(item.value.id)
    ]);
    if (productResult.status === 'fulfilled') {
      detail.value = productResult.value || {};
      resetForm();
    } else {
      message.error(productResult.reason?.message || '商品详情加载失败');
    }
    if (recordsResult.status === 'fulfilled') {
      links.value = Array.isArray(recordsResult.value) ? recordsResult.value : [];
    }
    loading.value = false;
  };

  const create = async () => {
    submitting.value = true;
    generatedUrl.value = '';
    generatedPurpose.value = '';
    try {
      const created = await createItemOfferLink(item.value.id, {
        ...buildOfferLinkPayload(form.value)
      });
      generatedUrl.value = created.shareUrl;
      generatedPurpose.value = created.purpose || 'PROMOTION';
      links.value = [created, ...links.value];
      message.success('专享购买链接已生成');
      resetForm();
      return true;
    } catch (e) {
      message.error(e.message || '生成失败');
      return false;
    } finally {
      submitting.value = false;
    }
  };

  const revoke = async record => {
    try {
      await revokeItemOfferLink(record.id);
      record.status = 'REVOKED';
      message.success('链接已作废');
    } catch (e) {
      message.error(e.message || '作废失败');
    }
  };

  const copyGeneratedUrl = async () => {
    if (!generatedUrl.value) {
      return;
    }
    try {
      await copyText(generatedUrl.value);
      message.success('链接已复制');
    } catch {
      message.error('复制失败');
    }
  };

  watch(open, visible => {
    if (visible) {
      generatedUrl.value = '';
      generatedPurpose.value = '';
      load();
    }
  }, { immediate: true });

  watch(() => [form.value.purpose, form.value.skuId, form.value.settlementCurrency], syncSettlementAmount);

  onBeforeUnmount(() => clearTimeout(recipientSearchTimer));

  return {
    detail,
    links,
    form,
    loading,
    submitting,
    generatedUrl,
    generatedPurpose,
    recipientLoading,
    recipientOptions,
    skuOptions,
    skuRequired,
    searchRecipients,
    create,
    revoke,
    copyGeneratedUrl
  };
}
