export const DELIVERY_MODE = {
  AUTOMATIC: 'AUTOMATIC',
  MANUAL: 'MANUAL',
  NONE: 'NONE'
};

export const DELIVERY_MODE_OPTIONS = [
  { label: '自动交付', value: DELIVERY_MODE.AUTOMATIC },
  { label: '人工交付', value: DELIVERY_MODE.MANUAL },
  { label: '无需交付', value: DELIVERY_MODE.NONE }
];

export const SHOP_TYPE_OPTIONS = [
  { label: 'Git 仓库开通', value: 'GIT_REPOSITORY_ACCESS' },
  { label: 'Git 仓库打赏开通', value: 'GIT_REPOSITORY_DONATION_ACCESS' },
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
  RUNTIME: 'runtime-license'
};

export const OFFICE_LICENSE_KIND_OPTIONS = [
  { label: '运行授权', value: OFFICE_LICENSE_KIND.RUNTIME }
];

export const OFFICE_LICENSE_FEATURE_OPTIONS = [
  { label: 'DOC', value: 'doc' },
  { label: 'DOCX', value: 'docx' },
  { label: 'PPT', value: 'ppt' },
  { label: 'PPTX', value: 'pptx' },
  { label: 'XLS', value: 'xls' },
  { label: 'XLSX', value: 'xlsx' },
  { label: 'XLSB', value: 'xlsb' },
  { label: '虚拟表格', value: 'virtual-excel' }
];

export const DEFAULT_OFFICE_LICENSE_FEATURES = OFFICE_LICENSE_FEATURE_OPTIONS.map(option => option.value);

const DELIVERY_MODES_BY_TYPE = {
  GIT_REPOSITORY_ACCESS: [DELIVERY_MODE.AUTOMATIC],
  GIT_REPOSITORY_DONATION_ACCESS: [DELIVERY_MODE.AUTOMATIC],
  DIGITAL_DOWNLOAD: [DELIVERY_MODE.AUTOMATIC, DELIVERY_MODE.MANUAL, DELIVERY_MODE.NONE],
  SERVICE_PACKAGE: [DELIVERY_MODE.MANUAL, DELIVERY_MODE.NONE],
  LICENSE: [DELIVERY_MODE.AUTOMATIC, DELIVERY_MODE.MANUAL, DELIVERY_MODE.NONE]
};

const DEFAULT_DELIVERY_MODE_BY_TYPE = {
  GIT_REPOSITORY_ACCESS: DELIVERY_MODE.AUTOMATIC,
  GIT_REPOSITORY_DONATION_ACCESS: DELIVERY_MODE.AUTOMATIC,
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

export const deliveryModeText = deliveryMode => DELIVERY_MODE_OPTIONS
  .find(option => option.value === deliveryMode)?.label || deliveryMode || '人工交付';

export const deliveryModeColor = deliveryMode => DELIVERY_MODE_COLOR[deliveryMode] || 'default';

export const orderStatusText = status => ORDER_STATUS_TEXT[status] || status || '未知';

export const orderStatusColor = status => ORDER_STATUS_COLOR[status] || 'default';

export const deliveryStatusText = (status, deliveryMode) => {
  if (status === 'WAITING' && deliveryMode === DELIVERY_MODE.MANUAL) {
    return '待人工交付';
  }
  const map = {
    WAITING: '待交付',
    PROCESSING: '交付中',
    SUCCESS: '交付成功',
    FAILED: '交付失败'
  };
  return map[status] || status || '未知';
};

export const deliveryStatusColor = status => {
  const colors = {
    WAITING: 'default',
    PROCESSING: 'processing',
    SUCCESS: 'success',
    FAILED: 'error'
  };
  return colors[status] || 'default';
};

export const isDigitalDownloadType = type => type === 'DIGITAL_DOWNLOAD';

export const isLicenseType = type => type === 'LICENSE';
