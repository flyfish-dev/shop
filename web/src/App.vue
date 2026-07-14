<script setup>
import enUS from 'ant-design-vue/es/locale/en_US';
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { useDebounceFn } from '@vueuse/core';
import { useI18n } from 'vue-i18n';
import useClientStore from '@/modules/auth/store/client.js';
import { useRoute, useRouter } from '@/router/use.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const AUTH_STORAGE_KEYS = new Set(['access_token', 'auth_changed_at']);

const store = useClientStore();
const router = useRouter();
const route = useRoute();
const { locale, t } = useI18n();
const { hasLowcode, hasShop, loaded: capabilitiesLoaded, portalTitle, loadPortalCapabilities } = usePortalCapabilities();
const antLocale = computed(() => locale.value === 'en-US' ? enUS : zhCN);
const appTitle = computed(() => {
  if (route.meta?.titleKey) {
    return t(route.meta.titleKey);
  }
  if (hasShop.value && !hasLowcode.value) {
    return t('brand.shopTitle');
  }
  if (hasLowcode.value) {
    return t('brand.lowcodeTitle');
  }
  return portalTitle.value || t('brand.defaultTitle');
});
const requiredCapability = computed(() => route.matched?.map(item => item.meta?.capability).find(Boolean));
const capabilityAllowed = computed(() => {
  if (!requiredCapability.value) {
    return true;
  }
  if (!capabilitiesLoaded.value) {
    return false;
  }
  return requiredCapability.value === 'lowcode' ? hasLowcode.value
    : requiredCapability.value === 'shop' ? hasShop.value
      : false;
});

const syncAuth = async () => {
  if (requiredCapability.value && !capabilitiesLoaded.value) {
    await loadPortalCapabilities().catch(() => {});
  }
  if (!capabilityAllowed.value) {
    return;
  }
  store.syncRouteAuth(router);
};

const syncExternalAuth = async () => {
  const tokenChanged = store.syncTokenFromStorage();
  if (tokenChanged) {
    await store.refreshUser();
    syncAuth();
    return;
  }
  syncAuth();
};

const syncExternalAuthDebounced = useDebounceFn(syncExternalAuth, 50);

const handleStorage = event => {
  if (AUTH_STORAGE_KEYS.has(event.key)) {
    syncExternalAuthDebounced();
  }
};

onMounted(() => {
  syncAuth();
  window.addEventListener('storage', handleStorage);
});

onBeforeUnmount(() => {
  window.removeEventListener('storage', handleStorage);
});

watch(() => route.fullPath, syncAuth);
watch(() => store.token, syncAuth);
watch(() => store.user?.id, syncAuth);
watch(appTitle, title => {
  document.title = title;
}, { immediate: true });
</script>

<template>
  <a-config-provider :locale="antLocale">
    <router-view />
  </a-config-provider>
</template>

<style lang='less'>
body, html, #app {
  width: 100%;
  padding: 0;
  margin: 0;
}

#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

.flyfish-command-dropdown {
  .ant-dropdown-menu {
    padding: 8px;
    border: 1px solid rgba(79, 110, 145, .12);
    border-radius: 12px;
    background: rgba(255, 255, 255, .98);
    box-shadow: 0 18px 42px rgba(33, 68, 104, .16);
    backdrop-filter: blur(14px);
  }

  .ant-dropdown-menu-item,
  .ant-dropdown-menu-submenu-title {
    min-height: 38px;
    padding: 8px 10px;
    border-radius: 9px;
    color: #2b4058;
    font-weight: 600;
    line-height: 1.3;
  }

  .ant-dropdown-menu-item:hover,
  .ant-dropdown-menu-submenu-title:hover,
  .ant-dropdown-menu-item-selected {
    background: #f2f7ff;
    color: #1677ff;
  }

  .ant-dropdown-menu-item-danger {
    color: #d4380d;

    &:hover {
      background: #fff2ee;
      color: #d4380d;
    }
  }

  .ant-dropdown-menu-item-icon {
    color: #5d728a;
  }
}

</style>
