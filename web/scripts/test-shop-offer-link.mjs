import assert from 'node:assert/strict';
import test from 'node:test';
import { createSSRApp, h, ref } from 'vue';
import { createPinia } from 'pinia';
import { renderToString } from 'vue/server-renderer';
import { createServer } from 'vite';

test('loads SKU options when the offer-link modal first mounts open', async () => {
  const originalFetch = globalThis.fetch;
  const windowDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const localStorageDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const storage = new Map();
  const localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: key => storage.delete(key)
  };
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorage });
  globalThis.window = {
    localStorage,
    location: { pathname: '/shop/manage/items', search: '', hash: '' },
    document: { body: { getBoundingClientRect: () => ({ width: 1280 }) } },
    addEventListener: () => {},
    scrollY: 0
  };
  const requested = [];
  globalThis.fetch = async url => {
    requested.push(url);
    const result = url === '/shops/items/1018'
      ? {
          id: 1018,
          skuMode: 'MULTI',
          skus: [
            { id: 34, name: '部署版', price: 899, enabled: true },
            { id: 35, name: '商业源码版', price: 3999, enabled: true },
            { id: 36, name: '企业版', price: 6999, enabled: true }
          ]
        }
      : [];
    return new Response(JSON.stringify({ success: true, result }), {
      headers: { 'Content-Type': 'application/json' }
    });
  };

  const vite = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' });
  try {
    const { useShopOfferLinkManage } = await vite.ssrLoadModule(
      '/src/modules/shop/pages/Shop/hooks/useShopOfferLinkManage.js'
    );
    let offerLink;
    const app = createSSRApp({
      setup() {
        offerLink = useShopOfferLinkManage({
          item: ref({ id: 1018 }),
          open: ref(true)
        });
        return () => h('div');
      }
    });
    app.use(createPinia());
    await renderToString(app);
    await new Promise(resolve => setTimeout(resolve, 0));

    assert.deepEqual(requested, [
      '/shops/items/1018',
      '/shops/managements/items/1018/offer-links'
    ]);
    assert.equal(offerLink.skuRequired.value, true);
    assert.deepEqual(offerLink.skuOptions.value.map(option => option.value), [34, 35, 36]);
  } finally {
    await vite.close();
    globalThis.fetch = originalFetch;
    if (windowDescriptor) {
      Object.defineProperty(globalThis, 'window', windowDescriptor);
    } else {
      delete globalThis.window;
    }
    if (localStorageDescriptor) {
      Object.defineProperty(globalThis, 'localStorage', localStorageDescriptor);
    } else {
      delete globalThis.localStorage;
    }
  }
});
