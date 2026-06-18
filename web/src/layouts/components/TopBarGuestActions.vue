<script setup>
import { FireTwoTone, LoginOutlined } from '@ant-design/icons-vue';
import { h } from 'vue';

defineProps({
  compactLogin: {
    type: Boolean,
    default: false
  },
  gutter: {
    type: Number,
    default: 20
  },
  showStart: {
    type: Boolean,
    default: false
  }
});

defineEmits(['login', 'start']);
</script>

<template>
  <a-space class='guest-actions' :size='gutter'>
    <a-button
      class='login-button'
      type='primary'
      :icon='h(LoginOutlined)'
      :aria-label="compactLogin ? '去登录' : undefined"
      @click="$emit('login')"
    >
      {{ compactLogin ? '' : '去登录' }}
    </a-button>
    <a-button v-if='showStart' type='primary' ghost :icon='h(FireTwoTone)' @click="$emit('start')">
      立即体验
    </a-button>
  </a-space>
</template>

<style scoped lang='less'>
.guest-actions {
  display: inline-flex;
  align-items: center;
}

.login-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  min-width: 104px;
  padding: 0 15px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(135deg, #1677ff, #2b88ff);
  box-shadow: 0 10px 26px rgba(22, 119, 255, .18);
  font-weight: 700;
  line-height: 1;
  transition: background .18s ease, box-shadow .18s ease, transform .18s ease;

  :deep(.ant-btn-icon),
  :deep(.anticon),
  :deep(span) {
    display: inline-flex;
    align-items: center;
    line-height: 1;
  }

  :deep(.ant-btn-icon) {
    margin-inline-end: 0;
  }

  &:hover,
  &:focus {
    background: linear-gradient(135deg, #116de8, #1f7fff);
    box-shadow: 0 12px 28px rgba(22, 119, 255, .24);
  }
}

@media only screen and (max-width: 640px) {
  .login-button {
    width: 34px;
    min-width: 34px;
    height: 34px;
    padding: 0;
  }
}
</style>
