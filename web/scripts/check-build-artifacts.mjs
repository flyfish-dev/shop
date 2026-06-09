import { readdir, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const distRoot = path.join(projectRoot, 'dist');
const assetsRoot = path.join(distRoot, 'assets');
const indexPath = path.join(distRoot, 'index.html');

const forbiddenInitialPatterns = [
  /LowcodeWorkbench/i,
  /MarketEntry/i,
  /CustomerServiceWidget/i,
  /MarkdownPreview/i,
  /ShopItemDetail/i,
  /ShopItemModal/i,
  /ShopMarkdownEditor/i,
  /SelectModelDesign/i,
  /SelectDataTable/i,
  /CodeGenerate/i,
  /OnlineLaunch/i,
  /IntegrationTest/i,
  /CouponManage/i,
  /GitRepositoryManage/i,
  /ItemManage/i,
  /OrderManage/i,
  /TicketManage/i,
  /UserManage/i,
  /customer-wechat/i,
  /\/assets\/(?:model|generate|launch|test)-/i,
  /codemirror/i,
  /md-editor/i,
  /monaco/i
];

const expectedLazyChunks = [
  /^LowcodeWorkbench-.+\.js$/,
  /^MarketEntry-.+\.js$/,
  /^CustomerServiceWidget-.+\.js$/,
  /^MarkdownPreview-.+\.js$/,
  /^ShopItemDetail-.+\.js$/,
  /^ShopMarkdownPreview-.+\.js$/,
  /^ShopMarkdownEditor-.+\.js$/,
  /^SelectModelDesign-.+\.js$/
];
const forbiddenMainEntryImplementationPatterns = [
  /\/portal\/workbench/i,
  /\/portal\/customer-service/i,
  /\/shop\/manage\/items/i,
  /flyfish:customer-service-open/i,
  /customer-wechat/i,
  /\/assets\/(?:model|generate|launch|test)-/i,
  /md-editor-v3/i,
  /codemirror/i,
  /monaco/i
];

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function attrValues(source, attribute) {
  const pattern = new RegExp(`${attribute}="([^"]+)"`, 'g');
  return Array.from(source.matchAll(pattern), match => match[1]);
}

function assertNoInitialBusinessAsset(indexHtml) {
  const initialRefs = attrValues(indexHtml, 'src')
    .concat(attrValues(indexHtml, 'href'));
  const offending = initialRefs.filter(ref => forbiddenInitialPatterns.some(pattern => pattern.test(ref)));
  if (offending.length > 0) {
    fail(`business-heavy assets must not be referenced by dist/index.html: ${offending.join(', ')}`);
  }
}

function assertOnlySharedPreloads(indexHtml) {
  const preloads = Array.from(indexHtml.matchAll(/<link[^>]+rel="modulepreload"[^>]+href="([^"]+)"/g), match => match[1]);
  const unexpected = preloads.filter(ref => !/\/assets\/vendor-(vue|ant-design)-.+\.js$/.test(ref));
  if (unexpected.length > 0) {
    fail(`unexpected modulepreload entries: ${unexpected.join(', ')}`);
  }
  if (!preloads.some(ref => /\/assets\/vendor-vue-.+\.js$/.test(ref))) {
    fail('vendor-vue chunk is not preloaded');
  }
  if (!preloads.some(ref => /\/assets\/vendor-ant-design-.+\.js$/.test(ref))) {
    fail('vendor-ant-design chunk is not preloaded');
  }
}

function assertSingleMainEntry(indexHtml) {
  const scripts = attrValues(indexHtml, 'src').filter(ref => /\/assets\/.+\.js$/.test(ref));
  if (scripts.length !== 1 || !/\/assets\/index-.+\.js$/.test(scripts[0])) {
    fail(`dist/index.html must reference exactly one main entry script, found: ${scripts.join(', ')}`);
  }
  return scripts[0];
}

function assertExpectedLazyChunks(assets) {
  const missing = expectedLazyChunks
    .filter(pattern => !assets.some(asset => pattern.test(asset)))
    .map(String);
  if (missing.length > 0) {
    fail(`expected lazy chunks were not emitted: ${missing.join(', ')}`);
  }
}

function findAsset(assets, pattern, label) {
  const asset = assets.find(file => pattern.test(file));
  if (!asset) {
    fail(`${label} chunk was not emitted`);
  }
  return asset;
}

function assertNoMainEntryBusinessImplementation(mainEntry, source) {
  const offending = forbiddenMainEntryImplementationPatterns
    .filter(pattern => pattern.test(source))
    .map(String);
  if (offending.length > 0) {
    fail(`${mainEntry} contains business-heavy implementation markers: ${offending.join(', ')}`);
  }
}

async function assetSource(asset) {
  return readFile(path.join(assetsRoot, asset), 'utf8');
}

function jsImports(source) {
  const imports = [];
  const staticPattern = /from"\.\/([^"]+\.js)"/g;
  const dynamicPattern = /import\("\.\/([^"]+\.js)"\)/g;
  const preloadPattern = /"assets\/([^"]+\.js)"/g;
  for (const pattern of [staticPattern, dynamicPattern, preloadPattern]) {
    for (const match of source.matchAll(pattern)) {
      imports.push(match[1]);
    }
  }
  return Array.from(new Set(imports));
}

async function assertMarkdownPreviewDoesNotLoadEditor(assets) {
  const markdownPreviewChunk = findAsset(assets, /^MarkdownPreview-.+\.js$/, 'MarkdownPreview');
  const shopMarkdownPreviewChunk = findAsset(assets, /^ShopMarkdownPreview-.+\.js$/, 'ShopMarkdownPreview');
  const shopMarkdownEditorChunk = findAsset(assets, /^ShopMarkdownEditor-.+\.js$/, 'ShopMarkdownEditor');
  const previewSources = new Map();
  for (const asset of [markdownPreviewChunk, shopMarkdownPreviewChunk]) {
    previewSources.set(asset, await assetSource(asset));
  }

  const previewDependencies = new Set();
  for (const [asset, source] of previewSources) {
    if (source.includes(shopMarkdownEditorChunk) || /MdEditorV3/i.test(source)) {
      fail(`${asset} must not import the markdown editor implementation`);
    }
    jsImports(source).forEach(dependency => previewDependencies.add(dependency));
  }

  previewDependencies.delete(markdownPreviewChunk);
  previewDependencies.delete(shopMarkdownPreviewChunk);
  previewDependencies.delete(shopMarkdownEditorChunk);
  for (const dependency of previewDependencies) {
    if (!assets.includes(dependency)) {
      continue;
    }
    const source = await assetSource(dependency);
    if (source.includes(shopMarkdownEditorChunk) || /MdEditorV3/i.test(source)) {
      fail(`markdown preview dependency ${dependency} must not contain editor implementation`);
    }
    const nestedBusinessDependencies = jsImports(source)
      .filter(asset => !/^vendor-(vue|ant-design)-.+\.js$/.test(asset));
    if (nestedBusinessDependencies.length > 0) {
      fail(`markdown preview dependency ${dependency} must not load nested non-vendor chunks: ${nestedBusinessDependencies.join(', ')}`);
    }
  }
}

if (!existsSync(indexPath) || !existsSync(assetsRoot)) {
  fail('missing dist output; run npm run build first');
}

const indexHtml = await readFile(indexPath, 'utf8');
const assets = await readdir(assetsRoot);
const mainEntry = assertSingleMainEntry(indexHtml);
const mainEntryPath = path.join(distRoot, mainEntry.replace(/^\//, ''));
const mainEntrySource = await readFile(mainEntryPath, 'utf8');

assertOnlySharedPreloads(indexHtml);
assertNoInitialBusinessAsset(indexHtml);
assertNoMainEntryBusinessImplementation(mainEntry, mainEntrySource);
assertExpectedLazyChunks(assets);
await assertMarkdownPreviewDoesNotLoadEditor(assets);

console.log('Frontend build artifact boundaries passed.');
