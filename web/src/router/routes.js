import { defineAsyncComponent } from 'vue';
import { authRoutes } from '@/modules/auth/routes.js';
import { shopRoutes } from '@/modules/shop/routes.js';

const lazy = loader => defineAsyncComponent(loader);

const Legal = lazy(() => import('@/pages/Legal'));
const LegacyWorkspaceLink = lazy(() => import('@/pages/LegacyWorkspaceLink.vue'));
const legacyRoutes = Object.fromEntries([
  'model-design', 'code-generate', 'online-launch', 'integrate-test'
].map(path => [`/${path}`, { component: LegacyWorkspaceLink }]));

const staticRoutes = {
  '/': {
    ...shopRoutes['/shop'],
    children: undefined
  },
  '/privacy': {
    name: '隐私政策',
    component: Legal,
    meta: {
      skipAuthSync: true,
      titleKey: 'legal.privacyTitle'
    }
  },
  '/terms': {
    name: '服务条款',
    component: Legal,
    meta: {
      skipAuthSync: true,
      titleKey: 'legal.termsTitle'
    }
  },
  ...legacyRoutes,
  ...authRoutes,
  ...shopRoutes
};

// 路由映射
export default staticRoutes;
