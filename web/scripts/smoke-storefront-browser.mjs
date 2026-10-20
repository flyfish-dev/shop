import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const output = fileURLToPath(new URL('../target/storefront-smoke/', import.meta.url));
const shop = process.env.FLYFISH_SHOP_PREVIEW_URL || 'http://127.0.0.1:9999';
const lowcode = process.env.FLYFISH_LOWCODE_PREVIEW_URL || 'http://127.0.0.1:9998';
const groups = [{ id: 1, name: 'Documents', enabled: true }, { id: 2, name: 'CAD & 3D', enabled: true }];
const products = Array.from({ length: 6 }, (_, i) => ({
  id: i + 1, name: `Viewer ${i + 1}`, enabled: true, groupId: i < 3 ? 1 : 2,
  recommended: i < 4, type: 'LICENSE', deliveryMode: 'AUTOMATIC', tags: [['Office'], ['Word Editor'], ['DWG'], ['Model Viewer'], ['WPS'], ['CAD']][i],
  price: 899, effectivePrice: 899, effectiveUsdPrice: 129, buyCount: 12,
  cover: '/images/shop/dwg-viewer-commercial-cover.webp'
}));
const user = admin => ({ id: 17, username: 'Preview User', nickname: 'Preview User',
  email: 'fixture@example.test', createTime: '2020-01-01 00:00:00',
  authorizations: admin ? { gitea: { openid: '1' } } : {} });

export async function smokeStorefront(browser) {
  await mkdir(output, { recursive: true });
  const results = [];
  for (const admin of [false, true]) {
    const context = await browser.newContext({ reducedMotion: 'reduce', locale: 'en-US' });
    let currentUser = admin ? user(true) : null;
    let catalogError = false;
    let empty = false;
    const errors = [];
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await context.route('**/*', async route => {
      const url = new URL(route.request().url());
      const path = url.pathname;
      const ok = result => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, result }) });
      if (path === '/portal/users/current') return ok(currentUser);
      if (path.includes('/portal/capabilities')) return ok([{ code: url.port === new URL(lowcode).port ? 'lowcode' : 'shop', path: '/', name: 'Flyfish' }]);
      if (path === '/shops/items') return catalogError
        ? route.fulfill({ status: 503, body: 'Unavailable' }) : ok(empty ? [] : products);
      if (path === '/shops/item-groups') return ok(groups);
      if (/^\/shops\/items\/\d+$/.test(path)) return ok(products.find(p => p.id === Number(path.split('/').at(-1))));
      if (path === '/oauth/providers') return ok({ github: true, email: true, wechat: false });
      if (/^\/(portal|shops|integrity|oauth|wx|email)(\/|$)/.test(path)) return ok([]);
      return route.continue();
    });
    try {
      for (const locale of ['zh-CN', 'en-US']) {
        await page.goto(shop);
        await page.evaluate(value => localStorage.setItem('flyfish.locale', value), locale);
        await page.reload();
        await page.waitForSelector('.featured-section .shop-product-card');
        for (const width of admin ? [320, 1440] : [320, 390, 768, 1920]) {
          await page.setViewportSize({ width, height: width < 640 ? 844 : 1000 });
          const layout = await page.evaluate(() => ({
            overflow: document.documentElement.scrollWidth > innerWidth + 1,
            cards: [...document.querySelectorAll('.featured-section .shop-product-card')].map(el => ({ width: el.offsetWidth, height: el.offsetHeight })),
            headerOverflow: document.querySelector('.store-header').scrollWidth > document.documentElement.clientWidth + 1
          }));
          assert.equal(layout.overflow || layout.headerOverflow, false, `${locale}/${width}/admin=${admin} overflows`);
          assert.equal(new Set(layout.cards.map(card => card.height)).size, 1, 'cards must have stable heights');
          assert.ok(layout.cards.every(card => card.width <= 310));
          assert.equal(new URL(page.url()).pathname, '/', 'admin must not redirect to dashboard');
          assert.equal(await page.locator('.management-toggle').count(), admin ? 1 : 0);
          results.push({ locale, width, admin, ...layout });
          if (locale === 'en-US') await page.screenshot({ path: `${output}${admin ? 'admin' : 'guest'}-${width}.png` });
        }
      }
      await page.locator('.collection-nav button').nth(1).click();
      await page.waitForFunction(() => document.querySelector('#featured-title')?.textContent === 'Document viewers');
      assert.equal(await page.locator('.featured-section').count(), 1);
      assert.equal(await page.locator('.product-row .shop-product-card').count(), 2);
      assert.equal(await page.locator('.product-row').evaluate(row => row.scrollLeft), 0);
      await page.locator('.collection-nav button').first().click();
      await page.locator('.storefront-search input').fill('Office');
      await page.locator('.storefront-search input').press('Enter');
      await page.waitForURL('**/shop/item-list?keyword=Office');
      await page.locator('.store-brand').click();
      await page.waitForSelector('.shop-home');
      if (!admin) {
        await page.goto(`${shop}/account/orders`);
        await page.waitForURL('**/login');
        assert.equal(await page.evaluate(() => localStorage.getItem('redirection')), '/account/orders');
        currentUser = user(false);
        await page.reload();
        await page.waitForURL('**/account/orders');
        assert.equal(await page.evaluate(() => Boolean(localStorage.getItem('redirection'))), false);
        await page.goto(shop);
        await page.waitForSelector('.shop-home');
        assert.equal(new URL(page.url()).pathname, '/');
        currentUser = null;
        catalogError = true;
        await page.reload();
        await page.waitForSelector('.home-load-error');
        catalogError = false;
        await page.locator('.home-load-error button').click();
        await page.waitForSelector('.shop-product-card');
        empty = true;
        await page.reload();
        await page.waitForSelector('.home-empty');
      }
      assert.deepEqual(errors, [], 'no uncaught browser exceptions');
      await page.goto(`${shop}/code-generate?model=42`);
      await page.waitForURL(`${lowcode}/#/code-generate?model=42`);
      await page.goto(`${lowcode}/#/`);
      await page.waitForSelector('.module-card');
      const links = await page.locator('.lowcode-site a').evaluateAll(anchors => anchors.map(anchor => anchor.href));
      assert.ok(links.some(link => link === `${shop}/`), 'workspace links back to shop');
      assert.equal(await page.locator('.shop-product-card').count(), 0);
      currentUser = null;
      await page.goto(`${lowcode}/#/account/profile`);
      await page.reload();
      await page.waitForURL(`${lowcode}/#/login`);
      assert.equal(await page.evaluate(() => localStorage.getItem('redirection')), '/#/account/profile');
      currentUser = user(false);
      await page.reload();
      await page.waitForURL(`${lowcode}/#/account/profile`);
      assert.equal(await page.evaluate(() => Boolean(localStorage.getItem('redirection'))), false);
      assert.deepEqual(errors, [], 'no exceptions in the independent workspace');
    } finally { await context.close(); }
  }
  return { passed: results.length, screenshots: output, results };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { chromium } = await import(process.env.FLYFISH_PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  try { console.log(JSON.stringify(await smokeStorefront(browser), null, 2)); }
  finally { await browser.close(); }
}
