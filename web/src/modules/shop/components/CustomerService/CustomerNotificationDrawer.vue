<script setup>
import {
  BellOutlined,
  FileTextOutlined,
} from '@ant-design/icons-vue';
import { ticketStatusColor, ticketStatusText } from '@/modules/shop/utils/supportTickets.js';

defineProps({
  open: Boolean,
  ticketUnreadCount: Number,
  tickets: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:open', 'open-tickets']);

const close = () => emit('update:open', false);
</script>

<template>
  <a-drawer
    :open='open'
    class='notice-drawer'
    width='360'
    placement='right'
    :body-style='{ padding: 0 }'
    @update:open='value => emit("update:open", value)'
  >
    <template #title>
      <div class='notice-title'>
        <bell-outlined />
        <span>工单提醒</span>
        <a-badge v-if='ticketUnreadCount' :count='ticketUnreadCount' :overflow-count='99' />
      </div>
    </template>

    <div class='notice-panel'>
      <section class='notice-section'>
        <div class='notice-section-head'>
          <span><file-text-outlined /> 未读工单</span>
          <a-badge :count='ticketUnreadCount' :overflow-count='99' />
        </div>
        <div v-if='tickets.length' class='notice-list'>
          <button
            v-for='ticket in tickets'
            :key='ticket.ticketNo'
            type='button'
            class='notice-item ticket'
            @click='emit("open-tickets"); close()'
          >
            <span class='notice-main'>
              <strong>{{ ticket.title }}</strong>
              <small>{{ ticket.ticketNo }} · {{ ticket.lastMessage || '有新的工单消息' }}</small>
            </span>
            <span class='notice-side'>
              <a-tag :color='ticketStatusColor(ticket.status)'>{{ ticketStatusText(ticket.status) }}</a-tag>
              <a-badge :count='ticket.unreadCount' :overflow-count='99' />
            </span>
          </button>
        </div>
        <a-empty v-else class='notice-empty' description='暂无未读工单' />
      </section>
    </div>
  </a-drawer>
</template>

<style scoped lang='less'>
.notice-title,
.notice-section-head,
.notice-section-head > span,
.notice-side {
  display: inline-flex;
  align-items: center;
}

.notice-title {
  gap: 8px;
  color: #203626;
}

.notice-panel {
  display: block;
  padding: 14px;
  background: #f6f8fb;
}

.notice-section {
  display: block;
  padding: 14px;
  border: 1px solid #e8eef5;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(24, 43, 70, .04);
}

.notice-section-head {
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  color: #203626;
  font-size: 14px;
  font-weight: 700;

  > span {
    gap: 6px;
  }
}

.notice-list {
  display: grid;
  gap: 8px;
  align-content: start;
  max-height: calc(100vh - 142px);
  overflow-y: auto;
  padding-right: 2px;
}

.notice-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 58px;
  padding: 9px;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  background: #fafcff;
  text-align: left;
  cursor: pointer;

  &.ticket {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  &:hover {
    border-color: rgba(22, 119, 255, .24);
    background: #f6fbff;
  }
}

.notice-main {
  display: grid;
  min-width: 0;
  gap: 2px;

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #26384f;
    font-size: 13px;
  }

  small {
    color: #788692;
    font-size: 12px;
  }
}

.notice-side {
  gap: 6px;
  color: #8c98a8;

  :deep(.ant-tag) {
    margin-inline-end: 0;
  }
}

.notice-empty {
  padding: 10px 0 4px;
}

@media only screen and (max-width: 640px) {
  :global(.notice-drawer .ant-drawer-content-wrapper) {
    width: 100vw !important;
    max-width: 100vw;
  }

  .notice-panel {
    padding: 12px;
  }

  .notice-section {
    padding: 12px;
  }

  .notice-item.ticket {
    grid-template-columns: minmax(0, 1fr);
  }

  .notice-side {
    justify-content: space-between;
  }
}
</style>
