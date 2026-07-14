<template>
  <router-link style="margin-right: 20px; float: left"><left-outlined /> {{ t('common.back') }}</router-link>
  <div style="margin: 0 auto; width: fit-content">
    <slot></slot>
  </div>
</template>

<script setup>
import { LeftOutlined } from '@ant-design/icons-vue';
import { useI18n } from 'vue-i18n';

defineOptions({
  name: 'NavigatorBar'
});

const { t } = useI18n();
</script>

<style scoped>

</style>
