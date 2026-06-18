import { defineAsyncComponent } from 'vue';
import MainLayout from '@/layouts/MainLayout.vue';

const lazy = loader => defineAsyncComponent(loader);

const Login = lazy(() => import('@/modules/auth/pages/Login'));
const AccountProfile = lazy(() => import('@/modules/auth/pages/Account/Profile.vue'));

export const authRoutes = {
  '/login': {
    name: '登录',
    component: Login,
    meta: {
      guestOnly: true
    }
  },
  '/account/profile': {
    name: '个人信息维护',
    component: AccountProfile,
    layout: MainLayout,
    meta: {
      requiresAuth: true
    }
  }
};
