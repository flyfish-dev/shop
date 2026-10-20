import { get } from '@/network/request.js';
import { isLowcodeApp } from '@/runtime/application.js';

const SPLIT_CAPABILITY_ENDPOINTS = [
  isLowcodeApp ? '/__lowcode/portal/capabilities' : '/__shop/portal/capabilities'
];

const normalizeCapabilities = result => Array.isArray(result)
  ? result.filter(item => item?.code === (isLowcodeApp ? 'lowcode' : 'shop')) : [];

const mergeCapabilities = results => {
  const merged = new Map();
  results
    .flatMap(normalizeCapabilities)
    .forEach(item => {
      if (item?.code && !merged.has(item.code)) {
        merged.set(item.code, item);
      }
    });
  return Array.from(merged.values());
};

const loadOptionalCapabilities = endpoint => get(endpoint, { authRedirect: false })
  .then(normalizeCapabilities)
  .catch(() => []);

export const PortalCapabilities = {
  get: async () => {
    const splitCapabilities = mergeCapabilities(
      await Promise.all(SPLIT_CAPABILITY_ENDPOINTS.map(loadOptionalCapabilities))
    );
    if (splitCapabilities.length > 0) {
      return splitCapabilities;
    }
    return normalizeCapabilities(await get('/portal/capabilities', { authRedirect: false }));
  }
};
