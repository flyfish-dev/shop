(function () {
  'use strict';

  const config = window.__FLYFISH_BOOTSTRAP_CONFIG__;
  if (!config || !config.release || !Array.isArray(config.origins)) {
    throw new Error('Invalid Flyfish static bootstrap configuration');
  }

  const cacheKey = `flyfish:static-origin:${config.release}`;
  const retryKey = `flyfish:static-retry:${config.release}`;
  const cacheTtl = Number(config.cacheTtl) || 6 * 60 * 60 * 1000;
  const probeTimeout = Number(config.probeTimeout) || 2500;
  const target = config.target === 'viewer' ? 'viewer' : 'app';

  const trimSlash = value => String(value || '').replace(/\/+$/, '');
  const normalizedOrigins = Array.from(new Set(config.origins.map(trimSlash).filter(Boolean)));

  const readJson = (storage, key) => {
    try {
      return JSON.parse(storage.getItem(key));
    } catch {
      return null;
    }
  };

  const removeStorage = (storage, key) => {
    try {
      storage.removeItem(key);
    } catch {
      // Storage can be disabled by browser privacy settings.
    }
  };

  const writeJson = (storage, key, value) => {
    try {
      storage.setItem(key, JSON.stringify(value));
    } catch {
      // The selected route remains valid for the current page without persistence.
    }
  };

  const cached = readJson(window.localStorage, cacheKey);
  const failedOrigin = (() => {
    try {
      return window.sessionStorage.getItem(retryKey) || '';
    } catch {
      return '';
    }
  })();
  const cachedOrigin = cached
    && cached.release === config.release
    && Date.now() - Number(cached.selectedAt || 0) < cacheTtl
    && normalizedOrigins.includes(cached.origin)
    && cached.origin !== failedOrigin
      ? cached.origin
      : '';

  const candidates = [
    ...(cachedOrigin ? [cachedOrigin] : []),
    ...normalizedOrigins.filter(origin => origin !== cachedOrigin && origin !== failedOrigin),
    ...normalizedOrigins.filter(origin => origin === failedOrigin)
  ];

  const releaseBase = origin => `${origin}/releases/${encodeURIComponent(config.release)}`;

  const validateAssetPath = value => {
    const path = String(value || '').replace(/^\.\//, '');
    if (!path || path.startsWith('/') || path.includes('..') || /^[a-z][a-z\d+.-]*:/i.test(path)) {
      throw new Error(`Unsafe static asset path: ${value}`);
    }
    return path;
  };

  const normalizeTargetManifest = manifest => {
    const source = target === 'viewer' ? manifest.viewer : manifest;
    if (!source || typeof source !== 'object') {
      throw new Error(`Static manifest does not contain ${target}`);
    }
    return {
      entry: validateAssetPath(source.entry),
      styles: Array.isArray(source.styles) ? source.styles.map(validateAssetPath) : [],
      modulePreloads: Array.isArray(source.modulePreloads)
        ? source.modulePreloads.map(validateAssetPath)
        : []
    };
  };

  const createRoute = (origin, manifest) => {
    if (manifest?.release !== config.release) {
      throw new Error('Static release does not match shell release');
    }
    const baseUrl = releaseBase(origin);
    return {
      origin,
      baseUrl: `${baseUrl}/`,
      manifest,
      targetManifest: normalizeTargetManifest(manifest)
    };
  };

  const embeddedManifest = config.manifest && config.manifest.release === config.release
    ? config.manifest
    : null;

  const probe = async origin => {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), probeTimeout);
    const baseUrl = releaseBase(origin);
    try {
      const response = await window.fetch(`${baseUrl}/bootstrap-manifest.json`, {
        cache: 'no-store',
        credentials: 'omit',
        mode: 'cors',
        signal: controller.signal
      });
      if (!response.ok) {
        throw new Error(`Static origin returned ${response.status}`);
      }
      return createRoute(origin, await response.json());
    } finally {
      window.clearTimeout(timeout);
    }
  };

  const raceCandidates = origins => {
    if (typeof Promise.any === 'function') {
      return Promise.any(origins.map(probe));
    }
    return new Promise((resolve, reject) => {
      let failures = 0;
      origins.forEach(origin => {
        probe(origin).then(resolve).catch(error => {
          failures += 1;
          if (failures === origins.length) {
            reject(error);
          }
        });
      });
    });
  };

  const selectRoute = async () => {
    if (cachedOrigin) {
      if (embeddedManifest) {
        return createRoute(cachedOrigin, embeddedManifest);
      }
      try {
        return await probe(cachedOrigin);
      } catch {
        removeStorage(window.localStorage, cacheKey);
      }
    }
    const racing = candidates.filter(origin => origin !== cachedOrigin && origin !== failedOrigin);
    if (!racing.length) {
      if (failedOrigin && normalizedOrigins.includes(failedOrigin)) {
        return probe(failedOrigin);
      }
      throw new Error('No static origin is configured');
    }
    try {
      return await raceCandidates(racing);
    } catch (error) {
      if (failedOrigin && normalizedOrigins.includes(failedOrigin)) {
        return probe(failedOrigin);
      }
      throw error;
    }
  };

  const assetUrl = (baseUrl, asset) => new URL(validateAssetPath(asset), baseUrl).href;

  const appendLink = (rel, href) => {
    const link = document.createElement('link');
    link.rel = rel;
    link.href = href;
    link.crossOrigin = 'anonymous';
    link.dataset.flyfishStatic = 'true';
    document.head.appendChild(link);
    return link;
  };

  const loadLink = (rel, route, asset) => new Promise((resolve, reject) => {
    const link = appendLink(rel, assetUrl(route.baseUrl, asset));
    link.addEventListener('load', resolve, { once: true });
    link.addEventListener('error', () => reject(new Error(`Failed to load ${link.href}`)), { once: true });
  });

  const loadEntry = async route => {
    // 先确认预加载模块和样式均可从当前线路读取，再执行入口模块，避免线路切换时重复挂载应用。
    await Promise.all([
      ...route.targetManifest.modulePreloads.map(asset => loadLink('modulepreload', route, asset)),
      ...route.targetManifest.styles.map(asset => loadLink('stylesheet', route, asset))
    ]);

    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.type = 'module';
      script.src = assetUrl(route.baseUrl, route.targetManifest.entry);
      script.crossOrigin = 'anonymous';
      script.dataset.flyfishStatic = 'true';
      script.addEventListener('load', resolve, { once: true });
      script.addEventListener('error', () => reject(new Error(`Failed to load ${script.src}`)), { once: true });
      document.head.appendChild(script);
    });
  };

  const removeInjectedAssets = () => {
    document.querySelectorAll('[data-flyfish-static="true"]').forEach(node => node.remove());
  };

  const exposeRuntime = route => {
    window.__FLYFISH_STATIC_RUNTIME__ = Object.freeze({
      release: config.release,
      origin: route.origin,
      baseUrl: route.baseUrl,
      candidates: normalizedOrigins.slice(),
      selectedAt: Date.now()
    });
  };

  const markSelected = route => {
    writeJson(window.localStorage, cacheKey, {
      release: config.release,
      origin: route.origin,
      selectedAt: Date.now()
    });
  };

  const installPreloadRecovery = () => {
    window.addEventListener('vite:preloadError', event => {
      event.preventDefault();
      const origin = window.__FLYFISH_STATIC_RUNTIME__?.origin;
      let retried = '';
      try {
        retried = window.sessionStorage.getItem(retryKey) || '';
      } catch {
        // Ignore unavailable session storage.
      }
      if (!origin || retried) {
        return;
      }
      removeStorage(window.localStorage, cacheKey);
      try {
        window.sessionStorage.setItem(retryKey, origin);
      } catch {
        return;
      }
      window.location.reload();
    });
  };

  const renderFailure = () => {
    const root = document.getElementById(target === 'viewer' ? 'viewer-status' : 'app') || document.body;
    root.innerHTML = '';
    const wrapper = document.createElement('div');
    wrapper.style.cssText = 'min-height:100%;display:grid;place-items:center;padding:24px;box-sizing:border-box;font:14px/1.6 system-ui;color:#43536a;background:#fff';
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = '重新加载';
    button.style.cssText = 'border:1px solid #18a058;border-radius:6px;padding:8px 16px;color:#147a43;background:#fff;cursor:pointer';
    button.addEventListener('click', () => {
      removeStorage(window.localStorage, cacheKey);
      removeStorage(window.sessionStorage, retryKey);
      window.location.reload();
    });
    wrapper.appendChild(button);
    root.appendChild(wrapper);
  };

  const boot = async () => {
    installPreloadRecovery();
    const attempted = new Set();
    while (attempted.size < candidates.length) {
      const available = candidates.filter(origin => !attempted.has(origin));
      let route;
      try {
        route = attempted.size === 0 ? await selectRoute() : await raceCandidates(available);
      } catch {
        break;
      }
      attempted.add(route.origin);
      exposeRuntime(route);
      try {
        await loadEntry(route);
        markSelected(route);
        return;
      } catch {
        removeInjectedAssets();
        removeStorage(window.localStorage, cacheKey);
      }
    }
    renderFailure();
  };

  window.__FLYFISH_STATIC_READY__ = boot();
})();
