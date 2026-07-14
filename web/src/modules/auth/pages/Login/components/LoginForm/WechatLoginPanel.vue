<script setup>
import {
  LoadingOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue';
import { useI18n } from 'vue-i18n';

defineProps({
  hasOverlay: Boolean,
  loading: Boolean,
  polling: Boolean,
  qrCode: { type: String, default: '' },
  scene: { type: String, default: '' },
  simpleMode: Boolean,
  status: { type: String, default: 'IDLE' },
  statusText: { type: String, default: '' }
});

defineEmits(['reload']);

const { t } = useI18n();
</script>

<template>
  <div class="wechat-panel">
    <div class="wechat-copy">
      <strong>{{ t('auth.wechat.title') }}</strong>
      <span v-if="scene">
        {{ simpleMode ? t('auth.wechat.reply') : t('auth.wechat.scanAndReply') }}
        <b>{{ scene }}</b>
      </span>
      <span v-else>{{ t('auth.wechat.hint') }}</span>
      <div class="login-status" :class="status.toLowerCase()">
        <loading-outlined v-if="polling && status !== 'CONFIRMED'" />
        {{ statusText }}
      </div>
    </div>

    <div class="qr-code">
      <img
        v-if="qrCode"
        :src="qrCode"
        :alt="t('auth.wechat.qrAlt')"
        :class="{ muted: hasOverlay }"
      />
      <loading-outlined v-else class="qr-placeholder" />
      <div v-if="hasOverlay" class="qr-overlay">
        <loading-outlined v-if="loading || status === 'LOADING'" />
        <template v-else>
          <span>{{ statusText }}</span>
          <button v-if="status !== 'CONFIRMED'" type="button" @click="$emit('reload')">
            <reload-outlined /> {{ t('auth.wechat.reload') }}
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="less">
.wechat-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 110px;
  gap: 14px;
  align-items: center;
  padding: 12px;
  border: 1px solid #cfe6d8;
  border-radius: 8px;
  background: rgba(247, 252, 249, .94);
  animation: panel-reveal .28s ease-out both;
}

.wechat-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: #53665c;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;

  strong { color: #173c2a; font-size: 15px; }
  b { margin-left: 3px; color: #0e8246; font-size: 16px; }
}

.qr-code {
  position: relative;
  width: 110px;
  height: 110px;
  padding: 5px;
  border: 1px solid #e0ebe4;
  background: #fff;
  box-shadow: 0 8px 20px rgba(26, 82, 51, .08);

  img { display: block; width: 100%; height: 100%; transition: opacity .18s ease; }
  img.muted { opacity: .22; }
}

.qr-placeholder {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #63a27c;
  font-size: 25px;
}

.qr-overlay {
  position: absolute;
  inset: 5px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 9px;
  background: rgba(17, 45, 31, .86);
  color: #fff;
  font-size: 11px;
  line-height: 1.45;
  text-align: center;

  button { border: 0; background: transparent; color: #c8f1d7; cursor: pointer; }
}

.login-status {
  display: flex;
  min-height: 20px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #607269;
  font-size: 11px;
  text-align: center;

  &.confirmed { color: #237804; }
  &.expired, &.error { color: #b42318; }
}

@keyframes panel-reveal {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 520px) {
  .wechat-panel {
    grid-template-columns: minmax(0, 1fr) 100px;
    gap: 10px;
    padding: 10px;
  }

  .qr-code { width: 100px; height: 100px; }
  .wechat-copy strong { font-size: 14px; }
}

@media (prefers-reduced-motion: reduce) {
  .wechat-panel { animation: none; }
}
</style>
