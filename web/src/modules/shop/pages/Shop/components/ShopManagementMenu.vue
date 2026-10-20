<script setup>
import { SettingOutlined } from '@ant-design/icons-vue';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
const { t, locale } = useI18n();
const entries = computed(() => [
  ['workbench', t('shop.shell.workbench')], ['shops', t('shop.shell.shops')],
  ['groups', t('shop.shell.groups')], ['items', t('shop.shell.items')],
  ['xianyu', locale.value === 'en-US' ? 'Xianyu Channel' : '闲鱼渠道'],
  ['repositories', t('shop.shell.repositories')], ['orders', t('shop.shell.orders')],
  ['users', t('shop.shell.users')], ['coupons', t('shop.shell.coupons')],
  ['contracts', t('shop.shell.contracts')], ['tickets', t('shop.shell.tickets')]
]);
</script>
<template>
  <a-dropdown :trigger="['click']" placement="bottomRight" overlay-class-name="flyfish-command-dropdown">
    <a-button type="text" class="management-toggle" :aria-label="t('shop.shell.manage')" :title="t('shop.shell.manage')"><setting-outlined /></a-button>
    <template #overlay><a-menu><a-menu-item v-for="[path, title] in entries" :key="path"><router-link :href="`/shop/manage/${path}`">{{ title }}</router-link></a-menu-item></a-menu></template>
  </a-dropdown>
</template>
<style scoped>.management-toggle { display: inline-flex; align-items: center; justify-content: center; width: 32px; padding: 0; color: #4a6557; }</style>
