import { computed, onBeforeUnmount, ref } from 'vue';
import { message } from 'ant-design-vue';
import useClientStore from '@/modules/auth/store/client.js';

const SOCKET_OPEN = 1;
const SOCKET_CONNECTING = 0;
const RECONNECT_DELAY = 1800;
const COMMAND_QUEUE_LIMIT = 30;

export function useCustomerServiceChat() {
  const store = useClientStore();
  const noticeOpen = ref(false);
  const chatOpen = ref(false);
  const loading = ref(false);
  const sending = ref(false);
  const connected = ref(false);
  const summary = ref(null);
  const conversations = ref([]);
  const detail = ref(null);
  const input = ref('');
  const messageType = ref('text');
  const attachments = ref([]);
  const uploadingAttachments = ref(false);
  const keyword = ref('');
  const pendingContext = ref({});

  let socket = null;
  let reconnectTimer = null;
  let manualClose = false;
  const commandQueue = [];

  const manager = computed(() => Boolean(summary.value?.manager));
  const unreadCount = computed(() => Number(summary.value?.unreadCount || 0));
  const customerMessageUnreadCount = computed(() => Number(summary.value?.customerMessageUnreadCount || 0));
  const ticketUnreadCount = computed(() => Number(summary.value?.ticketUnreadCount || 0));
  const ticketReminders = computed(() => summary.value?.ticketReminders || []);
  const notificationConversations = computed(() => manager.value
    ? conversations.value
    : summary.value?.conversations || []);
  const selectedConversation = computed(() => detail.value?.conversation || null);
  const messages = computed(() => detail.value?.messages || []);
  const sortedConversations = computed(() => [...conversations.value].sort((left, right) => {
    const leftTime = conversationTime(left);
    const rightTime = conversationTime(right);
    if (leftTime !== rightTime) {
      return rightTime - leftTime;
    }
    return Number(right.id || 0) - Number(left.id || 0);
  }));
  const unreadConversations = computed(() => sortedConversations.value
    .filter(item => Number(item.unreadCount || 0) > 0));
  const filteredConversations = computed(() => {
    const text = keyword.value.trim().toLowerCase();
    if (!text) {
      return sortedConversations.value;
    }
    return sortedConversations.value.filter(item => [
      item.displayName,
      item.lastMessage,
      item.wechatOpenid
    ].some(value => String(value || '').toLowerCase().includes(text)));
  });

  const reset = () => {
    noticeOpen.value = false;
    chatOpen.value = false;
    loading.value = false;
    sending.value = false;
    connected.value = false;
    summary.value = null;
    conversations.value = [];
    detail.value = null;
    input.value = '';
    messageType.value = 'text';
    attachments.value = [];
    uploadingAttachments.value = false;
    keyword.value = '';
    pendingContext.value = {};
    commandQueue.length = 0;
  };

  const socketUrl = () => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/portal/customer-service/ws?access_token=${encodeURIComponent(store.token)}`;
  };

  const conversationTime = conversation => {
    const value = conversation?.lastMessageTime || conversation?.lastInboundTime || conversation?.updateTime || '';
    const time = Date.parse(String(value).replace(/-/g, '/'));
    return Number.isFinite(time) ? time : 0;
  };

  const flushQueue = () => {
    while (socket?.readyState === SOCKET_OPEN && commandQueue.length) {
      sendRaw(commandQueue.shift());
    }
  };

  const enqueueCommand = command => {
    if (command.type === 'SYNC' && commandQueue.some(item => item.type === 'SYNC')) {
      return;
    }
    if (commandQueue.length >= COMMAND_QUEUE_LIMIT) {
      commandQueue.shift();
    }
    commandQueue.push(command);
  };

  const sendRaw = command => {
    try {
      socket?.send(JSON.stringify(command));
      return true;
    } catch (e) {
      return false;
    }
  };

  const applyState = payload => {
    summary.value = payload.summary || summary.value;
    conversations.value = payload.conversations || conversations.value;
    if (Object.prototype.hasOwnProperty.call(payload, 'detail')) {
      detail.value = payload.detail || null;
    }
  };

  const scheduleReconnect = () => {
    window.clearTimeout(reconnectTimer);
    if (manualClose || !store.token || !store.user?.id) {
      return;
    }
    reconnectTimer = window.setTimeout(connect, RECONNECT_DELAY);
  };

  const connect = () => {
    if (!store.token || !store.user?.id) {
      disconnect();
      return;
    }
    if (socket && (socket.readyState === SOCKET_OPEN || socket.readyState === SOCKET_CONNECTING)) {
      return;
    }
    manualClose = false;
    socket = new WebSocket(socketUrl());
    socket.addEventListener('open', () => {
      connected.value = true;
      sendCommand({ type: 'SYNC' });
      flushQueue();
    });
    socket.addEventListener('message', event => {
      try {
        const payload = JSON.parse(event.data);
        if (payload.type === 'STATE') {
          applyState(payload);
          return;
        }
        if (payload.type === 'ERROR') {
          message.warning(payload.message || '消息处理失败');
        }
      } catch (e) {
        message.warning('消息解析失败');
      } finally {
        loading.value = false;
        sending.value = false;
      }
    });
    socket.addEventListener('close', () => {
      connected.value = false;
      socket = null;
      scheduleReconnect();
    });
    socket.addEventListener('error', () => {
      connected.value = false;
    });
  };

  const disconnect = () => {
    manualClose = true;
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
    if (socket) {
      if (socket.readyState === SOCKET_OPEN) {
        sendRaw({ type: 'CLOSE' });
      }
      socket.close();
      socket = null;
    }
    reset();
  };

  const sendCommand = command => {
    if (!store.token || !store.user?.id) {
      return;
    }
    if (!socket || socket.readyState !== SOCKET_OPEN) {
      enqueueCommand(command);
      connect();
      return;
    }
    if (!sendRaw(command)) {
      enqueueCommand(command);
      socket?.close();
    }
  };

  const sync = () => {
    sendCommand({ type: 'SYNC' });
  };

  const openNotice = () => {
    noticeOpen.value = true;
    sync();
  };

  const closeNotice = () => {
    noticeOpen.value = false;
  };

  const openChat = payload => {
    chatOpen.value = true;
    loading.value = true;
    pendingContext.value = payload || {};
    const command = { type: 'OPEN' };
    if (payload?.conversationId) {
      command.conversationId = payload.conversationId;
    }
    if (payload?.userId) {
      command.userId = payload.userId;
    }
    if (!payload?.conversationId && !payload?.userId && manager.value && sortedConversations.value.length) {
      command.conversationId = unreadConversations.value[0]?.id || sortedConversations.value[0].id;
    }
    sendCommand(command);
  };

  const closeChat = () => {
    chatOpen.value = false;
    detail.value = null;
    sendCommand({ type: 'CLOSE' });
  };

  const refresh = () => {
    loading.value = true;
    sync();
  };

  const selectConversation = conversation => {
    if (!conversation?.id) {
      return;
    }
    loading.value = true;
    sendCommand({ type: 'OPEN', conversationId: conversation.id });
  };

  const send = () => {
    const content = input.value.trim();
    const files = attachments.value || [];
    if ((!content && !files.length) || sending.value || uploadingAttachments.value) {
      return;
    }
    sending.value = true;
    const command = {
      type: 'SEND',
      conversationId: selectedConversation.value?.id,
      content,
      messageType: messageType.value,
      attachments: files,
      relatedType: pendingContext.value?.relatedType,
      relatedNo: pendingContext.value?.relatedNo
    };
    sendCommand(command);
    input.value = '';
    messageType.value = 'text';
    attachments.value = [];
  };

  const markRead = () => {
    sendCommand({
      type: 'READ',
      conversationId: selectedConversation.value?.id
    });
  };

  const isMineMessage = item => {
    if (!item) {
      return false;
    }
    return manager.value ? item.direction === 'OUTBOUND' : item.direction === 'INBOUND';
  };

  onBeforeUnmount(disconnect);

  return {
    noticeOpen,
    chatOpen,
    loading,
    sending,
    connected,
    summary,
    conversations,
    notificationConversations,
    filteredConversations,
    unreadConversations,
    detail,
    input,
    messageType,
    attachments,
    uploadingAttachments,
    keyword,
    manager,
    unreadCount,
    customerMessageUnreadCount,
    ticketUnreadCount,
    ticketReminders,
    selectedConversation,
    messages,
    connect,
    disconnect,
    sync,
    openNotice,
    closeNotice,
    openChat,
    closeChat,
    refresh,
    selectConversation,
    send,
    markRead,
    isMineMessage
  };
}
