import { get } from '@/network/request.js';

export const PortalCapabilities = {
  get: async () => get('/portal/capabilities')
};
