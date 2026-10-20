<script setup>
import { computed } from 'vue';
import { SendOutlined, WhatsAppOutlined } from '@ant-design/icons-vue';
import { useI18n } from 'vue-i18n';
import { getShopContactImages } from '@/modules/shop/assets/contact.js';
import { SUPPORT_CONTACTS } from '@/utils/supportContacts.js';

const { locale, t } = useI18n();
const { customerWechatQr } = getShopContactImages();
const useInternationalContacts = computed(() => !String(locale.value).toLowerCase().startsWith('zh'));
</script>

<template>
  <div v-if='useInternationalContacts' class='offline-international-actions'>
    <a-button
      type='link'
      size='small'
      :href='SUPPORT_CONTACTS.whatsapp.href'
      target='_blank'
      rel='noopener noreferrer'
    >
      <template #icon><whats-app-outlined /></template>
      WhatsApp
    </a-button>
    <a-button
      type='link'
      size='small'
      :href='SUPPORT_CONTACTS.telegram.href'
      target='_blank'
      rel='noopener noreferrer'
    >
      <template #icon><send-outlined /></template>
      Telegram
    </a-button>
  </div>
  <a
    v-else
    class='offline-support-qr'
    :href='customerWechatQr'
    target='_blank'
    rel='noreferrer'
  >
    <img :src='customerWechatQr' :alt="t('customerService.wechatQrAlt')">
    <span>{{ t('customerService.openWechatQr') }}</span>
  </a>
</template>

<style scoped lang='less'>
.offline-support-qr {
  display: grid;
  overflow: hidden;
  width: min(210px, 100%);
  margin-top: 10px;
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  color: #1677ff;
  background: #fff;
  text-align: center;
  text-decoration: none;

  img {
    display: block;
    width: 100%;
    aspect-ratio: 1268 / 1729;
    object-fit: contain;
  }

  span {
    padding: 7px 10px;
    border-top: 1px solid #edf1f5;
    font-size: 12px;
    font-weight: 700;
  }
}

.offline-international-actions {
  display: flex;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 6px;

  :deep(.ant-btn) {
    height: 30px;
    padding: 0 8px;
    border: 1px solid #dce8f5;
    border-radius: 6px;
    background: #fff;
    font-weight: 700;
  }
}
</style>
