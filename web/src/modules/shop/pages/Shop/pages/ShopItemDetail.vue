<script setup>
import { useRouter } from '@/router/use.js';
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { getShopItemDetail } from '../apis/api.js';
import {
  CheckCircleOutlined,
  CrownOutlined,
  EyeOutlined,
  FireOutlined,
  GithubOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import gitea from '@/assets/gitea-text.svg';
import gitee from '@/assets/gitee.svg';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import {
  isGitRepositoryAccessType,
  isPureDonationType,
  resolveShopItemImages,
  setShopImageFallback
} from '@/modules/shop/utils/shopCovers.js';
import {
  deliveryModeColor,
  deliveryModeText,
  deliveryStatusColor,
  deliveryStatusText,
  normalizeDeliveryModeForType,
  orderStatusColor,
  orderStatusText,
  shopItemTypeText
} from '@/modules/shop/utils/shopDelivery.js';
import { getShopItemHighlight } from '@/modules/shop/utils/shopItemEffects.js';
import { useGitRepositoryBinding } from '../hooks/useGitRepositoryBinding.js';
import { useShopDonationAmount } from '../hooks/useShopDonationAmount.js';
import { useShopCoupon } from '../hooks/useShopCoupon.js';
import { useShopDefaultCoupon } from '../hooks/useShopDefaultCoupon.js';
import { useShopOrders } from '../hooks/useShopOrders.js';
import { useShopPayment } from '../hooks/useShopPayment.js';
import { useShopContractAgreement } from '../hooks/useShopContractAgreement.js';
import { useShopPurchaseAvailability } from '../hooks/useShopPurchaseAvailability.js';
import ShopSupportEntry from '../components/ShopSupportEntry.vue';
import ShopContractAgreementModal from '../components/ShopContractAgreementModal.vue';
import ShopPaymentMethodSelector from '../components/ShopPaymentMethodSelector.vue';
import { resolveLocalizedShopItem } from '@/modules/shop/utils/shopI18n.js';
import {
  buildOrderFormProperties,
  defaultOrderFormValues,
  normalizeOrderFormValues,
  parseShopOrderFormConfig
} from '@/modules/shop/utils/shopOrderForm.js';
import { message } from 'ant-design-vue';
import { formatShopMoney } from '@/modules/shop/utils/shopMoney.js';

const ShopMarkdownPreview = defineAsyncComponent(() => import('../components/ShopMarkdownPreview.vue'));

const store = useClientStore();
const { user } = storeToRefs(store);
const router = useRouter();
const { locale, t } = useI18n();

const loading = ref(true);
const data = ref({});
const selectedSkuId = ref(null);
const localizedData = computed(() => resolveLocalizedShopItem(data.value, locale.value) || {});
const enabledSkus = computed(() => (data.value.skus || []).filter(sku => sku.enabled !== false));
const localizedSkus = computed(() => enabledSkus.value.map(sku => resolveLocalizedShopItem(sku, locale.value)));
const selectedSku = computed(() => {
  if (!enabledSkus.value.length) {
    return null;
  }
  return enabledSkus.value.find(sku => String(sku.id) === String(selectedSkuId.value))
    || enabledSkus.value.find(sku => sku.defaultSelected)
    || enabledSkus.value[0];
});
const selectedLocalizedSku = computed(() => localizedSkus.value.find(sku =>
  String(sku.id) === String(selectedSku.value?.id)) || null);
const saleItem = computed(() => {
  const sku = selectedSku.value;
  if (!sku) {
    return data.value;
  }
  return {
    ...data.value,
    skuId: sku.id,
    skuCode: sku.code,
    skuName: sku.name,
    price: sku.price,
    usdPrice: sku.usdPrice,
    effectiveUsdPrice: sku.effectiveUsdPrice,
    cnyPerUsd: sku.cnyPerUsd || data.value.cnyPerUsd,
    type: sku.type || data.value.type,
    typeName: sku.typeName || data.value.typeName,
    deliveryMode: sku.deliveryMode || data.value.deliveryMode,
    deliveryModeName: sku.deliveryModeName || data.value.deliveryModeName,
    deliveryActions: sku.deliveryActions || data.value.deliveryActions,
    params: sku.params || data.value.params,
    tags: Array.isArray(sku.tags) && sku.tags.length ? sku.tags : data.value.tags,
    description: sku.description || data.value.description,
    i18n: Object.keys(sku.i18n || {}).length ? sku.i18n : data.value.i18n,
    defaultCouponEnabled: sku.defaultCouponEnabled,
    defaultCouponCode: sku.defaultCouponCode,
    defaultCouponPreview: sku.defaultCouponPreview || data.value.defaultCouponPreview,
    contractRequired: sku.contractRequired
  };
});
const localizedSaleData = computed(() => resolveLocalizedShopItem(saleItem.value, locale.value) || {});
const orderFormValues = ref({});
const orderFormConfig = computed(() => parseShopOrderFormConfig(saleItem.value.params, {
  locale: locale.value,
  t
}));
const orderFormReady = computed(() => normalizeOrderFormValues(orderFormConfig.value, orderFormValues.value, t).ok);
const detailError = ref('');
const activeTab = ref('1');
let detailRequestSeq = 0;

const itemDeliveryMode = computed(() => normalizeDeliveryModeForType(saleItem.value.type, saleItem.value.deliveryMode));
const saleItemTypeText = computed(() => shopItemTypeText(saleItem.value.type, t));
const saleItemDeliveryText = computed(() => deliveryModeText(itemDeliveryMode.value, t));
const images = computed(() => resolveShopItemImages(data.value));
const detailPreviewId = computed(() => `shop-item-detail-${data.value.id || 'empty'}`);
const recordTitle = computed(() => isGitRepositoryAccessType(data.value.type) ? t('shop.activationRecord') : t('shop.purchaseRecord'));
const viewCountText = computed(() => Number(data.value.viewCount || 0).toLocaleString(locale.value));
const priceStartBeforeAmount = computed(() => donationEnabled.value
  && String(locale.value || '').toLowerCase().startsWith('en'));
const displayCurrencySymbol = computed(() => paymentCurrency.value === 'CNY'
  && String(locale.value || '').toLowerCase().startsWith('en') ? 'CN¥' : currencySymbol.value);
const formatSkuPrice = sku => paymentCurrency.value === 'USD'
  ? formatShopMoney(sku?.effectiveUsdPrice, 'USD')
  : formatShopMoney(sku?.price, 'CNY');
const highlightIcons = {
  crown: CrownOutlined,
  badge: SafetyCertificateOutlined,
  spark: ThunderboltOutlined,
  fire: FireOutlined
};
const itemHighlight = computed(() => getShopItemHighlight(data.value, t));
const itemHighlightIcon = computed(() => highlightIcons[itemHighlight.value.icon]);

const {
  bindingLoading,
  gitProvider,
  gitProviderName,
  gitBindTitle,
  gitAuthorization,
  gitAccount,
  gitBindingReminderVisible,
  gitBindingReminderTitle,
  gitBindingReminderDescription,
  authorize,
  validateGitCheckout
} = useGitRepositoryBinding({ item: saleItem, user, store, router });

const {
  orders,
  ordersLoading,
  ordersError,
  loadOrders
} = useShopOrders({ item: data, user });

const {
  donationAmount,
  donationEnabled,
  minimumDonationAmount,
  paymentCurrency,
  currencySymbol,
  payableBaseAmount,
  validateDonationAmount,
  orderAmountPayload
} = useShopDonationAmount({ item: saleItem });

const {
  availabilityLoading,
  availabilityError,
  purchaseBlocked,
  purchaseBlockTitle,
  purchaseBlockMessage,
  availabilityNotice,
  loadPurchaseAvailability,
  validatePurchaseAvailability
} = useShopPurchaseAvailability({ item: saleItem, user, gitAuthorization });

const refreshOrderState = async () => {
  await Promise.allSettled([
    loadOrders(),
    loadPurchaseAvailability()
  ]);
};

const validateOrderForm = () => {
  const result = normalizeOrderFormValues(orderFormConfig.value, orderFormValues.value, t);
  if (!result.ok) {
    message.warning(result.message || t('shop.completeOrderDetails'));
    return false;
  }
  return true;
};

const validateCheckout = () => validateGitCheckout()
  && validatePurchaseAvailability()
  && validateDonationAmount()
  && validateOrderForm();

const canCheckout = computed(() => {
  if (!user.value?.id) {
    return false;
  }
  if (isGitRepositoryAccessType(saleItem.value.type) && !gitAuthorization.value) {
    return false;
  }
  if (purchaseBlocked.value || availabilityLoading.value) {
    return false;
  }
  if (!orderFormReady.value) {
    return false;
  }
  return data.value.enabled !== false;
});
const checkoutHint = computed(() => {
  if (availabilityLoading.value) {
    return t('shop.checkingStatus');
  }
  if (purchaseBlocked.value) {
    return t('shop.purchasedHint');
  }
  if (orderFormConfig.value.enabled && !orderFormReady.value) {
    return t('shop.completeOrderDetails');
  }
  return checkoutTip.value;
});
const checkoutButtonText = computed(() => {
  if (isPureDonationType(saleItem.value.type)) {
    return t('shop.immediateDonate');
  }
  return donationEnabled.value ? t('shop.donationAccess') : t('shop.immediateBuy');
});

const coupon = useShopCoupon({
  item: saleItem,
  user,
  store,
  router,
  baseAmount: payableBaseAmount,
  orderAmountPayload
});
const couponSupported = computed(() => paymentCurrency.value !== 'USD');
const {
  couponApplying,
  couponCode,
  appliedCoupon,
  couponError,
  currentPayableAmount,
  hasAppliedCoupon,
  applyCouponCode,
  resetCoupon
} = coupon;

const { defaultPromotion } = useShopDefaultCoupon({
  item: saleItem,
  couponCode,
  appliedCoupon,
  couponError,
  enabled: couponSupported
});

watch(couponSupported, supported => {
  if (!supported) {
    resetCoupon();
  }
});

const {
  payModalVisible,
  payLoading,
  currentOrder,
  paymentMethod,
  paymentMethodOptions,
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
} = useShopPayment({
  item: saleItem,
  user,
  store,
  router,
  itemDeliveryMode,
  loadOrders: refreshOrderState,
  validateCheckout,
  coupon,
  orderAmountPayload,
  paymentCurrency
});

const contractAgreement = useShopContractAgreement({
  item: saleItem,
  user,
  store,
  router
});

const handleCheckout = async () => {
  if (!validateCheckout()) {
    return;
  }
  const orderFormResult = normalizeOrderFormValues(orderFormConfig.value, orderFormValues.value, t);
  const contractPayload = await contractAgreement.ensureContractAgreement();
  if (contractPayload === null) {
    return;
  }
  await checkout({
    ...(contractPayload || {}),
    ...(orderFormConfig.value.enabled
      ? { properties: buildOrderFormProperties(orderFormResult.values) }
      : {})
  });
};

const useDefaultCover = event => setShopImageFallback(event, data.value?.type);

const resolveDefaultSkuId = detail => {
  const skus = (detail?.skus || []).filter(sku => sku.enabled !== false);
  return (skus.find(sku => sku.defaultSelected) || skus[0])?.id || null;
};

const loadDetail = async id => {
  if (!id) {
    return;
  }
  const requestSeq = ++detailRequestSeq;
  const currentId = String(id);
  loading.value = true;
  detailError.value = '';
  let loaded = false;
  try {
    resetCoupon();
    const detail = await getShopItemDetail(id);
    if (requestSeq === detailRequestSeq && String(router.route.params.id) === currentId) {
      data.value = detail;
      selectedSkuId.value = resolveDefaultSkuId(detail);
      const defaultSku = (detail.skus || []).find(sku => String(sku.id) === String(selectedSkuId.value));
      orderFormValues.value = defaultOrderFormValues(parseShopOrderFormConfig(
        defaultSku?.params || detail.params,
        { locale: locale.value, t }
      ));
      loaded = true;
    }
  } catch (e) {
    if (requestSeq === detailRequestSeq && String(router.route.params.id) === currentId) {
      data.value = {};
      detailError.value = e.message || t('shop.productUnavailable');
    }
  } finally {
    if (requestSeq === detailRequestSeq) {
      loading.value = false;
    }
  }
  if (loaded && requestSeq === detailRequestSeq && String(router.route.params.id) === currentId) {
    refreshOrderState();
  }
};

onMounted(async () => {
  await store.loadUser().catch(() => null);
  await loadDetail(router.route.params.id);
  paymentScene.value = detectPaymentScene();
  await restorePendingPayment();
  window.addEventListener('pageshow', handlePageShow);
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onBeforeUnmount(() => {
  stopPolling();
  window.removeEventListener('pageshow', handlePageShow);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

watch(() => router.route.params.id, async id => {
  await loadDetail(id);
});

watch(() => user.value?.id, () => {
  refreshOrderState();
});

watch(() => gitAuthorization.value, () => {
  loadPurchaseAvailability();
});

watch(() => [saleItem.value?.skuId, saleItem.value?.params], () => {
  orderFormValues.value = defaultOrderFormValues(orderFormConfig.value);
  resetCoupon();
  loadPurchaseAvailability();
});

const handleTabChange = key => {
  if (key === '2') {
    loadOrders();
  }
};
</script>

<template>
  <a-spin :spinning='loading'>
    <a-card>
      <a-page-header :title="t('shop.detailTitle')" @back='() => router.replace("/shop")'>
        <template #footer>
          <a-tabs v-model:activeKey='activeTab' @change='handleTabChange'>
            <a-tab-pane key='1' :tab="t('shop.detailTab')">
              <a-empty v-if='detailError' :description='detailError' />
              <div v-else-if='localizedSaleData.description' class='detail-content'>
                <section class='detail-showcase'>
                  <img :src='images[0]' alt='' @error='useDefaultCover' />
                  <div class='detail-showcase-text'>
                    <a-tag v-if='saleItem.type' color='blue'>{{ saleItemTypeText }}</a-tag>
                    <h3>{{ localizedData.name }}</h3>
                    <p v-if='selectedLocalizedSku'>{{ selectedLocalizedSku.name }}</p>
                    <p>{{ saleItemDeliveryText }}</p>
                  </div>
                </section>
                <ShopMarkdownPreview
                  :id='detailPreviewId'
                  class='description'
                  :model-value='localizedSaleData.description || ""'
                  :language='locale'
                  preview-theme='default'
                  code-theme='github'
                />
              </div>
              <a-empty v-else :description="t('shop.emptyDetail')" />
            </a-tab-pane>
            <a-tab-pane key='2' :tab='recordTitle'>
              <a-alert
                v-if='ordersError'
                :message='ordersError'
                type='warning'
                show-icon
              />
              <a-spin v-else :spinning='ordersLoading'>
                <a-empty v-if='!user?.id' :description="t('shop.loginToViewRecords')" />
                <a-empty v-else-if='!orders.length' :description="`${t('shop.noRecordPrefix')}${recordTitle}`" />
              <a-list v-else :data-source='orders' size='small' class='order-list'>
                <template #renderItem='{ item }'>
                  <a-list-item>
                    <a-list-item-meta :title='item.itemName || localizedData.name' :description='item.orderNo' />
                    <a-space class='order-meta'>
                      <a-tag :color='orderStatusColor(item.status)'>{{ orderStatusText(item.status, t) }}</a-tag>
                      <a-tag :color='deliveryStatusColor(item.deliveryStatus)'>
                        {{ deliveryStatusText(item.deliveryStatus, item.deliveryMode || itemDeliveryMode, t) }}
                      </a-tag>
                      <span>{{ item.paidTime || item.createTime }}</span>
                    </a-space>
                  </a-list-item>
                </template>
              </a-list>
              </a-spin>
            </a-tab-pane>
          </a-tabs>
        </template>
        <div v-if='!detailError' class='item-container'>
          <div class='item-info-box'>
            <div class='cover'>
	              <a-carousel v-if='images.length' arrows :dots='images.length > 1' dots-class='slick-dots slick-thumb'>
	                <template #customPaging='{ i }'>
	                  <a><img :src='images[i]' alt='' @error='useDefaultCover' /></a>
	                </template>
	                <div v-for='(img, i) in images' :key='i'>
	                  <img :src='img' alt='' @error='useDefaultCover' />
	                </div>
	              </a-carousel>
	            </div>
            <div class='content'>
              <h2>
                <span>{{ localizedData.name }}</span>
                <a-tag v-if='selectedLocalizedSku' color='cyan'>{{ selectedLocalizedSku.name }}</a-tag>
                <a-tag
                  v-if='itemHighlight.style'
                  class='detail-highlight-tag'
                  :color='itemHighlight.style.color'
                >
                  <component v-if='itemHighlightIcon' :is='itemHighlightIcon' />
                  {{ itemHighlight.style.label }}
                </a-tag>
              </h2>
              <div class='tags'>
                <a-tag v-if='saleItem.type' color='blue'>{{ saleItemTypeText }}</a-tag>
                <a-tag :color='deliveryModeColor(itemDeliveryMode)'>
                  {{ saleItemDeliveryText }}
                </a-tag>
                <a-tag color='green' v-for='tag in localizedSaleData.tags' :key='tag'>{{ tag }}</a-tag>
              </div>
              <div v-if='enabledSkus.length' class='sku-selector'>
                <span>{{ t('shop.package') }}</span>
                <a-radio-group v-model:value='selectedSkuId' class='sku-options'>
                  <a-radio-button
                    v-for='sku in localizedSkus'
                    :key='sku.id'
                    :value='sku.id'
                  >
                    <strong>{{ sku.name }}</strong>
                    <em>{{ formatSkuPrice(sku) }}</em>
                  </a-radio-button>
                </a-radio-group>
              </div>
              <div class='price-box'>
                <div class='price-main'>
                  <span v-if='hasAppliedCoupon' class='original-price'>{{ displayCurrencySymbol }}{{ appliedCoupon.originalAmount }}</span>
                  <span v-if='priceStartBeforeAmount' class='price-suffix'>{{ t('shop.priceStartSuffix') }}</span>
                  <span class='price'>
                    <span class='currency-mark'>{{ displayCurrencySymbol }}</span>{{ currentPayableAmount }}
                  </span>
                  <span v-if='donationEnabled && !priceStartBeforeAmount' class='price-suffix'>{{ t('shop.priceStartSuffix') }}</span>
                </div>
                <div class='item-stats'>
                  <span class='count'>{{ t('shop.soldCount', { count: data.buyCount ?? 0 }) }}</span>
                  <span class='view-count'>
                    <eye-outlined />
                    {{ t('shop.viewCount', { count: viewCountText }) }}
                  </span>
                </div>
              </div>
              <a-alert
                v-if='gitBindingReminderVisible'
                class='git-binding-reminder'
                :message='gitBindingReminderTitle'
                :description='gitBindingReminderDescription'
                type='warning'
                show-icon
              >
                <template #action>
                  <a-button size='small' type='link' @click.stop='authorize'>{{ t('shop.bindNow') }}</a-button>
                </template>
              </a-alert>
              <a-card v-if='isGitRepositoryAccessType(saleItem.type)' size='small' :title='gitBindTitle' class='account-bind'
                      :class='{loading: bindingLoading}' @click='authorize'>
                <template #extra>
                  <a-tooltip>
                    <template #title>{{ t('shop.bindGitTooltip') }}</template>
                    <question-circle-outlined />
                  </a-tooltip>
                </template>
                <a class='bind-button'>
                  <template v-if='gitAuthorization'>
                    <div class='bound-account'>
                      <a-avatar v-if='gitAccount.avatar' :src='gitAccount.avatar' :size='44' />
                      <a-avatar v-else :size='44'>
                        <user-outlined />
                      </a-avatar>
                      <div class='bound-main'>
                        <div class='bound-title'>
                          <span>{{ gitAccount.name }}</span>
                          <a-space :size='4' class='success'>
                            <check-circle-outlined />
                            {{ t('shop.boundAccount') }}
                          </a-space>
                        </div>
                        <div class='bound-meta'>
                          <span v-if='gitAccount.login'>@{{ gitAccount.login }}</span>
                          <span v-if='gitAccount.email'>{{ gitAccount.email }}</span>
                          <span v-if='!gitAccount.login && !gitAccount.email && gitAccount.openid'>
                            {{ t('shop.identifier', { id: gitAccount.maskedOpenid }) }}
                          </span>
                          <span v-if='gitAccount.authTime'>{{ gitAccount.authTime }}</span>
                        </div>
                        <a
                          v-if='gitAccount.profileUrl'
                          class='bound-link'
                          :href='gitAccount.profileUrl'
                          target='_blank'
                          rel='noreferrer'
                          @click.stop
                        >
                          {{ t('shop.viewProfile') }}
                        </a>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <github-outlined v-if='gitProvider === "github"' class='provider-login-icon' />
                    <img v-else-if='gitProvider === "gitee"' :src='gitee' :alt='gitProviderName'>
                    <img v-else :src='gitea' alt='Gitea'>
                    {{ bindingLoading ? t('shop.bindLoading') : t('shop.bindProvider', { provider: gitProviderName }) }}
                  </template>
                </a>
              </a-card>
              <a-alert
                v-if='purchaseBlocked'
                class='purchase-guard'
                :message='purchaseBlockTitle'
                :description='purchaseBlockMessage'
                type='warning'
                show-icon
              >
                <template #action>
                  <a-button size='small' type='link' @click.stop='goMyOrders'>{{ t('common.viewOrders') }}</a-button>
                </template>
              </a-alert>
              <a-alert
                v-else-if='availabilityError'
                class='purchase-guard'
                :message='availabilityError'
                type='info'
                show-icon
              />
              <a-alert
                v-else-if='availabilityNotice'
                class='purchase-guard'
                :message="t('shop.repositoryStatus')"
                :description='availabilityNotice'
                type='info'
                show-icon
              />
              <a-alert
                v-if='saleItem.contractRequired'
                class='contract-guard'
                :message="t('shop.contractRequired')"
                type='info'
                show-icon
              />
              <div v-if='orderFormConfig.enabled' class='order-form-panel'>
                <div v-if='orderFormConfig.title || orderFormConfig.description' class='order-form-heading'>
                  <h3 v-if='orderFormConfig.title'>{{ orderFormConfig.title }}</h3>
                  <p v-if='orderFormConfig.description'>{{ orderFormConfig.description }}</p>
                </div>
                <a-form layout='vertical' class='order-form'>
                  <a-form-item
                    v-for='field in orderFormConfig.fields'
                    :key='field.key'
                    :label='field.type === "checkbox" ? "" : field.label'
                    :required='field.required && field.type !== "checkbox"'
                  >
                    <a-checkbox
                      v-if='field.type === "checkbox"'
                      v-model:checked='orderFormValues[field.key]'
                    >
                      {{ field.label }}
                    </a-checkbox>
                    <a-textarea
                      v-else-if='field.type === "textarea"'
                      v-model:value='orderFormValues[field.key]'
                      :placeholder='field.placeholder'
                      :maxlength='field.maxLength'
                      :auto-size='{ minRows: 3, maxRows: 6 }'
                    />
                    <a-select
                      v-else-if='field.type === "select"'
                      v-model:value='orderFormValues[field.key]'
                      :placeholder='field.placeholder'
                      :options='field.options'
                      allow-clear
                    />
                    <a-input
                      v-else
                      v-model:value='orderFormValues[field.key]'
                      :placeholder='field.placeholder'
                      :maxlength='field.maxLength'
                      :type='field.type === "url" ? "url" : "text"'
                    />
                    <div v-if='field.help' class='order-form-help'>{{ field.help }}</div>
                  </a-form-item>
                </a-form>
              </div>
              <shop-support-entry variant='inline' />
              <a-divider />
              <div v-if='donationEnabled' class='donation-line'>
                <span>{{ t('shop.donationAmount') }}</span>
                <div class='donation-main'>
                  <a-input-number
                    v-model:value='donationAmount'
                    :min='minimumDonationAmount'
                    :precision='2'
                    :step='1'
                    :prefix='currencySymbol'
                  />
                  <a-tag color='green'>{{ t('shop.donationStart', {
                    symbol: currencySymbol,
                    amount: Number(minimumDonationAmount || 0).toFixed(2)
                  }) }}</a-tag>
                </div>
              </div>
              <div v-if='couponSupported' class='coupon-line'>
                <span>{{ t('shop.coupon') }}</span>
                <div class='coupon-main'>
                  <a-input-group compact class='coupon-control'>
                    <a-input
                      v-model:value='couponCode'
                      allow-clear
                      :status='couponError ? "error" : ""'
                      @pressEnter='applyCouponCode()'
                    />
                    <a-button
                      :loading='couponApplying'
                      :disabled='!couponCode?.trim()'
                      @click='applyCouponCode()'
                    >
                      {{ t('common.apply') }}
                    </a-button>
                  </a-input-group>
                  <div v-if='hasAppliedCoupon' class='coupon-feedback success'>
                    <a-tag color='green'>{{ t('common.applied') }}</a-tag>
                    <span>{{ t('shop.discountSaved', { amount: appliedCoupon.discountAmount, payable: appliedCoupon.payableAmount }) }}</span>
                    <a-tag v-if='defaultPromotion.active && appliedCoupon.couponCode === defaultPromotion.couponCode' color='red'>
                      {{ t('common.automaticDiscount') }}
                    </a-tag>
                  </div>
                  <div v-else-if='couponError' class='coupon-feedback error'>
                    {{ couponError }}
                  </div>
                </div>
              </div>
              <div class='payment-method-line'>
                <span>{{ t('shop.paymentMethod') }}</span>
                <ShopPaymentMethodSelector
                  v-model="paymentMethod"
                  :options="paymentMethodOptions"
                  :disabled="payLoading"
                />
              </div>
              <div class='checkout'>
                <a-button
                  type='primary'
                  class='checkout-btn'
                  :loading="payLoading || availabilityLoading"
                  :disabled="!canCheckout"
                  @click='handleCheckout'
                >
                  {{ checkoutButtonText }}
                </a-button>
                <span class='checkout-tip'>{{ checkoutHint }}</span>
              </div>
            </div>
          </div>
        </div>
        <a-empty v-else :description='detailError' />
      </a-page-header>
    </a-card>

    <a-modal
      v-model:open="payModalVisible"
      :title="t('shop.paymentTitle')"
      :footer="null"
      :maskClosable="false"
      wrap-class-name="shop-payment-modal"
      @cancel="handlePayModalClose"
    >
      <div class="qrcode-container" :class="{ finished: paymentFinished }">
        <div class="payment-state">
          <check-circle-outlined v-if="paymentFinished" class="success-icon" />
          <a-tag v-else-if="currentOrder" :color="orderStatusColor(currentOrder.status)">
            {{ orderStatusText(currentOrder.status, t) }}
          </a-tag>
          <h3>{{ paymentModalTitle }}</h3>
          <p>{{ paymentInstruction }}</p>
          <span v-if="paymentFinished && paymentOrderNoText" class="order-no">{{ paymentOrderNoText }}</span>
        </div>
        <div v-if="currentOrder && !paymentFinished" class="pay-amount">
          <strong>{{ currentOrder.currency === 'USD' ? '$' : '¥' }}{{ currentOrder.amount }}</strong>
          <span v-if="Number(currentOrder.discountAmount || 0) > 0">
            {{ t('shop.payAmountSaved', { amount: currentOrder.discountAmount }) }}
          </span>
        </div>
        <img v-if="qrCode && !paymentFinished" :src="qrCode" :alt="t('shop.paymentQrAlt')" class="qr-image" />
        <a-alert
          v-if="currentOrder?.status === 'FAILED' && currentOrder?.deliveryMessage"
          :message="currentOrder.deliveryMessage"
          type="error"
          show-icon
        />
        <div v-if="!paymentFinished" class="pay-actions">
          <a-button v-if="redirectPayment && payLink" type="primary" @click="openPayLink">
            {{ redirectPayButtonText }}
          </a-button>
        </div>
        <div class="payment-help">
          <span>{{ t('shop.paymentHelp') }}</span>
          <a-button type="link" size="small" @click="goSubmitTicket">{{ t('common.submitTicket') }}</a-button>
        </div>
        <a-space v-if="paymentFinished" class="success-actions">
          <a-button type="primary" @click="goMyOrders">{{ t('shop.viewMyOrders') }}</a-button>
          <a-button @click="continueShopping">{{ t('shop.continueShopping') }}</a-button>
        </a-space>
      </div>
    </a-modal>

    <shop-contract-agreement-modal :agreement="contractAgreement" />
  </a-spin>
</template>

<style lang='less'>
.shop-payment-modal {
  .ant-modal {
    max-width: calc(100vw - 32px);
  }

  .ant-modal-content {
    border-radius: 8px;
  }

  .ant-modal-body {
    padding: 20px 24px 24px;
  }
}

.item-container {
  .item-info-box {
    .slick-dots {
      position: relative;
      height: auto;
      margin-top: 15px;
    }

	    .slick-slide {
	      height: auto;
        aspect-ratio: 1 / 1;
	      overflow: hidden;
	      border: 1px solid gainsboro;
	      padding: 5px;
	      background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
	    }

	    .slick-slide img {
	      width: 100%;
	      height: 100%;
	      background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
	      object-fit: cover;
	      color: transparent;
	      font-size: 0;
	    }

    .slick-arrow {
      display: none !important;
    }

    .slick-thumb {
      bottom: 0;

      li {
        width: 60px;
        height: 45px;

	        img {
	          width: 100%;
	          height: 100%;
	          background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
	          filter: grayscale(100%);
	          display: block;
	          color: transparent;
	          font-size: 0;
	        }

        &.slick-active img {
          filter: grayscale(0%);
          border: 2px solid green;
        }
      }
    }
  }
}

@media only screen and (max-width: 760px) {
  .shop-payment-modal {
    .ant-modal {
      top: 16px;
      max-width: calc(100vw - 24px);
      margin: 0 auto;
    }

    .ant-modal-body {
      padding: 16px;
    }
  }

  .item-container {
    .item-info-box {
      .slick-dots {
        margin-top: 10px;
      }

      .slick-thumb {
        li {
          width: 46px;
          height: 36px;
        }
      }
    }
  }
}
</style>

<style scoped lang='less'>
:deep(.ant-card-body) {
  padding: 24px;
}

:deep(.ant-page-header) {
  padding: 16px 0 0;
}

.item-container {
  .item-info-box {
    width: 100%;
    display: flex;
    gap: 24px;
    align-items: flex-start;

    .cover {
      flex: 0 0 350px;
      width: 350px;
    }

    .content {
      flex: 1;
      min-width: 0;
      padding: 12px 0 0;
      text-align: left;

      h2 {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin: 0 0 12px;
        color: #1f2d24;
        font-size: 24px;
        line-height: 1.35;
        word-break: break-word;

        > span {
          min-width: 0;
        }
      }

      .detail-highlight-tag {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-inline-end: 0;
        border-radius: 999px;
        font-weight: 650;
      }

      .tags {
        display: flex;
        min-height: 24px;
        flex-wrap: wrap;
        gap: 6px;

        :deep(.ant-tag) {
          margin-inline-end: 0;
        }
      }

      .sku-selector {
        display: grid;
        gap: 8px;
        margin-top: 16px;

        > span {
          color: #64766a;
          font-size: 13px;
          font-weight: 700;
        }

        .sku-options {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
        }

        :deep(.ant-radio-button-wrapper) {
          display: inline-flex;
          height: auto;
          min-height: 42px;
          max-width: 220px;
          align-items: center;
          gap: 8px;
          border-inline-start-width: 1px;
          border-radius: 8px;
          line-height: 1.2;

          &::before {
            display: none;
          }

          .ant-radio-button + span {
            display: inline-flex;
            min-width: 0;
            align-items: center;
            gap: 8px;
          }

          strong {
            overflow: hidden;
            min-width: 0;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          em {
            color: #ef4444;
            font-style: normal;
            font-weight: 700;
            white-space: nowrap;
          }
        }
      }

      .price-box {
        display: flex;
        margin-top: 18px;
        justify-content: space-between;
        align-items: flex-end;
        gap: 16px;
        line-height: 1;

        .price-main {
          display: flex;
          min-width: 0;
          align-items: baseline;
          gap: 10px;
        }

        .original-price {
          color: #8d9a91;
          font-size: 15px;
          text-decoration: line-through;
        }

        .price-suffix {
          color: #64766a;
          font-size: 14px;
          font-weight: 700;
        }

        .price {
          font-size: 29px;
          color: red;
          text-align: right;

          .currency-mark {
            font-size: 20px;
          }
        }

        .item-stats {
          display: inline-flex;
          flex: 0 0 auto;
          align-items: center;
          gap: 10px;
          color: #8d8d8d;
          font-size: 13px;
          white-space: nowrap;
        }

        .view-count {
          display: inline-flex;
          align-items: center;
          gap: 4px;

          :deep(.anticon) {
            color: #6c8b76;
            font-size: 14px;
          }
        }
      }

      .account-bind {
        max-width: 100%;
        width: 360px;
        margin-top: 18px;

        .bind-button {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 16px;

          img {
            max-height: 25px;
            width: auto;
          }

          .provider-login-icon {
            color: #24292f;
            font-size: 28px;
          }
        }

        .bound-account {
          display: flex;
          width: 100%;
          align-items: center;
          gap: 12px;
          color: #203428;
        }

        .bound-main {
          min-width: 0;
          flex: 1;
        }

        .bound-title {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 12px;
          font-weight: 700;
          line-height: 1.35;

          > span {
            min-width: 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }

        .bound-meta {
          display: flex;
          margin-top: 4px;
          flex-wrap: wrap;
          gap: 6px 10px;
          color: #7b8b82;
          font-size: 12px;
          line-height: 1.5;
        }

        .bound-link {
          display: inline-flex;
          margin-top: 4px;
          color: #1677ff;
          font-size: 12px;
        }
      }

      .git-binding-reminder {
        width: min(100%, 520px);
        margin: 18px 0 0;
        text-align: left;
        border-color: #faad14;
        background: linear-gradient(135deg, #fffbe6, #fff7e6);
        box-shadow: 0 10px 28px rgba(250, 173, 20, 0.12);

        :deep(.ant-alert-message) {
          color: #ad6800;
          font-weight: 700;
        }

        :deep(.ant-alert-description) {
          color: #5f4b1b;
          line-height: 1.6;
        }

        :deep(.ant-alert-action) {
          align-self: center;
        }
      }

      .donation-line {
        display: grid;
        grid-template-columns: 72px minmax(0, 360px);
        align-items: center;
        gap: 10px;
        margin: 0 0 14px;

        > span {
          color: #5f6f66;
          font-weight: 600;
        }

        .donation-main {
          display: flex;
          min-width: 0;
          align-items: center;
          flex-wrap: wrap;
          gap: 8px;

          :deep(.ant-input-number) {
            width: 180px;
          }

          :deep(.ant-tag) {
            margin-inline-end: 0;
          }
        }
      }

      .coupon-line {
        display: grid;
        grid-template-columns: 58px minmax(0, 360px);
        align-items: start;
        gap: 10px;
        margin: 0 0 14px;

        > span {
          padding-top: 5px;
          color: #5f6f66;
          font-weight: 600;
        }

        .coupon-main {
          min-width: 0;
        }

        .coupon-control {
          display: flex;
          width: 100%;

          :deep(.ant-input-affix-wrapper),
          :deep(.ant-input) {
            flex: 1;
            min-width: 0;
          }

          :deep(.ant-btn) {
            width: 72px;
          }
        }

        .coupon-feedback {
          display: flex;
          margin-top: 8px;
          align-items: center;
          gap: 6px;
          font-size: 13px;
          line-height: 1.5;

          :deep(.ant-tag) {
            margin-inline-end: 0;
          }

          &.success {
            color: #237804;
          }

          &.error {
            color: #d4380d;
          }
        }
      }

      .payment-method-line {
        display: grid;
        grid-template-columns: 58px minmax(0, 1fr);
        align-items: start;
        gap: 10px;
        margin: 0 0 16px;

        > span {
          padding-top: 8px;
          color: #5f6f66;
          font-weight: 600;
        }
      }

      .purchase-guard {
        width: min(100%, 520px);
        margin: 16px 0 0;
        text-align: left;
        border-color: #faad14;
        background: #fffbe6;
        box-shadow: 0 10px 28px rgba(250, 173, 20, 0.12);

        :deep(.ant-alert-message) {
          color: #ad6800;
          font-weight: 700;
        }

        :deep(.ant-alert-description) {
          color: #5f4b1b;
          line-height: 1.6;
        }
      }

      .contract-guard {
        width: min(100%, 520px);
        margin: 16px 0 0;
        text-align: left;
        border-color: #91caff;
        background: #f0f7ff;

        :deep(.ant-alert-message) {
          color: #174a7c;
          font-weight: 700;
        }
      }

      .order-form-panel {
        width: min(100%, 520px);
        margin: 16px 0 4px;
        padding: 14px 16px;
        border: 1px solid #d9e7dd;
        border-radius: 8px;
        background: #fbfefd;

        .order-form-heading {
          margin-bottom: 10px;

          h3 {
            margin: 0;
            color: #1f3d2b;
            font-size: 15px;
            line-height: 1.5;
          }

          p {
            margin: 4px 0 0;
            color: #5f6f66;
            font-size: 13px;
            line-height: 1.6;
          }
        }

        .order-form {
          :deep(.ant-form-item) {
            margin-bottom: 12px;
          }

          :deep(.ant-form-item:last-child) {
            margin-bottom: 0;
          }

          :deep(.ant-input),
          :deep(.ant-select-selector),
          :deep(.ant-input-affix-wrapper) {
            min-width: 0;
          }
        }

        .order-form-help {
          margin-top: 4px;
          color: #6c7a71;
          font-size: 12px;
          line-height: 1.5;
        }
      }

      .checkout {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;

        .checkout-btn {
          width: 200px;
          height: auto !important;
          font-size: 22px;
          padding: 14px 0;
          line-height: 1;
        }

        .checkout-tip {
          color: #8d8d8d;
          font-size: 13px;
          line-height: 1.6;
        }
      }

      .success {
        color: #78cb4c;
        font-weight: bold;
      }

      .info {
        color: #343434;
        font-weight: bold;
      }
    }
  }
}

.detail-content {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
  margin: 28px 0 8px;
}

.detail-showcase {
  position: sticky;
  top: 88px;
  overflow: hidden;
  border: 1px solid #edf3ef;
  border-radius: 8px;
  background: #fbfdfc;

  img {
    width: 100%;
    aspect-ratio: 4 / 3;
    display: block;
    object-fit: cover;
    background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
  }

  .detail-showcase-text {
    padding: 16px;
    text-align: left;

    h3 {
      margin: 10px 0 6px;
      color: #1f2d24;
      font-size: 18px;
      line-height: 1.4;
    }

    p {
      margin: 0;
      color: #6c7a71;
      font-size: 13px;
    }
  }
}

.description {
  min-width: 0;
  background: white;
  margin: 0;
  box-sizing: border-box;
  text-align: left;
  word-break: break-word;
}

.order-list {
  :deep(.ant-list-item) {
    gap: 12px;
  }

  .order-meta {
    flex: 0 0 auto;
    color: #69766f;
    font-size: 13px;

    :deep(.ant-tag) {
      margin-inline-end: 0;
    }
  }
}

.qrcode-container {
  text-align: center;
  padding: 18px 0 4px;

  .payment-state {
    display: flex;
    align-items: center;
    flex-direction: column;
  }

  .success-icon {
    color: #16a34a;
    font-size: 44px;
    line-height: 1;
  }

  h3 {
    margin: 12px 0 6px;
    font-size: 18px;
    color: #1f2d24;
  }

  p {
    max-width: 320px;
    margin: 0 auto;
    color: #5f6f66;
    font-size: 14px;
    line-height: 1.6;
  }

  .order-no {
    margin-top: 8px;
    color: #8d9a91;
    font-size: 12px;
    word-break: break-all;
  }

  .pay-amount {
    display: flex;
    justify-content: center;
    align-items: baseline;
    gap: 10px;
    margin: 8px 0 2px;

    strong {
      color: #ef4444;
      font-size: 26px;
      line-height: 1.2;
    }

    span {
      color: #d97706;
      font-size: 13px;
    }
  }

  .qr-image {
    width: 220px;
    height: 220px;
    margin: 18px auto;
    display: block;
  }

  .pay-actions,
  .success-actions,
  .payment-help {
    margin-top: 18px;
  }

  .payment-help {
    display: flex;
    width: min(100%, 360px);
    margin-inline: auto;
    padding: 10px 12px;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    border: 1px solid #edf3ef;
    border-radius: 8px;
    background: #fbfdfc;
    color: #5f6f66;
    font-size: 13px;
    line-height: 1.5;
    text-align: left;

    span {
      min-width: 0;
    }

    :deep(.ant-btn) {
      flex: 0 0 auto;
      height: auto;
      padding: 0;
    }
  }

  .success-actions {
    justify-content: center;
  }

  &.finished {
    padding-top: 10px;
  }
}

@media only screen and (max-width: 760px) {
  :deep(.ant-card-body) {
    padding: 14px;
  }

  :deep(.ant-page-header) {
    padding-top: 8px;
  }

  :deep(.ant-page-header-heading) {
    gap: 10px;
    align-items: center;
  }

  :deep(.ant-page-header-heading-title) {
    font-size: 18px;
    line-height: 1.4;
  }

  .item-container .item-info-box {
    display: block;

    .cover {
      width: 100%;
      max-width: 420px;
      margin: 0 auto;
    }

    .content {
      padding-top: 18px;

      h2 {
        font-size: 21px;
      }

      .sku-selector {
        .sku-options {
          width: 100%;
          min-width: 0;
        }

        :deep(.ant-radio-button-wrapper) {
          width: 100%;
          max-width: 100%;
        }
      }

      .price-box {
        flex-direction: column;
        margin-top: 16px;
        align-items: flex-start;
        gap: 8px;

        .price {
          font-size: 28px;
        }

        .item-stats {
          flex-wrap: wrap;
          gap: 8px;
        }

        .count,
        .view-count {
          padding-top: 0;
          font-size: 13px;
          white-space: nowrap;
        }
      }

      .account-bind {
        width: 100%;

        .bind-button {
          gap: 12px;
          flex-wrap: wrap;
        }

        .bound-title {
          align-items: flex-start;
          flex-direction: column;
          gap: 4px;
        }
      }

      .checkout {
        align-items: stretch;
        flex-direction: column;
        gap: 10px;

        .checkout-btn {
          width: 100%;
          font-size: 20px;
          padding: 13px 0;
        }

        .checkout-tip {
          font-size: 13px;
        }
      }

      .donation-line {
        grid-template-columns: 1fr;
        gap: 6px;

        .donation-main {
          :deep(.ant-input-number) {
            width: 100%;
          }
        }
      }

      .coupon-line {
        grid-template-columns: 1fr;
        gap: 6px;

        > span {
          padding-top: 0;
        }

        .coupon-control {
          min-width: 0;

          :deep(.ant-input) {
            min-width: 0;
          }

          :deep(.ant-btn) {
            width: 68px;
            padding-inline: 0;
            flex: none;
          }
        }
      }

      .payment-method-line {
        grid-template-columns: 1fr;
        gap: 8px;

        > span {
          padding-top: 0;
        }
      }
    }
  }

  .detail-content {
    display: block;
    margin: 20px 0 8px;
  }

  .detail-showcase {
    position: static;
    margin-bottom: 20px;
  }

  .description {
    font-size: 14px;
  }

  .order-list {
    :deep(.ant-list-item) {
      align-items: flex-start;
      flex-direction: column;
    }

    :deep(.ant-list-item-meta) {
      width: 100%;
    }

    :deep(.ant-list-item-meta-title) {
      margin-bottom: 4px;
      white-space: normal;
      word-break: break-word;
    }

    :deep(.ant-list-item-meta-description) {
      word-break: break-all;
    }

    .order-meta {
      width: 100%;
      justify-content: space-between;
      flex-wrap: wrap;
      row-gap: 6px;
    }
  }

  .qrcode-container {
    padding: 8px 0 4px;

    h3 {
      font-size: 17px;
    }

    p {
      font-size: 13px;
    }

    .qr-image {
      width: min(220px, 70vw);
      height: min(220px, 70vw);
      margin: 16px auto;
    }

    .pay-actions,
    .success-actions,
    .payment-help {
      width: 100%;

      :deep(.ant-space-item) {
        width: 100%;
      }

      :deep(.ant-btn) {
        width: 100%;
      }
    }

    .payment-help {
      align-items: flex-start;
      flex-direction: column;
      gap: 6px;

      :deep(.ant-btn) {
        width: 100%;
      }
    }
  }
}
</style>
