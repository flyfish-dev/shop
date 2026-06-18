import { computed, ref, shallowReactive, watch } from 'vue';
import routes from '@/router/routes';
import { last, splitPath } from '@/utils/utils';
import { parse, stringify } from 'qs';
import RouterLink from '@/components/RouterLink/index.vue';
import RouterView from '@/components/RouterView/index.vue';
import { ROUTE_KEY, ROUTER_KEY } from '@/router/use';

const PATH_PARAM_PREFIX = '/:';
const ROUTE_PROPS = {
  path: '/',
  name: undefined,
  params: {},
  query: {},
  hash: '',
  fullPath: '/',
  matched: [],
  meta: {},
  redirectedFrom: undefined
};

const { location, history } = window;

const cleanQuery = query => Object.entries(query || {})
  .filter(([, value]) => value !== undefined && value !== null && value !== '')
  .reduce((result, [key, value]) => {
    result[key] = value;
    return result;
  }, {});

export const resolveRouteLocation = (target = '/', params = {}) => {
  const rawTarget = typeof target === 'string' ? target : target?.path || '/';
  const normalizedTarget = rawTarget.startsWith('/') || rawTarget.startsWith('http')
    ? rawTarget
    : `/${rawTarget}`;
  const url = new URL(normalizedTarget, location.origin);
  const external = url.origin !== location.origin;
  const targetQuery = typeof target === 'string' ? params : { ...(target.query || {}), ...params };
  const query = cleanQuery({
    ...parse(url.search.substring(1)),
    ...targetQuery
  });
  const queryString = stringify(query);
  const hash = typeof target === 'string' ? url.hash : (target.hash || url.hash || '');
  const path = url.pathname || '/';
  const fullPath = external ? url.href : `${path}${queryString ? `?${queryString}` : ''}${hash}`;
  return {
    external,
    path,
    fullPath,
    query,
    hash
  };
};

const readBrowserLocation = () => resolveRouteLocation(`${location.pathname}${location.search}${location.hash}`);

// 初始化逻辑
const setup = () => {

  const routeState = ref(readBrowserLocation());
  // 当前路由地址，保留给既有代码使用；它只表示 pathname，不混入 query/hash
  const currentRoute = computed(() => routeState.value.path);
  // 初始化返回事件监听
  window.addEventListener('popstate', () => routeState.value = readBrowserLocation());

  // 调用路由
  const doRoute = (operation, target) => {
    const next = resolveRouteLocation(target);
    if (next.external) {
      location.assign(next.fullPath);
      return;
    }
    // 判断，防止重复push
    if (next.fullPath !== routeState.value.fullPath) {
      const previousPath = routeState.value.path;
      routeState.value = next;
      // 目前简单处理，无论什么路由都跳转
      history[operation](null, '', next.fullPath);
      if (next.path !== previousPath) {
        window.scrollTo({ top: 0, left: 0 });
      }
    }
  };

  /**
   * 路由匹配
   * 约定：路径参数使用/:起手，跟在当前路由名后，允许带路径参数的路由有子路由，可以自由渲染
   * @param route
   * @param routes
   * @returns {*[]}
   */
  const routeMatch = (route, routes) => {
    // 直接匹配优先级最高，尝试匹配
    if (routes[route]) {
      return [routes[route]];
    }
    // 逐个匹配
    const paths = splitPath(route, routes);
    const founds = [];
    // 是否存在路径参数，存在则写入当前层的值
    let last = null;
    // 循环路径uri集合，匹配命中路由
    for (let i = 0; i < paths.length; i++) {
      const path = paths[i];
      if (!path) continue;
      // 如果存在last，代表读取路径参数，读取完则continue，不再匹配
      if (last) {
        last.params = { ...(last.params || {}), [last.pathCode]: path.substring(1) };
        // 尝试继续，判断下级路由
        if (last.children) {
          routes = last.children;
          continue;
        }
        last = null;
        break;
      }
      let found;
      // 根据路径直接匹配，存在直接命中，继续
      if (routes[path]) {
        found = { ...routes[path] };
      } else {
        // 遍历当前层级路由集，自动命中路径参数
        const matched = Object.keys(routes).find(code => code.includes(PATH_PARAM_PREFIX));
        // 路径参数命中，保存当前路由，并跳过下一路由
        if (matched) {
          // 优先注入路由参数，并继续向下查找路由命中
          const start = matched.indexOf(PATH_PARAM_PREFIX) + PATH_PARAM_PREFIX.length;
          found = { ...routes[matched], pathCode: matched.substring(start) };
          // 写入路径参数
          last = found;
        }
      }
      // 仅当匹配到才往下，否则不再匹配
      if (found) {
        founds.push(found);
        // 判断子路由
        if (found.children) {
          routes = found.children;
          continue;
        }
        // 如果有路径参数，继续循环
        if (last) {
          continue;
        }
      }
      break;
    }
    return founds;
  };

  const currentRoutes = computed(() => routeMatch(routeState.value.path, routes));

  // 路由对象，本身是异步对象
  const rawRoute = computed(() => ({
    ...ROUTE_PROPS,
    ...(last(currentRoutes.value) || {}),
    path: routeState.value.path,
    query: routeState.value.query,
    hash: routeState.value.hash,
    fullPath: routeState.value.fullPath,
    params: last(currentRoutes.value)?.params || {},
    matched: currentRoutes.value
  }));

  // 定义异步的route
  const route = {};
  for (const key in ROUTE_PROPS) {
    Object.defineProperty(route, key, {
      get: () => rawRoute.value[key],
      enumerable: true
    });
  }

  const router = {
    // 当前路由地址
    currentRoute,
    // 当前路由组件
    currentRoutes,
    // 基层匹配路由
    get route() {
      return rawRoute.value;
    },
    // 前进
    push: (path, params) => doRoute('pushState', resolveRouteLocation(path, params)),
    // 后退
    back: step => history.go(-step),
    // 替换
    replace: (path, params) => doRoute('replaceState', resolveRouteLocation(path, params)),
    resolve: resolveRouteLocation
  };

// 检查有效的路由，遇到路由存在子级，自动跳转第一个
  const checkRoute = currentRoutes => {
    if (currentRoutes.length) {
      const { children, layout } = last(currentRoutes);
      if (children && !layout) {
        const first = Object.keys(children)[0];
        // 替换到当前路径
        router.replace(`${routeState.value.path}${first}`, routeState.value.query);
      }
    }
  };

  // 初始化时检查路由
  checkRoute(currentRoutes.value);
  // 监听路由变动
  watch(currentRoutes, checkRoute);

  return {
    router,
    route
  };
};


// 路由安装器
const Installer = {
  install: app => {
    const { router, route } = setup();
    // 设置全局选项
    Object.assign(app.config.globalProperties, {
      $router: router
    });

    app.component('router-link', RouterLink);
    app.component('router-view', RouterView);
    app.provide(ROUTER_KEY, router);
    app.provide(ROUTE_KEY, shallowReactive(route));
  }
};

export default Installer;

export * from './use';
