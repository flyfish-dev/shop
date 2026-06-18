import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { PortalUsers } from '@/modules/auth/api.js';
import { useLocalStorage } from '@vueuse/core';

const ACCESS_TOKEN_KEY = 'access_token';
const AUTH_CHANGED_AT_KEY = 'auth_changed_at';
const REDIRECTION_KEY = 'redirection';
const OAUTH_LOGIN_REDIRECT_KEY = 'flyfish_oauth_login_redirect';
const PROFILE_RETURN_KEY = 'flyfish_profile_return';
const PROFILE_PROMPT_ACK_PREFIX = 'flyfish_profile_prompt_ack:';
const NEW_USER_PROFILE_PROMPT_WINDOW = 30 * 60 * 1000;
const OAUTH_LOGIN_REDIRECT_TTL = 10 * 60 * 1000;

const normalizeToken = value => value || '';
const readStoredToken = () => window.localStorage.getItem(ACCESS_TOKEN_KEY) || '';
const publishAuthChange = () => window.localStorage.setItem(AUTH_CHANGED_AT_KEY, String(Date.now()));

const safeInternalPath = (value, fallback = '/') => {
  const path = (value || '').trim();
  if (path && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/oauth/')) {
    return path;
  }
  return fallback;
};

const parseDateTime = value => {
  if (!value) {
    return 0;
  }
  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value;
    return new Date(year, month - 1, day, hour, minute, second).getTime();
  }
  const normalized = String(value).replace(' ', 'T');
  const time = new Date(normalized).getTime();
  return Number.isFinite(time) ? time : 0;
};

const pathsEqual = (left, right) => safeInternalPath(left) === safeInternalPath(right);

const useClientStore = defineStore('client', () => {
  const token = ref(readStoredToken());
  const redirection = useLocalStorage(REDIRECTION_KEY, '');
  const profileReturnPath = useLocalStorage(PROFILE_RETURN_KEY, '');

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
    const target = safeInternalPath(path, '');
    if (shouldRememberRedirect(target)) {
      redirection.value = target;
      window.localStorage.setItem(REDIRECTION_KEY, target);
    }
  };

  const peekRedirect = fallback => safeInternalPath(redirection.value, fallback);

  const consumeRedirect = fallback => {
    const target = peekRedirect(fallback || '/');
    redirection.value = '';
    window.localStorage.removeItem(REDIRECTION_KEY);
    return target;
  };

  const clearRedirectIfMatches = path => {
    if (redirection.value && pathsEqual(redirection.value, path)) {
      consumeRedirect('/');
    }
  };

  const rememberOAuthLoginRedirect = (path = redirection.value || `${window.location.pathname}${window.location.search}${window.location.hash}`) => {
    const target = safeInternalPath(path, '/');
    window.localStorage.setItem(OAUTH_LOGIN_REDIRECT_KEY, JSON.stringify({
      path: target,
      createdAt: Date.now(),
      ttl: OAUTH_LOGIN_REDIRECT_TTL
    }));
    return target;
  };

  const clearOAuthLoginRedirect = () => {
    window.localStorage.removeItem(OAUTH_LOGIN_REDIRECT_KEY);
  };

  const setProfileReturnPath = path => {
    const target = safeInternalPath(path, '/');
    profileReturnPath.value = target;
    window.localStorage.setItem(PROFILE_RETURN_KEY, target);
    return target;
  };

  const clearProfileReturnPath = () => {
    profileReturnPath.value = '';
    window.localStorage.removeItem(PROFILE_RETURN_KEY);
  };

  const profilePromptAckKey = userId => `${PROFILE_PROMPT_ACK_PREFIX}${userId}`;

  const markProfilePromptSeen = (currentUser = user.value) => {
    if (currentUser?.id) {
      window.localStorage.setItem(profilePromptAckKey(currentUser.id), String(Date.now()));
    }
  };

  const isRecentlyCreatedUser = currentUser => {
    const createdAt = parseDateTime(currentUser?.createTime);
    if (!createdAt) {
      return false;
    }
    return Date.now() - createdAt >= 0 && Date.now() - createdAt <= NEW_USER_PROFILE_PROMPT_WINDOW;
  };

  const shouldPromptProfileCompletion = (currentUser = user.value) => {
    if (!currentUser?.id || !isRecentlyCreatedUser(currentUser)) {
      return false;
    }
    return !window.localStorage.getItem(profilePromptAckKey(currentUser.id));
  };

  const profileTarget = fallback => {
    const target = consumeRedirect(fallback || '/');
    setProfileReturnPath(target);
    markProfilePromptSeen();
    return '/account/profile?welcome=1';
  };

  const resolvePostLoginTarget = (currentUser, fallback = '/') => {
    const target = peekRedirect(fallback || '/');
    if (shouldPromptProfileCompletion(currentUser)) {
      return profileTarget(target);
    }
    consumeRedirect(target);
    return target;
  };

  const returnToProfileOrigin = router => {
    const target = safeInternalPath(profileReturnPath.value, '/');
    clearProfileReturnPath();
    markProfilePromptSeen();
    router?.replace?.(target);
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
    const currentUser = await refreshUser();
    const target = resolvePostLoginTarget(currentUser, fallback);
    router?.replace?.(target);
  };

  const redirectAfterAuthentication = (router, fallback = '/') => {
    const target = resolvePostLoginTarget(user.value, fallback);
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
      redirectAfterAuthentication(router, '/');
      return;
    }
    if (routeRequiresAuth(route) && !isAuthenticated.value) {
      rememberRedirect(route.fullPath);
      router.replace('/login');
      return;
    }
    if (isAuthenticated.value && route.path !== '/account/profile') {
      const target = peekRedirect(route.fullPath);
      if (shouldPromptProfileCompletion()) {
        router.replace(profileTarget(target));
        return;
      }
      clearRedirectIfMatches(route.fullPath);
    }
  };

  const logout = async router => {
    try {
      await PortalUsers.logout({ authRedirect: false });
    } catch (e) {
      // 本地退出优先，后端 token 拉黑失败时也不能阻塞用户离开。
    } finally {
      clearAuth();
      window.localStorage.removeItem(REDIRECTION_KEY);
      clearOAuthLoginRedirect();
      clearProfileReturnPath();
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
    profileReturnPath,
    isAuthenticated,
    currentPath,
    scrollY,
    topbarProgress,
    collapsed: computed(() => topbarProgress.value > 0.96),
    setToken,
    syncTokenFromStorage,
    clearAuth,
    rememberRedirect,
    rememberOAuthLoginRedirect,
    clearOAuthLoginRedirect,
    consumeRedirect,
    peekRedirect,
    setProfileReturnPath,
    clearProfileReturnPath,
    markProfilePromptSeen,
    returnToProfileOrigin,
    loadUser,
    refreshUser,
    completeLogin,
    redirectAfterAuthentication,
    syncRouteAuth,
    logout,
  }
})

export default useClientStore;
