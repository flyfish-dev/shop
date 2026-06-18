import { get, post } from '@/network/request.js';

const BASE = '/portal/customer-service';

export const getCustomerServiceSummary = () => get(`${BASE}/summary`, { credential: true, authRedirect: false });

export const getMyCustomerConversation = () => get(`${BASE}/conversation`, { credential: true });

export const sendMyCustomerMessage = body => post(`${BASE}/messages`, { body, credential: true });

export const markMyCustomerConversationRead = () => post(`${BASE}/conversation/read`, {
  credential: true,
  authRedirect: false
});

export const getManagedCustomerConversations = params => get(`${BASE}/management/conversations`, {
  params,
  credential: true
});

export const getManagedWechatActivities = params => get(`${BASE}/management/wechat-activities`, {
  params,
  credential: true
});

export const getManagedCustomerConversation = conversationId => get(`${BASE}/management/conversations/${conversationId}`, {
  credential: true
});

export const getManagedCustomerConversationByUser = userId => get(`${BASE}/management/users/${userId}/conversation`, {
  credential: true
});

export const sendManagedCustomerMessage = (conversationId, body) => post(
  `${BASE}/management/conversations/${conversationId}/messages`,
  { body, credential: true }
);

export const markManagedCustomerConversationRead = conversationId => post(
  `${BASE}/management/conversations/${conversationId}/read`,
  { credential: true, authRedirect: false }
);
