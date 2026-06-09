<script>
/**
 * 超级简单的路由
 */
import { computed, h, inject, onMounted, provide, unref } from 'vue';
import { useRouter } from '@/router/use';
import NotFound from '@/pages/NotFound/index.vue';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

export default {
  name: 'RouterView',
  setup() {
    const router = useRouter();
    const { hasLowcode, hasShop, loaded, loadPortalCapabilities } = usePortalCapabilities();
    // 上一个路由深度
    const injectedDepth = inject('routerDepth', 0);
    const requiredCapability = computed(() => {
      return unref(router.currentRoutes)
        .map(route => route.meta?.capability)
        .find(Boolean);
    });
    const capabilityAllowed = computed(() => {
      const capability = requiredCapability.value;
      if (!capability) {
        return true;
      }
      if (!loaded.value) {
        return false;
      }
      return capability === 'lowcode' ? hasLowcode.value
        : capability === 'shop' ? hasShop.value
          : false;
    });
    // 实际路由表
    const matched = computed(() => {
      const all = unref(router.currentRoutes);
      const single = all.length === 1;
      // 单独命中时，自动应用布局+组件。多级命中，自动渲染布局路由（布局路由拥有两种职能）
      return all.flatMap(({ layout, children, component, ...route }) =>
        single && layout ? [ { ...route, component: layout }, { ...route, component } ]
          : [ {  ...route, component: layout || component } ]
      );
    })
    // 当前深度
    const depth = computed(() => {
      // const routes = matched.value;
      // 深度判定
      let initialDepth = unref(injectedDepth);
      // let matchedRoute;
      // while ((matchedRoute = routes[initialDepth]) && matchedRoute.children && initialDepth + 1 < routes.length) {
      //   initialDepth++;
      //   debugger
      // }
      return initialDepth;
    });
    // 匹配路由
    const matchedRouteRef = computed(() => matched.value[depth.value]);

    provide('routerDepth', computed(() => depth.value + 1));

    onMounted(() => {
      if (requiredCapability.value) {
        loadPortalCapabilities().catch(() => {});
      }
    });

    // 渲染
    return () => {
      if (requiredCapability.value && !loaded.value) {
        return h('div', { class: 'route-loading' });
      }
      if (!capabilityAllowed.value) {
        return h(NotFound);
      }
      return h(matchedRouteRef.value?.component || NotFound);
    };
  }
};
</script>

<style scoped>

</style>
