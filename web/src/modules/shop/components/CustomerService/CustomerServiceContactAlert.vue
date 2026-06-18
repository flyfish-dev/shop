<script setup>
import { ref } from 'vue';
import { QrcodeOutlined, WechatOutlined } from '@ant-design/icons-vue';
import { getShopContactImages } from '@/modules/shop/assets/contact.js';

defineProps({
  compact: Boolean
});

const qrOpen = ref(false);
const { customerWechatQr } = getShopContactImages();
</script>

<template>
  <div class='service-contact-alert' :class='{ compact }'>
    <a-alert type='info' show-icon>
      <template #message>
        <span class='alert-message'>
          回复不及时可添加客服微信 <strong>Yous_Gift</strong>
        </span>
      </template>
      <template #action>
        <a-button type='link' size='small' class='alert-action' @click='qrOpen = true'>
          <template #icon><qrcode-outlined /></template>
          查看二维码
        </a-button>
      </template>
    </a-alert>

    <a-modal
      v-model:open='qrOpen'
      title='客服微信'
      width='360'
      :footer='null'
      class='service-wechat-modal'
    >
      <div class='qr-panel'>
        <img :src='customerWechatQr' alt='客服微信二维码'>
        <strong>添加客服微信 Yous_Gift</strong>
        <p>可扫码添加，或保存图片后用微信扫一扫识别。</p>
        <a-button :href='customerWechatQr' target='_blank' download='flyfish-customer-wechat.jpg'>
          <template #icon><wechat-outlined /></template>
          打开图片
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
}
</style>
