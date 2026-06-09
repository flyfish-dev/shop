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
      <div class='drawer-title'>
        <message-outlined v-if='manager' />
        <customer-service-outlined v-else />
        <span>{{ drawerTitle }}</span>
        <a-tag :color='connected ? "green" : "default"'>{{ connected ? '在线' : '连接中' }}</a-tag>
      </div>
    </template>

    <a-spin :spinning='loading'>
      <div v-if='manager' class='manager-shell'>
        <a-tabs
          :active-key='managerPanel'
          class='manager-tabs'
          @change='handleManagerPanelChange'
        >
          <a-tab-pane key='chat' tab='客户会话'>
            <div class='chat-workspace manager'>
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
          </a-tab-pane>

          <a-tab-pane key='wechat' tab='公众号动态'>
            <customer-wechat-activity-list
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
          </a-tab-pane>
        </a-tabs>
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
  gap: 8px;
  color: #203626;

  :deep(.ant-tag) {
    margin-inline: 4px 0;
  }
}

.customer-chat-drawer {
  :deep(.ant-drawer-body) {
    overflow: hidden;
    background: #f7f9fb;
  }
}

.manager-shell,
.manager-tabs,
.manager-tabs :deep(.ant-tabs-content-holder),
.manager-tabs :deep(.ant-tabs-content),
.manager-tabs :deep(.ant-tabs-tabpane) {
  min-height: 0;
  height: 100%;
}

.manager-tabs {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);

  :deep(.ant-tabs-nav) {
    margin: 0;
    padding: 0 16px;
    background: rgba(255, 255, 255, .98);
  }

  :deep(.ant-tabs-content-holder) {
    min-height: 0;
  }
}

.chat-workspace {
  display: grid;
  height: calc(100vh - 55px);
  min-height: 520px;
  background: #f7f9fb;

  &.manager {
    grid-template-columns: 286px minmax(0, 1fr);
  }
}

.chat-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
  min-height: 0;
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
  .chat-workspace {
    height: calc(100vh - 55px);
    min-height: 0;

    &.manager {
      grid-template-columns: 1fr;
      grid-template-rows: 238px minmax(0, 1fr);
    }
  }
}
</style>
