<script setup>
import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { PaperClipOutlined } from '@ant-design/icons-vue';
import { PortalFiles } from '@/modules/auth/api.js';

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

const MAX_SIZE = 20 * 1024 * 1024;
const fileList = ref([]);
const uploadingCount = ref(0);

const uploading = computed(() => uploadingCount.value > 0);

watch(uploading, value => emit('uploading-change', value));

watch(() => props.value, value => {
  if (!value?.length) {
    fileList.value = [];
  }
});

const emitValue = () => {
  emit('update:value', fileList.value
    .filter(file => file.status === 'done' && (file.response?.url || file.url))
    .map(file => file.response || {
      id: file.uid,
      name: file.name,
      url: file.url,
      size: file.size,
      contentType: file.type,
      image: file.type?.startsWith('image/')
    }));
};

const beforeUpload = file => {
  if (file.size > MAX_SIZE) {
    message.error('附件不能超过 20MB');
    return false;
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
    message.error(e.message || '附件上传失败');
    options.onError?.(e);
  } finally {
    uploadingCount.value = Math.max(0, uploadingCount.value - 1);
  }
};

const handleChange = ({ fileList: next }) => {
  fileList.value = next.slice(-props.maxCount);
  emitValue();
};

const handleRemove = () => {
  queueMicrotask(emitValue);
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
    @remove='handleRemove'
  >
    <a-button size='small' :disabled='disabled'>
      <template #icon><paper-clip-outlined /></template>
      附件
    </a-button>
  </a-upload>
</template>
