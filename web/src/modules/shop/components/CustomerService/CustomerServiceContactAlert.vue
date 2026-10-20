<script setup>
import { computed, ref } from 'vue';
import { QrcodeOutlined, SendOutlined, WechatOutlined, WhatsAppOutlined } from '@ant-design/icons-vue';
import { getShopContactImages } from '@/modules/shop/assets/contact.js';
import { useI18n } from 'vue-i18n';
import { SUPPORT_CONTACTS } from '@/utils/supportContacts.js';

defineProps({
  compact: Boolean
});

const qrOpen = ref(false);
const { customerWechatQr } = getShopContactImages();
const { locale, t } = useI18n();
const useInternationalContacts = computed(() => !String(locale.value).toLowerCase().startsWith('zh'));
</script>

<template>
  <div class='service-contact-alert' :class='{ compact }'>
    <a-alert type='info' show-icon>
      <template #message>
        <span v-if='useInternationalContacts' class='alert-message'>
          {{ t('customerService.internationalReply') }}
        </span>
        <span v-else class='alert-message'>
          {{ t('customerService.delayedReply') }} <strong>Yous_Gift</strong>
        </span>
      </template>
      <template #action>
        <span v-if='useInternationalContacts' class='international-actions'>
          <a-button
            type='link'
            size='small'
            class='alert-action whatsapp'
            :href='SUPPORT_CONTACTS.whatsapp.href'
            target='_blank'
            rel='noopener noreferrer'
          >
            <template #icon><whats-app-outlined /></template>
            {{ t('customerService.openWhatsApp') }}
          </a-button>
          <a-button
            type='link'
            size='small'
            class='alert-action telegram'
            :href='SUPPORT_CONTACTS.telegram.href'
            target='_blank'
            rel='noopener noreferrer'
          >
            <template #icon><send-outlined /></template>
            {{ t('customerService.openTelegram') }}
          </a-button>
        </span>
        <a-button v-else type='link' size='small' class='alert-action' @click='qrOpen = true'>
          <template #icon><qrcode-outlined /></template>
          {{ t('customerService.viewQrCode') }}
        </a-button>
      </template>
    </a-alert>

    <a-modal
      v-if='!useInternationalContacts'
      v-model:open='qrOpen'
      :title="t('customerService.wechatTitle')"
      width='360'
      :footer='null'
      class='service-wechat-modal'
    >
      <div class='qr-panel'>
        <img :src='customerWechatQr' :alt="t('customerService.wechatQrAlt')">
        <strong>{{ t('customerService.addWechat') }}</strong>
        <p>{{ t('customerService.wechatQrHint') }}</p>
        <a-button :href='customerWechatQr' target='_blank' download='flyfish-customer-wechat.jpg'>
          <template #icon><wechat-outlined /></template>
          {{ t('customerService.openImage') }}
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang='less'>
.service-contact-alert {
  :deep(.ant-alert) {
    align-items: center;
    border-color: #d6e8ff;
    border-radius: 8px;
    background: #f7fbff;
  }

  :deep(.ant-alert-message) {
    color: #294059;
    font-size: 13px;
    line-height: 1.5;
  }

  :deep(.ant-alert-action) {
    margin-inline-start: 10px;
  }

  &.compact {
    :deep(.ant-alert) {
      padding: 7px 10px;
    }
  }
}

.alert-message strong {
  color: #1677ff;
}

.alert-action {
  height: 24px;
  padding: 0 2px;
  font-weight: 700;

  &.whatsapp {
    color: #128c4a;
  }

  &.telegram {
    color: #168ac0;
  }
}

.international-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.qr-panel {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding: 6px 0 4px;
  text-align: center;

  img {
    width: min(240px, 76vw);
    aspect-ratio: 1;
    border: 1px solid #edf1f5;
    border-radius: 8px;
    object-fit: cover;
  }

  strong {
    color: #203626;
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #6f7d90;
    font-size: 13px;
  }
}

:global(.service-wechat-modal .ant-modal) {
  max-width: calc(100vw - 24px);
}

@media only screen and (max-width: 640px) {
  .service-contact-alert {
    :deep(.ant-alert) {
      align-items: flex-start;
    }

    :deep(.ant-alert-content) {
      min-width: 0;
    }

    :deep(.ant-alert-action) {
      margin-inline-start: 0;
      padding-top: 4px;
    }
  }

  .international-actions {
    flex-wrap: wrap;
    gap: 4px 10px;
  }
}
</style>
