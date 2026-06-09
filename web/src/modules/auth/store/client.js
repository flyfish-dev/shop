import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { PortalUsers } from '@/modules/auth/api.js';
import { useLocalStorage } from '@vueuse/core';

const ACCESS_TOKEN_KEY = 'access_token';
const AUTH_CHANGED_AT_KEY = 'auth_changed_at';

const normalizeToken = value => value || '';
const readStoredToken = () => window.localStorage.getItem(ACCESS_TOKEN_KEY) || '';
const publishAuthChange = () => window.localStorage.setItem(AUTH_CHANGED_AT_KEY, String(Date.now()));

const useClientStore = defineStore('client', () => {
  const token = ref(readStoredToken());
  const redirection = useLocalStorage('redirection', '');

  const width = ref(getClientWidth());
  const user = ref(null);
  const initialized = ref(false);
  const loadingUser = ref(false);
  const currentPath = ref(window.location.pathname);
  const scrollY = ref(window.scrollY);
  let scrollTicking = false;
  let loadUserTask = null;
  let authRevision = 0;

  window.addEventListener('resize', e => {
    width.value = getClientWidth()
  })

  const updateScrollY = () => {
    scrollY.value = window.scrollY;
  }

  window.addEventListener('scroll', () => {
    if (scrollTicking) {
      return;
    }
    scrollTicking = true;
    window.requestAnimationFrame(() => {
      updateScrollY();
      scrollTicking = false;
    });
  }, { passive: true })

  function getClientWidth(target = window.document.body) {
    const { width } = target.getBoundingClientRect();
    return width;
  }

  const topbarProgress = computed(() => {
    if (currentPath.value !== '/') {
      return 1;
    }
    const collapsedHeight = width.value < 640 ? 112 : 72;
    const range = 445 - collapsedHeight;
    return Math.min(1, Math.max(0, scrollY.value / range));
  });

  const isAuthenticated = computed(() => Boolean(user.value?.id > 0));

  const writeStoredToken = normalizedToken => {
    if (normalizedToken) {
      window.localStorage.setItem(ACCESS_TOKEN_KEY, normalizedToken);
      return;
    }
    window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  };

  const updateTokenState = normalizedToken => {
    if (token.value === normalizedToken) {
      return false;
    }
    authRevision += 1;
    token.value = normalizedToken;
    return true;
  };

  const setToken = (nextToken, options = {}) => {
    const { broadcast = true } = options;
    const normalizedToken = normalizeToken(nextToken);
    const storedToken = readStoredToken();
    if (token.value === normalizedToken && storedToken === normalizedToken) {
      return false;
    }
    updateTokenState(normalizedToken);
    if (storedToken !== normalizedToken) {
      writeStoredToken(normalizedToken);
    }
    if (broadcast) {
      publishAuthChange();
    }
    return true;
  };

  const syncTokenFromStorage = () => updateTokenState(readStoredToken());

  const clearAuth = () => {
    setToken('');
    user.value = null;
    initialized.value = true;
  };

  const shouldRememberRedirect = path => {
    return path && path !== '/login' && !path.startsWith('/oauth/');
  };

  const rememberRedirect = (path = `${window.location.pathname}${window.location.search}${window.location.hash}`) => {
    if (shouldRememberRedirect(path)) {
      redirection.value = path;
      window.localStorage.setItem('redirection', path);
    }
  };

  const consumeRedirect = fallback => {
    const target = redirection.value || fallback || '/';
    redirection.value = '';
    window.localStorage.removeItem('redirection');
    return target;
  };

  const routeRequiresAuth = route => route?.matched?.some(item => item?.meta?.requiresAuth);
  const routeGuestOnly = route => route?.matched?.some(item => item?.meta?.guestOnly);

  const loadUser = async (options = {}) => {
    const { force = false, retryOnTokenRace = true } = options;
    if (!force && initialized.value) {
      return user.value;
    }
    if (loadUserTask && (!force || loadUserTask.revision === authRevision)) {
      return loadUserTask.promise;
    }
    loadingUser.value = true;
    const requestRevision = authRevision;
    const requestToken = token.value || '';
    const task = {
      revision: requestRevision,
      promise: null
    };
    task.promise = (async () => {
      try {
        const result = await PortalUsers.current({ authRedirect: false });
        if (requestRevision !== authRevision) {
          return user.value;
        }
        user.value = result?.id && result.id > 0 ? result : null;
        if (!user.value) {
          const latestToken = readStoredToken();
          if (retryOnTokenRace && latestToken && latestToken !== requestToken) {
            setToken(latestToken);
            return loadUser({ force: true, retryOnTokenRace: false });
          }
          clearAuth();
        }
        return user.value;
      } catch (e) {
        if (requestRevision === authRevision) {
          user.value = null;
        }
        return null;
      } finally {
        if (loadUserTask === task) {
          initialized.value = true;
          loadingUser.value = false;
          loadUserTask = null;
        }
      }
    })();
    loadUserTask = task;
    return task.promise;
  };

  const refreshUser = () => loadUser({ force: true });

  const completeLogin = async (nextToken, router, fallback = '/') => {
    if (!nextToken) {
      return;
    }
    setToken(nextToken);
    await refreshUser();
    const target = consumeRedirect(fallback);
    router?.replace?.(target);
  };

  const syncRouteAuth = async router => {
    if (!router) {
      return;
    }
    if (!initialized.value) {
      await loadUser();
    }

    const route = router.route;
    currentPath.value = route.path;
    if (routeGuestOnly(route) && isAuthenticated.value) {
      router.replace('/');
      return;
    }
    if (routeRequiresAuth(route) && !isAuthenticated.value) {
      rememberRedirect(route.fullPath);
      router.replace('/login');
    }
  };

  const logout = async router => {
    try {
      await PortalUsers.logout({ authRedirect: false });
    } catch (e) {
      // 本地退出优先，后端 token 拉黑失败时也不能阻塞用户离开。
    } finally {
      clearAuth();
      window.localStorage.removeItem('redirection');
      router?.replace?.('/login');
    }
  };

  return {
    width,
    user,
    token,
    redirection,
    initialized,
    loadingUser,
    isAuthenticated,
    currentPath,
    scrollY,
    topbarProgress,
    collapsed: computed(() => topbarProgress.value > 0.96),
    setToken,
    syncTokenFromStorage,
    clearAuth,
    rememberRedirect,
    consumeRedirect,
    loadUser,
    refreshUser,
    completeLogin,
    syncRouteAuth,
    logout,
  }
})

export default useClientStore;
