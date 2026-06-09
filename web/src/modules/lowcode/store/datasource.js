import { defineStore } from 'pinia';
import { deleteDataSource, listDataSource, validateDataSource } from '@/modules/lowcode/pages/ModelDesign/apis';

// 共享简单store
const useStore = defineStore('main', {
  persist: true,
  state: () => ({
    source: {},
  }),
  getters: {
    dataSources: state => {
      const source = state.source;
      return Object.keys(source).map(key => ({
        id: key,
        ...source[key]
      }));
    }
  },
  // 一些方法和异步操作
  actions: {
    setSources(list) {
      this.source = list;
    },
    setUser(user) {
      this.user = user;
    },
    async removeSource({ key }) {
      await deleteDataSource(key);
      await this.loadDataSources();
    },
    async setSource(data) {
      await validateDataSource(data);
      await this.loadDataSources();
    },
    async loadDataSources() {
      const source = await listDataSource();
      this.source = source.reduce((res, item) => {
        res[item.key] = item;
        return res;
      }, {});
    }
  }
});

export default useStore;
