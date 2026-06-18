<script setup>
import { nextTick, ref } from 'vue';
import { FileMarkdownOutlined, SendOutlined, SmileOutlined } from '@ant-design/icons-vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import CustomerImageUploadButton from './CustomerImageUploadButton.vue';

const props = defineProps({
  input: String,
  messageType: {
    type: String,
    default: 'text'
  },
  attachments: {
    type: Array,
    default: () => []
  },
  sending: Boolean,
  uploading: Boolean
});

const emit = defineEmits([
  'update:input',
  'update:messageType',
  'update:attachments',
  'uploading-change',
  'send'
]);

const emojiOpen = ref(false);

const ensureEmojiPicker = async () => {
  await import('emoji-picker-element');
};

const openEmoji = async () => {
  await ensureEmojiPicker();
  emojiOpen.value = true;
};

const appendEmoji = event => {
  const emoji = event?.detail?.unicode;
  if (!emoji) {
    return;
  }
  emit('update:input', `${props.input || ''}${emoji}`);
  emojiOpen.value = false;
  nextTick();
};

const appendImage = attachment => {
  emit('update:attachments', [...props.attachments, attachment].slice(-6));
  emit('update:messageType', 'image');
};

const toggleMarkdown = checked => {
  emit('update:messageType', checked ? 'markdown' : 'text');
};

const handleKeydown = event => {
  if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
    emit('send');
  }
};
</script>

<template>
  <div class='customer-composer'>
    <div class='composer-main'>
      <div class='composer-toolbar'>
        <a-popover v-model:open='emojiOpen' trigger='click' placement='topLeft'>
          <template #content>
            <emoji-picker class='emoji-picker' @emoji-click='appendEmoji' />
          </template>
          <a-tooltip title='表情'>
            <a-button size='small' @click='openEmoji'>
              <template #icon><smile-outlined /></template>
            </a-button>
          </a-tooltip>
        </a-popover>
        <customer-image-upload-button
          :disabled='sending'
          @uploaded='appendImage'
          @uploading-change='value => emit("uploading-change", value)'
        />
        <attachment-upload
          :value='attachments'
          :disabled='sending'
          @update:value='value => emit("update:attachments", value)'
          @uploading-change='value => emit("uploading-change", value)'
        />
        <a-tooltip title='Markdown'>
          <a-button
            size='small'
            :type='messageType === "markdown" ? "primary" : "default"'
            @click='toggleMarkdown(messageType !== "markdown")'
          >
            <template #icon><file-markdown-outlined /></template>
          </a-button>
        </a-tooltip>
      </div>
      <a-textarea
        :value='input'
        :maxlength='4096'
        :auto-size='{ minRows: 2, maxRows: 4 }'
        @update:value='value => emit("update:input", value)'
        @keydown='handleKeydown'
      />
    </div>
    <a-button
      type='primary'
      shape='circle'
      :disabled='uploading || (!input?.trim() && !attachments.length)'
      :loading='sending || uploading'
      @click='emit("send")'
    >
      <template #icon><send-outlined /></template>
    </a-button>
  </div>
</template>

<style scoped lang='less'>
.customer-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 38px;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid #edf1f5;
  background: #fff;

  :deep(.ant-btn) {
    align-self: center;
  }

  :deep(.ant-input) {
    border-radius: 8px;
  }
}

.composer-main {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.composer-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;

  :deep(.ant-upload-wrapper) {
    display: inline-flex;
  }
}

.emoji-picker {
  width: 320px;
  height: 360px;
}

@media only screen and (max-width: 640px) {
  .customer-composer {
    grid-template-columns: minmax(0, 1fr) 40px;
    gap: 8px;
    padding: 10px 10px max(10px, env(safe-area-inset-bottom));
  }

  .composer-toolbar {
    gap: 5px;
  }

  .emoji-picker {
    width: min(300px, calc(100vw - 32px));
    height: min(360px, calc(100dvh - 180px));
  }
}
</style>
