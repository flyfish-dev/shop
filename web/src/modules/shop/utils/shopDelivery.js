export const DELIVERY_MODE = {
  AUTOMATIC: 'AUTOMATIC',
  MANUAL: 'MANUAL',
  NONE: 'NONE'
};

export const isOrderRefundFrozen = order => Boolean(order &&
  (order.paymentProvider === 'xianyu' || String(order.salesChannel || '').startsWith('XIANYU_')) &&
  (Number(order.refundAmount || 0) > 0 || (order.refundState && !['NONE', 'CLOSED', 'REJECTED'].includes(order.refundState))));

export const refundStatusText = (state, t) => t(`orders.refundStates.${
  ['NONE', 'PENDING', 'PROCESSING', 'PARTIAL', 'REFUNDED', 'CLOSED', 'REJECTED'].includes(state) ? state : 'PENDING'
}`);

export const DELIVERY_MODE_OPTIONS = [
  { label: '自动交付', value: DELIVERY_MODE.AUTOMATIC },
  { label: '人工交付', value: DELIVERY_MODE.MANUAL },
  { label: '无需交付', value: DELIVERY_MODE.NONE }
];

export const SHOP_TYPE_OPTIONS = [
  { label: 'Git 仓库开通', value: 'GIT_REPOSITORY_ACCESS' },
  { label: 'Git 仓库打赏开通', value: 'GIT_REPOSITORY_DONATION_ACCESS' },
  { label: '自由打赏', value: 'DONATION' },
  { label: '数字下载', value: 'DIGITAL_DOWNLOAD' },
  { label: '服务套餐', value: 'SERVICE_PACKAGE' },
  { label: '授权许可', value: 'LICENSE' }
];

export const DELIVERY_ACTION = {
  GIT_REPOSITORY_ACCESS: 'GIT_REPOSITORY_ACCESS',
  DIGITAL_DOWNLOAD: 'DIGITAL_DOWNLOAD',
  LICENSE: 'LICENSE'
};

export const DELIVERY_ACTION_OPTIONS = [
  { label: '开通源码仓库', value: DELIVERY_ACTION.GIT_REPOSITORY_ACCESS },
  { label: '数字提货', value: DELIVERY_ACTION.DIGITAL_DOWNLOAD },
  { label: '授权发放', value: DELIVERY_ACTION.LICENSE }
];

export const OFFICE_LICENSE_EDITION_OPTIONS = [
  { label: '个人版', value: 'personal' },
  { label: '商业版', value: 'commercial' },
  { label: '企业版', value: 'enterprise' }
];

export const OFFICE_LICENSE_KIND = {
  RUNTIME: 'runtime-license',
  BRAND_REMOVAL: 'brand-removal'
};

export const OFFICE_LICENSE_KIND_OPTIONS = [
  { label: '运行授权', value: OFFICE_LICENSE_KIND.RUNTIME },
  { label: '品牌标识移除声明', value: OFFICE_LICENSE_KIND.BRAND_REMOVAL }
];

export const LICENSE_PRODUCT = {
  OFFICE_PREVIEW: 'office-preview',
  DWG_VIEWER: 'dwg-viewer',
  MODEL_VIEWER: 'model-viewer',
  FILE_VIEWER: 'file-viewer'
};

export const LICENSE_PRODUCT_OPTIONS = [
  { label: 'Office Preview', value: LICENSE_PRODUCT.OFFICE_PREVIEW },
  { label: 'DWG Viewer', value: LICENSE_PRODUCT.DWG_VIEWER },
  { label: 'Flyfish Model Viewer', value: LICENSE_PRODUCT.MODEL_VIEWER },
  { label: 'Flyfish Viewer', value: LICENSE_PRODUCT.FILE_VIEWER }
];

export const OFFICE_LICENSE_FEATURE_OPTIONS = [
  { label: 'DOC', value: 'doc' },
  { label: 'DOCX', value: 'docx' },
  { label: 'PPT', value: 'ppt' },
  { label: 'PPTX', value: 'pptx' },
  { label: 'XLS', value: 'xls' },
  { label: 'XLSX', value: 'xlsx' },
  { label: 'XLSB', value: 'xlsb' },
  { label: '虚拟表格', value: 'virtual-excel' },
  { label: 'DWG', value: 'dwg' },
  { label: '3D 模型预览', value: 'model-preview' },
  { label: 'glTF / GLB', value: 'gltf' },
  { label: 'STEP / STP', value: 'step' },
  { label: 'OBJ', value: 'obj' },
  { label: 'FBX', value: 'fbx' },
  { label: 'STL', value: 'stl' },
  { label: 'PLY', value: 'ply' },
  { label: 'COLLADA / DAE', value: 'dae' },
  { label: '3DS', value: '3ds' },
  { label: '3MF', value: '3mf' },
  { label: 'AMF', value: 'amf' },
  { label: 'USD / USDZ', value: 'usd' },
  { label: 'VRML', value: 'vrml' },
  { label: 'VTK', value: 'vtk' },
  { label: 'PCD', value: 'pcd' },
  { label: 'XYZ', value: 'xyz' },
  { label: 'VOX', value: 'vox' },
  { label: 'LightWave', value: 'lwo' },
  { label: 'KMZ', value: 'kmz' },
  { label: 'G-code', value: 'gcode' },
  { label: 'MD2', value: 'md2' },
  { label: 'BVH', value: 'bvh' },
  { label: 'PDB', value: 'pdb' },
  { label: 'LDraw', value: 'ldraw' },
  { label: 'Draco', value: 'draco' },
  { label: 'NRRD', value: 'nrrd' },
  { label: 'Rhino 3DM', value: 'rhino3dm' },
  { label: '移除界面可见品牌标识', value: 'visible-branding-removal' },
  { label: '保留版权与许可证声明', value: 'apache-2.0-attribution-required' }
];

export const DEFAULT_OFFICE_LICENSE_FEATURES = [
  'doc',
  'docx',
  'ppt',
  'pptx',
  'xls',
  'xlsx',
  'xlsb',
  'virtual-excel'
];

export const DEFAULT_DWG_LICENSE_FEATURES = ['dwg'];

export const DEFAULT_MODEL_LICENSE_FEATURES = [
  'model-preview', 'gltf', 'step', 'obj', 'fbx', 'stl', 'ply', 'dae', '3ds', '3mf',
  'amf', 'usd', 'vrml', 'vtk', 'pcd', 'xyz', 'vox', 'lwo', 'kmz', 'gcode', 'md2',
  'bvh', 'pdb', 'ldraw', 'draco', 'nrrd', 'rhino3dm'
];

export const defaultLicenseFeaturesForProduct = product => (
  product === LICENSE_PRODUCT.DWG_VIEWER
    ? [...DEFAULT_DWG_LICENSE_FEATURES]
    : product === LICENSE_PRODUCT.MODEL_VIEWER
      ? [...DEFAULT_MODEL_LICENSE_FEATURES]
    : [...DEFAULT_OFFICE_LICENSE_FEATURES]
);

export const DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES = [
  'visible-branding-removal',
  'apache-2.0-attribution-required'
];

export const VIEWER_BRAND_REMOVAL_REMARK = 'Apache 2.0 已允许修改和移除界面可见品牌标识；本授权声明用于采购与合规留痕。使用方仍需保留版权声明、许可证文本和项目来源说明，不得暗示 Flyfish 官方背书。';

const DELIVERY_MODES_BY_TYPE = {
  GIT_REPOSITORY_ACCESS: [DELIVERY_MODE.AUTOMATIC],
  GIT_REPOSITORY_DONATION_ACCESS: [DELIVERY_MODE.AUTOMATIC],
  DONATION: [DELIVERY_MODE.NONE],
  DIGITAL_DOWNLOAD: [DELIVERY_MODE.AUTOMATIC, DELIVERY_MODE.MANUAL, DELIVERY_MODE.NONE],
  SERVICE_PACKAGE: [DELIVERY_MODE.MANUAL, DELIVERY_MODE.NONE],
  LICENSE: [DELIVERY_MODE.AUTOMATIC, DELIVERY_MODE.MANUAL, DELIVERY_MODE.NONE]
};

const DEFAULT_DELIVERY_MODE_BY_TYPE = {
  GIT_REPOSITORY_ACCESS: DELIVERY_MODE.AUTOMATIC,
  GIT_REPOSITORY_DONATION_ACCESS: DELIVERY_MODE.AUTOMATIC,
  DONATION: DELIVERY_MODE.NONE,
  DIGITAL_DOWNLOAD: DELIVERY_MODE.AUTOMATIC,
  SERVICE_PACKAGE: DELIVERY_MODE.MANUAL,
  LICENSE: DELIVERY_MODE.AUTOMATIC
};

const DELIVERY_MODE_COLOR = {
  AUTOMATIC: 'blue',
  MANUAL: 'orange',
  NONE: 'default'
};

const ORDER_STATUS_TEXT = {
  PENDING: '待支付',
  PAYING: '支付中',
  PAID: '已支付',
  DELIVERED: '已交付',
  FAILED: '失败',
  CLOSED: '已关闭'
};

const translate = (t, key, fallback) => typeof t === 'function' ? t(key) : fallback;

const ORDER_STATUS_COLOR = {
  PENDING: 'default',
  PAYING: 'processing',
  PAID: 'blue',
  DELIVERED: 'success',
  FAILED: 'error',
  CLOSED: 'default'
};

export const deliveryModeOptionsForType = type => {
  const allowed = DELIVERY_MODES_BY_TYPE[type] || [DELIVERY_MODE.MANUAL];
  return DELIVERY_MODE_OPTIONS.filter(option => allowed.includes(option.value));
};

export const defaultDeliveryModeForType = type => DEFAULT_DELIVERY_MODE_BY_TYPE[type] || DELIVERY_MODE.MANUAL;

export const normalizeDeliveryModeForType = (type, deliveryMode) => {
  const allowed = deliveryModeOptionsForType(type).map(option => option.value);
  return allowed.includes(deliveryMode) ? deliveryMode : defaultDeliveryModeForType(type);
};

export const defaultDeliveryActionsForType = type => {
  if (type === 'GIT_REPOSITORY_ACCESS' || type === 'GIT_REPOSITORY_DONATION_ACCESS') {
    return [DELIVERY_ACTION.GIT_REPOSITORY_ACCESS];
  }
  if (type === 'DIGITAL_DOWNLOAD') {
    return [DELIVERY_ACTION.DIGITAL_DOWNLOAD];
  }
  if (type === 'LICENSE') {
    return [DELIVERY_ACTION.LICENSE];
  }
  return [];
};

export const normalizeDeliveryActionsForType = (type, actions) => {
  const defaults = defaultDeliveryActionsForType(type);
  const selected = Array.isArray(actions) && actions.length ? actions : defaults;
  const allowed = new Set(DELIVERY_ACTION_OPTIONS.map(option => option.value));
  const normalized = [...new Set(selected.filter(action => allowed.has(action)))];
  if ((type === 'GIT_REPOSITORY_ACCESS' || type === 'GIT_REPOSITORY_DONATION_ACCESS')
    && !normalized.includes(DELIVERY_ACTION.GIT_REPOSITORY_ACCESS)) {
    normalized.unshift(DELIVERY_ACTION.GIT_REPOSITORY_ACCESS);
  }
  if (type === 'DIGITAL_DOWNLOAD' && !normalized.includes(DELIVERY_ACTION.DIGITAL_DOWNLOAD)) {
    normalized.unshift(DELIVERY_ACTION.DIGITAL_DOWNLOAD);
  }
  if (type === 'LICENSE' && !normalized.includes(DELIVERY_ACTION.LICENSE)) {
    normalized.unshift(DELIVERY_ACTION.LICENSE);
  }
  return normalized;
};

export const deliveryActionText = action => DELIVERY_ACTION_OPTIONS
  .find(option => option.value === action)?.label || action;

export const shopItemTypeText = (type, t) => {
  const fallback = SHOP_TYPE_OPTIONS.find(option => option.value === type)?.label || type || '未知';
  return translate(t, `shop.itemTypes.${type}`, fallback);
};

export const deliveryModeText = (deliveryMode, t) => {
  const fallback = DELIVERY_MODE_OPTIONS
    .find(option => option.value === deliveryMode)?.label || deliveryMode || '人工交付';
  return translate(t, `shop.deliveryModes.${deliveryMode || DELIVERY_MODE.MANUAL}`, fallback);
};

export const deliveryModeColor = deliveryMode => DELIVERY_MODE_COLOR[deliveryMode] || 'default';

export const orderStatusText = (status, t) => translate(
  t,
  `shop.orderStatuses.${status}`,
  ORDER_STATUS_TEXT[status] || status || '未知'
);

export const orderStatusColor = status => ORDER_STATUS_COLOR[status] || 'default';

export const deliveryStatusText = (status, deliveryMode, t) => {
  if (status === 'WAITING' && deliveryMode === DELIVERY_MODE.MANUAL) {
    return translate(t, 'shop.deliveryStatuses.WAITING_MANUAL', '待人工交付');
  }
  const map = {
    WAITING: '待交付',
    PROCESSING: '交付中',
    SUCCESS: '交付成功',
    FAILED: '交付失败',
    SKIPPED: '已跳过'
  };
  return translate(t, `shop.deliveryStatuses.${status}`, map[status] || status || '未知');
};

export const deliveryStatusColor = status => {
  const colors = {
    WAITING: 'default',
    PROCESSING: 'processing',
    SUCCESS: 'success',
    FAILED: 'error',
    SKIPPED: 'default'
  };
  return colors[status] || 'default';
};

export const isDigitalDownloadType = type => type === 'DIGITAL_DOWNLOAD';

export const isLicenseType = type => type === 'LICENSE';
