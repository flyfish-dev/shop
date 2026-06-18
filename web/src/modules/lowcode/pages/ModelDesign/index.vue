<template>
  <a-spin :spinning="!!loading" :tip="loading">
    <a-card class="model-design">
      <template #title>
        <navigator-bar>
          <block-outlined /> 数据建模向导 - {{title}}
        </navigator-bar>
      </template>
      <div class="content">
        <a-steps size="small" :current="current" @update:current="setCurrent">
          <a-step title="选择数据源">
            <template #description>
              <a class="step-desc">{{dataSource?.name}}</a>
            </template>
          </a-step>
          <a-step title="选择数据表" :disabled="!dataSource">
            <template #description>
              <a class="step-desc">
                {{table}}
                <span style='color: green' v-if='current > 1 && !table'>[将新建表]</span>
              </a>
            </template>
          </a-step>
          <a-step title="模型设计" :disabled="!table">
            <template #description>
              <a class="step-desc">{{model?._confirm ? model.name : ''}}</a>
            </template>
          </a-step>
          <a-step title="保存模型" :disabled="!model" />
        </a-steps>
        <keep-alive>
          <router-view />
        </keep-alive>
      </div>
    </a-card>
  </a-spin>
</template>

<script>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue';
import useStore from '@/modules/lowcode/store/datasource.js';
import { useRouter } from '@/router/use';
import { getTableDetail, testTable, validateDataSource } from '@/modules/lowcode/pages/ModelDesign/apis';
import SelectDataSource from './pages/SelectDataSource';
import SelectDataTable from './pages/SelectDataTable';
import SelectModelDesign from './pages/SelectModelDesign';
import SelectSaveModel from './pages/SelectSaveModel';
import { BlockOutlined } from '@ant-design/icons-vue';
import { storeToRefs } from 'pinia';


const views = [
  'select-data-source',
  'select-data-table',
  'select-model-design',
  'select-save-model',
];

export default {
  name: 'ModelDesign',
  components: {
    SelectDataSource,
    SelectDataTable,
    SelectModelDesign,
    SelectSaveModel,
    BlockOutlined,
  },
  setup() {
    const router = useRouter();
    const store = useStore();
    const { dataSources } = storeToRefs(store);
    const { proxy } = getCurrentInstance()

    const current = computed(() => router.route.index);
    // 改变当前下标
    const routeParams = extra => ({
      source: dataSource.value,
      table: table.value,
      ...extra
    });
    const setCurrent = val => router.replace(`/model-design/${views[val]}`, routeParams());
    // 加载状态
    const loading = ref(null);
    // 数据源
    const dataSource = computed(() => router.route.query?.source)
    // 数据表
    const table = computed(() => router.route.query?.table)
    // 表模型
    const model = ref(null);
    // 保存状态
    const status = ref(null);
    // 状态信息
    const info = ref(null);
    // 设置数据源
    provide('setDataSource', async value => {
      loading.value = '加载数据源...';
      try {
        const source = await validateDataSource(value?.key ? { key: value.key } : value);
        model.value = null;
        router.replace(`/model-design/${views[1]}`, routeParams({ source, table: undefined }));
      } catch (e) {
        proxy.$message.error(e.message || e);
      } finally {
        loading.value = null;
      }
    })
    provide('info', info)
    provide('dataSource', dataSource)
    provide('setCurrent', setCurrent)
    provide('setTable', (value, link) => {
      const target = link ? 2 : current.value;
      if (link) {
        model.value = null;
      }
      router.replace(`/model-design/${views[target]}`, routeParams({ table: value }));
    })
    provide('table', table)
    provide('model', model)
    provide('setModel', value => {
      model.value = value;
      setCurrent(3);
    })
    // 加载表模型
    provide('loadModel', async target => {
      // 根据表的基本信息，查询字段信息并放入
      const detail = await getTableDetail(dataSource.value, table.value);
      const { name, comment, columns, indexes } = detail || {};
      // 目前仅对接了列、索引
      Object.assign(target, {
        name, comment, oldName: name,
        fields: columns.map((column, i) => {
          column._id = Date.now() + i;
          column.oldName = column.name;
          return column;
        }),
        indexes: indexes.map((index, i) => {
          index._id = Date.now() + i;
          index.oldName = index.name;
          return index;
        })
      });
    });
    // 测试表运行
    provide('testModel', async data => {
      const { name, oldName, comment, fields: columns, indexes, related } = data || model.value;
      return testTable(dataSource.value, { name, oldName, comment, columns, indexes, related });
    });
    // 提供出状态字段
    provide('status', status);
    // 提供数据源
    provide('dataSources', dataSources)
    // 路由守护
    const routeGuard = () => {
      if (current.value === 2) {
        return "刷新将丢失您的修改！";
      }
    };
    // 加载回调
    onMounted(() => {
      store.loadDataSources();
      window.addEventListener('beforeunload', routeGuard)
    });
    onBeforeUnmount(() => {
      window.removeEventListener('beforeunload', routeGuard)
    })
    // 在保存页面，但是模型为空，跳回表设计页面
    if (current.value === 3) {
      setCurrent(2);
    }
    return {
      loading,
      current,
      setCurrent,
      title: computed(() => router.route.name),
      dataSource: computed(() => store.dataSources.find(item => item.key === dataSource.value)),
      table,
      model,
    }
  }
}
</script>

<style lang='less'>
.ant-drawer-content-wrapper {
  max-width: 100% !important;
}
</style>

<style scoped lang="less">
.model-design {
  width: 100%;
  height: 100%;
  .content {
    max-width: 1000px;
    margin: 20px auto;
  }
  .step-desc {
    color: #1890ff;
    font-size: 12px;
  }
}
</style>
