import { readdir, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const srcRoot = path.join(projectRoot, 'src');
const extensions = new Set(['.js', '.jsx', '.ts', '.tsx', '.vue']);

const importPattern = /(?:import|export)\s+(?:[^'"()]*?\s+from\s*)?['"]([^'"]+)['"]|import\s*\(\s*['"]([^'"]+)['"]\s*\)/g;
const legacyPagePrefixes = [
  '@/pages/Account',
  '@/pages/CodeGenerate',
  '@/pages/IntegrationTest',
  '@/pages/Login',
  '@/pages/ModelDesign',
  '@/pages/OnlineLaunch',
  '@/pages/Shop',
  '@/pages/Support'
];
const legacyComponentPrefixes = [
  '@/components/CustomerService'
];
const legacyShopUtilityPrefixes = [
  '@/utils/gitRepositoryAccess',
  '@/utils/orderSort',
  '@/utils/shopCovers',
  '@/utils/shopDelivery',
  '@/utils/supportTickets',
  '@/utils/ticketSort'
];
const legacyDirectoryPrefixes = [
  'src/components/CustomerService/'
];
const legacyShopUtilityFiles = new Set([
  'src/utils/gitRepositoryAccess.js',
  'src/utils/orderSort.js',
  'src/utils/shopCovers.js',
  'src/utils/shopDelivery.js',
  'src/utils/supportTickets.js',
  'src/utils/ticketSort.js'
]);

const rules = [
  {
    scope: 'lowcode/src/',
    forbidden: ['src/modules/shop/'],
    reason: 'lowcode module must not depend on shop module'
  },
  {
    scope: 'src/modules/shop/',
    forbidden: ['lowcode/src/'],
    reason: 'shop module must not depend on lowcode module'
  },
  {
    scope: 'src/modules/auth/',
    forbidden: ['lowcode/src/', 'src/modules/shop/'],
    reason: 'auth module must stay shared and business-neutral'
  },
  {
    scope: 'src/modules/portal/',
    forbidden: ['lowcode/src/', 'src/modules/shop/'],
    reason: 'portal capability state must not import business modules'
  }
];

const publicScopes = [
  'src/App.vue',
  'src/components/',
  'src/layouts/',
  'src/main.js',
  'src/bootstrap.js',
  'src/network/',
  'src/pages/',
  'src/router/'
];

const publicBusinessAllowList = new Map([
  ['src/router/routes.js', new Set([
    'src/modules/shop/routes',
    'src/modules/shop/routes.js'
  ])]
]);
const publicBusinessDynamicAllowList = new Map([
  ['src/layouts/FooterBar.vue', new Set([
    'src/modules/shop/assets/contact',
    'src/modules/shop/assets/contact.js'
  ])]
]);
const shopAssetPaths = new Set([
  'src/assets/contact/customer-wechat.jpg',
  'public/images/contact/customer-wechat.jpg'
]);
const publicShopAssetSourceMarkers = [
  '/images/contact/customer-wechat.jpg'
];

const violations = [];

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const absolute = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...await walk(absolute));
      continue;
    }
    if (extensions.has(path.extname(entry.name))) {
      files.push(absolute);
    }
  }
  return files;
}

function normalizeRelative(file) {
  return path.relative(projectRoot, file).split(path.sep).join('/');
}

function resolveSpecifier(specifier, file) {
  if (specifier.startsWith('@lowcode/')) return `lowcode/src/${specifier.slice(9)}`;
  if (specifier.startsWith('@/')) {
    return `src/${specifier.slice(2)}`;
  }
  if (specifier.startsWith('.')) {
    return normalizeRelative(path.resolve(path.dirname(file), specifier));
  }
  return null;
}

function isPublicShellFile(relativeFile) {
  return publicScopes.some(scope => scope.endsWith('/')
    ? relativeFile.startsWith(scope)
    : relativeFile === scope);
}

function allowedPublicBusinessImport(relativeFile, target, isDynamicImport) {
  const allowed = publicBusinessAllowList.get(relativeFile);
  if (allowed?.has(target)) {
    return true;
  }
  const dynamicAllowed = publicBusinessDynamicAllowList.get(relativeFile);
  return Boolean(isDynamicImport && dynamicAllowed?.has(target));
}

function addViolation(file, specifier, message) {
  violations.push(`${file} imports ${specifier}: ${message}`);
}

function checkLegacyImports(relativeFile, specifier, target) {
  if (specifier === '@/store' || specifier.startsWith('@/store/')) {
    addViolation(relativeFile, specifier, 'legacy store import is forbidden; use src/modules/auth/store/client.js');
  }
  if (target === 'src/store' || target?.startsWith('src/store/')) {
    addViolation(relativeFile, specifier, 'legacy store path must stay empty after auth extraction');
  }
  if (specifier === '@/utils/authority' || specifier.startsWith('@/utils/authority')) {
    addViolation(relativeFile, specifier, 'authority helpers moved to src/modules/auth/authority.js');
  }
  if (specifier === '@/network/apis' || specifier === '@/network/apis.js') {
    addViolation(relativeFile, specifier, 'global API aggregation is forbidden; keep API wrappers in their owning module or page');
  }
  if (legacyPagePrefixes.some(prefix => specifier === prefix || specifier.startsWith(`${prefix}/`))) {
    addViolation(relativeFile, specifier, 'business pages must be imported from src/modules/*');
  }
  if (legacyComponentPrefixes.some(prefix => specifier === prefix || specifier.startsWith(`${prefix}/`))) {
    addViolation(relativeFile, specifier, 'customer service components moved to src/modules/shop/components/CustomerService');
  }
  if (legacyShopUtilityPrefixes.some(prefix => specifier === prefix || specifier.startsWith(`${prefix}.`))) {
    addViolation(relativeFile, specifier, 'shop utilities moved to src/modules/shop/utils');
  }
}

function checkModuleRules(relativeFile, specifier, target) {
  if (!target) {
    return;
  }
  for (const rule of rules) {
    if (!relativeFile.startsWith(rule.scope)) {
      continue;
    }
    if (rule.forbidden.some(prefix => target === prefix.slice(0, -1) || target.startsWith(prefix))) {
      addViolation(relativeFile, specifier, rule.reason);
    }
  }
}

function checkPublicShell(relativeFile, specifier, target, isDynamicImport) {
  if (!target || !isPublicShellFile(relativeFile)) {
    return;
  }
  const importsBusinessModule = target.startsWith('lowcode/src/') || target.startsWith('src/modules/shop/');
  if (importsBusinessModule && !allowedPublicBusinessImport(relativeFile, target, isDynamicImport)) {
    addViolation(relativeFile, specifier, 'public shell must not import business modules directly');
  }
  const importsShopAsset = target.startsWith('src/assets/shop/') || shopAssetPaths.has(target);
  if (importsShopAsset) {
    addViolation(relativeFile, specifier, 'public shell must not import shop-owned assets directly; load them through a shop-owned optional extension behind the shop capability');
  }
}

function checkPublicShellCapabilityMarkers(relativeFile, source) {
  if (!isPublicShellFile(relativeFile)) {
    return;
  }
  if (!/\/account\/(?:orders|tickets)|提交工单|我的订单/.test(source)) {
    return;
  }
  if (!source.includes('usePortalCapabilities') || !/\bhasShop\b/.test(source)) {
    addViolation(relativeFile, 'shop account entrypoint', 'shop account entrypoints in public shell must be gated by portal shop capability');
  }
  if (!/capability:\s*['"]shop['"]/.test(source)) {
    addViolation(relativeFile, 'shop account entrypoint', 'shop-only public menu items must declare capability: shop');
  }
}

function checkPublicShellShopAssetMarkers(relativeFile, source) {
  if (!isPublicShellFile(relativeFile)) {
    return;
  }
  for (const marker of publicShopAssetSourceMarkers) {
    if (source.includes(marker)) {
      addViolation(relativeFile, marker, 'public shell must not hard-code shop-owned asset URLs; load them through a shop-owned optional extension behind the shop capability');
    }
  }
}

function checkAuthBusinessNeutrality(relativeFile, source) {
  if (!relativeFile.startsWith('src/modules/auth/')) {
    return;
  }
  const forbidden = [
    ['isShopMaintainer', 'shop maintainer helpers belong to src/modules/shop'],
    ['requireShopMaintainer', 'shop maintainer helpers belong to src/modules/shop'],
    ['飞鱼小铺', 'auth module must not hard-code shop branding; use portal capability metadata'],
    ['/shop', 'auth module must not hard-code shop routes']
  ];
  for (const [marker, reason] of forbidden) {
    if (source.includes(marker)) {
      addViolation(relativeFile, marker, reason);
    }
  }
}

function checkPortalBusinessNeutrality(relativeFile, source) {
  if (!relativeFile.startsWith('src/modules/portal/')) {
    return;
  }
  const forbidden = [
    ['飞鱼小铺', 'portal capability state must derive shop branding from /portal/capabilities'],
    ['飞鱼低代码平台', 'portal capability state must derive lowcode branding from /portal/capabilities'],
    ['/shop', 'portal capability state must derive shop routes from /portal/capabilities'],
    ['/model-design', 'portal capability state must derive lowcode routes from /portal/capabilities']
  ];
  for (const [marker, reason] of forbidden) {
    if (source.includes(marker)) {
      addViolation(relativeFile, marker, reason);
    }
  }
}

function checkProductBusinessNeutrality(relativeFile, source) {
  const checks = [
    {
      scope: 'lowcode/src/',
      forbidden: [
        ['飞鱼小铺', 'lowcode module must not hard-code shop branding'],
        ['商品管理', 'lowcode module must not hard-code shop management labels'],
        ['/shop', 'lowcode module must not hard-code shop routes'],
        ['/shops', 'lowcode module must not hard-code shop API paths'],
        ['@/modules/shop', 'lowcode app must not import shop implementation']
      ]
    },
    {
      scope: 'src/modules/shop/',
      forbidden: [
        ['飞鱼低代码平台', 'shop module must not hard-code lowcode branding'],
        ['/model-design', 'shop module must not hard-code lowcode routes'],
        ['/code-generate', 'shop module must not hard-code lowcode routes'],
        ['/online-launch', 'shop module must not hard-code lowcode routes'],
        ['/integrate-test', 'shop module must not hard-code lowcode routes'],
        ['/integrity/', 'shop module must not hard-code lowcode API paths'],
        ['Lowcode', 'shop module must not reference lowcode implementation names'],
        ['lowcode', 'shop module must not reference lowcode implementation names']
      ]
    }
  ];
  for (const check of checks) {
    if (!relativeFile.startsWith(check.scope)) {
      continue;
    }
    for (const [marker, reason] of check.forbidden) {
      if (source.includes(marker)) {
        addViolation(relativeFile, marker, reason);
      }
    }
  }
}

async function checkFile(file) {
  const relativeFile = normalizeRelative(file);
  if (legacyDirectoryPrefixes.some(prefix => relativeFile.startsWith(prefix))) {
    addViolation(relativeFile, relativeFile, 'customer service UI belongs to src/modules/shop/components/CustomerService');
  }
  if (legacyShopUtilityFiles.has(relativeFile)) {
    addViolation(relativeFile, relativeFile, 'shop utilities belong to src/modules/shop/utils');
  }
  const source = await readFile(file, 'utf8');
  checkPublicShellCapabilityMarkers(relativeFile, source);
  checkPublicShellShopAssetMarkers(relativeFile, source);
  checkAuthBusinessNeutrality(relativeFile, source);
  checkPortalBusinessNeutrality(relativeFile, source);
  checkProductBusinessNeutrality(relativeFile, source);
  for (const match of source.matchAll(importPattern)) {
    const specifier = match[1] || match[2];
    const isDynamicImport = Boolean(match[2]);
    const target = resolveSpecifier(specifier, file);
    checkLegacyImports(relativeFile, specifier, target);
    checkModuleRules(relativeFile, specifier, target);
    checkPublicShell(relativeFile, specifier, target, isDynamicImport);
  }
}

if (!existsSync(srcRoot)) {
  throw new Error(`Missing src directory: ${srcRoot}`);
}

for (const file of [...await walk(srcRoot), ...await walk(path.join(projectRoot, 'lowcode/src'))]) {
  await checkFile(file);
}

if (violations.length > 0) {
  console.error(violations.join('\n'));
  process.exit(1);
}

console.log('Frontend architecture boundaries passed.');
