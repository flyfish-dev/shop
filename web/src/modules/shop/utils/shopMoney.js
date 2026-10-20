export const normalizeShopCurrency = value => String(value || 'CNY').trim().toUpperCase() === 'USD'
  ? 'USD'
  : 'CNY';

export const shopCurrencySymbol = currency => normalizeShopCurrency(currency) === 'USD' ? '$' : '¥';

export const formatShopMoney = (value, currency) => {
  const amount = Number(value || 0);
  const normalized = Number.isFinite(amount) ? amount : 0;
  return `${shopCurrencySymbol(currency)}${normalized.toFixed(2)}`;
};

export const formatShopCatalogMoney = (value, currency) => {
  const normalizedCurrency = normalizeShopCurrency(currency);
  if (normalizedCurrency !== 'USD') {
    return formatShopMoney(value, normalizedCurrency);
  }
  const amount = Number(value || 0);
  return `$${Number.isFinite(amount) ? Math.round(amount) : 0}`;
};

export const formatRevenueBreakdown = (cnyValue, usdValue) => {
  const cny = Number(cnyValue || 0);
  const usd = Number(usdValue || 0);
  const values = [];
  if (cny || !usd) {
    values.push(formatShopMoney(cny, 'CNY'));
  }
  if (usd) {
    values.push(formatShopMoney(usd, 'USD'));
  }
  return values.join(' · ');
};
