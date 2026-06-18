import { get } from '@/network/request.js';

export const getPortalWorkbench = async () => {
  return get('/portal/workbench', {
    credential: true
  });
};
