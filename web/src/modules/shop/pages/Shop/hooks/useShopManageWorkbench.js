import { computed, ref } from 'vue';
import { getCurrentShop, getOrders, getShopItemGroups, getShopItems } from '../apis/api.js';
import {
  getCoupons,
  getGitAccessTokens,
  getManagedGitRepositories,
  getManageUsers
} from '../apis/manage.js';
import { getManagedTickets } from '@/modules/shop/pages/Support/apis.js';
import { isTicketDone } from '@/modules/shop/utils/supportTickets.js';

const emptyArray = [];
const gitProviders = ['github', 'gitea', 'gitee'];

const fulfilledValue = (result, fallback = emptyArray) => (
  result.status === 'fulfilled' ? result.value : fallback
);

const listTotal = list => list?.page?.total ?? list?.length ?? 0;

const money = value => Number(value || 0);

const isPaidOrder = order => ['PAID', 'DELIVERED'].includes(order?.status) || Boolean(order?.paidTime);

const isWaitingDelivery = order => (
  ['PAID'].includes(order?.status)
  || ['WAITING', 'PROCESSING'].includes(order?.deliveryStatus)
);

const isToday = value => {
  if (!value) {
    return false;
  }
  const normalized = String(value).replace(' ', 'T');
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) {
    return false;
  }
  const now = new Date();
  return date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
};

const paidAt = order => order?.paidTime || order?.updateTime || order?.createTime;

export function useShopManageWorkbench() {
  const loading = ref(false);
  const error = ref('');
  const shop = ref(null);
  const groups = ref([]);
  const items = ref([]);
  const orders = ref([]);
  const tickets = ref([]);
  const users = ref([]);
  const coupons = ref([]);
  const repositories = ref([]);
  const tokens = ref([]);

  const loadWorkbench = async () => {
    loading.value = true;
    error.value = '';
    try {
      const results = await Promise.allSettled([
        getCurrentShop(),
        getShopItemGroups(),
        getShopItems({ page: 0, size: 500, includeDisabled: true }),
        getOrders(),
        getManagedTickets(),
        getManageUsers(),
        getCoupons(),
        getManagedGitRepositories({ includeDisabled: true }),
        ...gitProviders.map(provider => getGitAccessTokens({ provider }))
      ]);

      shop.value = fulfilledValue(results[0], null);
      groups.value = fulfilledValue(results[1]);
      items.value = fulfilledValue(results[2]);
      orders.value = fulfilledValue(results[3]);
      tickets.value = fulfilledValue(results[4]);
      users.value = fulfilledValue(results[5]);
      coupons.value = fulfilledValue(results[6]);
      repositories.value = fulfilledValue(results[7]);
      tokens.value = results.slice(8).flatMap(result => fulfilledValue(result));

      const rejected = results.find(result => result.status === 'rejected');
      if (rejected) {
        error.value = rejected.reason?.message || '部分数据加载失败';
      }
    } catch (e) {
      error.value = e.message || '工作台加载失败';
    } finally {
      loading.value = false;
    }
  };

  const paidOrders = computed(() => orders.value.filter(isPaidOrder));
  const todayPaidOrders = computed(() => paidOrders.value.filter(order => isToday(paidAt(order))));
  const pendingPaymentOrders = computed(() => orders.value.filter(order => ['PENDING', 'PAYING'].includes(order.status)));
  const waitingDeliveryOrders = computed(() => orders.value.filter(isWaitingDelivery));
  const activeTickets = computed(() => tickets.value.filter(ticket => !isTicketDone(ticket.status)));

  const summary = computed(() => ({
    shopName: shop.value?.name || '飞鱼小铺',
    itemTotal: listTotal(items.value),
    enabledItemCount: items.value.filter(item => item.enabled !== false).length,
    pinnedItemCount: items.value.filter(item => item.pinned).length,
    recommendedItemCount: items.value.filter(item => item.recommended).length,
    groupCount: groups.value.length,
    orderTotal: orders.value.length,
    paidOrderCount: paidOrders.value.length,
    pendingPaymentCount: pendingPaymentOrders.value.length,
    waitingDeliveryCount: waitingDeliveryOrders.value.length,
    revenueAmount: paidOrders.value.reduce((sum, order) => sum + money(order.amount), 0),
    todayOrderCount: todayPaidOrders.value.length,
    todayRevenueAmount: todayPaidOrders.value.reduce((sum, order) => sum + money(order.amount), 0),
    ticketTotal: tickets.value.length,
    activeTicketCount: activeTickets.value.length,
    userCount: users.value.length,
    couponCount: coupons.value.length,
    enabledCouponCount: coupons.value.filter(coupon => coupon.enabled !== false).length,
    repositoryCount: repositories.value.length,
    enabledRepositoryCount: repositories.value.filter(repository => repository.enabled !== false).length,
    tokenCount: tokens.value.length,
    enabledTokenCount: tokens.value.filter(token => token.enabled !== false && !token.expired).length
  }));

  return {
    loading,
    error,
    shop,
    groups,
    items,
    orders,
    tickets,
    users,
    coupons,
    repositories,
    tokens,
    summary,
    loadWorkbench
  };
}
