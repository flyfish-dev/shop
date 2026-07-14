<script setup>
import { computed, ref, watch } from 'vue';
import { message, Upload } from 'ant-design-vue';
import { PaperClipOutlined } from '@ant-design/icons-vue';
import { PortalFiles } from '@/modules/auth/api.js';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  value: {
    type: Array,
    default: () => []
  },
  disabled: Boolean,
  maxCount: {
    type: Number,
    default: 6
  }
});

const emit = defineEmits(['update:value', 'uploading-change']);
const { t } = useI18n();

const MAX_SIZE = 20 * 1024 * 1024;
const fileList = ref([]);
const uploadingCount = ref(0);

const uploading = computed(() => uploadingCount.value > 0);

watch(uploading, value => emit('uploading-change', value));

const uploadedAttachments = files => files
    .filter(file => file.status === 'done' && (file.response?.url || file.url))
    .map(file => file.response || {
      id: file.uid,
      name: file.name,
      url: file.url,
      size: file.size,
      contentType: file.type,
      image: file.type?.startsWith('image/')
    });

const attachmentKey = attachment => String(attachment?.id || attachment?.url || '');

const sameAttachments = (left, right) => left.length === right.length
  && left.every((attachment, index) => attachmentKey(attachment) === attachmentKey(right[index]));

const toUploadFile = (attachment, index) => ({
  uid: attachmentKey(attachment) || `attachment-${index}`,
  name: attachment.name || 'attachment',
  status: 'done',
  url: attachment.url,
  size: attachment.size,
  type: attachment.contentType,
  response: attachment
});

watch(() => props.value, value => {
  const attachments = Array.isArray(value) ? value : [];
  // Upload emits an intermediate "uploading" list before a response exists. Keep that
  // local state unless the parent actually changed its completed attachment collection.
  if (!sameAttachments(uploadedAttachments(fileList.value), attachments)) {
    fileList.value = attachments.map(toUploadFile);
  }
}, { immediate: true, deep: true });

const emitValue = () => {
  emit('update:value', uploadedAttachments(fileList.value));
};

const beforeUpload = file => {
  if (file.size > MAX_SIZE) {
    message.error(t('attachments.tooLarge'));
    return Upload.LIST_IGNORE;
  }
  return true;
};

const uploadFile = async options => {
  uploadingCount.value += 1;
  try {
    const form = new FormData();
    form.append('file', options.file);
    const attachment = await PortalFiles.upload(form);
    options.onSuccess?.(attachment);
  } catch (e) {
    message.error(e.message || t('attachments.uploadFailed'));
    options.onError?.(e);
  } finally {
    uploadingCount.value = Math.max(0, uploadingCount.value - 1);
  }
};

const handleChange = ({ file, fileList: next }) => {
  fileList.value = next.slice(-props.maxCount);
  if (file.status && file.status !== 'uploading') {
    emitValue();
  }
};
</script>

<template>
  <a-upload
    :file-list='fileList'
    :max-count='maxCount'
    :multiple='maxCount > 1'
    :disabled='disabled'
    :before-upload='beforeUpload'
    :custom-request='uploadFile'
    @change='handleChange'
  >
    <a-button size='small' :disabled='disabled'>
      <template #icon><paper-clip-outlined /></template>
      {{ t('attachments.label') }}
    </a-button>
  </a-upload>
</template>
