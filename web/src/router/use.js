import { inject } from 'vue';

// 路由注入key
export const ROUTER_KEY = 'router';

export const ROUTE_KEY = 'route';

/**
 * 提供给setup函数使用
 * @returns router 路由实例
 */
export function useRouter() {
  return inject(ROUTER_KEY);
}

/**
 * 提供给setup函数使用
 * @returns route 当前路由信息
 */
export function useRoute() {
  return inject(ROUTE_KEY);
}
