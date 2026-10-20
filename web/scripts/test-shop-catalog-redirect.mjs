import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveShopCatalogRedirect } from '../src/modules/shop/utils/shopCatalogRedirect.js';

const archived = {
  id: 52,
  params: JSON.stringify({ catalogRedirect: { itemId: 50, skuId: 62, skuIds: { 63: 61 } } })
};

test('catalog consolidation preserves offer and coupon while selecting the original edition', () => {
  const result = new URL(resolveShopCatalogRedirect(archived, { offer: 'one-use-link', coupon: 'private' }), 'https://shop.test');
  assert.equal(result.pathname, '/shop/detail/50');
  assert.equal(result.searchParams.get('sku'), '62');
  assert.equal(result.searchParams.get('offer'), 'one-use-link');
  assert.equal(result.searchParams.get('coupon'), 'private');
});

test('retired renewal links select the remaining renewal SKU', () => {
  assert.equal(resolveShopCatalogRedirect(archived, { sku: '63' }), '/shop/detail/50?sku=61');
  assert.equal(resolveShopCatalogRedirect(archived, { sku: '62' }), '/shop/detail/50?sku=62');
});

test('normal products and malformed or external destinations do not redirect', () => {
  for (const params of ['{', '{}', JSON.stringify({ catalogRedirect: { itemId: 52 } }), JSON.stringify({ catalogRedirect: { itemId: '//evil.test' } })]) {
    assert.equal(resolveShopCatalogRedirect({ id: 52, params }), null);
  }
});
