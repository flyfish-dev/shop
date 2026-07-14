<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import dayjs from 'dayjs';
import { storeToRefs } from 'pinia';
import {
  CopyOutlined,
  DownloadOutlined,
  EyeOutlined,
  FileDoneOutlined,
  MessageOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ShoppingCartOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import RouterLink from '@/components/RouterLink/index.vue';
import AttachmentList from '@/components/Attachments/AttachmentList.vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import { openCustomerService } from '@/modules/shop/components/CustomerService/customerServiceBus.js';
import { getOrders, getShopItems } from '../../apis/api.js';
import { getOrderDelivery, retryOrderDelivery, updateOrderDelivery } from '../../apis/manage.js';
import { useDeliveryFiles } from '../../hooks/useDeliveryFiles.js';
import useClientStore from '@/modules/auth/store/client.js';
import { sortOrdersByNewest } from '@/modules/shop/utils/orderSort.js';
import { formatRevenueBreakdown, formatShopMoney, normalizeShopCurrency } from '@/modules/shop/utils/shopMoney.js';
import {
  deliveryModeColor,
  deliveryModeText,
  deliveryStatusColor,
  deliveryStatusText,
  orderStatusColor,
  orderStatusText
} from '@/modules/shop/utils/shopDelivery.js';

const loading = ref(false);
const saving = ref(false);
const deliveryLoading = ref(false);
const deliveryUploading = ref(false);
const retryingOrderNo = ref('');
const dataSource = ref([]);
const orderItemOptions = ref([]);
const detailOpen = ref(false);
const deliveryOpen = ref(false);
const deliveryVisible = ref(false);
const selectedOrder = ref(null);
const currentOrder = ref(null);
const deliveryDetail = ref(null);
const clientStore = useClientStore();
const { width } = storeToRefs(clientStore);
const { downloadingFileCode, downloadDeliveryFile, fileKey } = useDeliveryFiles();

const formState = reactive({
  deliveryStatus: 'SUCCESS',
  deliveryMessage: '',
  deliveryTitle: '',
  deliveryContent: '',
  deliveryAttachments: []
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
});

const filterState = reactive({
  itemId: undefined,
  deliveryStatus: undefined,
  timeRange: []
});

const deliveryStatusOptions = [
  { label: '待交付', value: 'WAITING' },
  { label: '交付中', value: 'PROCESSING' },
  { label: '交付成功', value: 'SUCCESS' },
  { label: '交付失败', value: 'FAILED' },
  { label: '已跳过', value: 'SKIPPED' }
];

const itemSelectOptions = computed(() => orderItemOptions.value.map(item => ({
  label: item.name || `商品 ${item.id}`,
  value: item.id
})));

const pagedOrders = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  return dataSource.value.slice(start, start + pagination.pageSize);
});
const detailDrawerWidth = computed(() => {
  if (width.value < 720) {
    return '100%';
  }
  return width.value < 1180 ? Math.min(width.value - 48, 860) : 920;
});
const deliveryDrawerWidth = computed(() => width.value < 720 ? '100%' : 820);

const summary = computed(() => {
  const orders = dataSource.value;
  const paidOrders = orders.filter(order => ['PAID', 'DELIVERED', 'FAILED'].includes(order.status));
  const failedDeliveries = orders.filter(order => order.deliveryStatus === 'FAILED');
  const revenue = paidOrders.reduce((sum, order) => {
    sum[normalizeShopCurrency(order.currency)] += Number(order.amount || 0);
    return sum;
  }, { CNY: 0, USD: 0 });
  return {
    total: orders.length,
    paid: paidOrders.length,
    delivered: orders.filter(order => order.status === 'DELIVERED').length,
    failedDeliveries: failedDeliveries.length,
    revenue: formatRevenueBreakdown(revenue.CNY, revenue.USD)
  };
});

const normalizedText = value => {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
};

const orderProductName = record => record?.displayName || record?.itemName || `商品 ${record?.itemId || '-'}`;

const orderSkuText = record => {
  if (!record?.skuName) {
    return '-';
  }
  return record.skuCode ? `${record.skuName}（${record.skuCode}）` : record.skuName;
};

const orderContactText = record => record?.buyerPhone || record?.buyerEmail || '-';

const orderWechatNo = record => normalizedText(record?.outerNo)
  || normalizedText(record?.wechatTransactionId)
  || normalizedText(record?.properties?.wechatTransactionId)
  || normalizedText(record?.properties?.wechat_transaction_id);

const orderMerchantNo = record => normalizedText(record?.transactionCode)
  || normalizedText(record?.properties?.h5zhifuTradeNo)
  || normalizedText(record?.properties?.tradeNo);

const deliveryTypeText = type => {
  if (type === 'LICENSE') return '授权许可';
  if (type === 'DIGITAL') return '数字内容';
  if (type === 'MIXED') return '组合交付';
  return type || '交付内容';
};
const hasDeliveryFiles = delivery => Boolean((delivery?.files || []).length);
const isSensitiveDelivery = delivery => delivery?.sensitive === true;
const hasDeliveryContent = delivery => Boolean(delivery?.content && delivery.content !== delivery.securityMessage);

const canUpdateDelivery = record => {
  return !['PENDING', 'PAYING', 'CLOSED'].includes(record.status);
};

const canRetryDelivery = record => record?.deliveryRetryable === true;

const canViewDelivery = record => {
  return Boolean(record?.extractable)
    || record?.status === 'DELIVERED'
    || record?.deliveryStatus === 'SUCCESS';
};

const retryButtonText = record => {
  return record?.deliveryFailureTaskName ? `重试${record.deliveryFailureTaskName}` : '重试自动交付';
};

const formatRangeTime = value => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : undefined;

const buildOrderQuery = () => {
  const [startTime, endTime] = filterState.timeRange || [];
  return {
    itemId: filterState.itemId,
    deliveryStatus: filterState.deliveryStatus,
    startTime: formatRangeTime(startTime),
    endTime: formatRangeTime(endTime)
  };
};

const loadOrderItems = async () => {
  try {
    orderItemOptions.value = await getShopItems({
      page: 0,
      size: 200,
      includeDisabled: true
    });
  } catch (e) {
    orderItemOptions.value = [];
  }
};

const loadData = async () => {
  loading.value = true;
  try {
    const records = await getOrders(buildOrderQuery());
    dataSource.value = sortOrdersByNewest(records);
    pagination.total = records.page?.total ?? dataSource.value.length;
    if ((pagination.current - 1) * pagination.pageSize >= pagination.total) {
      pagination.current = 1;
    }
  } catch (e) {
    dataSource.value = [];
    pagination.total = 0;
    message.error(e.message || '订单加载失败');
  } finally {
    loading.value = false;
  }
};

const applyFilters = () => {
  pagination.current = 1;
  loadData();
};

const resetFilters = () => {
  filterState.itemId = undefined;
  filterState.deliveryStatus = undefined;
  filterState.timeRange = [];
  pagination.current = 1;
  loadData();
};

const handlePageChange = (page, pageSize) => {
  pagination.current = page;
  pagination.pageSize = pageSize;
};

const openDetail = record => {
  selectedOrder.value = record;
  detailOpen.value = true;
};

const openDelivery = record => {
  currentOrder.value = record;
  formState.deliveryStatus = 'SUCCESS';
  formState.deliveryMessage = '';
  formState.deliveryTitle = '';
  formState.deliveryContent = '';
  formState.deliveryAttachments = [];
  deliveryVisible.value = true;
};

const viewDelivery = async record => {
  if (!record?.orderNo || deliveryLoading.value) {
    return;
  }
  deliveryOpen.value = true;
  deliveryDetail.value = null;
  deliveryLoading.value = true;
  try {
    deliveryDetail.value = await getOrderDelivery(record.orderNo);
  } catch (e) {
    deliveryOpen.value = false;
    message.error(e.message || '交付快照加载失败');
  } finally {
    deliveryLoading.value = false;
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

const contactBuyer = record => {
  if (!record?.buyerId) {
    message.warning('缺少客户信息');
    return;
  }
  openCustomerService({
    userId: record.buyerId,
    relatedType: 'ORDER',
    relatedNo: record.orderNo
  });
};

const submitDelivery = async () => {
  if (!currentOrder.value?.orderNo) {
    return;
  }
  if (deliveryUploading.value) {
    message.warning('附件上传完成后再提交');
    return;
  }
  saving.value = true;
  try {
    await updateOrderDelivery(currentOrder.value.orderNo, { ...formState });
    message.success('交付状态已更新');
    deliveryVisible.value = false;
    await loadData();
  } catch (e) {
    message.error(e.message || '更新失败');
  } finally {
    saving.value = false;
  }
};

const retryDelivery = async record => {
  if (!record?.orderNo || retryingOrderNo.value) {
    return;
  }
  retryingOrderNo.value = record.orderNo;
  try {
    await retryOrderDelivery(record.orderNo);
    message.success('已重新发起自动交付');
    await loadData();
  } catch (e) {
    message.error(e.message || '重试交付失败');
    await loadData();
  } finally {
    retryingOrderNo.value = '';
  }
};

onMounted(async () => {
  await Promise.all([
    loadOrderItems(),
    loadData()
  ]);
});
</script>

<template>
  <div class="order-manage">
    <header class="manage-header">
      <div>
        <p class="eyebrow">Orders</p>
        <h2>订单管理</h2>
      </div>
      <a-button @click="loadData">
        <template #icon><reload-outlined /></template>
        刷新
      </a-button>
    </header>

    <section class="order-summary">
      <a-card :bordered="false">
        <a-statistic title="订单数" :value="summary.total" />
      </a-card>
      <a-card :bordered="false">
        <a-statistic title="已支付" :value="summary.paid" />
      </a-card>
      <a-card :bordered="false">
        <a-statistic title="已交付" :value="summary.delivered" />
      </a-card>
      <a-card :bordered="false">
        <a-statistic title="实收金额" :value="summary.revenue" />
      </a-card>
    </section>

    <a-alert
      v-if="summary.failedDeliveries"
      class="delivery-alert"
      type="warning"
      show-icon
      :message="`${summary.failedDeliveries} 个订单需要处理交付`"
    />

    <a-card class="orders-panel" :bordered="false">
      <section class="order-filters">
        <a-select
          v-model:value="filterState.itemId"
          class="filter-item"
          show-search
          allow-clear
          option-filter-prop="label"
          placeholder="按商品筛选"
          :options="itemSelectOptions"
        />
        <a-select
          v-model:value="filterState.deliveryStatus"
          class="filter-status"
          allow-clear
          placeholder="按交付状态筛选"
          :options="deliveryStatusOptions"
        />
        <a-range-picker
          v-model:value="filterState.timeRange"
          class="filter-range"
          show-time
          format="YYYY-MM-DD HH:mm:ss"
        />
        <a-space class="filter-actions">
          <a-button type="primary" @click="applyFilters">筛选</a-button>
          <a-button @click="resetFilters">重置</a-button>
        </a-space>
      </section>
      <a-spin :spinning="loading">
        <a-empty v-if="!pagedOrders.length && !loading" description="暂无订单记录" />
        <a-list v-else class="orders-list" :data-source="pagedOrders" item-layout="vertical">
          <template #renderItem="{ item }">
            <a-list-item>
              <article class="order-card">
                <div class="order-card__head">
                  <div class="buyer-cell">
                    <a-avatar v-if="item.buyerAvatar" :src="item.buyerAvatar" :size="44" />
                    <a-avatar v-else :size="44">
                      <user-outlined />
                    </a-avatar>
                    <div class="buyer-main">
                      <strong>{{ item.buyerName || `用户 ${item.buyerId}` }}</strong>
                      <span>{{ item.buyerPhone || item.buyerEmail || `ID ${item.buyerId}` }}</span>
                    </div>
                  </div>
                  <div class="amount-cell">
                    <strong>{{ formatShopMoney(item.amount, item.currency) }}</strong>
                    <span v-if="Number(item.discountAmount || 0) > 0">已优惠 {{ formatShopMoney(item.discountAmount, item.currency) }}</span>
                    <span v-else>实付金额</span>
                  </div>
                </div>

                <div class="order-card__body">
                  <div class="goods-line">
                    <span class="goods-icon"><shopping-cart-outlined /></span>
                    <router-link :href="`/shop/detail/${item.itemId}`">
                      {{ orderProductName(item) }}
                    </router-link>
                    <a-tag v-if="item.skuName" color="cyan">{{ item.skuName }}</a-tag>
                    <a-tag>数量 x{{ item.count || 1 }}</a-tag>
                  </div>

                  <a-space class="status-tags" wrap>
                    <a-tag :color="orderStatusColor(item.status)">{{ orderStatusText(item.status) }}</a-tag>
                    <a-tag :color="deliveryModeColor(item.deliveryMode)">
                      {{ item.deliveryModeName || deliveryModeText(item.deliveryMode) }}
                    </a-tag>
                    <a-tag :color="deliveryStatusColor(item.deliveryStatus)">
                      {{ deliveryStatusText(item.deliveryStatus, item.deliveryMode) }}
                    </a-tag>
                    <a-tag v-if="item.couponCode" color="green">优惠：{{ item.couponCode }}</a-tag>
                  </a-space>

                  <div class="order-meta">
                    <span v-if="item.paymentProvider">支付 {{ item.paymentProvider }}</span>
                    <span>创建 {{ item.createTime }}</span>
                    <span v-if="item.paidTime">支付 {{ item.paidTime }}</span>
                    <span v-if="item.expireTime && ['PENDING', 'PAYING'].includes(item.status)">过期 {{ item.expireTime }}</span>
                  </div>

                  <div class="order-number-strip">
                    <div class="order-number-item">
                      <span>飞鱼订单号</span>
                      <strong class="mono-value">{{ item.orderNo }}</strong>
                      <a-button type="link" size="small" @click="copyText(item.orderNo)">
                        <template #icon><copy-outlined /></template>
                        复制
                      </a-button>
                    </div>
                    <div v-if="orderWechatNo(item)" class="order-number-item order-number-item--wechat">
                      <span>渠道交易单号</span>
                      <strong class="mono-value">{{ orderWechatNo(item) }}</strong>
                      <a-button type="link" size="small" @click="copyText(orderWechatNo(item))">
                        <template #icon><copy-outlined /></template>
                        复制
                      </a-button>
                    </div>
                    <div v-if="orderMerchantNo(item)" class="order-number-item">
                      <span>支付会话 / 商户单号</span>
                      <strong class="mono-value">{{ orderMerchantNo(item) }}</strong>
                      <a-button type="link" size="small" @click="copyText(orderMerchantNo(item))">
                        <template #icon><copy-outlined /></template>
                        复制
                      </a-button>
                    </div>
                  </div>

                  <p v-if="item.deliveryMessage" class="delivery-message">{{ item.deliveryMessage }}</p>
                </div>

                <div class="order-card__actions">
                  <a-button size="small" @click="openDetail(item)">
                    <template #icon><eye-outlined /></template>
                    查看详情
                  </a-button>
                  <a-button size="small" :disabled="!canViewDelivery(item)" @click="viewDelivery(item)">
                    <template #icon><file-done-outlined /></template>
                    查看交付
                  </a-button>
                  <a-button size="small" @click="contactBuyer(item)">
                    <template #icon><message-outlined /></template>
                    联系客户
                  </a-button>
                  <a-button
                    size="small"
                    :disabled="!canUpdateDelivery(item)"
                    @click="openDelivery(item)"
                  >
                    处理交付
                  </a-button>
                  <a-button
                    v-if="canRetryDelivery(item)"
                    size="small"
                    type="primary"
                    danger
                    :loading="retryingOrderNo === item.orderNo"
                    @click="retryDelivery(item)"
                  >
                    {{ retryButtonText(item) }}
                  </a-button>
                </div>
              </article>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>

      <a-pagination
        v-if="pagination.total > pagination.pageSize"
        class="orders-pagination"
        :current="pagination.current"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        show-size-changer
        show-less-items
        :show-total="total => `共 ${total} 条`"
        @change="handlePageChange"
        @showSizeChange="handlePageChange"
      />
    </a-card>

    <a-drawer v-model:open="detailOpen" class="order-manage-drawer" title="订单详情" :width="detailDrawerWidth">
      <div v-if="selectedOrder" class="detail-drawer">
        <section class="detail-summary-panel">
          <div class="detail-summary-main">
            <span class="detail-kicker">飞鱼订单号</span>
            <div class="copy-value copy-value--large">
              <span class="mono-value">{{ selectedOrder.orderNo }}</span>
              <a-button type="link" size="small" @click="copyText(selectedOrder.orderNo)">
                <template #icon><copy-outlined /></template>
                复制
              </a-button>
            </div>
            <h3>{{ orderProductName(selectedOrder) }}</h3>
            <a-space wrap>
              <a-tag :color="orderStatusColor(selectedOrder.status)">
                {{ orderStatusText(selectedOrder.status) }}
              </a-tag>
              <a-tag :color="deliveryModeColor(selectedOrder.deliveryMode)">
                {{ selectedOrder.deliveryModeName || deliveryModeText(selectedOrder.deliveryMode) }}
              </a-tag>
              <a-tag :color="deliveryStatusColor(selectedOrder.deliveryStatus)">
                {{ deliveryStatusText(selectedOrder.deliveryStatus, selectedOrder.deliveryMode) }}
              </a-tag>
            </a-space>
          </div>
          <div class="detail-summary-amount">
            <span>实付金额</span>
            <strong>{{ formatShopMoney(selectedOrder.amount, selectedOrder.currency) }}</strong>
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section__head">
            <h3>支付信息</h3>
          </div>
          <div class="detail-grid">
            <div class="detail-field detail-field--full">
              <span class="detail-label">渠道交易单号</span>
              <div class="copy-value">
                <span class="mono-value">{{ orderWechatNo(selectedOrder) || '-' }}</span>
                <a-button
                  v-if="orderWechatNo(selectedOrder)"
                  type="link"
                  size="small"
                  @click="copyText(orderWechatNo(selectedOrder))"
                >
                  <template #icon><copy-outlined /></template>
                  复制
                </a-button>
              </div>
            </div>
            <div class="detail-field detail-field--full">
              <span class="detail-label">支付会话 / 商户单号</span>
              <div class="copy-value">
                <span class="mono-value">{{ orderMerchantNo(selectedOrder) || '-' }}</span>
                <a-button
                  v-if="orderMerchantNo(selectedOrder)"
                  type="link"
                  size="small"
                  @click="copyText(orderMerchantNo(selectedOrder))"
                >
                  <template #icon><copy-outlined /></template>
                  复制
                </a-button>
              </div>
            </div>
            <div class="detail-field">
              <span class="detail-label">支付渠道</span>
              <strong>{{ selectedOrder.paymentProvider || '-' }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">订单状态</span>
              <a-tag :color="orderStatusColor(selectedOrder.status)">
                {{ orderStatusText(selectedOrder.status) }}
              </a-tag>
            </div>
            <div class="detail-field">
              <span class="detail-label">原价</span>
              <strong>{{ formatShopMoney(selectedOrder.originalAmount, selectedOrder.currency) }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">优惠</span>
              <strong>{{ formatShopMoney(selectedOrder.discountAmount, selectedOrder.currency) }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">优惠券</span>
              <strong>{{ selectedOrder.couponCode || '-' }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">数量</span>
              <strong>{{ selectedOrder.count || 1 }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section__head">
            <h3>商品与客户</h3>
          </div>
          <div class="detail-grid">
            <div class="detail-field detail-field--full">
              <span class="detail-label">商品</span>
              <strong>{{ orderProductName(selectedOrder) }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">SKU</span>
              <strong>{{ orderSkuText(selectedOrder) }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">商品类型</span>
              <strong>{{ selectedOrder.itemTypeName || selectedOrder.itemType || '-' }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">客户</span>
              <strong>{{ selectedOrder.buyerName || `用户 ${selectedOrder.buyerId}` }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">联系</span>
              <strong>{{ orderContactText(selectedOrder) }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section__head">
            <h3>交付信息</h3>
          </div>
          <div class="detail-grid">
            <div class="detail-field">
              <span class="detail-label">交付方式</span>
              <a-tag :color="deliveryModeColor(selectedOrder.deliveryMode)">
                {{ selectedOrder.deliveryModeName || deliveryModeText(selectedOrder.deliveryMode) }}
              </a-tag>
            </div>
            <div class="detail-field">
              <span class="detail-label">交付状态</span>
              <a-tag :color="deliveryStatusColor(selectedOrder.deliveryStatus)">
                {{ deliveryStatusText(selectedOrder.deliveryStatus, selectedOrder.deliveryMode) }}
              </a-tag>
            </div>
            <div class="detail-field detail-field--full">
              <span class="detail-label">交付信息</span>
              <strong>{{ selectedOrder.deliveryMessage || '-' }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <div class="detail-section__head">
            <h3>时间信息</h3>
          </div>
          <div class="detail-grid">
            <div class="detail-field">
              <span class="detail-label">创建时间</span>
              <strong>{{ selectedOrder.createTime || '-' }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">支付时间</span>
              <strong>{{ selectedOrder.paidTime || '-' }}</strong>
            </div>
            <div class="detail-field">
              <span class="detail-label">过期时间</span>
              <strong>{{ selectedOrder.expireTime || '-' }}</strong>
            </div>
          </div>
        </section>

        <a-space class="drawer-actions" wrap>
          <a-button @click="contactBuyer(selectedOrder)">
            <template #icon><message-outlined /></template>
            联系客户
          </a-button>
          <a-button :disabled="!canViewDelivery(selectedOrder)" @click="viewDelivery(selectedOrder)">
            <template #icon><file-done-outlined /></template>
            查看交付
          </a-button>
          <a-button :disabled="!canUpdateDelivery(selectedOrder)" @click="openDelivery(selectedOrder)">
            处理交付
          </a-button>
          <a-button
            v-if="canRetryDelivery(selectedOrder)"
            type="primary"
            danger
            :loading="retryingOrderNo === selectedOrder.orderNo"
            @click="retryDelivery(selectedOrder)"
          >
            {{ retryButtonText(selectedOrder) }}
          </a-button>
        </a-space>
      </div>
    </a-drawer>

    <a-drawer v-model:open="deliveryOpen" class="order-manage-drawer" title="交付详情" :width="deliveryDrawerWidth">
      <a-spin :spinning="deliveryLoading">
        <div v-if="deliveryDetail" class="delivery-detail">
          <div class="delivery-title">
            <div>
              <h3>{{ deliveryDetail.title || '交付内容' }}</h3>
              <p>{{ deliveryDetail.orderNo }}</p>
            </div>
            <a-space wrap>
              <a-tag color="blue">{{ deliveryTypeText(deliveryDetail.deliveryType) }}</a-tag>
              <a-tag v-if="deliveryDetail.licenseNo" color="green">{{ deliveryDetail.licenseNo }}</a-tag>
            </a-space>
          </div>

          <a-descriptions bordered size="small" :column="{ xs: 1, sm: 1, md: 2 }">
            <a-descriptions-item label="订单号">{{ deliveryDetail.orderNo }}</a-descriptions-item>
            <a-descriptions-item label="交付类型">
              {{ deliveryTypeText(deliveryDetail.deliveryType) }}
            </a-descriptions-item>
            <a-descriptions-item label="授权编号">{{ deliveryDetail.licenseNo || '-' }}</a-descriptions-item>
            <a-descriptions-item label="首次提取">{{ deliveryDetail.extractedTime || '-' }}</a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="isSensitiveDelivery(deliveryDetail)"
            type="warning"
            show-icon
            :message="deliveryDetail.securityMessage || deliveryDetail.content"
          />

          <section v-if="hasDeliveryFiles(deliveryDetail)" class="delivery-downloads">
            <div class="section-title">
              <span>授权文件</span>
            </div>
            <article
              v-for="file in deliveryDetail.files"
              :key="file.code"
              class="delivery-file-card"
            >
              <div class="delivery-file-card__icon">
                <safety-certificate-outlined />
              </div>
              <div>
                <strong>{{ file.name }}</strong>
                <span>{{ file.description }}</span>
              </div>
              <a-button
                class="delivery-file-card__download"
                size="small"
                type="primary"
                :loading="downloadingFileCode === fileKey(deliveryDetail, file)"
                @click="downloadDeliveryFile(deliveryDetail, file)"
              >
                <template #icon><download-outlined /></template>
                下载
              </a-button>
            </article>
          </section>

          <section v-if="hasDeliveryContent(deliveryDetail)" class="delivery-content">
            <div class="section-title">
              <span>交付正文</span>
              <a-button size="small" @click="copyText(deliveryDetail.content)">
                <template #icon><copy-outlined /></template>
                复制
              </a-button>
            </div>
            <pre>{{ deliveryDetail.content }}</pre>
          </section>

          <section class="delivery-files">
            <div class="section-title">
              <span>其他附件</span>
            </div>
            <attachment-list :attachments="deliveryDetail.attachments || []" />
            <a-empty
              v-if="!(deliveryDetail.attachments || []).length"
              description="暂无附件"
            />
          </section>
        </div>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="deliveryVisible"
      title="处理交付"
      :confirm-loading="saving || deliveryUploading"
      @ok="submitDelivery"
    >
      <a-form layout="vertical" :model="formState">
        <a-form-item label="交付结果" name="deliveryStatus">
          <a-radio-group v-model:value="formState.deliveryStatus">
            <a-radio-button value="SUCCESS">交付完成</a-radio-button>
            <a-radio-button value="FAILED">交付失败</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="交付信息" name="deliveryMessage">
          <a-textarea v-model:value="formState.deliveryMessage" :auto-size="{ minRows: 3, maxRows: 5 }" />
        </a-form-item>
        <a-form-item label="补充资源标题" name="deliveryTitle">
          <a-input v-model:value="formState.deliveryTitle" placeholder="例如：专属商业 demo 下载包" />
        </a-form-item>
        <a-form-item label="补充资源说明" name="deliveryContent">
          <a-textarea v-model:value="formState.deliveryContent" :auto-size="{ minRows: 3, maxRows: 5 }" />
        </a-form-item>
        <a-form-item label="补充附件" name="deliveryAttachments">
          <attachment-upload
            v-model:value="formState.deliveryAttachments"
            :max-count="12"
            :disabled="saving"
            @uploading-change="value => deliveryUploading = value"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.order-manage {
  min-width: 0;
  padding: 20px;
}

.manage-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h2 {
    margin: 2px 0 0;
    color: #22364f;
    font-size: 26px;
    line-height: 1.2;
  }
}

.eyebrow {
  margin: 0;
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.order-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;

  :deep(.ant-card) {
    border-radius: 8px;
    background: linear-gradient(180deg, #ffffff, #f7fbff);
  }
}

.delivery-alert {
  margin-bottom: 12px;
  border-radius: 8px;
}

.orders-panel {
  border-radius: 8px;
}

.order-filters {
  display: grid;
  grid-template-columns: minmax(220px, 1.3fr) minmax(160px, 0.8fr) minmax(300px, 1.4fr) auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.filter-item,
.filter-status,
.filter-range {
  width: 100%;
  min-width: 0;
}

.filter-actions {
  justify-content: flex-end;
}

.orders-list {
  :deep(.ant-list-item) {
    padding: 10px 0;
    border-block-end: 0;
  }
}

.order-card {
  display: grid;
  width: 100%;
  gap: 14px;
  padding: 16px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgb(30 55 90 / 6%);
}

.order-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.buyer-cell {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.buyer-main {
  display: grid;
  min-width: 0;
  gap: 2px;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #243850;
    font-size: 15px;
  }

  span {
    color: #7b8794;
    font-size: 12px;
  }
}

.amount-cell {
  display: grid;
  flex: none;
  justify-items: end;
  gap: 2px;

  strong {
    color: #ef4444;
    font-size: 22px;
    line-height: 1.1;
  }

  span {
    color: #7b8794;
    font-size: 12px;
  }
}

.order-card__body {
  display: grid;
  gap: 9px;
}

.goods-line {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;

  a {
    overflow: hidden;
    color: #22364f;
    font-size: 16px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.goods-icon {
  display: inline-flex;
  width: 30px;
  height: 30px;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #edf7ff;
  color: #1677ff;
}

.status-tags,
.order-meta {
  min-width: 0;
}

.order-meta {
  display: flex;
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

.order-number-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.order-number-item {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #fafcff;

  span {
    grid-column: 1 / -1;
    color: #7b8794;
    font-size: 12px;
    line-height: 1.2;
  }

  :deep(.ant-btn-link) {
    height: 24px;
    padding: 0;
  }
}

.order-number-item--wechat {
  border-color: #bbf7d0;
  background: #f7fef9;
}

.mono-value {
  min-width: 0;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.delivery-message {
  margin: 0;
  color: #7b8794;
  font-size: 13px;
  line-height: 1.6;
}

.order-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.orders-pagination {
  margin-top: 14px;
  text-align: right;
}

.detail-drawer,
.delivery-detail {
  display: grid;
  gap: 16px;
}

.detail-summary-panel,
.detail-section {
  min-width: 0;
  border: 1px solid #e8eef5;
  border-radius: 8px;
  background: #fff;
}

.detail-summary-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
  padding: 16px;
  background: linear-gradient(180deg, #ffffff, #f8fbff);
}

.detail-summary-main {
  min-width: 0;

  h3 {
    margin: 6px 0 10px;
    color: #22364f;
    font-size: 18px;
    line-height: 1.35;
  }
}

.detail-kicker,
.detail-label {
  color: #7b8794;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.detail-summary-amount {
  display: grid;
  min-width: 128px;
  justify-items: end;
  gap: 3px;

  span {
    color: #7b8794;
    font-size: 12px;
  }

  strong {
    color: #ef4444;
    font-size: 24px;
    line-height: 1.15;
  }
}

.detail-section {
  padding: 14px;
}

.detail-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    color: #22364f;
    font-size: 15px;
    line-height: 1.35;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.detail-field {
  display: grid;
  min-width: 0;
  gap: 5px;
  align-content: start;
  min-height: 66px;
  padding: 10px 12px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #fbfcfe;

  strong {
    min-width: 0;
    color: #1f2937;
    font-size: 14px;
    line-height: 1.55;
    overflow-wrap: anywhere;
    word-break: break-word;
  }
}

.detail-field--full {
  grid-column: 1 / -1;
}

.copy-value {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;

  :deep(.ant-btn-link) {
    height: 24px;
    padding: 0;
  }
}

.copy-value--large {
  max-width: 100%;
  margin-top: 2px;

  .mono-value {
    color: #0f172a;
    font-size: 16px;
    font-weight: 700;
  }
}

.drawer-actions {
  padding-top: 2px;
}

.delivery-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h3 {
    margin: 0;
    color: #22364f;
    font-size: 18px;
  }

  p {
    margin: 4px 0 0;
    color: #7b8794;
    font-size: 12px;
  }
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #243850;
  font-weight: 700;
}

.delivery-content {
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

.delivery-downloads {
  display: grid;
  gap: 10px;
}

.delivery-file-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;

  strong,
  span {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1f344f;
    font-size: 14px;
  }

  span {
    margin-top: 2px;
    color: #6b7a8d;
    font-size: 12px;
  }
}

.delivery-file-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #eaf4ff;
  color: #1677ff;
  font-size: 20px;
}

.delivery-file-card__download {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  justify-self: end;
  gap: 6px;
  min-width: 88px;
  height: 34px;
  padding: 0 14px !important;
  color: #fff !important;
  line-height: 1 !important;
  white-space: nowrap;

  :deep(.ant-btn-icon),
  :deep(.anticon) {
    display: inline-flex !important;
    align-items: center;
    justify-content: center;
    margin-inline-end: 0 !important;
    color: #fff !important;
    line-height: 1;
  }

  :deep(.anticon svg) {
    fill: currentColor;
  }

  :deep(.ant-btn-icon + span) {
    display: inline-flex;
    align-items: center;
    color: #fff;
    line-height: 1;
  }
}

.delivery-files {
  :deep(.ant-empty) {
    margin: 12px 0 0;
  }
}

:global(.order-manage-drawer .ant-drawer-body) {
  min-width: 0;
}

@media only screen and (max-width: 900px) {
  .order-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .order-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-range,
  .filter-actions {
    grid-column: 1 / -1;
  }
}

@media only screen and (max-width: 640px) {
  .order-manage {
    padding: 0;
  }

  .manage-header {
    align-items: stretch;
    flex-direction: column;
    padding: 0 2px;
  }

  .order-summary {
    grid-template-columns: 1fr;
  }

  .order-filters {
    grid-template-columns: 1fr;
  }

  .filter-range,
  .filter-actions {
    grid-column: auto;
  }

  .filter-actions {
    justify-content: stretch;

    :deep(.ant-space-item),
    :deep(.ant-btn) {
      width: 100%;
    }
  }

  .order-card__head,
  .detail-summary-panel,
  .delivery-title {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .amount-cell {
    justify-items: start;
  }

  .detail-summary-amount {
    justify-items: start;
  }

  .goods-line {
    align-items: flex-start;

    a {
      white-space: normal;
    }
  }

  .orders-pagination {
    text-align: left;
  }

  .order-number-strip,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .delivery-file-card {
    grid-template-columns: 38px minmax(0, 1fr);

    .delivery-file-card__download {
      grid-column: 1 / -1;
      width: 100%;
      justify-self: stretch;
    }
  }

  :global(.order-manage-drawer .ant-drawer-content-wrapper) {
    width: 100vw !important;
    max-width: 100vw;
  }

  :global(.order-manage-drawer .ant-drawer-body) {
    padding: 14px;
  }

  :global(.order-manage-drawer .ant-descriptions-view) {
    overflow-x: auto;
  }
}
</style>
