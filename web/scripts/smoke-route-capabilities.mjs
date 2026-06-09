import { readdir, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const srcRoot = path.join(projectRoot, 'src');
const distRoot = path.join(projectRoot, 'dist');
const assetsRoot = path.join(distRoot, 'assets');
const indexPath = path.join(distRoot, 'index.html');

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

async function source(relativePath) {
  return readFile(path.join(projectRoot, relativePath), 'utf8');
}

function assertIncludes(sourceText, needle, label) {
  if (!sourceText.includes(needle)) {
    fail(`${label} must include ${needle}`);
  }
}

function assertNotIncludes(sourceText, needle, label) {
  if (sourceText.includes(needle)) {
    fail(`${label} must not include ${needle}`);
  }
}

function assertBefore(sourceText, first, second, label) {
  const firstIndex = sourceText.indexOf(first);
  const secondIndex = sourceText.indexOf(second);
  if (firstIndex < 0 || secondIndex < 0 || firstIndex > secondIndex) {
    fail(`${label} must evaluate ${first} before ${second}`);
  }
}

function attrValues(sourceText, attribute) {
  const pattern = new RegExp(`${attribute}="([^"]+)"`, 'g');
  return Array.from(sourceText.matchAll(pattern), match => match[1]);
}

function findAsset(assets, pattern, label) {
  const asset = assets.find(file => pattern.test(file));
  if (!asset) {
    fail(`${label} chunk was not emitted`);
  }
  return asset;
}

async function assetSource(asset) {
  return readFile(path.join(assetsRoot, asset), 'utf8');
}

function jsImports(sourceText) {
  const imports = [];
  const staticPattern = /from"\.\/([^"]+\.js)"/g;
  const dynamicPattern = /import\("\.\/([^"]+\.js)"\)/g;
  const preloadPattern = /"assets\/([^"]+\.js)"/g;
  for (const pattern of [staticPattern, dynamicPattern, preloadPattern]) {
    for (const match of sourceText.matchAll(pattern)) {
      imports.push(match[1]);
    }
  }
  return Array.from(new Set(imports));
}

function assertRouteImportsStayInModule(sourceText, moduleName, label) {
  const dynamicImports = Array.from(sourceText.matchAll(/import\(['"]([^'"]+)['"]\)/g), match => match[1]);
  const unexpected = dynamicImports.filter(specifier => !specifier.startsWith(`@/modules/${moduleName}/`));
  if (unexpected.length > 0) {
    fail(`${label} has route imports outside ${moduleName}: ${unexpected.join(', ')}`);
  }
}

function assertBusinessImplementationAbsent(sourceText, forbiddenPatterns, label) {
  const hits = forbiddenPatterns
    .filter(pattern => pattern.test(sourceText))
    .map(String);
  if (hits.length > 0) {
    fail(`${label} contains forbidden business markers: ${hits.join(', ')}`);
  }
}

if (!existsSync(srcRoot)) {
  fail(`missing src directory: ${srcRoot}`);
}
if (!existsSync(indexPath) || !existsSync(assetsRoot)) {
  fail('missing dist output; run npm run build first');
}

const [
  appSource,
  homeSource,
  portalCapabilitiesSource,
  topBarStateSource,
  routerViewSource,
  rootRoutesSource,
  lowcodeRoutesSource,
  shopRoutesSource,
  shopAccountLayoutSource,
  userInfoSource
] = await Promise.all([
  source('src/App.vue'),
  source('src/pages/Home/index.vue'),
  source('src/modules/portal/usePortalCapabilities.js'),
  source('src/layouts/hooks/useTopBarState.js'),
  source('src/components/RouterView/index.vue'),
  source('src/router/routes.js'),
  source('src/modules/lowcode/routes.js'),
  source('src/modules/shop/routes.js'),
  source('src/modules/shop/layouts/ShopAccountLayout.vue'),
  source('src/components/UserInfo/index.vue')
]);

assertIncludes(rootRoutesSource, "capability: 'lowcode'", 'root lowcode route builder');
assertIncludes(shopRoutesSource, "capability: 'shop'", 'shop route metadata');
assertIncludes(shopRoutesSource, 'ShopAccountLayout', 'shop account routes');
assertRouteImportsStayInModule(lowcodeRoutesSource, 'lowcode', 'lowcode routes');
assertRouteImportsStayInModule(shopRoutesSource, 'shop', 'shop routes');

assertNotIncludes(appSource, 'CustomerServiceWidget', 'App shell');
assertNotIncludes(appSource, '@/modules/shop/', 'App shell');
assertIncludes(appSource, 'portalTitle', 'App shell title');
assertNotIncludes(appSource, '飞鱼小铺', 'App shell');
assertNotIncludes(appSource, '飞鱼低代码平台', 'App shell');
assertBefore(appSource, 'if (!capabilityAllowed.value)', 'store.syncRouteAuth(router)', 'App auth sync');
assertIncludes(portalCapabilitiesSource, 'defaultEntryPath', 'portal capability state');
assertNotIncludes(portalCapabilitiesSource, '飞鱼小铺', 'portal capability state');
assertNotIncludes(portalCapabilitiesSource, '飞鱼低代码平台', 'portal capability state');
assertNotIncludes(portalCapabilitiesSource, '/shop/item-list', 'portal capability state');
assertNotIncludes(portalCapabilitiesSource, '/model-design', 'portal capability state');
assertIncludes(topBarStateSource, 'defaultEntryPath', 'topbar start action');
assertNotIncludes(topBarStateSource, "router.push('/model-design')", 'topbar start action');
assertNotIncludes(topBarStateSource, "router.push('/shop/item-list')", 'topbar start action');
assertNotIncludes(topBarStateSource, '飞鱼 - 低代码开发平台', 'topbar title');

assertNotIncludes(homeSource, '@/router/routes', 'Home page');
assertIncludes(homeSource, "import('@/modules/lowcode/nav.js')", 'Home lowcode nav');
assertBefore(homeSource, 'if (!hasLowcode.value || lowcodeModules.value.length > 0)', "import('@/modules/lowcode/nav.js')", 'Home lowcode nav');
assertIncludes(homeSource, "import('@/modules/lowcode/components/LowcodeWorkbench.vue')", 'Home lowcode workbench');
assertIncludes(homeSource, "import('@/modules/shop/components/MarketEntry.vue')", 'Home shop entry component');
assertNotIncludes(homeSource, '/portal/workbench', 'Home page');
assertNotIncludes(homeSource, 'workbench?.shop', 'Home page');
assertNotIncludes(homeSource, '/shop/manage/items', 'Home page');
assertIncludes(homeSource, 'shopEntryName', 'Home shop entry');
assertIncludes(homeSource, 'shopEntryPath', 'Home shop entry');

assertBefore(routerViewSource, 'if (requiredCapability.value && !loaded.value)', 'return h(matchedRouteRef.value?.component', 'RouterView loading gate');
assertBefore(routerViewSource, 'if (!capabilityAllowed.value)', 'return h(matchedRouteRef.value?.component', 'RouterView capability gate');
assertIncludes(routerViewSource, 'return h(NotFound)', 'RouterView capability fallback');

assertIncludes(shopAccountLayoutSource, '@/modules/shop/components/CustomerService/CustomerServiceWidget.vue', 'shop account layout');
assertNotIncludes(shopAccountLayoutSource, '@/modules/lowcode/', 'shop account layout');
assertIncludes(userInfoSource, 'usePortalCapabilities', 'user menu');
assertIncludes(userInfoSource, 'hasShop', 'user menu');
assertIncludes(userInfoSource, "capability: 'shop'", 'user menu shop actions');

const indexHtml = await readFile(indexPath, 'utf8');
const scripts = attrValues(indexHtml, 'src').filter(ref => /\/assets\/.+\.js$/.test(ref));
if (scripts.length !== 1 || !/\/assets\/index-.+\.js$/.test(scripts[0])) {
  fail(`dist/index.html must reference exactly one main entry script, found: ${scripts.join(', ')}`);
}
const preloads = Array.from(indexHtml.matchAll(/<link[^>]+rel="modulepreload"[^>]+href="([^"]+)"/g), match => match[1]);
const unexpectedPreloads = preloads.filter(ref => !/\/assets\/vendor-(vue|ant-design)-.+\.js$/.test(ref));
if (unexpectedPreloads.length > 0) {
  fail(`business chunks must not be modulepreloaded: ${unexpectedPreloads.join(', ')}`);
}

const assets = await readdir(assetsRoot);
const mainEntry = scripts[0].replace('/assets/', '');
const mainSource = await assetSource(mainEntry);
assertBusinessImplementationAbsent(mainSource, [
  /\/portal\/workbench/i,
  /\/portal\/customer-service/i,
  /\/shop\/manage\/items/i,
  /flyfish:customer-service-open/i,
  /\/assets\/(?:model|generate|launch|test)-/i,
  /md-editor-v3/i,
  /codemirror/i,
  /monaco/i
], 'main entry');

const shopAccountLayoutChunk = findAsset(assets, /^ShopAccountLayout-.+\.js$/, 'ShopAccountLayout');
const customerServiceChunk = findAsset(assets, /^CustomerServiceWidget-.+\.js$/, 'CustomerServiceWidget');
const shopItemDetailChunk = findAsset(assets, /^ShopItemDetail-.+\.js$/, 'ShopItemDetail');
const shopItemListChunk = findAsset(assets, /^ShopItemList-.+\.js$/, 'ShopItemList');
const markdownPreviewChunk = findAsset(assets, /^MarkdownPreview-.+\.js$/, 'MarkdownPreview');
const shopMarkdownPreviewChunk = findAsset(assets, /^ShopMarkdownPreview-.+\.js$/, 'ShopMarkdownPreview');
const shopMarkdownEditorChunk = findAsset(assets, /^ShopMarkdownEditor-.+\.js$/, 'ShopMarkdownEditor');
const lowcodeWorkbenchChunk = findAsset(assets, /^LowcodeWorkbench-.+\.js$/, 'LowcodeWorkbench');
const marketEntryChunk = findAsset(assets, /^MarketEntry-.+\.js$/, 'MarketEntry');
const selectModelDesignChunk = findAsset(assets, /^SelectModelDesign-.+\.js$/, 'SelectModelDesign');
const selectDataSourceChunk = findAsset(assets, /^SelectDataSource-.+\.js$/, 'SelectDataSource');

const [
  shopAccountLayoutBuilt,
  customerServiceBuilt,
  shopItemDetailBuilt,
  shopItemListBuilt,
  markdownPreviewBuilt,
  shopMarkdownPreviewBuilt,
  selectModelDesignBuilt,
  selectDataSourceBuilt
] = await Promise.all([
  assetSource(shopAccountLayoutChunk),
  assetSource(customerServiceChunk),
  assetSource(shopItemDetailChunk),
  assetSource(shopItemListChunk),
  assetSource(markdownPreviewChunk),
  assetSource(shopMarkdownPreviewChunk),
  assetSource(selectModelDesignChunk),
  assetSource(selectDataSourceChunk)
]);

assertIncludes(shopAccountLayoutBuilt, customerServiceChunk, 'built shop account layout');
assertBusinessImplementationAbsent(shopAccountLayoutBuilt, [/integrity\/sources/i, /SelectModel/i], 'built shop account layout');
assertIncludes(customerServiceBuilt, '/portal/customer-service', 'built customer service widget');
assertBusinessImplementationAbsent(customerServiceBuilt, [/integrity\/sources/i, /SelectModel/i], 'built customer service widget');
assertBusinessImplementationAbsent(shopItemDetailBuilt, [/integrity\/sources/i, /SelectModel/i, /\/portal\/customer-service/i], 'built shop item detail');
assertBusinessImplementationAbsent(shopItemListBuilt, [/integrity\/sources/i, /SelectModel/i, /\/portal\/customer-service/i], 'built shop item list');
assertBusinessImplementationAbsent(markdownPreviewBuilt, [new RegExp(shopMarkdownEditorChunk), /MdEditorV3/i], 'built markdown preview');
assertBusinessImplementationAbsent(shopMarkdownPreviewBuilt, [new RegExp(shopMarkdownEditorChunk), /MdEditorV3/i], 'built shop markdown preview');
assertBusinessImplementationAbsent(selectModelDesignBuilt, [/\/shops/i, /\/portal\/customer-service/i, /ShopItem/i], 'built lowcode model design');
assertBusinessImplementationAbsent(selectDataSourceBuilt, [/\/shops/i, /\/portal\/customer-service/i, /ShopItem/i], 'built lowcode data source');

const markdownPreviewDependencies = new Set([
  ...jsImports(markdownPreviewBuilt),
  ...jsImports(shopMarkdownPreviewBuilt)
]);
markdownPreviewDependencies.delete(markdownPreviewChunk);
markdownPreviewDependencies.delete(shopMarkdownPreviewChunk);
markdownPreviewDependencies.delete(shopMarkdownEditorChunk);
for (const dependency of markdownPreviewDependencies) {
  if (!assets.includes(dependency)) {
    continue;
  }
  const dependencySource = await assetSource(dependency);
  assertBusinessImplementationAbsent(dependencySource,
    [new RegExp(shopMarkdownEditorChunk), /MdEditorV3/i],
    `built markdown preview dependency ${dependency}`);
  const nestedBusinessDependencies = jsImports(dependencySource)
    .filter(asset => !/^vendor-(vue|ant-design)-.+\.js$/.test(asset));
  if (nestedBusinessDependencies.length > 0) {
    fail(`built markdown preview dependency ${dependency} must not load nested non-vendor chunks: ${nestedBusinessDependencies.join(', ')}`);
  }
}

console.log('Frontend route capability smoke passed.');
