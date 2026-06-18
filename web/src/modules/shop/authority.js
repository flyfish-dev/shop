import { isMaintainer } from '@/modules/auth/authority.js';

export function isShopMaintainer(user) {
  return isMaintainer(user);
}
