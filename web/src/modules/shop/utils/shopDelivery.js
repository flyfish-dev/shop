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
