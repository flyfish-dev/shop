<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  CheckCircleOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  MessageOutlined,
  ReloadOutlined,
  SendOutlined
} from '@ant-design/icons-vue';
import AttachmentList from '@/components/Attachments/AttachmentList.vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import { openCustomerService } from '@/modules/shop/components/CustomerService/customerServiceBus.js';
import useClientStore from '@/modules/auth/store/client.js';
import {
  TICKET_STATUS_OPTIONS,
  isTicketDone,
  ticketCategoryText,
  ticketPriorityColor,
  ticketPriorityText,
  ticketStatusColor,
  ticketStatusText
} from '@/modules/shop/utils/supportTickets.js';
import { sortTicketsByNewest } from '@/modules/shop/utils/ticketSort.js';
import {
  getManagedTicket,
  getManagedTickets,
  replyManagedTicket,
  resolveManagedTicket
} from '@/modules/shop/pages/Support/apis.js';

const loading = ref(false);
const clientStore = useClientStore();
const detailLoading = ref(false);
const replyLoading = ref(false);
const replyUploading = ref(false);
const resolving = ref(false);
const tickets = ref([]);
const selected = ref(null);
const status = ref('');
const detailOpen = ref(false);
const replyContent = ref('');
const replyAttachments = ref([]);

const unresolvedCount = computed(() => tickets.value.filter(ticket => !isTicketDone(ticket.status)).length);
const isMobile = computed(() => clientStore.width < 720);
const drawerWidth = computed(() => isMobile.value ? '100%' : 660);

const columns = [
  {
    title: '工单',
    dataIndex: 'title',
    width: 260,
  },
  {
    title: '提交用户',
    dataIndex: 'creatorName',
    width: 130,
  },
  {
    title: '类型',
    dataIndex: 'category',
    width: 110,
  },
  {
    title: '优先级',
    dataIndex: 'priority',
    width: 90,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 110,
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    width: 160,
  },
  {
    title: '操作',
    dataIndex: 'action',
    width: 116,
    fixed: 'right',
  }
];

const loadTickets = async () => {
  loading.value = true;
  try {
    tickets.value = sortTicketsByNewest(await getManagedTickets({ status: status.value }));
  } catch (e) {
    tickets.value = [];
    message.error(e.message || '工单加载失败');
  } finally {
    loading.value = false;
  }
};

const openDetail = async record => {
  detailOpen.value = true;
  detailLoading.value = true;
  replyContent.value = '';
  replyAttachments.value = [];
  try {
    selected.value = await getManagedTicket(record.ticketNo);
  } catch (e) {
    message.error(e.message || '工单详情加载失败');
  } finally {
    detailLoading.value = false;
  }
};

const contactCreator = record => {
  const target = record || selected.value;
  if (!target?.creatorId) {
    message.warning('缺少客户信息');
    return;
  }
  openCustomerService({
    userId: target.creatorId,
    relatedType: 'TICKET',
    relatedNo: target.ticketNo
  });
};

const submitReply = async () => {
  const content = replyContent.value.trim();
  if ((!content && !replyAttachments.value.length) || !selected.value?.ticketNo || replyUploading.value) {
    return;
  }
  replyLoading.value = true;
  try {
    selected.value = await replyManagedTicket(selected.value.ticketNo, {
      content,
      attachments: replyAttachments.value
    });
    replyContent.value = '';
    replyAttachments.value = [];
    await loadTickets();
    message.success('已回复');
  } catch (e) {
    message.error(e.message || '回复失败');
  } finally {
    replyLoading.value = false;
  }
};

const markResolved = async () => {
  if (!selected.value?.ticketNo) {
    return;
  }
  resolving.value = true;
  try {
    selected.value = await resolveManagedTicket(selected.value.ticketNo);
    await loadTickets();
    message.success('已标记解决');
  } catch (e) {
    message.error(e.message || '操作失败');
  } finally {
    resolving.value = false;
  }
};

watch(status, loadTickets);
onMounted(loadTickets);
</script>

<template>
  <div class="ticket-manage">
    <a-card>
      <template #title>
        <div class="card-title">
          <file-text-outlined />
          <span>工单管理</span>
          <a-tag color="blue">{{ unresolvedCount }} 待处理</a-tag>
        </div>
      </template>
      <template #extra>
        <a-button @click="loadTickets">
          <template #icon><reload-outlined /></template>
          刷新
        </a-button>
      </template>

      <div class="ticket-toolbar">
        <a-radio-group v-model:value="status" button-style="solid">
          <a-radio-button v-for="item in TICKET_STATUS_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-radio-button>
        </a-radio-group>
      </div>

      <a-table
        v-if="!isMobile"
        :loading="loading"
        :columns="columns"
        :data-source="tickets"
        :pagination="{ pageSize: 10, showSizeChanger: false }"
        :scroll="{ x: 980 }"
        row-key="ticketNo"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'title'">
            <div class="ticket-title">
              <strong>{{ record.title }}</strong>
              <span>{{ record.ticketNo }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'category'">
            {{ ticketCategoryText(record.category) }}
          </template>
          <template v-else-if="column.dataIndex === 'priority'">
            <a-tag :color="ticketPriorityColor(record.priority)">{{ ticketPriorityText(record.priority) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="ticketStatusColor(record.status)">{{ ticketStatusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space class="ticket-actions" direction="vertical" :size="2">
              <a-button type="link" size="small" @click="openDetail(record)">处理</a-button>
              <a-button type="link" size="small" @click="contactCreator(record)">
                <template #icon><message-outlined /></template>
                联系
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-spin v-else :spinning="loading">
        <a-empty v-if="!tickets.length && !loading" description="暂无工单" />
        <a-list v-else class="mobile-ticket-list" :data-source="tickets">
          <template #renderItem="{ item }">
            <a-list-item class="mobile-ticket-card" @click="openDetail(item)">
              <div class="mobile-ticket-main">
                <strong>{{ item.title }}</strong>
                <span>{{ item.ticketNo }}</span>
                <p v-if="item.lastMessage">{{ item.lastMessage }}</p>
                <div class="mobile-ticket-tags">
                  <a-tag :color="ticketStatusColor(item.status)">{{ ticketStatusText(item.status) }}</a-tag>
                  <a-tag :color="ticketPriorityColor(item.priority)">{{ ticketPriorityText(item.priority) }}</a-tag>
                  <span>{{ item.updateTime || item.createTime }}</span>
                </div>
              </div>
              <a-button type="link" size="small" @click.stop="contactCreator(item)">
                <template #icon><message-outlined /></template>
                联系
              </a-button>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-card>

    <a-drawer v-model:open="detailOpen" class="ticket-manage-drawer" title="处理工单" :width="drawerWidth">
      <a-spin :spinning="detailLoading">
        <template v-if="selected">
          <div class="detail-head">
            <div>
              <h3>{{ selected.title }}</h3>
              <div class="detail-meta">
                <span>{{ selected.ticketNo }}</span>
                <span>{{ selected.creatorName }}</span>
                <span v-if="selected.contact">{{ selected.contact }}</span>
                <span>{{ selected.createTime }}</span>
              </div>
            </div>
            <a-space wrap>
              <a-tag :color="ticketStatusColor(selected.status)">{{ ticketStatusText(selected.status) }}</a-tag>
              <a-tag :color="ticketPriorityColor(selected.priority)">{{ ticketPriorityText(selected.priority) }}</a-tag>
            </a-space>
          </div>

          <div class="customer-box">
            <div class="customer-box-head">
              <strong>客户信息</strong>
              <a-button size="small" type="primary" ghost @click="contactCreator(selected)">
                <template #icon><message-outlined /></template>
                微信客服
              </a-button>
            </div>
            <a-descriptions bordered size="small" :column="1">
              <a-descriptions-item label="用户" :span="3">{{ selected.creatorName }}</a-descriptions-item>
              <a-descriptions-item label="手机号" :span="3">{{ selected.creatorPhone || '--' }}</a-descriptions-item>
              <a-descriptions-item label="邮箱" :span="3">{{ selected.creatorEmail || '--' }}</a-descriptions-item>
              <a-descriptions-item label="联系方式" :span="3">{{ selected.contact || '--' }}</a-descriptions-item>
            </a-descriptions>
          </div>

          <div class="message-list">
            <div
              v-for="item in selected.messages"
              :key="item.id"
              class="message-item"
              :class="{ admin: item.senderRole === 'ADMIN' }"
            >
              <a-avatar v-if="item.senderAvatar" :src="item.senderAvatar" />
              <a-avatar v-else>
                <customer-service-outlined />
              </a-avatar>
              <div class="message-content">
                <div class="message-top">
                  <strong>{{ item.senderName }}</strong>
                  <span>{{ item.createTime }}</span>
                </div>
                <p>{{ item.content }}</p>
                <attachment-list :attachments="item.attachments" />
              </div>
            </div>
          </div>

          <div v-if="!isTicketDone(selected.status)" class="reply-box">
            <a-textarea v-model:value="replyContent" :auto-size="{ minRows: 4, maxRows: 8 }" />
            <attachment-upload
              v-model:value="replyAttachments"
              @uploading-change="value => replyUploading = value"
            />
            <div class="reply-actions">
              <a-button
                :disabled="replyUploading || (!replyContent.trim() && !replyAttachments.length)"
                :loading="replyLoading || replyUploading"
                @click="submitReply"
              >
                <template #icon><send-outlined /></template>
                回复
              </a-button>
              <a-button type="primary" :loading="resolving" @click="markResolved">
                <template #icon><check-circle-outlined /></template>
                标记解决
              </a-button>
            </div>
          </div>
        </template>
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped lang="less">
.ticket-manage {
  padding: 20px;
  min-width: 0;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ticket-toolbar {
  margin-bottom: 16px;
  overflow-x: auto;
}

.ticket-title {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;

  strong {
    color: #24364d;
    line-height: 1.4;
  }

  span {
    color: #7a8797;
    font-size: 12px;
  }
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;

  h3 {
    margin: 0;
    color: #24364d;
    font-size: 20px;
    line-height: 1.4;
  }
}

.detail-meta {
  display: flex;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #687789;
  font-size: 13px;
}

.customer-box {
  margin-bottom: 18px;
}

.customer-box-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;

  strong {
    color: #24364d;
  }
}

.ticket-actions {
  :deep(.ant-btn-link) {
    height: 24px;
    padding: 0;
  }
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;

  &.admin .message-content {
    background: #f5fbf6;
    border-color: #dff1df;
  }
}

.message-content {
  min-width: 0;
  flex: 1;
  padding: 12px 14px;
  border: 1px solid #edf0f4;
  border-radius: 8px;
  background: #fff;

  p {
    margin: 8px 0 0;
    color: #35445a;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.message-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #6f7d90;
  font-size: 13px;

  strong {
    color: #24364d;
  }
}

.reply-box {
  display: flex;
  margin-top: 22px;
  flex-direction: column;
  gap: 12px;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.mobile-ticket-list {
  :deep(.ant-list-items) {
    display: grid;
    gap: 10px;
  }
}

.mobile-ticket-card {
  align-items: flex-start !important;
  padding: 12px !important;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #fff;
}

.mobile-ticket-main {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 5px;

  strong,
  span,
  p {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  strong {
    color: #24364d;
    line-height: 1.35;
  }

  > span {
    color: #7a8797;
    font-size: 12px;
    white-space: nowrap;
  }

  p {
    margin: 0;
    color: #526071;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
}

.mobile-ticket-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  color: #8b98a8;
  font-size: 12px;

  :deep(.ant-tag) {
    margin-inline-end: 0;
  }
}

@media only screen and (max-width: 640px) {
  .ticket-manage {
    padding: 0;
  }

  .detail-head {
    flex-direction: column;
  }

  .reply-actions {
    flex-direction: column;

    .ant-btn {
      width: 100%;
    }
  }

  :deep(.ant-card-head) {
    min-height: 50px;
    padding: 0 12px;
  }

  :deep(.ant-card-body) {
    padding: 12px;
  }

  :deep(.ticket-manage-drawer .ant-drawer-content-wrapper) {
    width: 100vw !important;
    max-width: 100vw;
  }

  :deep(.ticket-manage-drawer .ant-drawer-content) {
    height: 100dvh;
    max-height: 100dvh;
  }

  :deep(.ticket-manage-drawer .ant-drawer-body) {
    padding: 16px 12px max(18px, env(safe-area-inset-bottom));
  }

  .customer-box-head,
  .message-top {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

<style lang="less">
@media only screen and (max-width: 640px) {
  .ticket-manage-drawer .ant-drawer-content-wrapper {
    width: 100vw !important;
    max-width: 100vw;
  }

  .ticket-manage-drawer .ant-drawer-content {
    height: 100dvh;
    max-height: 100dvh;
  }

  .ticket-manage-drawer .ant-drawer-body {
    padding: 16px 12px max(18px, env(safe-area-inset-bottom));
  }
}
</style>
