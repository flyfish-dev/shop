const toTime = value => {
  if (!value) {
    return 0;
  }
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
};

const newestTicketTime = ticket => toTime(ticket?.updateTime) || toTime(ticket?.createTime);

export const sortTicketsByNewest = records => {
  return [...(Array.isArray(records) ? records : [])].sort((a, b) => {
    const timeDiff = newestTicketTime(b) - newestTicketTime(a);
    if (timeDiff !== 0) {
      return timeDiff;
    }
    return String(b?.ticketNo || '').localeCompare(String(a?.ticketNo || ''), 'zh-Hans-CN', { numeric: true });
  });
};
