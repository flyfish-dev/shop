<script setup>
import { computed } from 'vue';
import { FileOutlined } from '@ant-design/icons-vue';

const props = defineProps({
  attachments: {
    type: Array,
    default: () => []
  }
});

const files = computed(() => props.attachments.filter(item => item?.url));

const isImage = item => item?.image || String(item?.contentType || '').startsWith('image/');

const fileSize = size => {
  const value = Number(size || 0);
  if (!value) return '';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
};
</script>

<template>
  <div v-if='files.length' class='attachment-list'>
    <template v-for='item in files' :key='item.url'>
      <a class='image-attachment' v-if='isImage(item)' :href='item.url' target='_blank' rel='noreferrer'>
        <img :src='item.url' :alt='item.name || "图片附件"'>
      </a>
      <a v-else class='file-attachment' :href='item.url' target='_blank' rel='noreferrer'>
        <file-outlined />
        <span>
          <strong>{{ item.name || '附件' }}</strong>
          <small>{{ fileSize(item.size) }}</small>
        </span>
      </a>
    </template>
  </div>
</template>

<style scoped lang='less'>
.attachment-list {
  display: flex;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 8px;
}

.image-attachment {
  display: block;
  overflow: hidden;
  width: 120px;
  height: 86px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #f8fafc;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.file-attachment {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 260px;
  padding: 8px 10px;
  border: 1px solid #e5edf6;
  border-radius: 8px;
  color: #31465e;
  background: #f8fbff;
  text-decoration: none;

  > span {
    display: grid;
    min-width: 0;
    gap: 2px;
  }

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    font-size: 12px;
  }

  small {
    color: #7d8a99;
    font-size: 11px;
  }
}
</style>
