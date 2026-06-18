const toTime = value => {
  if (!value) {
    return 0;
  }
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
};

const newestOrderTime = order => toTime(order?.createTime) || toTime(order?.paidTime) || toTime(order?.expireTime);

export const sortOrdersByNewest = records => {
  return [...(Array.isArray(records) ? records : [])].sort((a, b) => {
    const timeDiff = newestOrderTime(b) - newestOrderTime(a);
    if (timeDiff !== 0) {
      return timeDiff;
    }
    return String(b?.orderNo || '').localeCompare(String(a?.orderNo || ''), 'zh-Hans-CN', { numeric: true });
  });
};
