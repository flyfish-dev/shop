import { computed, ref } from 'vue';
import { getManageWorkbenchSummary } from '../apis/manage.js';

const defaultSummary = () => ({
  shopName: '飞鱼小铺',
  itemTotal: 0,
  enabledItemCount: 0,
  pinnedItemCount: 0,
  recommendedItemCount: 0,
  groupCount: 0,
  orderTotal: 0,
  paidOrderCount: 0,
  pendingPaymentCount: 0,
  waitingDeliveryCount: 0,
  revenueAmount: 0,
  todayOrderCount: 0,
  todayRevenueAmount: 0,
  ticketTotal: 0,
  activeTicketCount: 0,
  userCount: 0,
  couponCount: 0,
  enabledCouponCount: 0,
  repositoryCount: 0,
  enabledRepositoryCount: 0,
  tokenCount: 0,
  enabledTokenCount: 0
});

export function useShopManageWorkbench() {
  const fetching = ref(false);
  const loaded = ref(false);
  const error = ref('');
  const summary = ref(defaultSummary());
  let inFlight = null;
  let requestVersion = 0;

  const loadWorkbench = async () => {
    if (inFlight) {
      return inFlight;
    }
    const version = ++requestVersion;
    fetching.value = true;
    error.value = '';
    inFlight = getManageWorkbenchSummary()
      .then(data => {
        if (version !== requestVersion) {
          return;
        }
        summary.value = {
          ...defaultSummary(),
          ...(data || {})
        };
        loaded.value = true;
      })
      .catch(e => {
        if (version === requestVersion) {
          error.value = e.message || '工作台数据加载失败';
        }
      })
      .finally(() => {
        if (version === requestVersion) {
          fetching.value = false;
        }
        inFlight = null;
      });
    return inFlight;
  };

  const loading = computed(() => fetching.value && !loaded.value);
  const refreshing = computed(() => fetching.value && loaded.value);

  return {
    loading,
    overviewLoading: loading,
    insightLoading: loading,
    refreshing,
    loaded,
    error,
    summary,
    loadWorkbench
  };
}
