export const TICKET_STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'OPEN' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '待反馈', value: 'WAITING_USER' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' }
];

export const TICKET_PRIORITY_OPTIONS = [
  { label: '低', value: 'LOW' },
  { label: '普通', value: 'NORMAL' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' }
];

export const TICKET_CATEGORY_OPTIONS = [
  { label: '常规问题', value: 'GENERAL' },
  { label: '账号绑定', value: 'ACCOUNT' },
  { label: '支付订单', value: 'PAYMENT' },
  { label: '权限开通', value: 'DELIVERY' },
  { label: '功能异常', value: 'BUG' }
];

const statusTextMap = {
  OPEN: '待处理',
  PROCESSING: '处理中',
  WAITING_USER: '待反馈',
  RESOLVED: '已解决',
  CLOSED: '已关闭'
};

const statusColorMap = {
  OPEN: 'blue',
  PROCESSING: 'processing',
  WAITING_USER: 'warning',
  RESOLVED: 'success',
  CLOSED: 'default'
};

const priorityTextMap = {
  LOW: '低',
  NORMAL: '普通',
  HIGH: '高',
  URGENT: '紧急'
};

const priorityColorMap = {
  LOW: 'default',
  NORMAL: 'blue',
  HIGH: 'orange',
  URGENT: 'red'
};

const categoryTextMap = Object.fromEntries(TICKET_CATEGORY_OPTIONS.map(item => [item.value, item.label]));

const translate = (t, key, fallback) => typeof t === 'function' ? t(key) : fallback;

export const ticketStatusOptions = t => TICKET_STATUS_OPTIONS.map(option => ({
  ...option,
  label: option.value
    ? translate(t, `tickets.statuses.${option.value}`, option.label)
    : translate(t, 'tickets.statuses.all', option.label)
}));

export const ticketPriorityOptions = t => TICKET_PRIORITY_OPTIONS.map(option => ({
  ...option,
  label: translate(t, `tickets.priorities.${option.value}`, option.label)
}));

export const ticketCategoryOptions = t => TICKET_CATEGORY_OPTIONS.map(option => ({
  ...option,
  label: translate(t, `tickets.categories.${option.value}`, option.label)
}));

export const ticketStatusText = (status, t) => translate(
  t,
  `tickets.statuses.${status}`,
  statusTextMap[status] || status || '未知'
);

export const ticketStatusColor = status => statusColorMap[status] || 'default';

export const ticketPriorityText = (priority, t) => translate(
  t,
  `tickets.priorities.${priority}`,
  priorityTextMap[priority] || priority || '普通'
);

export const ticketPriorityColor = priority => priorityColorMap[priority] || 'default';

export const ticketCategoryText = (category, t) => translate(
  t,
  `tickets.categories.${category}`,
  categoryTextMap[category] || category || '常规问题'
);

export const isTicketDone = status => ['RESOLVED', 'CLOSED'].includes(status);
