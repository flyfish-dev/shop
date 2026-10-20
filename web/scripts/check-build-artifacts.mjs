import { readdir, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const distRoot = path.join(projectRoot, 'dist');
const shellRoot = path.join(distRoot, 'shell');
const releaseIdPath = path.join(distRoot, 'release-id.txt');

async function assertBrandFavicon(indexHtml) {
  const faviconPath = path.join(shellRoot, 'favicon.ico');
  if (!existsSync(faviconPath)) {
    fail('shell/favicon.ico is required by browser tabs and notifications');
  }
  if (!/href=["']\/favicon\.ico\?v=flyfish-brand-\d+["']/.test(indexHtml)) {
    fail('shell/index.html must reference the versioned Flyfish brand favicon from the site root');
  }

  const favicon = await readFile(faviconPath);
  if (favicon.length < 6 || favicon.readUInt16LE(0) !== 0 || favicon.readUInt16LE(2) !== 1) {
    fail('shell/favicon.ico is not a valid ICO file');
  }
  const imageCount = favicon.readUInt16LE(4);
  const sizes = Array.from({ length: imageCount }, (_, index) => {
    const width = favicon[6 + index * 16];
    return width === 0 ? 256 : width;
  }).sort((left, right) => left - right);
  if (sizes.join(',') !== '16,32,48') {
    fail(`shell/favicon.ico must contain 16, 32 and 48 pixel images, found: ${sizes.join(', ')}`);
  }
}

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
  /(?:^|\/)assets\/(?:model|generate|launch|test)-/i,
  /codemirror/i,
  /md-editor/i,
  /monaco/i
];

const expectedLazyChunks = [
  /^ShopEntry-.+\.js$/,
  /^CustomerServiceWidget-.+\.js$/,
  /^MarkdownPreview-.+\.js$/,
  /^ShopItemDetail-.+\.js$/,
  /^ShopMarkdownPreview-.+\.js$/,
  /^ShopMarkdownEditor-.+\.js$/
];
const forbiddenMainEntryImplementationPatterns = [
  /\/portal\/workbench/i,
  /\/portal\/customer-service/i,
  /\/shop\/manage\/items/i,
  /flyfish:customer-service-open/i,
  /customer-wechat/i,
  /(?:^|\/)assets\/(?:model|generate|launch|test)-/i,
  /md-editor-v3/i,
  /codemirror/i,
  /monaco/i
];

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function assertShellHasNoBuildAssets(html, label) {
  const refs = Array.from(html.matchAll(/(?:src|href)=["']([^"']+)["']/g), match => match[1]);
  const offending = refs.filter(ref => /(?:^|\/)assets\/.+\.(?:js|css)$/i.test(ref));
  if (offending.length > 0) {
    fail(`${label} references build assets directly: ${offending.join(', ')}`);
  }
  if (!html.includes('__FLYFISH_BOOTSTRAP_CONFIG__') || !html.includes('__FLYFISH_STATIC_RUNTIME__')) {
    fail(`${label} does not contain the static bootstrap runtime`);
  }
}

function assertNoInitialBusinessAsset(manifest) {
  const initialRefs = [manifest.entry, ...manifest.styles, ...manifest.modulePreloads];
  const offending = initialRefs.filter(ref => forbiddenInitialPatterns.some(pattern => pattern.test(ref)));
  if (offending.length > 0) {
    fail(`business-heavy assets must not be initial assets: ${offending.join(', ')}`);
  }
}

function assertOnlySharedPreloads(manifest) {
  const unexpected = manifest.modulePreloads
    .filter(ref => !/assets\/vendor-(vue|ant-design)-.+\.js$/.test(ref));
  if (unexpected.length > 0) {
    fail(`unexpected modulepreload entries: ${unexpected.join(', ')}`);
  }
  if (!manifest.modulePreloads.some(ref => /assets\/vendor-vue-.+\.js$/.test(ref))) {
    fail('vendor-vue chunk is not preloaded');
  }
  if (!manifest.modulePreloads.some(ref => /assets\/vendor-ant-design-.+\.js$/.test(ref))) {
    fail('vendor-ant-design chunk is not preloaded');
  }
}

function assertMainEntry(manifest) {
  if (!/^assets\/index-.+\.js$/.test(manifest.entry)) {
    fail(`manifest must reference one main entry, found: ${manifest.entry}`);
  }
  return manifest.entry;
}

function assertExpectedLazyChunks(assets) {
  if (assets.some(asset => /^(LowcodeWorkbench|SelectModelDesign|SelectDataSource|MarketEntry)-/.test(asset))) {
    fail('shop build must not contain lowcode or the retired portal home');
  }
  const missing = expectedLazyChunks.filter(pattern => !assets.some(asset => pattern.test(asset))).map(String);
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
  const offending = forbiddenMainEntryImplementationPatterns.filter(pattern => pattern.test(source)).map(String);
  if (offending.length > 0) {
    fail(`${mainEntry} contains business-heavy implementation markers: ${offending.join(', ')}`);
  }
}

function jsImports(source) {
  const imports = [];
  const patterns = [
    /from"\.\/([^"?]+\.js)"/g,
    /import\("\.\/([^"?]+\.js)"\)/g,
    /"assets\/([^"?]+\.js)"/g
  ];
  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      imports.push(match[1]);
    }
  }
  return Array.from(new Set(imports));
}

async function assertMarkdownPreviewDoesNotLoadEditor(assetsRoot, assets) {
  const sourceOf = asset => readFile(path.join(assetsRoot, asset), 'utf8');
  const markdownPreviewChunk = findAsset(assets, /^MarkdownPreview-.+\.js$/, 'MarkdownPreview');
  const shopMarkdownPreviewChunk = findAsset(assets, /^ShopMarkdownPreview-.+\.js$/, 'ShopMarkdownPreview');
  const shopMarkdownEditorChunk = findAsset(assets, /^ShopMarkdownEditor-.+\.js$/, 'ShopMarkdownEditor');
  const previewSources = new Map();
  for (const asset of [markdownPreviewChunk, shopMarkdownPreviewChunk]) {
    previewSources.set(asset, await sourceOf(asset));
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
    const source = await sourceOf(dependency);
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

if (!existsSync(releaseIdPath) || !existsSync(path.join(shellRoot, 'index.html'))) {
  fail('missing split dist output; run npm run build first');
}

const release = (await readFile(releaseIdPath, 'utf8')).trim();
const releaseRoot = path.join(distRoot, 'static', 'releases', release);
const assetsRoot = path.join(releaseRoot, 'assets');
const manifestPath = path.join(releaseRoot, 'bootstrap-manifest.json');
if (!existsSync(assetsRoot) || !existsSync(manifestPath)) {
  fail(`missing static release output: ${release}`);
}
if (!existsSync(path.join(shellRoot, 'qr.png'))) {
  fail('shell/qr.png is required by the WeChat verification-code fallback');
}
if (existsSync(path.join(releaseRoot, 'qr.png'))) {
  fail('qr.png must stay same-origin in the shell instead of the static release');
}

const [indexHtml, viewerHtml, manifestSource, assets] = await Promise.all([
  readFile(path.join(shellRoot, 'index.html'), 'utf8'),
  readFile(path.join(shellRoot, 'file-viewer', 'index.html'), 'utf8'),
  readFile(manifestPath, 'utf8'),
  readdir(assetsRoot)
]);
const manifest = JSON.parse(manifestSource);
if (manifest.release !== release) {
  fail(`manifest release ${manifest.release} does not match ${release}`);
}
if (!manifest.viewer?.entry || !existsSync(path.join(releaseRoot, manifest.viewer.entry))) {
  fail('contract viewer entry is missing from static release');
}

assertShellHasNoBuildAssets(indexHtml, 'shell/index.html');
assertShellHasNoBuildAssets(viewerHtml, 'shell/file-viewer/index.html');
await assertBrandFavicon(indexHtml);
assertOnlySharedPreloads(manifest);
assertNoInitialBusinessAsset(manifest);
const mainEntry = assertMainEntry(manifest);
const mainEntrySource = await readFile(path.join(releaseRoot, mainEntry), 'utf8');
assertNoMainEntryBusinessImplementation(mainEntry, mainEntrySource);
assertExpectedLazyChunks(assets);
await assertMarkdownPreviewDoesNotLoadEditor(assetsRoot, assets);

console.log(`Frontend split release boundaries passed: ${release}`);
