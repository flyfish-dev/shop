import { defineAsyncComponent } from 'vue';
import MainLayout from '@/layouts/MainLayout.vue';
import { authRoutes } from '@/modules/auth/routes.js';
import { modules } from '@/modules/lowcode/routes.js';
import { shopRoutes } from '@/modules/shop/routes.js';

const lazy = loader => defineAsyncComponent(loader);

const Home = lazy(() => import('@/pages/Home'));

const buildRoutes = list => {
  return list.reduce((res, item, index) => {
    const { children, code, ...rest } = item;
    const route = {
      ...rest,
      index,
      meta: {
        ...(rest.meta || {}),
        capability: 'lowcode'
      }
    };
    res[`/${code}`] = route;
    if (children) {
      route.children = buildRoutes(children);
    }
    return res;
  }, {});
};

const staticRoutes = {
  '/': {
    name: '首页',
    // 精确命中时，展示组件，如果没指定layout，也命中组件
    component: Home,
    // 多级命中时，展示布局
    layout: MainLayout,
    // 构建下级路由
    children: buildRoutes(modules)
  },
  ...authRoutes,
  ...shopRoutes
};

// 路由映射
export default staticRoutes;
