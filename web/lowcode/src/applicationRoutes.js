import { defineAsyncComponent } from 'vue';
import { modules } from './routes.js';
import Layout from './layouts/LowcodeLayout.vue';

const lazy = loader => defineAsyncComponent(loader);
const buildRoutes = items => Object.fromEntries(items.map(({ children, code, ...item }) => [
  `/${code}`, {
    ...item,
    meta: { capability: 'lowcode' },
    ...(children ? { children: buildRoutes(children) } : {})
  }
]));

export default {
  '/': { component: lazy(() => import('./Home.vue')), layout: Layout, children: buildRoutes(modules) },
  '/login': { component: lazy(() => import('@/modules/auth/pages/Login')), meta: { guestOnly: true } },
  '/account/profile': { component: lazy(() => import('@/modules/auth/pages/Account/Profile.vue')), layout: Layout, meta: { requiresAuth: true } },
  '/privacy': { component: lazy(() => import('@/pages/Legal')), meta: { skipAuthSync: true, titleKey: 'legal.privacyTitle' } },
  '/terms': { component: lazy(() => import('@/pages/Legal')), meta: { skipAuthSync: true, titleKey: 'legal.termsTitle' } }
};
