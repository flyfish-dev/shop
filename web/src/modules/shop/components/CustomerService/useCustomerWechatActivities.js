import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getManagedWechatActivities } from './apis.js';

const REFRESH_INTERVAL = 20000;

export function useCustomerWechatActivities() {
  const loading = ref(false);
  const activities = ref([]);
  const keyword = ref('');
  const activityType = ref('ALL');

  let requestId = 0;
  let refreshTimer = null;

  const hasFilter = computed(() => Boolean(keyword.value.trim()) || activityType.value !== 'ALL');

  const loadActivities = async (options = {}) => {
    const { silent = false } = options;
    const currentRequest = ++requestId;
    if (!silent) {
      loading.value = true;
    }
    try {
      const result = await getManagedWechatActivities({
        keyword: keyword.value.trim() || undefined,
        activityType: activityType.value === 'ALL' ? undefined : activityType.value,
        limit: 120
      });
      if (currentRequest === requestId) {
        activities.value = Array.isArray(result) ? result : [];
      }
    } catch (e) {
      if (currentRequest === requestId && !silent) {
        message.warning(e?.message || '公众号动态加载失败');
      }
    } finally {
      if (currentRequest === requestId && (!silent || loading.value)) {
        loading.value = false;
      }
    }
  };

  const resetFilters = () => {
    keyword.value = '';
    activityType.value = 'ALL';
    loadActivities();
  };

  const startAutoRefresh = () => {
    if (refreshTimer) {
      return;
    }
    loadActivities();
    refreshTimer = window.setInterval(() => {
      loadActivities({ silent: true });
    }, REFRESH_INTERVAL);
  };

  const stopAutoRefresh = () => {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  };

  return {
    loading,
    activities,
    keyword,
    activityType,
    hasFilter,
    loadActivities,
    resetFilters,
    startAutoRefresh,
    stopAutoRefresh
  };
}
