<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { BellOutlined, CustomerServiceOutlined } from '@ant-design/icons-vue';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import { useRouter } from '@/router/use.js';
import { listenCustomerServiceOpen } from './customerServiceBus.js';
import CustomerChatDrawer from './CustomerChatDrawer.vue';
import CustomerNotificationDrawer from './CustomerNotificationDrawer.vue';
import { useCustomerServiceChat } from './useCustomerServiceChat.js';
import { useCustomerServiceNotifications } from './useCustomerServiceNotifications.js';

const store = useClientStore();
const router = useRouter();
const { user, token, width } = storeToRefs(store);

const {
  noticeOpen,
  chatOpen,
  loading,
  sending,
  connected,
  notificationConversations,
  filteredConversations,
  input,
  messageType,
  keyword,
  manager,
  customerMessageUnreadCount,
  ticketUnreadCount,
  ticketReminders,
  selectedConversation,
  messages,
  attachments,
  uploadingAttachments,
  connect,
  disconnect,
  openNotice,
  openChat,
  closeChat,
  refresh,
  selectConversation,
  send
} = useCustomerServiceChat();

let removeOpenListener;

const visible = computed(() => Boolean(user.value?.id));
const drawerWidth = computed(() => {
  if (width.value < 720) {
    return '100%';
  }
  return manager.value ? 820 : 430;
});

const goTickets = () => {
  router.push(manager.value ? '/shop/manage/tickets' : '/account/tickets');
};

const { requestPermission: requestNotificationPermission } = useCustomerServiceNotifications({
  userId: computed(() => user.value?.id),
  manager,
  conversations: notificationConversations,
  chatOpen,
  selectedConversation,
  ticketReminders,
  openChat,
  openTickets: goTickets
});

const handleOpenNotice = () => {
  requestNotificationPermission();
  openNotice();
};

const handleOpenChat = payload => {
  requestNotificationPermission();
  openChat(payload);
};

const updateAttachmentUploading = value => {
  uploadingAttachments.value = value;
};

watch([() => user.value?.id, () => token.value], ([id, nextToken]) => {
  if (id && nextToken) {
    connect();
    return;
  }
  disconnect();
}, { immediate: true });

onMounted(() => {
  removeOpenListener = listenCustomerServiceOpen(openChat);
});

onBeforeUnmount(() => {
  removeOpenListener?.();
  disconnect();
});
</script>

<template>
  <div v-if='visible' class='customer-service-widget'>
    <button type='button' class='customer-bell' aria-label='消息中心' @click='handleOpenNotice'>
      <a-badge :count='ticketUnreadCount' :overflow-count='99' size='small'>
        <bell-outlined />
      </a-badge>
    </button>

    <a-badge class='customer-float-badge' :count='customerMessageUnreadCount' :overflow-count='99' size='small'>
      <button type='button' class='customer-float' aria-label='打开客服聊天' @click='handleOpenChat()'>
        <customer-service-outlined />
        <span class='customer-float-text'>客服</span>
      </button>
    </a-badge>

    <customer-notification-drawer
      v-model:open='noticeOpen'
      :ticket-unread-count='ticketUnreadCount'
      :tickets='ticketReminders'
      @open-tickets='goTickets'
    />

    <customer-chat-drawer
      v-model:open='chatOpen'
      v-model:input='input'
      v-model:message-type='messageType'
      v-model:attachments='attachments'
      v-model:keyword='keyword'
      :width='drawerWidth'
      :loading='loading'
      :sending='sending'
      :uploading='uploadingAttachments'
      :connected='connected'
      :manager='manager'
      :conversations='filteredConversations'
      :selected-conversation='selectedConversation'
      :messages='messages'
      @refresh='refresh'
      @select='selectConversation'
      @uploading-change='updateAttachmentUploading'
      @send='send'
      @close='closeChat'
    />
  </div>
</template>

<style scoped lang='less'>
.customer-bell,
.customer-float-badge {
  position: fixed;
  z-index: 120;
}

.customer-bell,
.customer-float {
  border: 0;
  cursor: pointer;
}

.customer-bell {
  top: 86px;
  right: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .96);
  color: #1677ff;
  font-size: 18px;
  box-shadow: 0 10px 26px rgba(24, 43, 70, .12);
}

.customer-float-badge {
  right: 22px;
  bottom: 118px;
}

.customer-float {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 42px;
  padding: 0 14px;
  border-radius: 999px;
  background: linear-gradient(135deg, #33a204, #1677ff);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 16px 34px rgba(38, 125, 74, .24);

  :deep(.anticon) {
    display: inline-flex;
    font-size: 18px;
    line-height: 1;
  }
}

@media only screen and (max-width: 640px) {
  .customer-bell {
    top: 74px;
    right: 12px;
  }

  .customer-float-badge {
    right: 12px;
    bottom: 84px;
  }

  .customer-float {
    width: 42px;
    justify-content: center;
    padding: 0;

    .customer-float-text {
      display: none;
    }
  }
}
</style>
