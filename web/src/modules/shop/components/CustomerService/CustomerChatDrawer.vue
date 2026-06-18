<script setup>
import { computed, ref, watch } from 'vue';
import {
  CustomerServiceOutlined,
  MessageOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import CustomerConversationList from './CustomerConversationList.vue';
import CustomerMessageComposer from './CustomerMessageComposer.vue';
import CustomerMessageList from './CustomerMessageList.vue';
import CustomerServiceContactAlert from './CustomerServiceContactAlert.vue';
import CustomerWechatActivityList from './CustomerWechatActivityList.vue';
import { customerDisplayName } from './customerDisplay.js';

const props = defineProps({
  open: Boolean,
  width: [String, Number],
  loading: Boolean,
  sending: Boolean,
  connected: Boolean,
  manager: Boolean,
  conversations: {
    type: Array,
    default: () => []
  },
  selectedConversation: Object,
  messages: {
    type: Array,
    default: () => []
  },
  wechatActivities: {
    type: Array,
    default: () => []
  },
  activityLoading: Boolean,
  activityKeyword: String,
  activityType: String,
  hasActivityFilter: Boolean,
  input: String,
  messageType: String,
  attachments: {
    type: Array,
    default: () => []
  },
  uploading: Boolean,
  keyword: String
});

const emit = defineEmits([
  'update:open',
  'update:input',
  'update:messageType',
  'update:attachments',
  'update:keyword',
  'update:activityKeyword',
  'update:activityType',
  'refresh',
  'refreshActivities',
  'resetActivityFilters',
  'select',
  'uploading-change',
  'send',
  'close'
]);

const drawerTitle = computed(() => props.manager ? '客户消息' : '飞鱼小铺客服');
const managerPanel = ref('chat');
const managerPanelOptions = [
  { label: '客户会话', value: 'chat' },
  { label: '公众号动态', value: 'wechat' }
];
const activeName = computed(() => {
  if (props.manager) {
    return props.selectedConversation ? customerDisplayName(props.selectedConversation) : '选择客户会话';
  }
  return '飞鱼小铺客服';
});
const activeDescription = computed(() => {
  if (props.manager) {
    return props.selectedConversation ? '站内客服会话' : '左侧选择客户后即可回复';
  }
  return '客服会话';
});
const loaded = computed(() => props.manager ? props.conversations.length > 0 : props.messages.length > 0);
const transportStatus = computed(() => {
  if (props.connected) {
    return { text: '在线', color: 'green' };
  }
  if (loaded.value) {
    return { text: '已加载', color: 'blue' };
  }
  return { text: '连接中', color: 'default' };
});

const close = value => {
  emit('update:open', value);
  if (!value) {
    emit('close');
  }
};

const handleManagerPanelChange = key => {
  managerPanel.value = key;
  if (key === 'wechat') {
    emit('refreshActivities');
  }
};

watch(() => props.open, open => {
  if (open && props.manager && managerPanel.value === 'wechat') {
    emit('refreshActivities');
  }
});
</script>

<template>
  <a-drawer
    :open='open'
    class='customer-chat-drawer'
    :width='width'
    placement='right'
    :body-style='{ padding: 0 }'
    @update:open='close'
  >
    <template #title>
      <span class='drawer-title'>
        <message-outlined v-if='manager' />
        <customer-service-outlined v-else />
        <span>{{ drawerTitle }}</span>
        <a-tag :color='transportStatus.color'>{{ transportStatus.text }}</a-tag>
      </span>
    </template>

    <template #extra>
      <a-radio-group
        v-if='manager'
        :value='managerPanel'
        size='small'
        button-style='solid'
        class='drawer-panel-switch'
        @change='event => handleManagerPanelChange(event.target.value)'
      >
        <a-radio-button
          v-for='item in managerPanelOptions'
          :key='item.value'
          :value='item.value'
        >
          {{ item.label }}
        </a-radio-button>
      </a-radio-group>
    </template>

    <a-spin :spinning='loading'>
      <div v-if='manager' class='manager-shell'>
        <div v-show='managerPanel === "chat"' class='chat-workspace manager'>
          <customer-conversation-list
            :keyword='keyword'
            :conversations='conversations'
            :selected-conversation='selectedConversation'
            @update:keyword='value => emit("update:keyword", value)'
            @refresh='emit("refresh")'
            @select='conversation => emit("select", conversation)'
          />

          <section class='chat-main'>
            <header class='chat-header'>
              <a-avatar v-if='selectedConversation?.avatar' :src='selectedConversation.avatar' :size='42' />
              <a-avatar v-else :size='42'><user-outlined /></a-avatar>
              <div class='chat-header-main'>
                <strong>{{ activeName }}</strong>
                <span>{{ activeDescription }}</span>
              </div>
            </header>

            <template v-if='selectedConversation'>
              <div class='chat-message-area'>
                <customer-message-list
                  :manager='manager'
                  :conversation='selectedConversation'
                  :messages='messages'
                />
              </div>

              <customer-message-composer
                :input='input'
                :message-type='messageType'
                :attachments='attachments'
                :sending='sending'
                :uploading='uploading'
                @update:input='value => emit("update:input", value)'
                @update:message-type='value => emit("update:messageType", value)'
                @update:attachments='value => emit("update:attachments", value)'
                @uploading-change='value => emit("uploading-change", value)'
                @send='emit("send")'
              />
            </template>

            <div v-else class='manager-empty'>
              <a-empty description='暂无选中会话' />
            </div>
          </section>
        </div>

        <customer-wechat-activity-list
          v-show='managerPanel === "wechat"'
          :activities='wechatActivities'
          :loading='activityLoading'
          :keyword='activityKeyword'
          :activity-type='activityType'
          :has-filter='hasActivityFilter'
          @refresh='emit("refreshActivities")'
          @reset='emit("resetActivityFilters")'
          @update:keyword='value => emit("update:activityKeyword", value)'
          @update:activity-type='value => emit("update:activityType", value)'
        />
      </div>

      <div v-else class='chat-workspace'>
        <section class='chat-main'>
          <header class='chat-header'>
            <a-avatar :size='42'><customer-service-outlined /></a-avatar>
            <div class='chat-header-main'>
              <strong>{{ activeName }}</strong>
              <span>{{ activeDescription }}</span>
            </div>
          </header>

          <div class='chat-message-area'>
            <customer-service-contact-alert compact class='chat-contact-alert' />
            <customer-message-list
              :manager='manager'
              :conversation='selectedConversation'
              :messages='messages'
            />
          </div>

          <customer-message-composer
            :input='input'
            :message-type='messageType'
            :attachments='attachments'
            :sending='sending'
            :uploading='uploading'
            @update:input='value => emit("update:input", value)'
            @update:message-type='value => emit("update:messageType", value)'
            @update:attachments='value => emit("update:attachments", value)'
            @uploading-change='value => emit("uploading-change", value)'
            @send='emit("send")'
          />
        </section>
      </div>
    </a-spin>
  </a-drawer>
</template>

<style scoped lang='less'>
.drawer-title {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  color: #203626;

  > span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.ant-tag) {
    flex: none;
    margin-inline: 4px 0;
  }
}

.drawer-panel-switch {
  flex: none;
  white-space: nowrap;

  :deep(.ant-radio-button-wrapper) {
    height: 28px;
    padding: 0 12px;
    line-height: 26px;
    font-weight: 700;
  }

  :deep(.ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled)) {
    border-color: #33a204;
    background: #33a204;
    color: #fff;
  }
}

.customer-chat-drawer {
  :deep(.ant-drawer-content-wrapper) {
    height: 100vh;
    max-height: 100vh;
  }

  :deep(.ant-drawer-content) {
    height: 100vh;
    max-height: 100vh;
  }

  :deep(.ant-drawer-wrapper-body) {
    display: flex;
    min-height: 0;
    height: 100%;
    flex-direction: column;
    overflow: hidden;
  }

  :deep(.ant-drawer-header) {
    flex: none;
    min-height: 56px;
    padding: 10px 18px;
  }

  :deep(.ant-drawer-header-title),
  :deep(.ant-drawer-title) {
    min-width: 0;
  }

  :deep(.ant-drawer-title) {
    display: flex;
    align-items: center;
  }

  :deep(.ant-drawer-extra) {
    margin-inline-start: 12px;
  }

  :deep(.ant-drawer-body) {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    background: #f7f9fb;
  }

  :deep(.ant-spin-nested-loading),
  :deep(.ant-spin-container) {
    min-height: 0;
    height: 100%;
  }
}

.manager-shell {
  height: 100%;
  min-height: 0;
}

.chat-workspace {
  display: grid;
  height: 100%;
  min-height: 0;
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  background: #f7f9fb;

  &.manager {
    grid-template-columns: 286px minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);

    > * {
      min-height: 0;
    }

    .chat-message-area {
      grid-template-rows: minmax(0, 1fr);
    }
  }
}

.chat-main {
  display: grid;
  height: 100%;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.chat-message-area {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  background: #f7f9fb;
}

.chat-contact-alert {
  margin: 10px 12px 0;
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 14px 16px;
  border-bottom: 1px solid #edf1f5;
  background: rgba(255, 255, 255, .96);
  text-align: left;
}

.chat-header-main {
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
    color: #203626;
    font-size: 15px;
  }

  span {
    color: #7b8794;
    font-size: 12px;
  }
}

.manager-empty {
  display: grid;
  min-height: 360px;
  place-items: center;
}

@media only screen and (max-width: 720px) {
  .drawer-title {
    gap: 8px;
  }

  .customer-chat-drawer {
    :deep(.ant-drawer-content-wrapper) {
      width: 100vw !important;
      max-width: 100vw;
    }

    :deep(.ant-drawer-content) {
      height: 100dvh;
      max-height: 100dvh;
    }

    :deep(.ant-drawer-header) {
      align-items: flex-start;
      flex-wrap: wrap;
      padding: 10px 12px;
    }

    :deep(.ant-drawer-extra) {
      width: 100%;
      margin-inline-start: 0;
    }
  }

  .drawer-panel-switch {
    display: flex;
    width: 100%;

    :deep(.ant-radio-button-wrapper) {
      flex: 1;
      padding: 0 8px;
      font-size: 12px;
      text-align: center;
    }
  }

  .chat-workspace {
    &.manager {
      grid-template-columns: 1fr;
      grid-template-rows: minmax(168px, 34dvh) minmax(0, 1fr);
    }
  }

  .chat-header {
    padding: 10px 12px;
  }

  .chat-contact-alert {
    margin: 8px 10px 0;
  }
}
</style>

<style lang='less'>
/*
 * Ant Design Vue 的 Drawer 默认通过 Portal 挂到 body 下，scoped 选择器无法稳定命中
 * 其内部骨架。这里用组件专属 class 做全局定向约束，保证客服窗口始终是：
 * 顶部标题固定、中间区域内部滚动、底部输入框固定可见。
 */
.customer-chat-drawer .ant-drawer-content,
.customer-chat-drawer.ant-drawer-content {
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
}

.customer-chat-drawer .ant-drawer-wrapper-body {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.customer-chat-drawer .ant-drawer-header {
  flex: none;
  min-height: 56px;
  padding: 10px 18px;
}

.customer-chat-drawer .ant-drawer-header-title,
.customer-chat-drawer .ant-drawer-title {
  min-width: 0;
}

.customer-chat-drawer .ant-drawer-title {
  display: flex;
  align-items: center;
  flex: 1 1 auto;
}

.customer-chat-drawer .ant-drawer-extra {
  flex: none;
  margin-inline-start: 12px;
}

.customer-chat-drawer .ant-drawer-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  background: #f7f9fb;
}

.customer-chat-drawer .ant-spin-nested-loading,
.customer-chat-drawer .ant-spin-container,
.customer-chat-drawer .manager-shell,
.customer-chat-drawer .chat-workspace {
  height: 100%;
  min-height: 0;
}

.customer-chat-drawer .chat-workspace {
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
}

.customer-chat-drawer .chat-workspace.manager {
  grid-template-rows: minmax(0, 1fr);
}

.customer-chat-drawer .chat-workspace.manager > * {
  min-height: 0;
}

.customer-chat-drawer .chat-main {
  height: 100%;
  overflow: hidden;
}

.customer-chat-drawer .chat-workspace.manager .chat-message-area {
  grid-template-rows: minmax(0, 1fr);
}

.customer-chat-drawer .drawer-panel-switch .ant-radio-button-wrapper {
  height: 28px;
  padding: 0 12px;
  line-height: 26px;
  font-weight: 700;
}

.customer-chat-drawer .drawer-panel-switch .ant-radio-button-wrapper-checked:not(.ant-radio-button-wrapper-disabled) {
  border-color: #33a204;
  background: #33a204;
  color: #fff;
}

@media only screen and (max-width: 720px) {
  .customer-chat-drawer .ant-drawer-content-wrapper {
    width: 100vw !important;
    max-width: 100vw;
  }

  .customer-chat-drawer .ant-drawer-content {
    height: 100dvh;
    max-height: 100dvh;
  }

  .customer-chat-drawer .ant-drawer-header {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 10px 12px;
  }

  .customer-chat-drawer .ant-drawer-extra {
    width: 100%;
    margin-inline-start: 0;
  }

  .customer-chat-drawer .drawer-panel-switch {
    display: flex;
    width: 100%;
  }

  .customer-chat-drawer .drawer-panel-switch .ant-radio-button-wrapper {
    flex: 1;
    padding: 0 8px;
    font-size: 12px;
    text-align: center;
  }

  .customer-chat-drawer .chat-workspace.manager {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(168px, 34dvh) minmax(0, 1fr);
  }

  .customer-chat-drawer .chat-header {
    padding: 10px 12px;
  }

  .customer-chat-drawer .customer-composer {
    padding-bottom: max(12px, env(safe-area-inset-bottom));
  }
}
</style>
