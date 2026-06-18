<script setup>
import { ReloadOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue';
import { customerDisplayName } from './customerDisplay.js';

defineProps({
  conversations: {
    type: Array,
    default: () => []
  },
  selectedConversation: Object,
  keyword: String
});

const emit = defineEmits([
  'update:keyword',
  'refresh',
  'select'
]);
</script>

<template>
  <aside class='customer-conversations'>
    <div class='conversation-toolbar'>
      <span><team-outlined /> 客户会话</span>
      <a-button size='small' @click='emit("refresh")'>
        <template #icon><reload-outlined /></template>
      </a-button>
    </div>

    <a-input-search
      :value='keyword'
      size='small'
      allow-clear
      @update:value='value => emit("update:keyword", value)'
      @search='emit("refresh")'
    />

    <div class='conversation-list'>
      <button
        v-for='item in conversations'
        :key='item.id'
        type='button'
        class='conversation-item'
        :class='{ active: selectedConversation?.id === item.id, unread: Number(item.unreadCount || 0) > 0 }'
        @click='emit("select", item)'
      >
        <a-badge :count='item.unreadCount' :overflow-count='99' :offset='[-2, 31]' size='small'>
          <a-avatar v-if='item.avatar' :src='item.avatar' :size='38' />
          <a-avatar v-else :size='38'><user-outlined /></a-avatar>
        </a-badge>

        <span class='conversation-main'>
          <span class='conversation-name'>
            <strong>{{ customerDisplayName(item) }}</strong>
            <small>{{ item.lastMessageTime || item.lastInboundTime || '' }}</small>
          </span>
          <span class='conversation-preview'>{{ item.lastMessage || '暂无消息' }}</span>
        </span>
      </button>

      <div v-if='!conversations.length' class='conversation-empty'>
        <a-empty description='暂无会话' />
      </div>
    </div>
  </aside>
</template>

<style scoped lang='less'>
.customer-conversations {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  height: 100%;
  padding: 14px;
  border-right: 1px solid #edf1f5;
  background: #fff;
}

.conversation-toolbar,
.conversation-toolbar > span,
.conversation-item,
.conversation-name {
  display: flex;
  align-items: center;
}

.conversation-toolbar {
  justify-content: space-between;
  color: #203626;
  font-size: 14px;
  font-weight: 700;

  > span {
    gap: 6px;
  }
}

.conversation-list {
  display: grid;
  align-content: start;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
}

.conversation-item {
  width: 100%;
  min-width: 0;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease, box-shadow .18s ease;

  &:hover {
    background: #f5f9ff;
  }

  &.unread {
    border-color: rgba(255, 77, 79, .12);
    background: #fffafa;

    .conversation-preview,
    .conversation-name strong {
      color: #1f3325;
      font-weight: 800;
    }
  }

  &.active {
    border-color: rgba(51, 162, 4, .3);
    background: #f5fbf6;
    box-shadow: inset 3px 0 0 #33a204;
  }
}

.conversation-main {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}

.conversation-name {
  min-width: 0;
  justify-content: space-between;
  gap: 8px;

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    min-width: 0;
    color: #203626;
    font-size: 13px;
  }

  small {
    flex: none;
    color: #9aa5b1;
    font-size: 11px;
  }
}

.conversation-preview {
  overflow: hidden;
  color: #7a8792;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-empty {
  display: grid;
  min-height: 220px;
  place-items: center;
}
</style>
