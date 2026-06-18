<script setup>
import { useRouter } from '@/router/use.js';
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { getShopItemDetail } from '../apis/api.js';
import {
  CheckCircleOutlined,
  CrownOutlined,
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
  isGitRepositoryDonationAccessType,
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
  orderStatusText
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

const ShopMarkdownPreview = defineAsyncComponent(() => import('../components/ShopMarkdownPreview.vue'));

const store = useClientStore();
const { user } = storeToRefs(store);
const router = useRouter();

const loading = ref(true);
const data = ref({});
const detailError = ref('');
const activeTab = ref('1');
let detailRequestSeq = 0;

const itemDeliveryMode = computed(() => normalizeDeliveryModeForType(data.value.type, data.value.deliveryMode));
const images = computed(() => resolveShopItemImages(data.value));
const detailPreviewId = computed(() => `shop-item-detail-${data.value.id || 'empty'}`);
const recordTitle = computed(() => isGitRepositoryAccessType(data.value.type) ? '开通记录' : '购买记录');
const highlightIcons = {
  crown: CrownOutlined,
  badge: SafetyCertificateOutlined,
  spark: ThunderboltOutlined,
  fire: FireOutlined
};
const itemHighlight = computed(() => getShopItemHighlight(data.value));
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
} = useGitRepositoryBinding({ item: data, user, store, router });

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
  payableBaseAmount,
  validateDonationAmount,
  orderAmountPayload
} = useShopDonationAmount({ item: data });

const {
  availabilityLoading,
  availabilityError,
  purchaseBlocked,
  purchaseBlockTitle,
  purchaseBlockMessage,
  availabilityNotice,
  loadPurchaseAvailability,
  validatePurchaseAvailability
} = useShopPurchaseAvailability({ item: data, user, gitAuthorization });

const refreshOrderState = async () => {
  await Promise.allSettled([
    loadOrders(),
    loadPurchaseAvailability()
  ]);
};

const validateCheckout = () => validateGitCheckout() && validatePurchaseAvailability() && validateDonationAmount();

const canCheckout = computed(() => {
  if (!user.value?.id) {
    return false;
  }
  if (isGitRepositoryAccessType(data.value.type) && !gitAuthorization.value) {
    return false;
  }
  if (purchaseBlocked.value || availabilityLoading.value) {
    return false;
  }
  return data.value.enabled !== false;
});
const checkoutHint = computed(() => {
  if (availabilityLoading.value) {
    return '正在确认购买和开通状态';
  }
  if (purchaseBlocked.value) {
    return '已购买过，可在我的订单查看开通记录';
  }
  return checkoutTip.value;
});
const checkoutButtonText = computed(() => donationEnabled.value ? '打赏开通' : '立即购买');

const coupon = useShopCoupon({
  item: data,
  user,
  store,
  router,
  baseAmount: payableBaseAmount,
  orderAmountPayload
});
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
  item: data,
  couponCode,
  appliedCoupon,
  couponError
});

const {
  payModalVisible,
  payLoading,
  currentOrder,
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
  item: data,
  user,
  store,
  router,
  itemDeliveryMode,
  loadOrders: refreshOrderState,
  validateCheckout,
  coupon,
  orderAmountPayload
});

const contractAgreement = useShopContractAgreement({
  item: data,
  user,
  store,
  router
});

const handleCheckout = async () => {
  if (!validateCheckout()) {
    return;
  }
  const contractPayload = await contractAgreement.ensureContractAgreement();
  if (contractPayload === null) {
    return;
  }
  await checkout(contractPayload || {});
};

const useDefaultCover = event => setShopImageFallback(event, data.value?.type);

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
      loaded = true;
    }
  } catch (e) {
    if (requestSeq === detailRequestSeq && String(router.route.params.id) === currentId) {
      data.value = {};
      detailError.value = e.message || '商品不存在或已下架';
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

const handleTabChange = key => {
  if (key === '2') {
    loadOrders();
  }
};
</script>

<template>
  <a-spin :spinning='loading'>
    <a-card>
      <a-page-header title='商品详情' @back='() => router.replace("/shop")'>
        <template #footer>
          <a-tabs v-model:activeKey='activeTab' @change='handleTabChange'>
            <a-tab-pane key='1' tab='商品详情'>
              <a-empty v-if='detailError' :description='detailError' />
              <div v-else-if='data.description' class='detail-content'>
                <section class='detail-showcase'>
                  <img :src='images[0]' alt='' @error='useDefaultCover' />
                  <div class='detail-showcase-text'>
                    <a-tag v-if='data.typeName' color='blue'>{{ data.typeName }}</a-tag>
                    <h3>{{ data.name }}</h3>
                    <p>{{ deliveryModeText(itemDeliveryMode) }}</p>
                  </div>
                </section>
                <ShopMarkdownPreview
                  :id='detailPreviewId'
                  class='description'
                  :model-value='data.description || ""'
                  language='zh-CN'
                  preview-theme='default'
                  code-theme='github'
                />
              </div>
              <a-empty v-else description='暂无商品详情' />
            </a-tab-pane>
            <a-tab-pane key='2' :tab='recordTitle'>
              <a-alert
                v-if='ordersError'
                :message='ordersError'
                type='warning'
                show-icon
              />
              <a-spin v-else :spinning='ordersLoading'>
                <a-empty v-if='!user?.id' description='登录后展示你的购买记录' />
                <a-empty v-else-if='!orders.length' :description='`当前账号暂无${recordTitle}`' />
              <a-list v-else :data-source='orders' size='small' class='order-list'>
                <template #renderItem='{ item }'>
                  <a-list-item>
                    <a-list-item-meta :title='item.itemName || data.name' :description='item.orderNo' />
                    <a-space class='order-meta'>
                      <a-tag :color='orderStatusColor(item.status)'>{{ orderStatusText(item.status) }}</a-tag>
                      <a-tag :color='deliveryStatusColor(item.deliveryStatus)'>
                        {{ deliveryStatusText(item.deliveryStatus, item.deliveryMode || itemDeliveryMode) }}
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
                <span>{{ data.name }}</span>
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
                <a-tag v-if='data.typeName' color='blue'>{{ data.typeName }}</a-tag>
                <a-tag :color='deliveryModeColor(itemDeliveryMode)'>
                  {{ data.deliveryModeName || deliveryModeText(itemDeliveryMode) }}
                </a-tag>
                <a-tag color='green' v-for='tag in data.tags' :key='tag'>{{ tag }}</a-tag>
              </div>
              <div class='price-box'>
                <div class='price-main'>
                  <span v-if='hasAppliedCoupon' class='original-price'>¥{{ appliedCoupon.originalAmount }}</span>
                  <span v-else-if='isGitRepositoryDonationAccessType(data.type)' class='price-prefix'>最低</span>
                  <span class='price'>{{ currentPayableAmount }}</span>
                </div>
                <span class='count'>{{ data.buyCount ?? 0 }}人购买</span>
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
                  <a-button size='small' type='link' @click.stop='authorize'>去绑定</a-button>
                </template>
              </a-alert>
              <a-card v-if='isGitRepositoryAccessType(data.type)' size='small' :title='gitBindTitle' class='account-bind'
                      :class='{loading: bindingLoading}' @click='authorize'>
                <template #extra>
                  <a-tooltip>
                    <template #title>先绑定 Git 账号</template>
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
                            已绑定账号
                          </a-space>
                        </div>
                        <div class='bound-meta'>
                          <span v-if='gitAccount.login'>@{{ gitAccount.login }}</span>
                          <span v-if='gitAccount.email'>{{ gitAccount.email }}</span>
                          <span v-if='!gitAccount.login && !gitAccount.email && gitAccount.openid'>
                            识别码 {{ gitAccount.maskedOpenid }}
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
                          查看账号主页
                        </a>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <github-outlined v-if='gitProvider === "github"' class='provider-login-icon' />
                    <img v-else-if='gitProvider === "gitee"' :src='gitee' alt='码云'>
                    <img v-else :src='gitea' alt='Gitea'>
                    {{ bindingLoading ? '绑定中...' : `绑定${gitProviderName}账号` }}
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
                  <a-button size='small' type='link' @click.stop='goMyOrders'>查看订单</a-button>
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
                message='仓库权限状态'
                :description='availabilityNotice'
                type='info'
                show-icon
              />
              <a-alert
                v-if='data.contractRequired'
                class='contract-guard'
                message='购买前需确认合同'
                type='info'
                show-icon
              />
              <shop-support-entry variant='inline' />
              <a-divider />
              <div v-if='donationEnabled' class='donation-line'>
                <span>打赏金额</span>
                <div class='donation-main'>
                  <a-input-number
                    v-model:value='donationAmount'
                    :min='minimumDonationAmount'
                    :precision='2'
                    :step='1'
                    prefix='¥'
                  />
                  <a-tag color='green'>最低 ¥{{ Number(minimumDonationAmount || 0).toFixed(2) }}</a-tag>
                </div>
              </div>
              <div class='coupon-line'>
                <span>优惠券</span>
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
                      应用
                    </a-button>
                  </a-input-group>
                  <div v-if='hasAppliedCoupon' class='coupon-feedback success'>
                    <a-tag color='green'>已应用</a-tag>
                    <span>已优惠 ¥{{ appliedCoupon.discountAmount }}，应付 ¥{{ appliedCoupon.payableAmount }}</span>
                    <a-tag v-if='defaultPromotion.active && appliedCoupon.couponCode === defaultPromotion.couponCode' color='red'>
                      自动优惠
                    </a-tag>
                  </div>
                  <div v-else-if='couponError' class='coupon-feedback error'>
                    {{ couponError }}
                  </div>
                </div>
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
      title="支付订单"
      :footer="null"
      :maskClosable="false"
      wrap-class-name="shop-payment-modal"
      @cancel="handlePayModalClose"
    >
      <div class="qrcode-container" :class="{ finished: paymentFinished }">
        <div class="payment-state">
          <check-circle-outlined v-if="paymentFinished" class="success-icon" />
          <a-tag v-else-if="currentOrder" :color="orderStatusColor(currentOrder.status)">
            {{ orderStatusText(currentOrder.status) }}
          </a-tag>
          <h3>{{ paymentModalTitle }}</h3>
          <p>{{ paymentInstruction }}</p>
          <span v-if="paymentFinished && paymentOrderNoText" class="order-no">{{ paymentOrderNoText }}</span>
        </div>
        <div v-if="currentOrder && !paymentFinished" class="pay-amount">
          <strong>¥{{ currentOrder.amount }}</strong>
          <span v-if="Number(currentOrder.discountAmount || 0) > 0">
            已优惠 ¥{{ currentOrder.discountAmount }}
          </span>
        </div>
        <img v-if="qrCode && !paymentFinished" :src="qrCode" alt="支付二维码" class="qr-image" />
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
          <span>支付或开通遇到问题，优先提交工单；也可以联系页面底部客服。</span>
          <a-button type="link" size="small" @click="goSubmitTicket">提交工单</a-button>
        </div>
        <a-space v-if="paymentFinished" class="success-actions">
          <a-button type="primary" @click="goMyOrders">查看我的订单</a-button>
          <a-button @click="continueShopping">继续逛小铺</a-button>
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

        .price-prefix {
          color: #64766a;
          font-size: 14px;
          font-weight: 700;
        }

        .price {
          font-size: 29px;
          color: red;
          text-align: right;

          &::before {
            content: '¥';
            font-size: 20px;
          }
        }

        .count {
          color: #8d8d8d;
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

      .price-box {
        flex-direction: column;
        margin-top: 16px;
        align-items: flex-start;
        gap: 8px;

        .price {
          font-size: 28px;
        }

        .count {
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
