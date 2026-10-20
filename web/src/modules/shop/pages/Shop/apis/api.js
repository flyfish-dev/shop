import { get, post } from '@/network/request.js';
import { isXianyuRuntime, storageKey } from '@/modules/xianyu/runtime.js';

export const getCurrentShop = async () => {
  return get('/shops/current')
}

export const getPaymentMethods = async () => {
  return get('/shops/payment-methods');
};

export const getShopPricing = async () => {
  return get('/shops/pricing');
};

export const getShopItemGroups = async (params) => {
  return get('/shops/item-groups', { params });
};

export const getShopItems = async params => {
  return get('/shops/items', { params });
};

export const getDonationItems = async params => {
  return getShopItems({
    type: 'DONATION',
    ...params
  });
};

export const getShopItemPage = async params => {
  const records = await getShopItems(params);
  const items = Array.isArray(records) ? records : [];
  return {
    records: items,
    page: items.page || null
  };
};

export const getShopItemDetail = async id => {
  return get(`/shops/items/${id}`);
};

export const checkPurchaseAvailability = async (id, skuId) => {
  return get(`/shops/items/${id}/purchase-availability`, {
    params: skuId ? { skuId } : undefined,
    credential: true
  });
};

export const getLicenseRenewalEligibility = async (id, skuId) => {
  return get(`/shops/items/${id}/license-renewal`, {
    params: skuId ? { skuId } : undefined,
    credential: true
  });
};

export const getItemContracts = async (id, skuId, maintenanceAgreement = false, locale) => {
  return get(`/shops/items/${id}/contracts`, {
    params: {
      ...(skuId ? { skuId } : {}),
      ...(maintenanceAgreement ? { maintenanceAgreement: true } : {}),
      ...(locale ? { locale } : {})
    }
  });
};

export const signItemContractFile = async (id, data, skuId, maintenanceAgreement = false) => {
  return post(`/shops/items/${id}/contracts/signatures`, {
    params: {
      ...(skuId ? { skuId } : {}),
      ...(maintenanceAgreement ? { maintenanceAgreement: true } : {})
    },
    body: data,
    credential: true
  });
};

export const prepareGitBinding = async (provider = 'gitea', redirect = `${location.pathname}${location.search}`) => {
  return post(`/oauth/bind/${provider}`, {
    credential: true,
    params: {
      redirect
    }
  });
};

export const prepareGiteaBinding = async (redirect = `${location.pathname}${location.search}`) => {
  return prepareGitBinding('gitea', redirect);
};

export const prepareGithubBinding = async (redirect = `${location.pathname}${location.search}`) => {
  return prepareGitBinding('github', redirect);
};

export const createOrder = async (params) => {
  let headers;
  const key = storageKey('checkout-request');
  if (isXianyuRuntime) {
    const fingerprint = JSON.stringify(params);
    let saved;
    try { saved = JSON.parse(sessionStorage.getItem(key)); } catch { saved = null; }
    if (!saved || saved.fingerprint !== fingerprint) {
      saved = { fingerprint, id: crypto.randomUUID() };
      sessionStorage.setItem(key, JSON.stringify(saved));
    }
    headers = { 'Idempotency-Key': saved.id };
  }
  const result = await post('/shops/orders', { body: params, credential: true, headers });
  if (isXianyuRuntime) sessionStorage.removeItem(key);
  return result;
};

export const applyCoupon = async (params) => {
  return post('/shops/coupons/apply', { body: params, credential: true });
};

export const previewOfferLink = async params => {
  return post('/shops/offer-links/preview', { body: params, credential: true, authRedirect: false });
};

export const getOrders = async (params) => {
  return get('/shops/orders', { params, credential: true });
};

export const getMyOrders = async () => {
  return get('/shops/orders/mine', { credential: true });
};

export const getOrder = async (orderNo) => {
  return get(`/shops/orders/${orderNo}`, { credential: true });
};

export const extractOrderDelivery = async orderNo => {
  return post(`/shops/orders/${orderNo}/delivery/extract`, { credential: true });
};

export const downloadOrderDeliveryFile = async (orderNo, fileCode, licenseNo) => {
  return get(`/shops/orders/${orderNo}/delivery/files/${fileCode}`, {
    credential: true,
    blob: true,
    params: licenseNo ? { licenseNo } : undefined
  });
};

export const pay = createOrder;
