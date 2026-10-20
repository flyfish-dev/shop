import { defineAsyncComponent } from 'vue';
import MiniShop from './MiniShop.vue';
const lazy = load => defineAsyncComponent(load);
const Catalog = lazy(() => import('@/modules/shop/pages/Shop/pages/ShopItemList.vue'));
const Detail = lazy(() => import('@/modules/shop/pages/Shop/pages/ShopItemDetail.vue'));
const Orders = lazy(() => import('@/modules/shop/pages/Account/Orders.vue'));
const Tickets = lazy(() => import('@/modules/shop/pages/Account/Tickets.vue'));
export default {
  '/shop': { component: MiniShop, children: {
    '/item-list': { component: Catalog },
    '/detail/:id': { component: Detail }
  } },
  '/account/orders': { component: Orders, meta: { requiresAuth: true } },
  '/account/tickets': { component: Tickets, meta: { requiresAuth: true } },
  '/account/channel-link': { component: lazy(() => import('./AccountLink.vue')), meta: { requiresAuth: true } },
  '/account/native-claim': { component: lazy(() => import('./NativeClaim.vue')), meta: { requiresAuth: true } },
  '/login': { component: Catalog }
};
