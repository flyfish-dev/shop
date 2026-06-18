<template>
  <div class="select-data-table">
    <a-affix>
      <h2 class="table-tool-bar">数据表
        <a class="opt-btn" @click="setTable('', true)"><plus-outlined/>新建</a>
        <a class="opt-btn" @click="changeSearch" :class="{red: search}">
          <search-outlined/>{{search ? '隐藏搜索' : '搜索'}}
        </a>
        <a class="opt-btn" @click="showQuery"><console-sql-outlined /> 运行SQL</a>
      </h2>
      <a-input-search v-show="search" ref='searchInput' v-model:value="filterText" />
    </a-affix>
    <a-spin :spinning="loading">
      <a-row :gutter="[12, 12]" class="table-list">
        <a-col :key="item.name" v-for="item in filtered" :xs='24' :sm='12' :md='8' :lg="6">
          <a-card @click="setTable(item.name, true)" hoverable class="list-item" :class="{selected: table === item.name}">
            <div class="float-btn-group">
              <a-button @click.stop size="small" shape="circle" @click='view(item)'>
                <template #icon><eye-filled /></template>
              </a-button>
              <a-button @click.stop size='small' shape='circle' @click='ddl(item)'>
                <template #icon><info-outlined /></template>
              </a-button>
              <a-popconfirm ok-text="复制" cancel-text="取消" @confirm="copy(item)">
                <template #title>
                  <h4>复制 {{item.name}}？</h4>
                </template>
                <a-button @click.stop size="small" type="primary" shape="circle">
                  <template #icon><copy-filled /></template>
                </a-button>
              </a-popconfirm>
              <a-popconfirm ok-text="删除" ok-type="danger" cancel-text="取消" @confirm="drop(item)">
                <template #title>
                  <h4>删除 {{item.name}}？</h4>
                </template>
                <a-button @click.stop size="small" type="primary" danger shape="circle">
                  <template #icon><delete-filled /></template>
                </a-button>
              </a-popconfirm>
            </div>
            <a-card-meta>
              <template #description>
                <text-highlight :keyword="filter" :content="item.comment || item.name" />
              </template>
              <template #title>
                <text-highlight :keyword="filter" :content="item.name" />
              </template>
              <template #avatar>
                <a-avatar style="background-color: #87d068">
                  <template #icon>
                    <table-outlined />
                  </template>
                </a-avatar>
              </template>
            </a-card-meta>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
    <data-list ref='dataTable' />
    <sql-drawer v-model:visible='sql' />
  </div>
</template>

<script lang='jsx'>
import * as Vue from 'vue';
import {
  TableOutlined,
  PlusOutlined,
  SearchOutlined,
  DeleteFilled,
  CopyFilled,
  EyeFilled,
  InfoOutlined,
  ConsoleSqlOutlined,
} from '@ant-design/icons-vue';
import { copyTable, dropTable, getTableDdl, getTables } from '@/modules/lowcode/pages/ModelDesign/apis';
import TextHighlight from '@/components/TextHighlight';
import { message as $message, Modal, Skeleton } from 'ant-design-vue';
import debounce from 'lodash/debounce';
import DataList from '@/modules/lowcode/pages/ModelDesign/pages/components/DataList.vue';
import SqlDrawer from '@/modules/lowcode/pages/ModelDesign/pages/components/SqlDrawer.vue';

const { computed, inject, nextTick, onMounted, ref, watch } = Vue;

export default {
  name: 'SelectDataTable',
  components: {
    SqlDrawer,
    DataList,
    TextHighlight,
    InfoOutlined,
    TableOutlined,
    PlusOutlined,
    SearchOutlined,
    DeleteFilled,
    CopyFilled,
    EyeFilled,
    ConsoleSqlOutlined,
  },
  setup() {
    const loading = ref(false)
    const searchInput = ref(null)
    const dataTable = ref(null);
    const sql = ref(false);
    const search = ref(false);
    const dataSource = inject('dataSource')
    const table = inject('table')

    const setTable = inject('setTable')
    const tables = ref([]);
    const filterText = ref('');
    const filter = ref('');
    const fetchTables = async () => {
      if (!dataSource.value) return;
      loading.value = true;
      try {
        const result = await getTables(dataSource.value);
        tables.value = result || [];
      } catch (e) {
        tables.value = [];
        $message.error(e.message || '加载数据表失败');
      } finally {
        loading.value = false;
      }
    };
    watch(() => dataSource.value, () => fetchTables())
    watch(filterText, debounce(value => filter.value = value, 200))
    onMounted(() => fetchTables())
    return {
      filterText,
      filter,
      search,
      fetchTables,
      setTable,
      tables,
      filtered: computed(() => tables.value.filter(({ name, comment = '' }) =>
          name.includes(filter.value) || comment.includes(filter.value))),
      changeSearch() {
        search.value = !search.value;
        if (!search.value) {
          filterText.value = ''
        } else {
          nextTick(() => searchInput.value.focus());
        }
      },
      showQuery() {
        sql.value = true;
      },
      async drop(item) {
        await dropTable(dataSource.value, item.name)
        $message.success(`成功删除表${item.name}！`);
        await fetchTables();
      },
      async copy(item) {
        await copyTable(dataSource.value, item.name)
        $message.success(`成功复制表${item.name}为${item.name}_copy！`);
        await fetchTables();
      },
      view(item) {
        dataTable.value.show(item);
      },
      async ddl(item) {
        const ddl = ref(null);
        Modal.info({
          title: '查看DDL',
          content: () => (
            <Skeleton loading={!ddl.value}>
              <pre style='font-size: 12px'>{ddl.value}</pre>
            </Skeleton>
          ),
          width: 1000
        });
        ddl.value = await getTableDdl(dataSource.value, item.name)
      },
      dataSource,
      searchInput,
      dataTable,
      sql,
      table,
      loading,
    };
  },
}
</script>

<style scoped lang="less">
.select-data-table {
  margin: 40px auto;
  :deep(.ant-affix) {
    .table-tool-bar {
      background-color: white;
      padding: 15px 0;
      border-bottom: 1px solid lightgrey;
    }
  }
  .table-list {
    margin-top: 30px;
    .list-item {
      transition: 0.5s background-color ease;
      :deep(.ant-card-body) {
        padding: 15px;
        .ant-card-meta-title {
          font-size: 13px;
        }
        .ant-card-meta-description {
          font-size: 12px;
        }
      }
      .float-btn-group {
        position: absolute;
        right: -10px;
        bottom: -10px;
        display: none;
        button:not(:last-child) {
          margin-right: 5px;
        }
      }
      &:hover {
        background-color: #e6f4ff;
        cursor: pointer;
        .float-btn-group {
          display: block;
        }
      }
      &.selected {
        background-color: #a1d5fa;
      }
    }
  }
  .opt-btn {
    margin-left: 20px;
    &.red {
      color: red;
    }
  }
}
</style>
