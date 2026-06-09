<script setup>
import { onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import RouterLink from '@/components/RouterLink/index.vue';
import { MessageOutlined, UserOutlined } from '@ant-design/icons-vue';
import { openCustomerService } from '@/modules/shop/components/CustomerService/customerServiceBus.js';
import { getOrders } from '../../apis/api.js';
import { updateOrderDelivery } from '../../apis/manage.js';
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
const dataSource = ref([]);
const deliveryVisible = ref(false);
const currentOrder = ref(null);

const formState = reactive({
  deliveryStatus: 'SUCCESS',
  deliveryMessage: ''
});

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: total => `共 ${total} 条`,
  showSizeChanger: true,
  showLessItems: true
});

const columns = [
  {
    title: '订单号',
    dataIndex: 'orderNo',
    width: 210
  },
  {
    title: '商品',
    dataIndex: 'itemName',
    width: 180,
    key: 'item'
  },
  {
    title: '客户',
    dataIndex: 'buyerId',
    width: 180,
    key: 'buyer'
  },
  {
    title: '金额',
    dataIndex: 'amount',
    width: 100,
    key: 'amount'
  },
  {
    title: '订单状态',
    dataIndex: 'status',
    width: 110,
    key: 'status'
  },
  {
    title: '交付方式',
    dataIndex: 'deliveryMode',
    width: 120,
    key: 'deliveryMode'
  },
  {
    title: '交付状态',
    dataIndex: 'deliveryStatus',
    width: 130,
    key: 'deliveryStatus'
  },
  {
    title: '支付时间',
    dataIndex: 'paidTime',
    width: 170
  },
  {
    title: '操作',
    key: 'action',
    width: 138,
    fixed: 'right'
  }
];

const canUpdateDelivery = record => {
  return !['PENDING', 'PAYING', 'CLOSED'].includes(record.status)
    && record.deliveryStatus !== 'SUCCESS';
};

const loadData = async () => {
  loading.value = true;
  try {
    const records = await getOrders({
      page: pagination.value.current - 1,
      size: pagination.value.pageSize
    });
    dataSource.value = sortOrdersByNewest(records);
    pagination.value.total = records.page?.total ?? records.length;
  } finally {
    loading.value = false;
  }
};

const handleTableChange = pag => {
  pagination.value.current = pag.current;
  pagination.value.pageSize = pag.pageSize;
  loadData();
};

const openDelivery = record => {
  currentOrder.value = record;
  formState.deliveryStatus = 'SUCCESS';
  formState.deliveryMessage = '';
  deliveryVisible.value = true;
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

onMounted(loadData);
</script>

<template>
  <div class="order-manage">
    <a-card title="订单管理">
      <template #extra>
        <a-button @click="loadData">刷新</a-button>
      </template>

      <a-table
        :loading="loading"
        :columns="columns"
        :data-source="dataSource"
        :pagination="pagination"
        :scroll="{ x: 1320 }"
        row-key="orderNo"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'item'">
            <router-link :href="`/shop/detail/${record.itemId}`">
              {{ record.itemName || `商品 ${record.itemId}` }}
            </router-link>
          </template>
          <template v-else-if="column.key === 'amount'">
            <div class="amount-cell">
              <span>¥{{ record.amount }}</span>
              <span v-if="Number(record.discountAmount || 0) > 0" class="discount">
                已优惠 ¥{{ record.discountAmount }}
              </span>
            </div>
            <span class="count">x{{ record.count || 1 }}</span>
          </template>
          <template v-else-if="column.key === 'buyer'">
            <div class="buyer-cell">
              <a-avatar v-if="record.buyerAvatar" :src="record.buyerAvatar" :size="34" />
              <a-avatar v-else :size="34">
                <user-outlined />
              </a-avatar>
              <div class="buyer-main">
                <strong>{{ record.buyerName || `用户 ${record.buyerId}` }}</strong>
                <span>{{ record.buyerPhone || record.buyerEmail || `ID ${record.buyerId}` }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="orderStatusColor(record.status)">{{ orderStatusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'deliveryMode'">
            <a-tag :color="deliveryModeColor(record.deliveryMode)">
              {{ record.deliveryModeName || deliveryModeText(record.deliveryMode) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'deliveryStatus'">
            <a-space direction="vertical" :size="4">
              <a-tag :color="deliveryStatusColor(record.deliveryStatus)">
                {{ deliveryStatusText(record.deliveryStatus, record.deliveryMode) }}
              </a-tag>
              <span v-if="record.deliveryMessage" class="delivery-message">{{ record.deliveryMessage }}</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space class="action-cell" direction="vertical" :size="2">
              <a-button type="link" size="small" @click="contactBuyer(record)">
                <template #icon><message-outlined /></template>
                联系客户
              </a-button>
              <a-button
                type="link"
                size="small"
                :disabled="!canUpdateDelivery(record)"
                @click="openDelivery(record)"
              >
                处理交付
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

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

.count {
  margin-left: 6px;
  color: #7b8794;
  font-size: 12px;
}

.amount-cell {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
}

.discount {
  color: #e67e22;
  font-size: 12px;
}

.buyer-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.buyer-main {
  display: flex;
  min-width: 0;
  flex-direction: column;

  strong {
    overflow: hidden;
    color: #26384f;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    overflow: hidden;
    color: #7b8794;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.delivery-message {
  max-width: 220px;
  overflow: hidden;
  color: #7b8794;
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-cell {
  :deep(.ant-btn-link) {
    height: 24px;
    padding: 0;
  }
}

@media only screen and (max-width: 640px) {
  .order-manage {
    padding: 0;
  }

  :deep(.ant-card) {
    border-radius: 8px;
  }
}
</style>
