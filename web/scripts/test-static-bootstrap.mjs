import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { staticAssetUrl } from '../src/utils/staticAssets.js';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const runtimeSource = await readFile(path.join(projectRoot, 'scripts', 'static-bootstrap-runtime.js'), 'utf8');
const origins = ['https://static-cn.example', 'https://static.example'];
const release = 'test-release';

class MemoryStorage {
  constructor(values = {}) {
    this.values = new Map(Object.entries(values));
  }

  getItem(key) {
    return this.values.has(key) ? this.values.get(key) : null;
  }

  setItem(key, value) {
    this.values.set(key, String(value));
  }

  removeItem(key) {
    this.values.delete(key);
  }
}

function createElement(tagName, documentNodes) {
  const listeners = new Map();
  const children = [];
  return {
    tagName: tagName.toUpperCase(),
    dataset: {},
    style: {},
    children,
    addEventListener(type, listener) {
      listeners.set(type, listener);
    },
    appendChild(child) {
      children.push(child);
    },
    remove() {
      const index = documentNodes.indexOf(this);
      if (index >= 0) {
        documentNodes.splice(index, 1);
      }
    },
    dispatch(type, event = {}) {
      listeners.get(type)?.(event);
    }
  };
}

async function runBootstrap({
  delays = {},
  failingOrigins = [],
  scriptFailingOrigins = [],
  localStorage = new MemoryStorage(),
  sessionStorage = new MemoryStorage()
} = {}) {
  const windowListeners = new Map();
  const documentNodes = [];
  const fetchCalls = [];
  let reloads = 0;
  const appRoot = createElement('div', documentNodes);
  const document = {
    head: {
      appendChild(node) {
        documentNodes.push(node);
        if (node.tagName === 'SCRIPT' || node.rel === 'stylesheet' || node.rel === 'modulepreload') {
          const href = node.src || node.href;
          const assetOrigin = origins.find(candidate => href.startsWith(candidate));
          queueMicrotask(() => node.dispatch(scriptFailingOrigins.includes(assetOrigin) ? 'error' : 'load'));
        }
      }
    },
    body: createElement('body', documentNodes),
    createElement: tagName => createElement(tagName, documentNodes),
    createTextNode: text => ({ textContent: text }),
    getElementById: id => id === 'app' ? appRoot : null,
    querySelectorAll: selector => selector === '[data-flyfish-static="true"]'
      ? documentNodes.filter(node => node.dataset?.flyfishStatic === 'true')
      : []
  };
  const window = {
    __FLYFISH_BOOTSTRAP_CONFIG__: {
      release,
      target: 'app',
      origins,
      cacheTtl: 6 * 60 * 60 * 1000,
      probeTimeout: 500,
      manifest: {
        release,
        entry: 'assets/index-test.js',
        styles: ['assets/index-test.css'],
        modulePreloads: ['assets/vendor-vue-test.js'],
        viewer: {
          entry: 'file-viewer/assets/index-test.js',
          styles: [],
          modulePreloads: []
        }
      }
    },
    localStorage,
    sessionStorage,
    location: {
      reload() {
        reloads += 1;
      }
    },
    setTimeout,
    clearTimeout,
    addEventListener(type, listener) {
      windowListeners.set(type, listener);
    },
    async fetch(url) {
      const origin = origins.find(candidate => url.startsWith(candidate));
      fetchCalls.push(origin);
      await new Promise(resolve => setTimeout(resolve, delays[origin] || 0));
      if (failingOrigins.includes(origin)) {
        throw new Error(`unreachable ${origin}`);
      }
      return {
        ok: true,
        async json() {
          return {
            release,
            entry: 'assets/index-test.js',
            styles: ['assets/index-test.css'],
            modulePreloads: ['assets/vendor-vue-test.js'],
            viewer: {
              entry: 'file-viewer/assets/index-test.js',
              styles: [],
              modulePreloads: []
            }
          };
        }
      };
    }
  };

  vm.runInNewContext(runtimeSource, {
    window,
    document,
    URL,
    AbortController,
    Promise,
    Error,
    Date,
    JSON,
    Object,
    Array,
    Set,
    RegExp,
    Number,
    String,
    console,
    queueMicrotask
  });
  await window.__FLYFISH_STATIC_READY__;

  return {
    window,
    fetchCalls,
    localStorage,
    sessionStorage,
    reloads: () => reloads,
    dispatchWindow(type, event) {
      windowListeners.get(type)?.(event);
    }
  };
}

test('first visit selects the fastest healthy static origin', async () => {
  const result = await runBootstrap({
    delays: {
      [origins[0]]: 30,
      [origins[1]]: 5
    }
  });
  assert.equal(result.window.__FLYFISH_STATIC_RUNTIME__.origin, origins[1]);
  assert.deepEqual(new Set(result.fetchCalls), new Set(origins));
});

test('valid six-hour cache loads the embedded manifest without another probe', async () => {
  const localStorage = new MemoryStorage({
    [`flyfish:static-origin:${release}`]: JSON.stringify({
      release,
      origin: origins[0],
      selectedAt: Date.now()
    })
  });
  const result = await runBootstrap({ localStorage });
  assert.equal(result.window.__FLYFISH_STATIC_RUNTIME__.origin, origins[0]);
  assert.deepEqual(result.fetchCalls, []);
});

test('unreachable direct origin falls back to the global origin', async () => {
  const result = await runBootstrap({ failingOrigins: [origins[0]] });
  assert.equal(result.window.__FLYFISH_STATIC_RUNTIME__.origin, origins[1]);
});

test('asset failure after a successful probe switches to the other origin', async () => {
  const result = await runBootstrap({
    delays: { [origins[0]]: 1, [origins[1]]: 20 },
    scriptFailingOrigins: [origins[0]]
  });
  assert.equal(result.window.__FLYFISH_STATIC_RUNTIME__.origin, origins[1]);
});

test('vite preload failure reloads once and excludes the failed route', async () => {
  const localStorage = new MemoryStorage();
  const sessionStorage = new MemoryStorage();
  const first = await runBootstrap({
    delays: { [origins[0]]: 1, [origins[1]]: 20 },
    localStorage,
    sessionStorage
  });
  let prevented = false;
  first.dispatchWindow('vite:preloadError', {
    preventDefault() {
      prevented = true;
    }
  });
  assert.equal(prevented, true);
  assert.equal(first.reloads(), 1);

  const second = await runBootstrap({ localStorage, sessionStorage });
  assert.equal(second.window.__FLYFISH_STATIC_RUNTIME__.origin, origins[1]);
  second.dispatchWindow('vite:preloadError', { preventDefault() {} });
  assert.equal(second.reloads(), 0);
});

test('only public product images use the selected static origin', () => {
  globalThis.window = {
    __FLYFISH_STATIC_RUNTIME__: {
      origin: origins[1]
    }
  };
  try {
    assert.equal(staticAssetUrl('/images/2026/08/hash/cover.png'), `${origins[1]}/images/2026/08/hash/cover.png`);
    assert.equal(staticAssetUrl('/shops/orders/O1/delivery-files/1'), '/shops/orders/O1/delivery-files/1');
    assert.equal(staticAssetUrl('https://example.com/cover.png'), 'https://example.com/cover.png');
  } finally {
    delete globalThis.window;
  }
});

test('bundled images use immutable release paths without redirecting uploaded files', () => {
  globalThis.window = { __FLYFISH_STATIC_RUNTIME__: {
    origin: origins[1], baseUrl: `${origins[1]}/releases/test-release/`
  } };
  try {
    assert.equal(staticAssetUrl('/images/shop/dwg-viewer-commercial-cover.webp'), `${origins[1]}/releases/test-release/images/shop/dwg-viewer-commercial-cover.webp`);
    assert.equal(staticAssetUrl('/images/shop/customer-upload.png'), `${origins[1]}/images/shop/customer-upload.png`);
    assert.equal(staticAssetUrl('/images/contact/customer-wechat.jpg?v=1'), `${origins[1]}/releases/test-release/images/contact/customer-wechat.jpg?v=1`);
    assert.equal(staticAssetUrl('/shops/orders/O1/delivery-files/1'), '/shops/orders/O1/delivery-files/1');
  } finally { delete globalThis.window; }
});
