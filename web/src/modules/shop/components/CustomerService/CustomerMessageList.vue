<script setup>
import { CustomerServiceOutlined, UserOutlined } from '@ant-design/icons-vue';
import { defineAsyncComponent, nextTick, ref, watch } from 'vue';
import AttachmentList from '@/components/Attachments/AttachmentList.vue';
import { customerSenderName } from './customerDisplay.js';

const MarkdownPreview = defineAsyncComponent(() => import('@/components/Markdown/MarkdownPreview.vue'));

const props = defineProps({
  manager: Boolean,
  conversation: Object,
  messages: {
    type: Array,
    default: () => []
  }
});

const messageBodyRef = ref(null);

const isAdminMessage = item => item?.senderRole === 'ADMIN' || item?.direction === 'OUTBOUND';
const isMineMessage = item => props.manager ? item?.direction === 'OUTBOUND' : item?.direction === 'INBOUND';
const senderName = item => isAdminMessage(item)
  ? item?.senderName || '飞鱼小铺客服'
  : customerSenderName(item, props.conversation);
const senderAvatar = item => item?.senderAvatar || (!isAdminMessage(item) ? props.conversation?.avatar : '');
const isMarkdown = item => item?.messageType === 'markdown';

const scrollToBottom = async () => {
  await nextTick();
  const body = messageBodyRef.value;
  if (body) {
    body.scrollTop = body.scrollHeight;
  }
};

watch(() => props.messages, scrollToBottom, { deep: true, immediate: true });
</script>

<template>
  <div ref='messageBodyRef' class='customer-message-list'>
    <div v-if='!messages.length' class='message-empty'>
      <a-empty description='暂无消息' />
    </div>

    <div
      v-for='item in messages'
      :key='item.id'
      class='chat-message'
      :class='{ mine: isMineMessage(item), failed: item.sendStatus === "FAILED" }'
    >
      <a-avatar v-if='senderAvatar(item)' class='message-avatar' :src='senderAvatar(item)' :size='34' />
      <a-avatar v-else class='message-avatar' :size='34'>
        <customer-service-outlined v-if='isAdminMessage(item)' />
        <user-outlined v-else />
      </a-avatar>

      <div class='message-content'>
        <div class='message-meta'>
          <strong>{{ senderName(item) }}</strong>
          <span>{{ item.createTime }}</span>
        </div>
        <div class='bubble'>
          <markdown-preview
            v-if='isMarkdown(item)'
            class='message-markdown'
            :model-value='item.content || ""'
            language='zh-CN'
            preview-theme='default'
            code-theme='github'
          />
          <p v-else>{{ item.content }}</p>
          <attachment-list :attachments='item.attachments' />
          <small v-if='item.sendStatus === "FAILED"'>发送失败</small>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang='less'>
.customer-message-list {
  display: flex;
  min-height: 0;
  padding: 16px;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  background:
    linear-gradient(180deg, rgba(247, 249, 251, .96), rgba(247, 249, 251, .96)),
    radial-gradient(circle at 20% 0, rgba(51, 162, 4, .08), transparent 32%);
}

.message-empty {
  display: grid;
  min-height: 280px;
  place-items: center;
}

.chat-message {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: start;
  gap: 9px;
  max-width: 86%;
  align-self: flex-start;

  &.mine {
    grid-template-columns: minmax(0, 1fr) 34px;
    align-self: flex-end;

    .message-avatar {
      grid-column: 2;
      grid-row: 1;
    }

    .message-content {
      grid-column: 1;
      grid-row: 1;
      justify-items: end;
    }

    .message-meta {
      justify-content: flex-end;
    }

    .bubble {
      border-color: rgba(51, 162, 4, .16);
      background: #eaf8e8;
    }
  }

  &.failed .bubble {
    border-color: rgba(255, 77, 79, .24);
    background: #fff2f0;
  }
}

.message-content {
  display: grid;
  min-width: 0;
  justify-items: start;
  gap: 5px;
}

.message-meta {
  display: flex;
  max-width: 100%;
  align-items: center;
  gap: 8px;
  color: #8b98a8;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #486071;
    font-size: 12px;
    font-weight: 700;
  }

  span {
    font-size: 11px;
  }
}

.bubble {
  max-width: min(100%, 430px);
  padding: 10px 12px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  box-shadow: 0 8px 18px rgba(31, 56, 88, .05);

  p {
    margin: 0;
    color: #26384a;
    font-size: 14px;
    line-height: 1.65;
    white-space: pre-wrap;
    word-break: break-word;
  }

  small {
    display: block;
    margin-top: 4px;
    color: #cf1322;
    font-size: 11px;
  }
}

.message-markdown {
  :deep(.md-editor-preview-wrapper) {
    padding: 0;
  }

  :deep(.md-editor-preview) {
    color: #26384a;
    font-size: 14px;
    line-height: 1.65;
  }
}

@media only screen and (max-width: 640px) {
  .customer-message-list {
    padding: 12px;
  }

  .chat-message {
    max-width: 94%;
  }
}
</style>
