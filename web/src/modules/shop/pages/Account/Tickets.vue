<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  FileTextOutlined,
  MessageOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined
} from '@ant-design/icons-vue';
import AttachmentList from '@/components/Attachments/AttachmentList.vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import CustomerServiceContactAlert from '@/modules/shop/components/CustomerService/CustomerServiceContactAlert.vue';
import { openCustomerService } from '@/modules/shop/components/CustomerService/customerServiceBus.js';
import { useRoute, useRouter } from '@/router/use.js';
import {
  TICKET_CATEGORY_OPTIONS,
  TICKET_PRIORITY_OPTIONS,
  TICKET_STATUS_OPTIONS,
  isTicketDone,
  ticketCategoryText,
  ticketPriorityColor,
  ticketPriorityText,
  ticketStatusColor,
  ticketStatusText
} from '@/modules/shop/utils/supportTickets.js';
import { createTicket, getTicket, getTickets, replyTicket } from '@/modules/shop/pages/Support/apis.js';
import { sortTicketsByNewest } from '@/modules/shop/utils/ticketSort.js';

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const tickets = ref([]);
const status = ref('');
const createOpen = ref(false);
const detailOpen = ref(false);
const saving = ref(false);
const detailLoading = ref(false);
const replyLoading = ref(false);
const createUploading = ref(false);
const replyUploading = ref(false);
const selected = ref(null);
const createFormRef = ref(null);
const replyContent = ref('');
const createAttachments = ref([]);
const replyAttachments = ref([]);
const formState = reactive({
  title: '',
  category: 'GENERAL',
  priority: 'NORMAL',
  contact: '',
  content: ''
});

const activeTickets = computed(() => tickets.value.filter(ticket => !isTicketDone(ticket.status)).length);
const resolvedTickets = computed(() => tickets.value.filter(ticket => ticket.status === 'RESOLVED').length);

const resetForm = () => {
  formState.title = '';
  formState.category = 'GENERAL';
  formState.priority = 'NORMAL';
  formState.contact = '';
  formState.content = '';
  createAttachments.value = [];
};

const loadTickets = async () => {
  loading.value = true;
  try {
    tickets.value = sortTicketsByNewest(await getTickets({ status: status.value }));
  } catch (e) {
    tickets.value = [];
    message.error(e.message || '工单加载失败');
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  resetForm();
  createOpen.value = true;
};

const closeCreate = () => {
  createOpen.value = false;
  if (route.query?.create) {
    router.replace('/account/tickets');
  }
};

const submitTicket = async () => {
  saving.value = true;
  try {
    await createFormRef.value?.validate();
    if (!formState.content.trim() && !createAttachments.value.length) {
      message.warning('请填写问题内容或上传附件');
      return;
    }
    selected.value = await createTicket({
      title: formState.title.trim(),
      category: formState.category,
      priority: formState.priority,
      contact: formState.contact.trim(),
      content: formState.content.trim(),
      attachments: createAttachments.value
    });
    message.success('工单已提交');
    createOpen.value = false;
    detailOpen.value = true;
    await loadTickets();
  } catch (e) {
    if (e?.errorFields) {
      message.warning('请先修正表单中的提示');
    } else {
      message.error(e.message || '提交失败');
    }
  } finally {
    saving.value = false;
  }
};

const openDetail = async ticket => {
  detailOpen.value = true;
  detailLoading.value = true;
  replyContent.value = '';
  replyAttachments.value = [];
  try {
    selected.value = await getTicket(ticket.ticketNo);
  } catch (e) {
    message.error(e.message || '工单详情加载失败');
  } finally {
    detailLoading.value = false;
  }
};

const contactService = ticket => {
  openCustomerService({
    relatedType: 'TICKET',
    relatedNo: ticket?.ticketNo
  });
};

const submitReply = async () => {
  const content = replyContent.value.trim();
  if ((!content && !replyAttachments.value.length) || !selected.value?.ticketNo || replyUploading.value) {
    return;
  }
  replyLoading.value = true;
  try {
    selected.value = await replyTicket(selected.value.ticketNo, {
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

watch(status, loadTickets);
watch(() => route.query?.create, value => {
  if (value) {
    openCreate();
  }
}, { immediate: true });

onMounted(loadTickets);
</script>

<template>
  <div class="tickets-page">
    <header class="tickets-header">
      <div>
        <p class="eyebrow">Support Center</p>
        <h2>我的工单</h2>
      </div>
      <a-space>
        <a-button @click="loadTickets">
          <template #icon><reload-outlined /></template>
          刷新
        </a-button>
        <a-button type="primary" @click="openCreate">
          <template #icon><plus-outlined /></template>
          提交工单
        </a-button>
      </a-space>
    </header>

    <div class="tickets-summary">
      <a-card :bordered="false">
        <a-statistic title="工单数" :value="tickets.length" />
      </a-card>
      <a-card :bordered="false">
        <a-statistic title="处理中" :value="activeTickets" />
      </a-card>
      <a-card :bordered="false">
        <a-statistic title="已解决" :value="resolvedTickets" />
      </a-card>
    </div>

    <a-card class="tickets-panel" :bordered="false">
      <div class="tickets-toolbar">
        <a-radio-group v-model:value="status" button-style="solid">
          <a-radio-button v-for="item in TICKET_STATUS_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-radio-button>
        </a-radio-group>
      </div>
      <a-spin :spinning="loading">
        <a-empty v-if="!tickets.length && !loading" description="暂无工单" />
        <a-list v-else class="tickets-list" :data-source="tickets" item-layout="vertical">
          <template #renderItem="{ item }">
            <a-list-item class="ticket-item" @click="openDetail(item)">
              <div class="ticket-row">
                <div class="ticket-icon">
                  <file-text-outlined />
                </div>
                <div class="ticket-main">
                  <div class="ticket-title">
                    <span>{{ item.title }}</span>
                    <a-space wrap>
                      <a-button type="link" size="small" @click.stop="contactService(item)">
                        <template #icon><message-outlined /></template>
                        联系客服
                      </a-button>
                      <a-tag :color="ticketStatusColor(item.status)">{{ ticketStatusText(item.status) }}</a-tag>
                      <a-tag :color="ticketPriorityColor(item.priority)">{{ ticketPriorityText(item.priority) }}</a-tag>
                    </a-space>
                  </div>
                  <div class="ticket-meta">
                    <span>{{ item.ticketNo }}</span>
                    <span>{{ ticketCategoryText(item.category) }}</span>
                    <span>{{ item.updateTime || item.createTime }}</span>
                  </div>
                  <p v-if="item.lastMessage" class="ticket-message">{{ item.lastMessage }}</p>
                </div>
              </div>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-card>

    <a-drawer
      v-model:open="createOpen"
      title="提交工单"
      width="520"
      :body-style="{ paddingBottom: '88px' }"
      @close="closeCreate"
    >
      <a-form ref="createFormRef" :model="formState" layout="vertical">
        <a-form-item label="标题" name="title" :rules="[{ required: true, message: '请输入工单标题' }]">
          <a-input v-model:value="formState.title" :maxlength="120" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="类型" name="category">
              <a-select v-model:value="formState.category" :options="TICKET_CATEGORY_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="优先级" name="priority">
              <a-select v-model:value="formState.priority" :options="TICKET_PRIORITY_OPTIONS" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="联系方式" name="contact">
          <a-input v-model:value="formState.contact" :maxlength="512" />
        </a-form-item>
        <a-form-item label="问题内容" name="content">
          <a-textarea v-model:value="formState.content" :maxlength="4096" :auto-size="{ minRows: 6, maxRows: 10 }" />
        </a-form-item>
        <a-form-item label="附件">
          <attachment-upload
            v-model:value="createAttachments"
            @uploading-change="value => createUploading = value"
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="closeCreate">取消</a-button>
          <a-button type="primary" :loading="saving || createUploading" @click="submitTicket">提交</a-button>
        </a-space>
      </template>
    </a-drawer>

    <a-drawer v-model:open="detailOpen" title="工单详情" width="620">
      <a-spin :spinning="detailLoading">
        <template v-if="selected">
          <div class="detail-head">
            <div>
              <h3>{{ selected.title }}</h3>
              <div class="ticket-meta">
                <span>{{ selected.ticketNo }}</span>
                <span>{{ ticketCategoryText(selected.category) }}</span>
                <span>{{ selected.createTime }}</span>
              </div>
            </div>
            <a-space wrap>
              <a-button type="primary" ghost size="small" @click="contactService(selected)">
                <template #icon><message-outlined /></template>
                微信客服
              </a-button>
              <a-tag :color="ticketStatusColor(selected.status)">{{ ticketStatusText(selected.status) }}</a-tag>
              <a-tag :color="ticketPriorityColor(selected.priority)">{{ ticketPriorityText(selected.priority) }}</a-tag>
            </a-space>
          </div>

          <customer-service-contact-alert compact class="ticket-contact-alert" />

          <div class="message-list">
            <div
              v-for="item in selected.messages"
              :key="item.id"
              class="message-item"
              :class="{ admin: item.senderRole === 'ADMIN' }"
            >
              <a-avatar v-if="item.senderAvatar" :src="item.senderAvatar" />
              <a-avatar v-else>
                <message-outlined />
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

          <div class="reply-box">
            <a-textarea v-model:value="replyContent" :auto-size="{ minRows: 4, maxRows: 8 }" />
            <attachment-upload
              v-model:value="replyAttachments"
              @uploading-change="value => replyUploading = value"
            />
            <a-button
              type="primary"
              :disabled="replyUploading || (!replyContent.trim() && !replyAttachments.length)"
              :loading="replyLoading || replyUploading"
              @click="submitReply"
            >
              <template #icon><send-outlined /></template>
              回复
            </a-button>
          </div>
        </template>
      </a-spin>
    </a-drawer>
  </div>
</template>

<style scoped lang="less">
.tickets-page {
  width: min(1120px, calc(100vw - 48px));
  margin: 0 auto 48px;
  text-align: left;
}

.tickets-header {
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
}

.eyebrow {
  margin: 0;
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.tickets-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;

  :deep(.ant-card) {
    border-radius: 8px;
    background: linear-gradient(180deg, #fff, #f8fbff);
  }
}

.tickets-panel {
  border-radius: 8px;
}

.tickets-toolbar {
  margin-bottom: 16px;
  overflow-x: auto;
}

.ticket-item {
  cursor: pointer;
  transition: background .18s ease;

  &:hover {
    background: #f8fbff;
  }
}

.ticket-row,
.message-item {
  display: flex;
  gap: 14px;
  min-width: 0;
}

.ticket-icon {
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

.ticket-main {
  min-width: 0;
  flex: 1;
}

.ticket-title,
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.ticket-title {
  align-items: center;

  > span {
    min-width: 0;
    overflow: hidden;
    color: #24364d;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.ticket-meta {
  display: flex;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #687789;
  font-size: 13px;
}

.ticket-message {
  margin: 10px 0 0;
  color: #47566a;
  line-height: 1.7;
}

.detail-head {
  align-items: flex-start;
  margin-bottom: 22px;
  padding-bottom: 18px;
  border-bottom: 1px solid #f0f0f0;

  h3 {
    margin: 0;
    color: #24364d;
    font-size: 20px;
    line-height: 1.4;
  }
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ticket-contact-alert {
  margin-bottom: 16px;
}

.message-item {
  align-items: flex-start;

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

  .ant-btn {
    align-self: flex-end;
  }
}

@media only screen and (max-width: 640px) {
  .tickets-page {
    width: calc(100vw - 24px);
    margin-bottom: 30px;
  }

  .tickets-header {
    align-items: stretch;
    flex-direction: column;
    padding-top: 18px;

    h2 {
      font-size: 24px;
    }
  }

  .tickets-summary {
    grid-template-columns: 1fr;
  }

  .ticket-title,
  .detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .reply-box .ant-btn {
    width: 100%;
  }
}
</style>
