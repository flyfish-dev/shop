<script setup>
import FooterBar from '@/layouts/FooterBar.vue';
import StorefrontHeader from '@/layouts/StorefrontHeader.vue';
import ShopManagementMenu from './components/ShopManagementMenu.vue';
import CustomerServiceWidget from '@/modules/shop/components/CustomerService/CustomerServiceWidget.vue';
import useClientStore from '@/modules/auth/store/client.js';
import { computed, onMounted, ref } from 'vue';
import { isShopMaintainer } from '@/modules/shop/authority.js';
import { useRoute } from '@/router/use.js';
import { useI18n } from 'vue-i18n';

const store = useClientStore();
const route = useRoute();
const { t } = useI18n();
const userLoading = ref(true);
const canManage = computed(() => isShopMaintainer(store.user));
const isManageRoute = computed(() => route.path.startsWith('/shop/manage'));
const isCatalogRoute = computed(() => route.path.replace(/\/$/, '') === '/shop/item-list');
const isHomeRoute = computed(() => ['/', '/shop', '/shop/'].includes(route.path));
const showManageGuard = computed(() => isManageRoute.value && !canManage.value);
onMounted(async () => {
  try { await store.loadUser(); } finally { userLoading.value = false; }
});
</script>

<template>
  <a-config-provider :theme="{ token: { colorPrimary: '#17825c', borderRadius: 6 } }">
    <div class="shop-page">
      <storefront-header>
        <template #actions><shop-management-menu v-if="canManage" /></template>
      </storefront-header>
      <div class="shop-container" :class="{ 'shop-container-home': isHomeRoute, 'shop-container-catalog': isCatalogRoute, 'shop-container-manage': isManageRoute }">
        <div v-if="userLoading && isManageRoute" class="shop-auth-state"><a-spin :tip="t('shop.shell.checkingAccess')" /></div>
        <a-result v-else-if="showManageGuard" class="shop-auth-state" status="403" :title="t('shop.shell.accessDenied')">
          <template #extra><router-link href="/"><a-button type="primary">{{ t('shop.shell.returnStore') }}</a-button></router-link></template>
        </a-result>
        <router-view v-else />
      </div>
      <footer-bar />
      <customer-service-widget />
    </div>
  </a-config-provider>
</template>

<style scoped lang="less">
.shop-page { min-height: 100vh; background: #fafbfa; }
.shop-container { width: 100%; min-height: 60vh; max-width: 1200px; margin: 0 auto; padding: 26px 24px 48px; }
.shop-container-home { max-width: none; padding: 0; background: #fff; }
.shop-container-catalog { max-width: 1508px; }
.shop-container-manage { max-width: 1480px; }
.shop-auth-state { min-height: 420px; padding-top: 80px; text-align: center; }
@media (max-width: 640px) {
  .shop-container { padding: 16px 12px 32px; }
  .shop-container-home { padding: 0; }
}
</style>
