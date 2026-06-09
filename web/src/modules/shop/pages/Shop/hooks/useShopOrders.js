import { ref } from 'vue';
import { getOrders } from '../apis/api.js';
import { sortOrdersByNewest } from '@/modules/shop/utils/orderSort.js';

export function useShopOrders({ item, user }) {
  const orders = ref([]);
  const ordersLoading = ref(false);
  const ordersError = ref('');
  let ordersRequestSeq = 0;

  const loadOrders = async () => {
    const requestSeq = ++ordersRequestSeq;
    if (!user.value?.id || !item.value?.id) {
      orders.value = [];
      ordersError.value = '';
      ordersLoading.value = false;
      return;
    }
    const itemId = item.value.id;
    ordersLoading.value = true;
    ordersError.value = '';
    try {
      const result = sortOrdersByNewest(await getOrders({ itemId }));
      if (requestSeq === ordersRequestSeq && item.value?.id === itemId) {
        orders.value = result;
      }
    } catch (e) {
      if (requestSeq === ordersRequestSeq && item.value?.id === itemId) {
        orders.value = [];
        ordersError.value = e.message || '购买记录加载失败';
      }
    } finally {
      if (requestSeq === ordersRequestSeq) {
        ordersLoading.value = false;
      }
    }
  };

  return {
    orders,
    ordersLoading,
    ordersError,
    loadOrders
  };
}
