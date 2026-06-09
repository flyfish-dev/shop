<script setup>
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  CopyOutlined,
  MessageOutlined,
  ReloadOutlined,
  ShoppingCartOutlined
} from '@ant-design/icons-vue';
import RouterLink from '@/components/RouterLink/index.vue';
import AttachmentList from '@/components/Attachments/AttachmentList.vue';
import { openCustomerService } from '@/modules/shop/components/CustomerService/customerServiceBus.js';
import { extractOrderDelivery, getMyOrders } from '@/modules/shop/pages/Shop/apis/api.js';
import { sortOrdersByNewest } from '@/modules/shop/utils/orderSort.js';
import {
  deliveryModeColor,
  deliveryModeText,
  deliveryStatusColor,
  deliveryStatusText,
  orderStatusColor,
  orderStatusText
} from '@/modules/shop/utils/shopDelivery.js';

const loading = ref(false);
const orders = ref([]);
const extracting = ref(false);
const deliveryOpen = ref(false);
const extractedDelivery = ref(null);

const totalAmount = computed(() => orders.value.reduce((sum, order) => sum + Number(order.amount || 0), 0).toFixed(2));
const deliveredCount = computed(() => orders.value.filter(order => order.status === 'DELIVERED').length);

const loadOrders = async () => {
  loading.value = true;
  try {
    orders.value = sortOrdersByNewest(await getMyOrders());
  } catch (e) {
    orders.value = [];
    message.error(e.message || '订单加载失败');
  } finally {
    loading.value = false;
  }
};

const copyText = async value => {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    message.success('已复制');
  } catch (e) {
    message.warning('当前浏览器不支持自动复制');
  }
};

const contactService = order => {
  openCustomerService({
    relatedType: 'ORDER',
    relatedNo: order?.orderNo
  });
};

const canExtract = order => Boolean(order?.extractable);

const extractDelivery = async order => {
  if (!order?.orderNo || extracting.value) {
    return;
  }
  extracting.value = true;
  try {
    extractedDelivery.value = await extractOrderDelivery(order.orderNo);
    deliveryOpen.value = true;
  } catch (e) {
    message.error(e.message || '提货失败');
  } finally {
    extracting.value = false;
  }
};

onMounted(loadOrders);
</script>

<template>
  <div class='orders-page'>
    <header class='orders-header'>
      <div>
        <p class='eyebrow'>Order Center</p>
        <h2>我的订单</h2>
      </div>
      <a-button @click='loadOrders'>
        <template #icon><reload-outlined /></template>
        刷新
      </a-button>
    </header>

    <div class='orders-summary'>
      <a-card :bordered='false'>
        <a-statistic title='订单数' :value='orders.length' />
      </a-card>
      <a-card :bordered='false'>
        <a-statistic title='已交付' :value='deliveredCount' />
      </a-card>
      <a-card :bordered='false'>
        <a-statistic title='累计金额' :value='totalAmount' prefix='¥' />
      </a-card>
    </div>

    <a-card class='orders-panel' :bordered='false'>
      <a-spin :spinning='loading'>
        <a-empty v-if='!orders.length && !loading' description='暂无订单记录' />
        <a-list v-else class='orders-list' :data-source='orders' item-layout='vertical'>
          <template #renderItem='{ item }'>
            <a-list-item>
              <div class='order-row'>
                <div class='order-icon'>
                  <shopping-cart-outlined />
                </div>
                <div class='order-main'>
                  <div class='order-title'>
                    <router-link :href='`/shop/detail/${item.itemId}`'>
                      {{ item.itemName || `商品 ${item.itemId}` }}
                    </router-link>
                    <a-space wrap>
                      <a-tag :color='orderStatusColor(item.status)'>{{ orderStatusText(item.status) }}</a-tag>
                      <a-tag :color='deliveryModeColor(item.deliveryMode)'>
                        {{ item.deliveryModeName || deliveryModeText(item.deliveryMode) }}
                      </a-tag>
                      <a-tag :color='deliveryStatusColor(item.deliveryStatus)'>
                        {{ deliveryStatusText(item.deliveryStatus, item.deliveryMode) }}
                      </a-tag>
                    </a-space>
                  </div>
                  <div class='order-meta'>
                    <span>订单号 {{ item.orderNo }}</span>
                    <a-button type='link' size='small' @click='copyText(item.orderNo)'>
                      <template #icon><copy-outlined /></template>
                      复制
                    </a-button>
                    <a-button type='link' size='small' @click='contactService(item)'>
                      <template #icon><message-outlined /></template>
                      联系客服
                    </a-button>
                    <a-button
                      v-if='canExtract(item)'
                      type='link'
                      size='small'
                      :loading='extracting'
                      @click='extractDelivery(item)'
                    >
                      提取
                    </a-button>
                    <span v-if='item.transactionCode'>流水号 {{ item.transactionCode }}</span>
                    <span>数量 {{ item.count || 1 }}</span>
                    <span>金额 ¥{{ item.amount }}</span>
                    <span v-if='Number(item.discountAmount || 0) > 0'>优惠 ¥{{ item.discountAmount }}</span>
                    <span v-if='item.couponCode'>券码 {{ item.couponCode }}</span>
                    <span>{{ item.paidTime || item.createTime }}</span>
                  </div>
                  <p v-if='item.deliveryMessage' class='delivery-message'>{{ item.deliveryMessage }}</p>
                </div>
              </div>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-card>

    <a-modal
      v-model:open='deliveryOpen'
      :title='extractedDelivery?.title || "提货内容"'
      width='720px'
      :footer='null'
    >
      <div class='delivery-extract'>
        <a-tag v-if='extractedDelivery?.deliveryType' color='blue'>
          {{ extractedDelivery.deliveryType === 'LICENSE' ? '授权许可' : '数字商品' }}
        </a-tag>
        <a-tag v-if='extractedDelivery?.licenseNo' color='green'>
          {{ extractedDelivery.licenseNo }}
        </a-tag>
        <pre v-if='extractedDelivery?.content'>{{ extractedDelivery.content }}</pre>
        <attachment-list :attachments='extractedDelivery?.attachments || []' />
        <a-button
          v-if='extractedDelivery?.content'
          type='primary'
          @click='copyText(extractedDelivery.content)'
        >
          复制内容
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang='less'>
.orders-page {
  width: min(1120px, calc(100vw - 48px));
  margin: 0 auto 48px;
  text-align: left;
}

.orders-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 30px 0 22px;

  h2 {
    margin: 4px 0 8px;
    color: #24364d;
    font-size: 30px;
    line-height: 1.2;
  }

  p {
    margin: 0;
    color: #687789;
  }
}

.eyebrow {
  color: #1677ff !important;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.orders-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;

  :deep(.ant-card) {
    border-radius: 8px;
    background: linear-gradient(180deg, #fff, #f8fbff);
  }
}

.orders-panel {
  border-radius: 8px;
}

.orders-list {
  :deep(.ant-list-item) {
    padding: 18px 0;
  }
}

.order-row {
  display: flex;
  gap: 14px;
  min-width: 0;
}

.order-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 8px;
  background: #eff6ff;
  color: #1677ff;
  font-size: 20px;
}

.order-main {
  min-width: 0;
  flex: 1;
}

.order-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  a {
    overflow: hidden;
    color: #23364d;
    font-size: 16px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.order-meta {
  display: flex;
  margin-top: 8px;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  color: #6f7e8f;
  font-size: 13px;

  :deep(.ant-btn-link) {
    height: 22px;
    padding: 0;
  }
}

.delivery-message {
  margin: 8px 0 0;
  color: #7b8794;
  font-size: 13px;
  line-height: 1.6;
}

.delivery-extract {
  display: grid;
  gap: 12px;

  pre {
    max-height: 420px;
    margin: 0;
    overflow: auto;
    padding: 12px;
    border: 1px solid #edf1f5;
    border-radius: 8px;
    background: #f8fafc;
    color: #26384a;
    font-size: 13px;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

@media only screen and (max-width: 720px) {
  .orders-page {
    width: min(100%, calc(100vw - 24px));
  }

  .orders-header {
    align-items: stretch;
    flex-direction: column;
    padding-top: 20px;
  }

  .orders-summary {
    grid-template-columns: 1fr;
  }

  .order-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;

    a {
      white-space: normal;
    }
  }
}
</style>
