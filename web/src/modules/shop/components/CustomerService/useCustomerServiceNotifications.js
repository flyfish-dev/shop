import { computed, ref, watch } from 'vue';
import {
  browserNotificationPermission,
  requestBrowserNotificationPermission,
  showBrowserNotification
} from '@/utils/browserNotifications.js';
import { customerDisplayName } from './customerDisplay.js';
import { useI18n } from 'vue-i18n';

const EMPTY_LIST = [];

export function useCustomerServiceNotifications(options) {
  const { locale, t } = useI18n();
  const permission = ref(browserNotificationPermission());
  const initialized = ref(false);
  const previousConversationUnread = new Map();
  const previousTicketUnread = new Map();
  const userId = computed(() => options.userId?.value || null);
  const conversations = computed(() => options.conversations?.value || EMPTY_LIST);
  const ticketReminders = computed(() => options.ticketReminders?.value || EMPTY_LIST);

  const requestPermission = async () => {
    permission.value = await requestBrowserNotificationPermission();
    return permission.value;
  };

  const refreshPermission = () => {
    permission.value = browserNotificationPermission();
  };

  const updateBaselines = () => {
    replaceUnreadMap(previousConversationUnread, conversations.value, item => item.id);
    replaceUnreadMap(previousTicketUnread, ticketReminders.value, item => item.ticketNo || item.id);
  };

  watch(userId, () => {
    initialized.value = false;
    previousConversationUnread.clear();
    previousTicketUnread.clear();
  });

  watch(
    [conversations, ticketReminders],
    () => {
      refreshPermission();
      if (!initialized.value) {
        initialized.value = true;
        updateBaselines();
        return;
      }
      notifyCustomerMessage();
      notifyTicketReply();
      updateBaselines();
    },
    { deep: true }
  );

  const notifyCustomerMessage = () => {
    const changed = conversations.value
      .filter(item => unread(item) > previousUnread(previousConversationUnread, item.id))
      .sort((left, right) => recentTime(right) - recentTime(left))[0];
    if (!changed || shouldSkipOpenedConversation(changed)) {
      return;
    }

    const manager = Boolean(options.manager?.value);
    showBrowserNotification({
      title: manager ? t('customerService.newCustomerMessage') : t('customerService.supportReplied'),
      body: notificationBody(
        customerDisplayName(changed, locale.value),
        changed.lastMessage || t('customerService.newSupportMessage'),
        locale.value
      ),
      tag: `flyfish-customer-${changed.id}`,
      onClick: () => options.openChat?.({ conversationId: changed.id })
    });
  };

  const notifyTicketReply = () => {
    const changed = ticketReminders.value
      .filter(item => unread(item) > previousUnread(previousTicketUnread, item.ticketNo || item.id))
      .sort((left, right) => recentTime(right) - recentTime(left))[0];
    if (!changed) {
      return;
    }

    showBrowserNotification({
      title: Boolean(options.manager?.value)
        ? t('customerService.newTicketTitle')
        : t('customerService.ticketReplied'),
      body: notificationBody(
        changed.title || changed.ticketNo || t('customerService.ticket'),
        changed.lastMessage || t('customerService.newTicketMessage'),
        locale.value
      ),
      tag: `flyfish-ticket-${changed.ticketNo || changed.id}`,
      onClick: () => options.openTickets?.()
    });
  };

  const shouldSkipOpenedConversation = conversation => {
    if (!options.chatOpen?.value || options.selectedConversation?.value?.id !== conversation.id) {
      return false;
    }
    return typeof document !== 'undefined' && document.hasFocus();
  };

  return {
    notificationPermission: permission,
    requestPermission
  };
}

const replaceUnreadMap = (target, items, keyOf) => {
  target.clear();
  items.forEach(item => {
    const key = keyOf(item);
    if (key !== null && key !== undefined) {
      target.set(String(key), unread(item));
    }
  });
};

const previousUnread = (source, key) => source.get(String(key)) || 0;

const unread = item => Number(item?.unreadCount || 0);

const recentTime = item => {
  const value = item?.lastMessageTime || item?.lastInboundTime || item?.updateTime || item?.createTime || '';
  const time = Date.parse(String(value).replace(/-/g, '/'));
  return Number.isFinite(time) ? time : 0;
};

const notificationBody = (title, message, locale) => {
  const prefix = String(title || '').trim();
  const content = String(message || '').replace(/\s+/g, ' ').trim();
  if (!prefix) {
    return content;
  }
  if (!content) {
    return prefix;
  }
  return String(locale || '').startsWith('en') ? `${prefix}: ${content}` : `${prefix}：${content}`;
};
