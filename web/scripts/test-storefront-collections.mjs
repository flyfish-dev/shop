import assert from 'node:assert/strict';
import { readFileSync, statSync } from 'node:fs';
import test from 'node:test';
import { buildStorefrontCollections, selectStorefrontHighlights, storefrontCollectionKeys } from '../src/modules/shop/utils/storefrontCollections.js';

test('discovery honors license metadata without depending on product IDs or display names', () => {
  assert.deepEqual(storefrontCollectionKeys({ id: 98765, name: 'Custom title', defaultSku: { params: JSON.stringify({ licenseDelivery: { product: 'model-viewer' } }) } }), ['models']);
  assert.deepEqual(storefrontCollectionKeys({ params: { product: 'dwg-viewer' } }), ['cad']);
  assert.deepEqual(storefrontCollectionKeys({ params: '{invalid', tags: ['CAD'] }), ['cad']);
});

test('existing Chinese, English and serialized tags produce useful collections', () => {
  assert.deepEqual(storefrontCollectionKeys({ tags: ['["Word / Excel / PPT"', '"源码"]'] }), ['documents']);
  assert.deepEqual(storefrontCollectionKeys({ name: 'Flyfish Word Editor', tags: ['DOCX'] }), ['editing']);
  assert.deepEqual(storefrontCollectionKeys({ tags: ['三维预览', '多格式'] }), ['models']);
  assert.deepEqual(storefrontCollectionKeys({ i18n: { 'en-US': { tags: ['DWG Viewer'] } } }), ['cad']);
  assert.deepEqual(storefrontCollectionKeys({ name: 'Combined tools', tags: ['Office Preview', 'DWG Viewer', 'Model Viewer'] }), ['documents', 'cad', 'models']);
});

test('services, donations, disabled items and unclassified tools do not pollute capability tabs', () => {
  for (const item of [{ type: 'SERVICE_PACKAGE' }, { type: 'DONATION' }, { enabled: false }]) {
    assert.deepEqual(storefrontCollectionKeys({ ...item, name: 'Office Preview' }), []);
  }
  assert.deepEqual(storefrontCollectionKeys({ name: 'Unknown tool', params: 'null' }), []);
});

const items = [
  { id: 1, tags: ['Office'], recommended: true },
  { id: 2, tags: ['Word Editor'], recommended: true },
  { id: 3, name: 'Office setup', type: 'SERVICE_PACKAGE', recommended: true },
  { id: 4, tags: ['DWG'], recommended: true },
  { id: 5, tags: ['WPS'], recommended: true },
  { id: 6, tags: ['Model Viewer'], recommended: true },
  { id: 7, tags: ['Office', 'DWG', 'Model Viewer'], recommended: true },
  { id: 8, tags: ['CAD'], recommended: true, enabled: false }
];

test('selected products cover capabilities, prefer individual products, and never repeat bundles', () => {
  const original = structuredClone(items);
  assert.deepEqual(selectStorefrontHighlights(items).map(item => item.id), [1, 4, 6, 2]);
  assert.deepEqual(selectStorefrontHighlights([items[6], items[3]]).map(item => item.id), [7, 4]);
  assert.deepEqual(items, original);
  assert.deepEqual(selectStorefrontHighlights([]), []);
});

test('collections keep configured recommendation order, counts, limits and deduplication', () => {
  const collections = buildStorefrontCollections([...items, items[0]], 2);
  assert.deepEqual(collections.map(value => value.key), ['documents', 'editing', 'cad', 'models']);
  assert.deepEqual(collections[0].items.map(item => item.id), [1, 5]);
  assert.equal(collections[0].itemCount, 3);
  assert.deepEqual(buildStorefrontCollections([]), []);
});

test('hero is a real versioned image with translated editable copy, search and safe demo link', () => {
  const source = readFileSync(new URL('../src/modules/shop/pages/Shop/components/StorefrontHero.vue', import.meta.url), 'utf8');
  assert.match(source, /import heroImage from '@\/assets\/shop\/storefront-hero.webp'/);
  assert.match(source, /fetchpriority="high"/);
  assert.match(source, /prefers-reduced-motion/);
  assert.match(source, /rel="noopener noreferrer"/);
  assert.match(source, /@submit.prevent="emit\('search'\)"/);
  assert.ok(statSync(new URL('../src/assets/shop/storefront-hero.webp', import.meta.url)).size < 120000);
});

test('collection changes reset the mobile rail without changing catalog card layout', () => {
  const home = readFileSync(new URL('../src/modules/shop/pages/Shop/pages/ShopHome.vue', import.meta.url), 'utf8');
  const card = readFileSync(new URL('../src/modules/shop/pages/Shop/components/ShopProductCard.vue', import.meta.url), 'utf8');
  assert.match(home, /transition-group v-else :key="activeCollection"/);
  assert.match(home, /aria-controls="storefront-products"/);
  assert.match(card, /&\.shop-product-card-home\s*\{\s*display: flex;\s*height: auto;/);
});

test('storefront sign-in has its own accessible style and reuses existing redirect logic', () => {
  const header = readFileSync(new URL('../src/layouts/StorefrontHeader.vue', import.meta.url), 'utf8');
  const user = readFileSync(new URL('../src/components/UserInfo/index.vue', import.meta.url), 'utf8');
  assert.match(header, /<user-info compact appearance="storefront"/);
  assert.match(user, /:aria-label='loginLabel'/);
  assert.match(user, /\.user-container-storefront/);
  assert.match(user, /store.rememberRedirect\(router.route.fullPath\)/);
  assert.match(user, /router.push\('\/login'\)/);
});
