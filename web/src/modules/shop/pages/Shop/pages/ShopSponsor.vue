<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { storeToRefs } from 'pinia';
import { message } from 'ant-design-vue';
import {
  CheckCircleOutlined,
  CodeOutlined,
  GiftOutlined,
  GithubOutlined,
  HeartOutlined,
  LinkOutlined,
  ReloadOutlined,
  ShoppingCartOutlined
} from '@ant-design/icons-vue';
import useClientStore from '@/modules/auth/store/client.js';
import { PortalOauth } from '@/modules/auth/api.js';
import { useRouter } from '@/router/use.js';
import { getDonationItems, getShopItemDetail } from '../apis/api.js';
import { useShopCoupon } from '../hooks/useShopCoupon.js';
import { useShopDonationAmount } from '../hooks/useShopDonationAmount.js';
import { useShopPayment } from '../hooks/useShopPayment.js';
import {
  isPureDonationType,
  resolveShopItemCover,
  setShopImageFallback
} from '@/modules/shop/utils/shopCovers.js';
import { resolveLocalizedShopItem } from '@/modules/shop/utils/shopI18n.js';
import {
  orderStatusColor,
  orderStatusText
} from '@/modules/shop/utils/shopDelivery.js';
import ShopPaymentMethodSelector from '../components/ShopPaymentMethodSelector.vue';

const DEFAULT_DONATION_ITEM_ID = '1009';
const SPONSOR_PROJECT = 'file-viewer';
const SPONSOR_ENTRY = 'file-viewer-sponsor';

const TIER_COPY = [
  ['shopSponsor.tiers.light.title', 'shopSponsor.tiers.light.description'],
  ['shopSponsor.tiers.kind.title', 'shopSponsor.tiers.kind.description'],
  ['shopSponsor.tiers.focus.title', 'shopSponsor.tiers.focus.description'],
  ['shopSponsor.tiers.stable.title', 'shopSponsor.tiers.stable.description'],
  ['shopSponsor.tiers.longTerm.title', 'shopSponsor.tiers.longTerm.description']
];
const TIERS_BY_CURRENCY = {
  CNY: [5, 18.88, 66, 128, 268],
  USD: [1, 5, 18.88, 50, 100]
};

const store = useClientStore();
const { user } = storeToRefs(store);
const router = useRouter();
const { locale, t } = useI18n();

const loading = ref(false);
const loadError = ref('');
const item = ref(null);
const sponsoringAmount = ref(null);
const oauthRedirecting = ref(false);

const routeQueryValue = value => Array.isArray(value) ? value[0] : value;
const normalizeSource = value => {
  const source = String(routeQueryValue(value) || '').trim().toLowerCase();
  return ['github', 'npm'].includes(source) ? source : 'direct';
};

const referrerSource = (() => {
  try {
    const hostname = new URL(document.referrer).hostname.toLowerCase();
    return hostname === 'github.com' || hostname.endsWith('.github.com') ? 'github' : 'direct';
  } catch {
    return 'direct';
  }
})();
const sponsorSource = computed(() => {
  const querySource = String(routeQueryValue(router.route.query.source) || '').trim();
  return querySource ? normalizeSource(querySource) : referrerSource;
});
const sponsorSourceLabel = computed(() => {
  if (sponsorSource.value === 'github') {
    return 'GitHub';
  }
  if (sponsorSource.value === 'npm') {
    return 'npm';
  }
  return t('shopSponsor.directSource');
});
const preferredItemId = computed(() => String(routeQueryValue(router.route.query.itemId) || '').trim());
const saleItem = computed(() => item.value || {});
const localizedItem = computed(() => resolveLocalizedShopItem(saleItem.value, locale.value) || {});
const itemDeliveryMode = computed(() => saleItem.value?.deliveryMode || 'NONE');
const itemCover = computed(() => resolveShopItemCover(localizedItem.value));
const sponsorReturnPath = computed(() => {
  const query = { ...router.route.query };
  delete query._oauth;
  if (sponsorSource.value === 'github') {
    query.source = 'github';
  }
  return router.resolve({
    path: router.route.path,
    query,
    hash: router.route.hash
  }).fullPath;
});

const {
  donationAmount,
  minimumDonationAmount,
  paymentCurrency,
  currencySymbol,
  payableBaseAmount,
  validateDonationAmount,
  orderAmountPayload
} = useShopDonationAmount({ item: saleItem });

const coupon = useShopCoupon({
  item: saleItem,
  user,
  store,
  router,
  baseAmount: payableBaseAmount,
  orderAmountPayload
});

const formatAmount = value => {
  const amount = Number(value || 0);
  if (!Number.isFinite(amount)) {
    return '0';
  }
  return amount.toLocaleString(locale.value, {
    minimumFractionDigits: amount % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2
  });
};

const sameAmount = (left, right) => Number(left || 0).toFixed(2) === Number(right || 0).toFixed(2);
const amountTiers = computed(() => {
  const minimum = Number(minimumDonationAmount.value || 0);
  const tiers = TIERS_BY_CURRENCY[paymentCurrency.value]
    .map((amount, index) => ({
      amount,
      titleKey: TIER_COPY[index][0],
      descriptionKey: TIER_COPY[index][1]
    }))
    .filter(tier => tier.amount >= minimum || sameAmount(tier.amount, minimum))
    .map(tier => ({
      ...tier,
      title: t(tier.titleKey),
      description: t(tier.descriptionKey)
    }));
  if (minimum > 0 && !tiers.some(tier => sameAmount(tier.amount, minimum))) {
    tiers.unshift({
      amount: minimum,
      title: t('shopSponsor.tiers.minimum.title'),
      description: t('shopSponsor.tiers.minimum.description')
    });
  }
  return tiers;
});

const candidateText = candidate => [
  candidate?.name,
  ...(candidate?.tags || []),
  candidate?.i18n?.['en-US']?.name,
  ...(candidate?.i18n?.['en-US']?.tags || [])
].filter(Boolean).join(' ').toLowerCase();

const chooseDonationCandidate = records => {
  const donationItems = (Array.isArray(records) ? records : []).filter(candidate => isPureDonationType(candidate?.type));
  if (!donationItems.length) {
    return null;
  }
  return donationItems.find(candidate => String(candidate.id) === DEFAULT_DONATION_ITEM_ID)
    || donationItems.find(candidate => /file[-\s]?viewer|文件预览|预览/.test(candidateText(candidate)))
    || donationItems[0];
};

const loadDonationDetail = async id => {
  if (!id) {
    return null;
  }
  const detail = await getShopItemDetail(id);
  return isPureDonationType(detail?.type) ? detail : null;
};

const loadDonationByList = async () => {
  const queries = [
    { page: 0, size: 20, keyword: 'file-viewer' },
    { page: 0, size: 20, keyword: '打赏' },
    { page: 0, size: 20 }
  ];
  for (const query of queries) {
    try {
      const candidate = chooseDonationCandidate(await getDonationItems(query));
      if (!candidate?.id) {
        continue;
      }
      const detail = await loadDonationDetail(candidate.id);
      if (detail) {
        return detail;
      }
    } catch (ignore) {
      // Continue through the fallback list; older deployments may not support every query shape yet.
    }
  }
  return null;
};

const loadDonationItem = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const ids = [...new Set([preferredItemId.value, DEFAULT_DONATION_ITEM_ID].filter(Boolean))];
    for (const id of ids) {
      try {
        const detail = await loadDonationDetail(id);
        if (detail) {
          item.value = detail;
          return;
        }
      } catch (ignore) {
        // Fall back to list lookup below.
      }
    }

    const detail = await loadDonationByList();
    if (!detail) {
      throw new Error(t('shopSponsor.itemMissing'));
    }
    item.value = detail;
  } catch (e) {
    item.value = null;
    loadError.value = e.message || t('shopSponsor.itemMissing');
  } finally {
    loading.value = false;
  }
};

const sponsorProperties = () => ({
  sponsorProject: SPONSOR_PROJECT,
  sponsorSource: sponsorSource.value,
  sponsorEntry: SPONSOR_ENTRY
});

const validateCheckout = () => {
  if (!saleItem.value?.id) {
    message.warning(t('shopSponsor.itemMissing'));
    return false;
  }
  if (!isPureDonationType(saleItem.value.type)) {
    message.warning(t('shopSponsor.itemMismatch'));
    return false;
  }
  return validateDonationAmount();
};

const refreshSponsorOrders = async () => {};

const startGithubSponsorLogin = async ({ force = false } = {}) => {
  if (user.value?.id || sponsorSource.value !== 'github' || oauthRedirecting.value) {
    return false;
  }
  const returnPath = sponsorReturnPath.value;
  if (!force && store.hasOAuthLoginIntent('github', returnPath)) {
    return false;
  }

  oauthRedirecting.value = true;
  let navigating = false;
  try {
    const providers = await PortalOauth.providers();
    if (providers?.github !== true) {
      return false;
    }
    store.rememberRedirect(returnPath);
    store.rememberOAuthLoginIntent('github', returnPath, { skipProfilePrompt: true });
    store.rememberOAuthLoginRedirect(returnPath);
    navigating = true;
    window.location.assign('/oauth/github');
    return true;
  } catch {
    return false;
  } finally {
    if (!navigating) {
      oauthRedirecting.value = false;
    }
  }
};

const {
  payModalVisible,
  payLoading,
  currentOrder,
  paymentMethod,
  paymentMethodOptions,
  selectedPaymentMethod,
  qrCode,
  payLink,
  redirectPayment,
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
  loadOrders: refreshSponsorOrders,
  validateCheckout,
  coupon,
  orderAmountPayload,
  paymentCurrency
});

const startSponsor = async amount => {
  if (amount) {
    donationAmount.value = amount;
  }
  if (!user.value?.id && sponsorSource.value === 'github') {
    if (await startGithubSponsorLogin({ force: true })) {
      return;
    }
    store.rememberRedirect(sponsorReturnPath.value);
    router.push('/login');
    return;
  }
  if (!validateCheckout()) {
    return;
  }
  sponsoringAmount.value = amount || 'custom';
  try {
    await checkout({
      properties: sponsorProperties()
    });
  } finally {
    sponsoringAmount.value = null;
  }
};

const tierLoading = amount => payLoading.value && sameAmount(sponsoringAmount.value, amount);
const customLoading = computed(() => payLoading.value && sponsoringAmount.value === 'custom');

onMounted(async () => {
  const currentUser = await store.loadUser().catch(() => null);
  if (!currentUser && sponsorSource.value === 'github'
    && await startGithubSponsorLogin()) {
    return;
  }
  await loadDonationItem();
  detectPaymentScene();
  await restorePendingPayment();
  window.addEventListener('pageshow', handlePageShow);
  document.addEventListener('visibilitychange', handleVisibilityChange);
});

onBeforeUnmount(() => {
  stopPolling();
  window.removeEventListener('pageshow', handlePageShow);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

watch(preferredItemId, () => {
  loadDonationItem();
});
</script>

<template>
  <div class="shop-sponsor-page">
    <a-spin
      :spinning="loading || oauthRedirecting"
      :tip="oauthRedirecting ? t('shopSponsor.githubLoginRedirecting') : undefined"
    >
      <a-result
        v-if="loadError"
        status="warning"
        :title="loadError"
        class="sponsor-state"
      >
        <template #extra>
          <a-button type="primary" @click="loadDonationItem">
            <reload-outlined />
            {{ t('shopSponsor.retry') }}
          </a-button>
        </template>
      </a-result>

      <template v-else>
        <section class="sponsor-hero">
          <div class="hero-copy">
            <div class="hero-kicker">
              <a-tag color="green">{{ t('shopSponsor.projectTag') }}</a-tag>
              <a-tag color="blue">{{ t('shopSponsor.sourceTag', { source: sponsorSourceLabel }) }}</a-tag>
            </div>
            <h1>{{ t('shopSponsor.title') }}</h1>
            <p>{{ t('shopSponsor.subtitle') }}</p>
            <div class="hero-actions">
              <a-button type="primary" size="large" :disabled="!amountTiers.length" @click="startSponsor(amountTiers[0]?.amount)">
                <heart-outlined />
                {{ t('shopSponsor.primaryAction') }}
              </a-button>
              <a-button size="large" href="https://viewer.flyfish.dev" target="_blank" rel="noreferrer">
                <link-outlined />
                {{ t('shopSponsor.projectLink') }}
              </a-button>
            </div>
          </div>

          <aside class="sponsor-summary">
            <img :src="itemCover" alt="" @error="event => setShopImageFallback(event, saleItem.type)" />
            <div class="summary-content">
              <span>{{ t('shopSponsor.currentProduct') }}</span>
              <strong>{{ localizedItem.name || t('shopSponsor.fallbackProductName') }}</strong>
              <div class="summary-meta">
                <div>
                  <span>{{ t('shopSponsor.minimumAmount') }}</span>
                  <strong>{{ currencySymbol }}{{ formatAmount(minimumDonationAmount) }}</strong>
                </div>
                <div>
                  <span>{{ t('shopSponsor.paymentMethod') }}</span>
                  <strong>{{ selectedPaymentMethod.label }}</strong>
                </div>
              </div>
            </div>
          </aside>
        </section>

        <section class="sponsor-amounts">
          <div class="section-heading">
            <span><gift-outlined /> {{ t('shopSponsor.amountSection') }}</span>
            <p>{{ t('shopSponsor.amountHint') }}</p>
          </div>

          <div class="sponsor-payment-method">
            <span>{{ t('shop.paymentMethod') }}</span>
            <ShopPaymentMethodSelector
              v-model="paymentMethod"
              :options="paymentMethodOptions"
              :disabled="payLoading"
            />
          </div>

          <div class="amount-grid">
            <button
              v-for="tier in amountTiers"
              :key="tier.amount"
              type="button"
              class="amount-tier"
              :class="{ selected: sameAmount(donationAmount, tier.amount) }"
              :disabled="payLoading || !saleItem.id"
              @click="startSponsor(tier.amount)"
            >
              <span class="tier-icon"><shopping-cart-outlined /></span>
              <span class="tier-amount">{{ currencySymbol }}{{ formatAmount(tier.amount) }}</span>
              <strong>{{ tier.title }}</strong>
              <span>{{ tier.description }}</span>
              <span class="tier-action">{{ tierLoading(tier.amount) ? t('common.loading') : t('shopSponsor.supportThis') }}</span>
            </button>
          </div>

          <div class="custom-amount">
            <div>
              <strong>{{ t('shopSponsor.customTitle') }}</strong>
              <span>{{ t('shopSponsor.customHint', { symbol: currencySymbol, amount: formatAmount(minimumDonationAmount) }) }}</span>
            </div>
            <div class="custom-control">
              <a-input-number
                v-model:value="donationAmount"
                :min="minimumDonationAmount || 1"
                :precision="2"
                :step="paymentCurrency === 'USD' ? 1 : 5"
                size="large"
                :prefix="currencySymbol"
              />
              <a-button
                type="primary"
                size="large"
                :loading="customLoading"
                :disabled="payLoading || !saleItem.id"
                @click="startSponsor()"
              >
                {{ t('shopSponsor.customAction') }}
              </a-button>
            </div>
          </div>
        </section>

        <section class="sponsor-thanks">
          <code-outlined />
          <div>
            <strong>{{ t('shopSponsor.thanksTitle') }}</strong>
            <p>{{ t('shopSponsor.thanksBody') }}</p>
          </div>
          <div class="source-marks">
            <span><github-outlined /> GitHub</span>
            <span>npm</span>
          </div>
        </section>
      </template>
    </a-spin>

    <a-modal
      v-model:open="payModalVisible"
      :title="t('shop.paymentTitle')"
      :footer="null"
      :maskClosable="false"
      wrap-class-name="shop-sponsor-payment-modal"
      @cancel="handlePayModalClose"
    >
      <div class="sponsor-payment" :class="{ finished: paymentFinished }">
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
          <span>{{ t('shopSponsor.paymentAmountHint') }}</span>
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
  </div>
</template>

<style scoped lang="less">
.shop-sponsor-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 28px 0 8px;
}

.sponsor-state {
  min-height: 420px;
  padding-top: 80px;
}

.sponsor-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: stretch;
  padding: clamp(24px, 4vw, 46px);
  border: 1px solid rgba(37, 129, 84, .14);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, .94), rgba(240, 251, 245, .9)),
    #fff;
  box-shadow: 0 18px 44px rgba(42, 112, 81, .08);
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;

  .hero-kicker {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 18px;
  }

  h1 {
    max-width: 720px;
    margin: 0;
    color: #182f25;
    font-size: clamp(34px, 5vw, 56px);
    line-height: 1.08;
    letter-spacing: 0;
  }

  p {
    max-width: 680px;
    margin: 18px 0 0;
    color: #4c6559;
    font-size: 17px;
    line-height: 1.8;
  }
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;

  .ant-btn {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    border-radius: 8px;
  }
}

.sponsor-summary {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(38, 125, 74, .12);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(28, 73, 54, .08);

  img {
    width: 100%;
    aspect-ratio: 16 / 10;
    object-fit: cover;
    background: #edf7f0;
  }
}

.summary-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 9px;
  padding: 18px;

  > span {
    color: #6b7d72;
    font-size: 13px;
  }

  > strong {
    color: #1e3027;
    font-size: 18px;
    line-height: 1.35;
  }
}

.summary-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: auto;

  div {
    padding: 12px;
    border-radius: 8px;
    background: #f6faf8;
  }

  span,
  strong {
    display: block;
  }

  span {
    color: #6b7d72;
    font-size: 12px;
  }

  strong {
    margin-top: 4px;
    color: #217244;
    font-size: 16px;
  }
}

.sponsor-amounts {
  padding: 28px;
  border: 1px solid rgba(33, 114, 68, .12);
  border-radius: 8px;
  background: #fff;
}

.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;

  > span {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #1e3027;
    font-size: 20px;
    font-weight: 700;
  }

  p {
    max-width: 560px;
    margin: 0;
    color: #607469;
    line-height: 1.7;
  }
}

.amount-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.sponsor-payment-method {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  margin: 0 0 18px;

  > span {
    padding-top: 8px;
    color: #607469;
    font-size: 13px;
    font-weight: 700;
  }
}

.amount-tier {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-height: 188px;
  padding: 18px;
  border: 1px solid rgba(37, 129, 84, .14);
  border-radius: 8px;
  background: #fbfdfc;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;

  &:hover:not(:disabled),
  &.selected {
    border-color: rgba(51, 162, 4, .48);
    box-shadow: 0 14px 28px rgba(40, 122, 77, .12);
    transform: translateY(-2px);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: .72;
  }

  .tier-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 8px;
    background: rgba(22, 119, 255, .1);
    color: #1677ff;
    font-size: 18px;
  }

  .tier-amount {
    margin-top: 16px;
    color: #1f7a36;
    font-size: 28px;
    font-weight: 800;
    line-height: 1;
  }

  strong {
    margin-top: 12px;
    color: #1e3027;
    font-size: 16px;
  }

  > span:not(.tier-icon):not(.tier-amount):not(.tier-action) {
    margin-top: 8px;
    color: #61756b;
    font-size: 13px;
    line-height: 1.5;
  }

  .tier-action {
    margin-top: auto;
    color: #267d4a;
    font-size: 13px;
    font-weight: 700;
  }
}

.custom-amount {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 18px;
  padding: 18px;
  border-radius: 8px;
  background: #f7fafc;

  strong,
  span {
    display: block;
  }

  strong {
    color: #1e3027;
    font-size: 17px;
  }

  span {
    margin-top: 6px;
    color: #65776d;
  }
}

.custom-control {
  display: flex;
  gap: 10px;
  align-items: center;
  flex: none;

  .ant-input-number {
    width: 180px;
  }

  .ant-btn {
    border-radius: 8px;
  }
}

.sponsor-thanks {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 20px 24px;
  border: 1px solid rgba(22, 119, 255, .14);
  border-radius: 8px;
  background: #f8fbff;
  color: #243447;

  > .anticon {
    color: #1677ff;
    font-size: 28px;
  }

  strong {
    font-size: 17px;
  }

  p {
    margin: 6px 0 0;
    color: #53687c;
    line-height: 1.7;
  }
}

.source-marks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 10px;
    border-radius: 8px;
    background: #fff;
    color: #334155;
    font-size: 13px;
    font-weight: 700;
  }
}

@media only screen and (max-width: 1060px) {
  .sponsor-hero {
    grid-template-columns: 1fr;
  }

  .sponsor-summary {
    display: grid;
    grid-template-columns: 240px minmax(0, 1fr);

    img {
      height: 100%;
      aspect-ratio: auto;
    }
  }

  .amount-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media only screen and (max-width: 720px) {
  .shop-sponsor-page {
    padding-top: 18px;
  }

  .sponsor-hero,
  .sponsor-amounts,
  .sponsor-thanks {
    padding: 18px;
  }

  .hero-copy {
    h1 {
      font-size: 34px;
    }

    p {
      font-size: 15px;
    }
  }

  .hero-actions {
    .ant-btn {
      width: 100%;
      justify-content: center;
    }
  }

  .sponsor-summary {
    display: flex;
  }

  .section-heading,
  .sponsor-payment-method,
  .custom-amount,
  .sponsor-thanks {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .sponsor-payment-method {
    gap: 8px;

    > span {
      padding-top: 0;
    }
  }

  .amount-grid {
    grid-template-columns: 1fr;
  }

  .amount-tier {
    min-height: 166px;
  }

  .custom-control {
    width: 100%;
    flex-direction: column;
    align-items: stretch;

    .ant-input-number,
    .ant-btn {
      width: 100%;
    }
  }

  .source-marks {
    justify-content: flex-start;
  }
}
</style>

<style lang="less">
.shop-sponsor-payment-modal {
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

.sponsor-payment {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  text-align: center;

  .payment-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;

    h3 {
      margin: 4px 0 0;
      color: #1f2d26;
      font-size: 22px;
    }

    p {
      max-width: 360px;
      margin: 0;
      color: #60746a;
      line-height: 1.7;
    }

    .success-icon {
      color: #33a204;
      font-size: 42px;
    }

    .order-no {
      color: #789084;
      font-size: 13px;
    }
  }

  .pay-amount {
    display: flex;
    flex-direction: column;
    gap: 4px;
    color: #64786d;

    strong {
      color: #1f7a36;
      font-size: 30px;
      line-height: 1;
    }
  }

  .qr-image {
    width: 220px;
    height: 220px;
    padding: 10px;
    border: 1px solid rgba(51, 162, 4, .15);
    border-radius: 8px;
    background: #fff;
  }

  .pay-actions,
  .payment-help,
  .success-actions {
    display: flex;
    justify-content: center;
  }

  .payment-help {
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    color: #7b8e84;
    font-size: 13px;
    line-height: 1.6;
  }
}

@media only screen and (max-width: 520px) {
  .shop-sponsor-payment-modal {
    .ant-modal-body {
      padding: 18px 16px 20px;
    }
  }

  .sponsor-payment {
    .qr-image {
      width: 200px;
      height: 200px;
    }
  }
}
</style>
