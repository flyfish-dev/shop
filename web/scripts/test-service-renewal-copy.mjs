import assert from 'node:assert/strict';
import test from 'node:test';
import { createSSRApp, h, ref } from 'vue';
import { createI18n } from 'vue-i18n';
import { renderToString } from 'vue/server-renderer';
import { createServer } from 'vite';
import zhCN from '../src/i18n/locales/zh-CN.js';
import enUS from '../src/i18n/locales/en-US.js';

test('renewal copy separates service terms from existing perpetual licenses in both languages', () => {
  for (const locale of [zhCN, enUS]) {
    const copy = locale.shop.licenseRenewal;
    assert.doesNotMatch(JSON.stringify(copy), /授权续费|续费授权|license renewal|renew license|license to renew/i);
    assert.match(copy.description, /永久授权|perpetual license/i);
    assert.match(copy.startDate, /服务|service/i);
    assert.match(copy.years, /服务|service/i);
  }
});

test('known server reason codes use current locale copy without exposing legacy license-renewal wording', async () => {
  const windowDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const storageDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  const localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorage });
  globalThis.window = { localStorage, location: { pathname: '/shop/detail/1', search: '', hash: '' }, addEventListener: () => {} };
  const vite = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' });
  try {
    const { useLicenseRenewal } = await vite.ssrLoadModule('/src/modules/shop/pages/Shop/hooks/useLicenseRenewal.js');
    let renewal;
    const i18n = createI18n({ legacy: false, locale: 'en-US', messages: { 'zh-CN': zhCN, 'en-US': enUS } });
    const app = createSSRApp({
      setup() {
        renewal = useLicenseRenewal({ item: ref({ id: 1, params: '{}' }), user: ref(null) });
        return () => h('div');
      }
    });
    app.use(i18n);
    await renderToString(app);
    renewal.renewalEligibility.value = { reasonCode: 'LICENSE_RENEWAL_NOT_ELIGIBLE', message: '购买授权续费前，需要先持有授权' };
    assert.equal(renewal.renewalUnavailableMessage.value, enUS.shop.licenseRenewal.notEligible);
    i18n.global.locale.value = 'zh-CN';
    assert.equal(renewal.renewalUnavailableMessage.value, zhCN.shop.licenseRenewal.notEligible);
    renewal.renewalEligibility.value = { reasonCode: 'NOT_LICENSE_RENEWAL_SKU', message: '当前套餐不是授权续费' };
    assert.equal(renewal.renewalUnavailableMessage.value, zhCN.shop.licenseRenewal.notRenewal);
    renewal.renewalEligibility.value = { reasonCode: 'SERVICE_UNAVAILABLE', message: 'Retry later' };
    assert.equal(renewal.renewalUnavailableMessage.value, 'Retry later');
  } finally {
    await vite.close();
    if (windowDescriptor) Object.defineProperty(globalThis, 'window', windowDescriptor);
    else delete globalThis.window;
    if (storageDescriptor) Object.defineProperty(globalThis, 'localStorage', storageDescriptor);
    else delete globalThis.localStorage;
  }
});
