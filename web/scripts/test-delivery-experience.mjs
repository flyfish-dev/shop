import assert from 'node:assert/strict';
import test from 'node:test';
import {
  compactDeliveryExperience,
  normalizeDeliveryExperience,
  parseDeliveryExperience
} from '../src/modules/shop/pages/Shop/hooks/useDeliveryExperienceConfiguration.js';

test('parses delivery experience without changing unrelated product parameters', () => {
  const value = parseDeliveryExperience({
    provider: 'github',
    deliveryExperience: {
      enabled: true,
      title: '从综合 Demo 开始',
      links: [{
        title: 'Office Demo',
        url: 'https://github.com/flyfish-dev/office-render-demo',
        kind: 'repository'
      }]
    }
  });

  assert.equal(value.enabled, true);
  assert.equal(value.links[0].kind, 'REPOSITORY');
  assert.equal(value.links[0].primary, true);
});

test('keeps exactly one primary link in the submitted configuration', () => {
  const value = compactDeliveryExperience(normalizeDeliveryExperience({
    enabled: true,
    links: [
      { title: 'Demo', url: 'https://github.com/flyfish-dev/office-render-demo', primary: true },
      { title: 'Docs', url: 'https://example.com/docs', primary: true }
    ]
  }));

  assert.equal(value.links.filter(link => link.primary).length, 1);
  assert.equal(value.links[0].primary, true);
});

test('rejects non-HTTPS experience links before saving a product', () => {
  assert.throws(() => compactDeliveryExperience({
    enabled: true,
    links: [{ title: 'Unsafe', url: 'http://example.com/demo' }]
  }), /HTTPS/);
});

test('omits disabled experience configuration from product parameters', () => {
  assert.equal(compactDeliveryExperience({ enabled: false }), null);
});
