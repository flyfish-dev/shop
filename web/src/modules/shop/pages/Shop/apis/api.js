import { get, post } from '@/network/request.js';

export const getCurrentShop = async () => {
  return get('/shops/current')
}

export const getShopItemGroups = async (params) => {
  return get('/shops/item-groups', { params });
};

export const getShopItems = async params => {
  return get('/shops/items', { params });
};

export const getShopItemDetail = async id => {
  return get(`/shops/items/${id}`);
};

export const checkPurchaseAvailability = async id => {
  return get(`/shops/items/${id}/purchase-availability`, { credential: true });
};

export const getItemContracts = async id => {
  return get(`/shops/items/${id}/contracts`);
};

export const signItemContractFile = async (id, data) => {
  return post(`/shops/items/${id}/contracts/signatures`, { body: data, credential: true });
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
  return post('/shops/orders', { body: params, credential: true });
};

export const applyCoupon = async (params) => {
  return post('/shops/coupons/apply', { body: params, credential: true });
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

export const downloadOrderDeliveryFile = async (orderNo, fileCode) => {
  return get(`/shops/orders/${orderNo}/delivery/files/${fileCode}`, {
    credential: true,
    blob: true
  });
};

export const pay = createOrder;
