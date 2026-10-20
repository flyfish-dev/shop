import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { applicationBoundaries, forbiddenApplicationModule } from './application-boundaries.mjs';
import { normalizeShopTags } from '../src/modules/shop/utils/shopTags.js';
import { lowcodeLink, isSeparateApplicationPath } from '../src/runtime/application.js';

const source = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');
globalThis.window = { location: { origin: 'https://dev.flyfish.group' } };

test('legacy workspace links preserve deep routes and queries without credentials', () => {
  assert.equal(lowcodeLink('/model-design/42?step=3'), 'https://dev.flyfish.group/lowcode/#/model-design/42?step=3');
  assert.equal(lowcodeLink('/code-generate', 'https://studio.example.com/'), 'https://studio.example.com/#/code-generate');
  assert.equal(lowcodeLink('/'), 'https://dev.flyfish.group/lowcode/#/');
});

test('standalone paths trigger full navigation while shop and similarly named paths do not', () => {
  assert.equal(isSeparateApplicationPath('/lowcode/'), true);
  assert.equal(isSeparateApplicationPath('/lowcode'), true);
  assert.equal(isSeparateApplicationPath('/lowcode/index.html'), true);
  assert.equal(isSeparateApplicationPath('/lowcode-other'), false);
  assert.equal(isSeparateApplicationPath('/shop/detail/1007'), false);
});

test('actual build graph rejects importing code from the other application', () => {
  assert.equal(forbiddenApplicationModule('/web/lowcode/src/Home.vue', 'shop'), true);
  assert.equal(forbiddenApplicationModule('/node_modules/@surely-vue/table/index.js', 'shop'), true);
  assert.equal(forbiddenApplicationModule('/web/src/modules/shop/routes.js', 'lowcode'), true);
  assert.equal(forbiddenApplicationModule('/web/src/modules/auth/api.js', 'lowcode'), false);
  for (const target of ['shop', 'lowcode']) {
    const plugin = applicationBoundaries();
    plugin.configResolved({ define: { 'import.meta.env.VITE_APP_TARGET': JSON.stringify(target) } });
    const forbidden = target === 'shop' ? '/web/lowcode/src/Home.vue' : '/web/src/modules/shop/routes.js';
    const context = { getModuleIds: () => [forbidden], error: message => { throw new Error(message); } };
    assert.throws(() => plugin.generateBundle.call(context), /crosses application boundaries/);
  }
});

test('home is the public shop for every role and lowcode owns its routes and table plugin', () => {
  const routes = source('src/router/routes.js');
  assert.match(routes, /'\/':\s*\{\s*\.\.\.shopRoutes\['\/shop'\]/);
  assert.doesNotMatch(routes, /isMaintainer|isShopMaintainer|@lowcode/);
  assert.doesNotMatch(source('src/bootstrap.js'), /surely-vue|\.\/license/);
  assert.match(source('lowcode/src/main.js'), /surely-vue/);
  assert.match(source('lowcode/src/applicationRoutes.js'), /\.\/routes.js/);
  assert.doesNotMatch(source('lowcode/src/applicationRoutes.js'), /modules\/shop/);
});

test('hash navigation and authentication keep the separate application path', () => {
  assert.match(source('src/router/index.js'), /hashRouting = (?:isXianyuRuntime \|\| )?isLowcodeApp/);
  assert.match(source('src/components/RouterLink/index.vue'), /router.href\(href.value\)/);
  assert.match(source('src/modules/auth/store/client.js'), /applicationPath\(target\)/);
  assert.match(source('src/modules/auth/pages/Account/Profile.vue'), /applicationPath\('\/account\/profile'\)/);
  assert.doesNotMatch(source('lowcode/src/Home.vue'), /consumeRedirect/);
});

test('product labels normalize serialized arrays without damaging ordinary labels', () => {
  assert.deepEqual(normalizeShopTags(['["Word / Excel / PPT"', '"Commercial"', '"Source"]']), ['Word / Excel / PPT', 'Commercial', 'Source']);
  assert.deepEqual(normalizeShopTags('["Office","CAD"]'), ['Office', 'CAD']);
  assert.deepEqual(normalizeShopTags([' [Beta] ', 'Office', '', 'Office']), ['[Beta]', 'Office']);
  assert.deepEqual(normalizeShopTags(null), []);
});

test('home motion is optional and releases its observer', () => {
  const hook = source('src/modules/shop/pages/Shop/hooks/useStorefrontMotion.js');
  assert.match(hook, /prefers-reduced-motion/);
  assert.match(hook, /onBeforeUnmount/);
  assert.match(hook, /disconnect\(/);
});
