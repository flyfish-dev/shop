import assert from 'node:assert/strict';
import test from 'node:test';
import enUS from '../src/i18n/locales/en-US.js';
import { parseShopOrderFormConfig } from '../src/modules/shop/utils/shopOrderForm.js';

const readMessage = key => key.split('.').reduce((value, part) => value?.[part], enUS);
const t = (key, params = {}) => {
  const value = readMessage(key);
  if (typeof value !== 'string') {
    return key;
  }
  return Object.entries(params).reduce(
    (text, [name, replacement]) => text.replaceAll(`{${name}}`, String(replacement)),
    value
  );
};

const paramsWithForm = orderForm => JSON.stringify({ orderForm });

test('localizes the deployed Word Editor origin form', () => {
  const config = parseShopOrderFormConfig(paramsWithForm({
    enabled: true,
    title: '部署授权信息',
    description: '用于签发生产部署运行授权。',
    fields: [{
      key: 'origin',
      label: '授权部署域名',
      type: 'url',
      required: true,
      target: 'license.allowedOrigins',
      normalize: 'origin',
      placeholder: 'https://example.com',
      help: '填写正式部署访问地址。'
    }]
  }), { locale: 'en-US', t });

  assert.equal(config.title, 'Deployment License Details');
  assert.equal(config.fields[0].label, 'Licensed Production Origin');
  assert.match(config.description, /production origin/i);
  assert.match(config.fields[0].help, /Local development URLs/i);
});

test('localizes existing website and license declaration forms', () => {
  const website = parseShopOrderFormConfig(paramsWithForm({
    enabled: true,
    title: '授权网站',
    fields: [{
      key: 'websiteOrigin',
      label: '非盈利性网站地址',
      type: 'url',
      required: true,
      target: 'license.allowedOrigins'
    }, {
      key: 'nonCommercialCommitment',
      label: '我承诺仅用于非商业化用途',
      type: 'checkbox',
      required: true
    }]
  }), { locale: 'en-US', t });
  assert.equal(website.title, 'Licensed Website');
  assert.equal(website.fields[0].label, 'Non-commercial Website URL');
  assert.match(website.fields[1].label, /non-commercial purposes/i);

  const declaration = parseShopOrderFormConfig(paramsWithForm({
    enabled: true,
    title: '授权声明信息',
    fields: [{ key: 'licenseeName', label: '授权主体', type: 'text', required: true }]
  }), { locale: 'en-US', t });
  assert.equal(declaration.title, 'License Declaration Details');
  assert.equal(declaration.fields[0].label, 'License Holder or Company Name');
});

test('prefers explicit form translations over semantic fallbacks', () => {
  const config = parseShopOrderFormConfig(paramsWithForm({
    enabled: true,
    title: '中文标题',
    fields: [{ key: 'origin', label: '域名', type: 'url', target: 'license.allowedOrigins' }],
    i18n: {
      'en-US': {
        title: 'Custom License Setup',
        fields: {
          origin: { label: 'Custom Origin' }
        }
      }
    }
  }), { locale: 'en-US', t });

  assert.equal(config.title, 'Custom License Setup');
  assert.equal(config.fields[0].label, 'Custom Origin');
});
