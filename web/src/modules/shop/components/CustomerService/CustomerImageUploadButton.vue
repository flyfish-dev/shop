<script setup>
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { PictureOutlined } from '@ant-design/icons-vue';
import { PortalFiles } from '@/modules/auth/api.js';

defineProps({
  disabled: Boolean
});

const emit = defineEmits(['uploaded', 'uploading-change']);

const uploading = ref(false);
const MAX_SIZE = 20 * 1024 * 1024;

const beforeUpload = file => {
  if (!file.type?.startsWith('image/')) {
    message.error('请选择图片');
    return false;
  }
  if (file.size > MAX_SIZE) {
    message.error('图片不能超过 20MB');
    return false;
  }
  return true;
};

const uploadImage = async options => {
  uploading.value = true;
  emit('uploading-change', true);
  try {
    const form = new FormData();
    form.append('file', options.file);
    const attachment = await PortalFiles.upload(form);
    emit('uploaded', {
      ...attachment,
      image: true
    });
    options.onSuccess?.(attachment);
  } catch (e) {
    message.error(e.message || '图片上传失败');
    options.onError?.(e);
  } finally {
    uploading.value = false;
    emit('uploading-change', false);
  }
};
</script>

<template>
  <a-upload
    :show-upload-list='false'
    accept='image/*'
    :disabled='disabled || uploading'
    :before-upload='beforeUpload'
    :custom-request='uploadImage'
  >
    <a-tooltip title='图片'>
      <a-button size='small' :loading='uploading' :disabled='disabled'>
        <template #icon><picture-outlined /></template>
      </a-button>
    </a-tooltip>
  </a-upload>
</template>
