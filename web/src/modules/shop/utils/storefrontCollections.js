import { selectFeaturedShopItems, SHOP_HOME_SECTION_LIMIT } from './shopHome.js';
import { normalizeShopTags } from './shopTags.js';

export const STOREFRONT_COLLECTIONS = ['documents', 'editing', 'cad', 'models'];

const PRODUCT_TYPES = new Set([
  'GIT_REPOSITORY_ACCESS', 'GIT_REPOSITORY_DONATION_ACCESS', 'DIGITAL_DOWNLOAD', 'LICENSE'
]);
const PRODUCT_COLLECTIONS = {
  'office-preview': 'documents', 'file-viewer': 'documents',
  'dwg-viewer': 'cad', 'model-viewer': 'models'
};
const COLLECTION_TERMS = {
  documents: /\b(?:docx?|pptx?|xlsx?|xlsb|office|wps|file[\s-]?viewer)\b|文档预览|文档渲染|幻灯片|表格|纯前端word|doc文档/i,
  editing: /\b(?:editor|editing)\b|编辑器|文档编辑/i,
  cad: /\b(?:dwg|dxf|cad)\b|工程图|图纸/i,
  models: /\b(?:3d|gltf|glb|step|stp|stl|fbx|obj|model[\s-]?viewer)\b|三维|模型预览/i
};

const parseParams = value => {
  if (value && typeof value === 'object') return value;
  try { return JSON.parse(value || '{}') || {}; } catch { return {}; }
};

// Discovery uses existing public product metadata, not item IDs or private delivery data.
export function storefrontCollectionKeys(item) {
  if (!item || item.enabled === false || (item.type && !PRODUCT_TYPES.has(item.type))) return [];
  const params = [parseParams(item.params), parseParams(item.defaultSku?.params)];
  const keys = new Set(params.map(value => PRODUCT_COLLECTIONS[value.licenseDelivery?.product || value.product]).filter(Boolean));
  const labels = [item.name, ...normalizeShopTags(item.tags),
    ...Object.values(item.i18n || {}).flatMap(value => [value?.name, ...normalizeShopTags(value?.tags)])
  ].filter(Boolean).join(' ');
  for (const key of STOREFRONT_COLLECTIONS) {
    if (COLLECTION_TERMS[key].test(labels)) keys.add(key);
  }
  if (keys.has('editing')) keys.delete('documents');
  return STOREFRONT_COLLECTIONS.filter(key => keys.has(key));
}

export function buildStorefrontCollections(items, limit = SHOP_HOME_SECTION_LIMIT) {
  const ranked = selectFeaturedShopItems(items, items?.length || 0);
  const classified = ranked.map(item => ({ item, keys: storefrontCollectionKeys(item) }));
  return STOREFRONT_COLLECTIONS.map(key => {
    const matches = classified.filter(value => value.keys.includes(key));
    return { key, itemCount: matches.length, items: matches.slice(0, limit).map(value => value.item) };
  }).filter(collection => collection.itemCount);
}

export function selectStorefrontHighlights(items, limit = SHOP_HOME_SECTION_LIMIT) {
  const ranked = selectFeaturedShopItems(items, items?.length || 0);
  const selected = new Map();
  for (const key of ['documents', 'cad', 'models', 'editing']) {
    const candidates = ranked.filter(item => !selected.has(String(item.id)) && storefrontCollectionKeys(item).includes(key));
    const representative = candidates.find(item => storefrontCollectionKeys(item).length === 1) || candidates[0];
    if (representative) selected.set(String(representative.id), representative);
  }
  for (const item of ranked) {
    if (!selected.has(String(item.id))) selected.set(String(item.id), item);
  }
  return [...selected.values()].slice(0, limit);
}
