import { del, get, post, put } from '@/network/request';

// 管理工作台
export const getManageWorkbenchSummary = () => get('/shops/managements/workbench/summary', { credential: true });
export const getManageWorkbenchOverview = () => get('/shops/managements/workbench/overview', { credential: true });
export const getManageWorkbenchInsights = () => get('/shops/managements/workbench/insights', { credential: true });

// 店铺管理
export const updateShop = (id, data) => put(`/shops/managements/${id}`, { body: data, credential: true });

// 商品分组管理
export const createItemGroup = (data) => post(`/shops/managements/item-groups`, { body: data, credential: true });
export const updateItemGroup = (id, data) => put(`/shops/managements/item-groups/${id}`, { body: data, credential: true });
export const deleteItemGroup = (id) => del(`/shops/managements/item-groups/${id}`, { credential: true });

// 商品管理
export const createItem = (data) => post(`/shops/managements/items`, { body: data, credential: true });
export const updateItem = (id, data) => put(`/shops/managements/items/${id}`, { body: data, credential: true });
export const deleteItem = (id) => del(`/shops/managements/items/${id}`, { credential: true });
export const getGitRepositories = (params) => get('/shops/managements/repositories', { params, credential: true });
export const getGitRepositoryOptions = (params) => get('/shops/managements/git/repository-options', { params, credential: true });

// 代码仓库与 API Token 管理
export const getManagedGitRepositories = (params) => get('/shops/managements/git/repositories', { params, credential: true });
export const createManagedGitRepository = (data) => post('/shops/managements/git/repositories', { body: data, credential: true });
export const updateManagedGitRepository = (id, data) => put(`/shops/managements/git/repositories/${id}`, { body: data, credential: true });
export const deleteManagedGitRepository = (id) => del(`/shops/managements/git/repositories/${id}`, { credential: true });
export const getRemoteGitRepositories = (params) => get('/shops/managements/git/remote-repositories', { params, credential: true });
export const syncManagedGitRepositories = (data) => post('/shops/managements/git/repositories/sync', { body: data, credential: true });
export const getGitAccessTokens = (params) => get('/shops/managements/git/api-tokens', { params, credential: true });
export const createGitAccessToken = (data) => post('/shops/managements/git/api-tokens', { body: data, credential: true });
export const updateGitAccessToken = (id, data) => put(`/shops/managements/git/api-tokens/${id}`, { body: data, credential: true });
export const deleteGitAccessToken = (id) => del(`/shops/managements/git/api-tokens/${id}`, { credential: true });

// 用户管理
export const getManageUsers = (params) => get('/shops/managements/users', { params, credential: true });

// 合同管理
export const getContracts = (params) => get('/shops/managements/contracts', { params, credential: true });
export const createContract = (data) => post('/shops/managements/contracts', { body: data, credential: true });
export const updateContract = (id, data) => put(`/shops/managements/contracts/${id}`, { body: data, credential: true });
export const deleteContract = (id) => del(`/shops/managements/contracts/${id}`, { credential: true });
export const uploadContractFile = (id, data) => post(`/shops/managements/contracts/${id}/files`, { body: data, credential: true });
export const updateContractFile = (id, fileId, data) => put(`/shops/managements/contracts/${id}/files/${fileId}`, { body: data, credential: true });
export const deleteContractFile = (id, fileId) => del(`/shops/managements/contracts/${id}/files/${fileId}`, { credential: true });
export const getContractSignatures = () => get('/shops/managements/contract-signatures', { credential: true });

// 优惠券管理
export const getCoupons = () => get('/shops/managements/coupons', { credential: true });
export const createCoupon = (data) => post('/shops/managements/coupons', { body: data, credential: true });
export const updateCoupon = (id, data) => put(`/shops/managements/coupons/${id}`, { body: data, credential: true });
export const deleteCoupon = (id) => del(`/shops/managements/coupons/${id}`, { credential: true });

// 订单交付管理
export const getOrderDelivery = (orderNo) => get(`/shops/managements/orders/${orderNo}/delivery`, { credential: true });
export const updateOrderDelivery = (orderNo, data) => put(`/shops/managements/orders/${orderNo}/delivery`, { body: data, credential: true });
export const retryOrderDelivery = (orderNo) => post(`/shops/managements/orders/${orderNo}/delivery/retry`, { credential: true });

// 文件上传
export const uploadImage = (data) => post('/shops/managements/upload', { body: data, credential: true });
