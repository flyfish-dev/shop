import { reactive, ref } from 'vue';
import { get, post } from '@/network/request.js';
import { message } from 'ant-design-vue';
const root = '/shops/managements/xianyu';
export function useXianyuManagement() {
  const status = ref(null);
  const records = reactive({ sellers: [], capabilities: [], listings: [], orders: [], jobs: [], messages: [] });
  const loading = ref(false);
  const error = ref('');
  const busy = reactive(new Set());
  const load = async () => {
    loading.value = true; error.value = '';
    try {
      status.value = await get(`${root}/status`, { credential: true });
      if (!status.value.configured) return;
      const names = Object.keys(records);
      const results = await Promise.allSettled(names.map(name => get(`${root}/${name}`, { credential: true })));
      results.forEach((r, i) => {
        if (r.status === 'fulfilled') records[names[i]] = r.value || [];
        else error.value = r.reason.message;
      });
    } catch (e) { error.value = e.message; }
    finally { loading.value = false; }
  };
  const execute = async (operation, body, key = operation) => {
    if (busy.has(key)) return;
    busy.add(key);
    try {
      const result = await post(`${root}/${operation}`, { body, credential: true });
      message.success('操作已记录'); await load(); return result;
    } catch (e) { message.error(e.message); throw e; }
    finally { busy.delete(key); }
  };
  return { status, records, loading, error, busy, load, execute };
}
