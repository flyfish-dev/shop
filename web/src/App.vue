<script setup>
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { useDebounceFn } from '@vueuse/core';
import useClientStore from '@/modules/auth/store/client.js';
import { useRoute, useRouter } from '@/router/use.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const AUTH_STORAGE_KEYS = new Set(['access_token', 'auth_changed_at']);

const store = useClientStore();
const router = useRouter();
const route = useRoute();
const { hasLowcode, hasShop, loaded: capabilitiesLoaded, portalTitle, loadPortalCapabilities } = usePortalCapabilities();
const appTitle = computed(() => portalTitle.value);
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
  <a-config-provider :locale="zhCN">
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

</style>
