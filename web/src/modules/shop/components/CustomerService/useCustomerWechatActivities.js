import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { getManagedWechatActivities } from './apis.js';

export function useCustomerWechatActivities() {
  const loading = ref(false);
  const activities = ref([]);
  const keyword = ref('');
  const activityType = ref('ALL');

  let requestId = 0;

  const hasFilter = computed(() => Boolean(keyword.value.trim()) || activityType.value !== 'ALL');

  const loadActivities = async () => {
    const currentRequest = ++requestId;
    loading.value = true;
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
      if (currentRequest === requestId) {
        message.warning(e?.message || '公众号动态加载失败');
      }
    } finally {
      if (currentRequest === requestId) {
        loading.value = false;
      }
    }
  };

  const resetFilters = () => {
    keyword.value = '';
    activityType.value = 'ALL';
    loadActivities();
  };

  return {
    loading,
    activities,
    keyword,
    activityType,
    hasFilter,
    loadActivities,
    resetFilters
  };
}
