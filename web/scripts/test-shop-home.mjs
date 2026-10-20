import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { buildOfferLinkPayload } from '../src/modules/shop/utils/shopOfferLink.js';
import {
  buildShopHomeSections,
  selectFeaturedShopItems,
  sortShopGroups
} from '../src/modules/shop/utils/shopHome.js';

const groups = [
  { id: 1, name: 'Repositories', sort: 20, enabled: true },
  { id: 2, name: 'Licenses', sort: 10, enabled: true },
  { id: 3, name: 'Hidden', sort: 0, enabled: false }
];

const items = [
  { id: 11, groupId: 1, enabled: true, recommended: true },
  { id: 12, groupId: 1, enabled: true },
  { id: 13, groupId: 1, enabled: true },
  { id: 14, groupId: 1, enabled: true },
  { id: 21, groupId: 2, enabled: true, pinned: true },
  { id: 31, groupId: 3, enabled: true },
  { id: 99, groupId: 2, enabled: false, recommended: true }
];

test('shop groups are enabled and sorted without mutating input', () => {
  const snapshot = structuredClone(groups);
  assert.deepEqual(sortShopGroups(groups).map(group => group.id), [2, 1]);
  assert.deepEqual(groups, snapshot);
});

test('shop home groups products and limits each shelf', () => {
  const sections = buildShopHomeSections(groups, items, 3);
  assert.deepEqual(sections.map(group => group.id), [2, 1]);
  assert.equal(sections[0].itemCount, 1);
  assert.deepEqual(sections[1].items.map(item => item.id), [11, 12, 13]);
  assert.equal(sections[1].itemCount, 4);
});

test('shop entry always renders the storefront without an admin redirect', () => {
  const entryPath = fileURLToPath(new URL(
    '../src/modules/shop/pages/Shop/pages/ShopEntry.vue',
    import.meta.url
  ));
  const source = readFileSync(entryPath, 'utf8');
  assert.match(source, /<shop-home\s*\/>/);
  assert.doesNotMatch(source, /isShopMaintainer|loadUser|router\.replace/);
});

test('shop home cards keep a responsive visual width instead of stretching fixed columns', () => {
  const homePath = fileURLToPath(new URL(
    '../src/modules/shop/pages/Shop/pages/ShopHome.vue',
    import.meta.url
  ));
  const source = readFileSync(homePath, 'utf8');
  const rowStyles = source.slice(source.indexOf('.product-row,'), source.indexOf('.product-skeletons {\n  min-height'));
  assert.match(rowStyles, /display:\s*flex/);
  assert.match(rowStyles, /flex-wrap:\s*wrap/);
  assert.match(rowStyles, /flex:\s*1\s+1\s+var\(--home-card-min-width\)/);
  assert.match(rowStyles, /--home-card-max-width:\s*310px/);
  assert.match(rowStyles, /justify-content:\s*center/);
  assert.doesNotMatch(rowStyles, /repeat\(3,/);
});

test('featured products prefer recommended then pinned products', () => {
  assert.deepEqual(selectFeaturedShopItems(items, 3).map(item => item.id), [11, 21, 12]);
});

test('signed and paid fulfillment links submit audit fields without promotion fields', () => {
  const payload = buildOfferLinkPayload({
    name: 'Paid customer delivery',
    skuId: 8,
    purpose: 'SIGNED_PAID_FULFILLMENT',
    recipientUserId: 100,
    contractReference: 'CONTRACT-1',
    paymentReference: 'PAYMENT-1',
    confirmationNote: 'Verified',
    settlementAmount: 4300,
    settlementCurrency: 'CNY',
    validDays: 5,
    type: 'FREE',
    discountValue: 1
  });
  assert.equal(payload.settlementAmount, 4300);
  assert.equal(payload.recipientUserId, 100);
  assert.equal('type' in payload, false);
  assert.equal('discountValue' in payload, false);
});

test('signed and paid fulfillment links support an unregistered named recipient', () => {
  const payload = buildOfferLinkPayload({
    name: 'Zhejiang Hongyi delivery',
    skuId: 8,
    purpose: 'SIGNED_PAID_FULFILLMENT',
    recipientName: '浙江鸿翼',
    contractReference: 'CONTRACT-2',
    paymentReference: 'PAYMENT-2',
    settlementAmount: 4999,
    settlementCurrency: 'CNY',
    validDays: 5
  });
  assert.equal(payload.recipientUserId, undefined);
  assert.equal(payload.recipientName, '浙江鸿翼');
});

test('product cover keeps a fixed ratio independent of image intrinsic dimensions', () => {
  const cardPath = fileURLToPath(new URL(
    '../src/modules/shop/pages/Shop/components/ShopProductCard.vue',
    import.meta.url
  ));
  const source = readFileSync(cardPath, 'utf8');
  const coverStyles = source.slice(source.indexOf('  .cover-box {'), source.indexOf('  .status-badges {'));
  assert.match(coverStyles, /aspect-ratio:\s*1\.32\s*\/\s*1/);
  assert.match(coverStyles, /overflow:\s*hidden/);
  assert.match(coverStyles, /position:\s*absolute/);
  assert.match(coverStyles, /inset:\s*0/);
});
