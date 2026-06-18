import { computed, ref } from 'vue';
import { PortalCapabilities } from '@/modules/portal/api.js';

const capabilities = ref([]);
const loaded = ref(false);
const loading = ref(false);
let pending = null;

const capabilityCodes = computed(() => new Set(capabilities.value.map(item => item.code)));
const hasLowcode = computed(() => capabilityCodes.value.has('lowcode'));
const hasShop = computed(() => capabilityCodes.value.has('shop'));
const capabilityByCode = computed(() => new Map(capabilities.value.map(item => [item.code, item])));
const lowcodeCapability = computed(() => capabilityByCode.value.get('lowcode') || null);
const shopCapability = computed(() => capabilityByCode.value.get('shop') || null);
const lowcodeEntryPath = computed(() => lowcodeCapability.value?.path || '/');
const shopEntryName = computed(() => shopCapability.value?.name || '应用入口');
const shopEntryPath = computed(() => shopCapability.value?.path || '/');
const portalTitle = computed(() => {
  if (hasShop.value && !hasLowcode.value) {
    return shopCapability.value?.name || '飞鱼';
  }
  return lowcodeCapability.value?.name || capabilities.value[0]?.name || '飞鱼';
});
const defaultEntryPath = computed(() => {
  if (lowcodeCapability.value) {
    return lowcodeEntryPath.value;
  }
  if (shopCapability.value) {
    return shopEntryPath.value;
  }
  return '/';
});

const loadPortalCapabilities = async ({ force = false } = {}) => {
  if (!force && loaded.value) {
    return capabilities.value;
  }
  if (!force && pending) {
    return pending;
  }
  loading.value = true;
  pending = PortalCapabilities.get()
    .then(result => {
      capabilities.value = Array.isArray(result) ? result : [];
      loaded.value = true;
      return capabilities.value;
    })
    .finally(() => {
      pending = null;
      loading.value = false;
    });
  return pending;
};

export const usePortalCapabilities = () => {
  return {
    capabilities,
    capabilityCodes,
    capabilityByCode,
    lowcodeCapability,
    shopCapability,
    lowcodeEntryPath,
    shopEntryName,
    shopEntryPath,
    hasLowcode,
    hasShop,
    portalTitle,
    defaultEntryPath,
    loaded,
    loading,
    loadPortalCapabilities
  };
};
