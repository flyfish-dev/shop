<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
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
import { openCustomerService } from '@/modules/shop/components/CustomerService/customerServiceBus.js';
import { getOrders } from '../../apis/api.js';
import { getOrderDelivery, retryOrderDelivery, updateOrderDelivery } from '../../apis/manage.js';
import { useDeliveryFiles } from '../../hooks/useDeliveryFiles.js';
import useClientStore from '@/modules/auth/store/client.js';
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
const saving = ref(false);
const deliveryLoading = ref(false);
const retryingOrderNo = ref('');
const dataSource = ref([]);
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
  deliveryMessage: ''
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
});

const pagedOrders = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  return dataSource.value.slice(start, start + pagination.pageSize);
});
const detailDrawerWidth = computed(() => width.value < 720 ? '100%' : 760);
const deliveryDrawerWidth = computed(() => width.value < 720 ? '100%' : 820);

const summary = computed(() => {
  const orders = dataSource.value;
  const paidOrders = orders.filter(order => ['PAID', 'DELIVERED', 'FAILED'].includes(order.status));
  const failedDeliveries = orders.filter(order => order.deliveryStatus === 'FAILED');
  const revenue = paidOrders.reduce((sum, order) => sum + Number(order.amount || 0), 0);
  return {
    total: orders.length,
    paid: paidOrders.length,
    delivered: orders.filter(order => order.status === 'DELIVERED').length,
    failedDeliveries: failedDeliveries.length,
    revenue: revenue.toFixed(2)
  };
});

const money = value => Number(value || 0).toFixed(2);

const deliveryTypeText = type => {
  if (type === 'LICENSE') return '授权许可';
  if (type === 'DIGITAL') return '数字内容';
  return type || '交付内容';
};
const hasDeliveryFiles = delivery => Boolean((delivery?.files || []).length);
const isSensitiveDelivery = delivery => delivery?.sensitive === true;

const canUpdateDelivery = record => {
  return !['PENDING', 'PAYING', 'CLOSED'].includes(record.status)
    && record.deliveryStatus !== 'SUCCESS';
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

const loadData = async () => {
  loading.value = true;
  try {
    const records = await getOrders();
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

onMounted(loadData);
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
        <a-statistic title="实收金额" :value="summary.revenue" prefix="¥" />
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
                    <strong>¥{{ money(item.amount) }}</strong>
                    <span v-if="Number(item.discountAmount || 0) > 0">已优惠 ¥{{ money(item.discountAmount) }}</span>
                    <span v-else>实付金额</span>
                  </div>
                </div>

                <div class="order-card__body">
                  <div class="goods-line">
                    <span class="goods-icon"><shopping-cart-outlined /></span>
                    <router-link :href="`/shop/detail/${item.itemId}`">
                      {{ item.itemName || `商品 ${item.itemId}` }}
                    </router-link>
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
                    <span>订单号 {{ item.orderNo }}</span>
                    <a-button type="link" size="small" @click="copyText(item.orderNo)">
                      <template #icon><copy-outlined /></template>
                      复制
                    </a-button>
                    <span v-if="item.transactionCode">流水号 {{ item.transactionCode }}</span>
                    <span v-if="item.paymentProvider">支付 {{ item.paymentProvider }}</span>
                    <span>创建 {{ item.createTime }}</span>
                    <span v-if="item.paidTime">支付 {{ item.paidTime }}</span>
                    <span v-if="item.expireTime && ['PENDING', 'PAYING'].includes(item.status)">过期 {{ item.expireTime }}</span>
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
        <a-descriptions bordered size="small" :column="{ xs: 1, sm: 1, md: 2 }">
          <a-descriptions-item label="订单号">
            <a-space>
              <span>{{ selectedOrder.orderNo }}</span>
              <a-button type="link" size="small" @click="copyText(selectedOrder.orderNo)">复制</a-button>
            </a-space>
          </a-descriptions-item>
          <a-descriptions-item label="订单状态">
            <a-tag :color="orderStatusColor(selectedOrder.status)">
              {{ orderStatusText(selectedOrder.status) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="商品">
            {{ selectedOrder.itemName || `商品 ${selectedOrder.itemId}` }}
          </a-descriptions-item>
          <a-descriptions-item label="商品类型">
            {{ selectedOrder.itemTypeName || selectedOrder.itemType }}
          </a-descriptions-item>
          <a-descriptions-item label="客户">
            {{ selectedOrder.buyerName || `用户 ${selectedOrder.buyerId}` }}
          </a-descriptions-item>
          <a-descriptions-item label="联系">
            {{ selectedOrder.buyerPhone || selectedOrder.buyerEmail || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="数量">{{ selectedOrder.count || 1 }}</a-descriptions-item>
          <a-descriptions-item label="实付">¥{{ money(selectedOrder.amount) }}</a-descriptions-item>
          <a-descriptions-item label="原价">¥{{ money(selectedOrder.originalAmount) }}</a-descriptions-item>
          <a-descriptions-item label="优惠">¥{{ money(selectedOrder.discountAmount) }}</a-descriptions-item>
          <a-descriptions-item label="优惠券">{{ selectedOrder.couponCode || '-' }}</a-descriptions-item>
          <a-descriptions-item label="支付渠道">{{ selectedOrder.paymentProvider || '-' }}</a-descriptions-item>
          <a-descriptions-item label="支付流水" :span="{ xs: 1, sm: 1, md: 2 }">
            {{ selectedOrder.transactionCode || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="交付方式">
            <a-tag :color="deliveryModeColor(selectedOrder.deliveryMode)">
              {{ selectedOrder.deliveryModeName || deliveryModeText(selectedOrder.deliveryMode) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="交付状态">
            <a-tag :color="deliveryStatusColor(selectedOrder.deliveryStatus)">
              {{ deliveryStatusText(selectedOrder.deliveryStatus, selectedOrder.deliveryMode) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="交付信息" :span="{ xs: 1, sm: 1, md: 2 }">
            {{ selectedOrder.deliveryMessage || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ selectedOrder.createTime || '-' }}</a-descriptions-item>
          <a-descriptions-item label="支付时间">{{ selectedOrder.paidTime || '-' }}</a-descriptions-item>
          <a-descriptions-item label="过期时间">{{ selectedOrder.expireTime || '-' }}</a-descriptions-item>
        </a-descriptions>

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

          <section v-if="deliveryDetail.content && !isSensitiveDelivery(deliveryDetail)" class="delivery-content">
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
      :confirm-loading="saving"
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

  .order-card__head,
  .delivery-title {
    flex-direction: column;
  }

  .amount-cell {
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
