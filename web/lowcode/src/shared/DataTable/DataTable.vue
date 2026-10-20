<template>
  <div class='data-table'>
    <s-table :columns='mappedColumns'
             :data-source='data'
             :loading='loading'
             :pagination='pagination'
             :row-class-name='rowClassName'
             :custom-cell='customCell'
             :size='size'
             bordered
             :scroll='scroll'
             ref='table'
             :row-key='rowKey'
             :row-selection='rowSelection'
             @change='handleTableChange'
             @resizeColumn='handleResize'
    >
      <template #title>
        <div class='data-table-header'>
          <a-space class='data-table-header-left'>
            <span class='title'><table-outlined /></span>
            <span class='title'>数据管理</span>
          </a-space>
          <a-space class='data-table-header-right'>
            <a-space :class="{ active: editing }">
              <a-switch :checked="editing" @update:checked='handleEdit' checked-children="已启用编辑" un-checked-children="点击启用编辑" />
              <table-header-tool :disabled='!editing || changed' primary icon='plus-circle' name='添加行' @click='add' />
              <table-header-tool :disabled='!editing || !selectedRowKeys.length' danger icon='delete'
                                 confirm='请谨慎操作，删除选中行后不可恢复！' name='删除选中行' @click='remove' />
              <table-header-tool :disabled='!changing' primary icon='check-circle' name='完成编辑'
                                 confirm='确定要保存吗？您的更改将立即同步到数据库！' @click='edit' />
              <table-header-tool :disabled='!changing' danger icon='history' name='撤销编辑'
                                 confirm='确定要撤销编辑吗？您的修改不会保存！' @click='revoke' />
            </a-space>
            <a-divider type='vertical' v-if='client.width > 547'/>
            <a-space>
              <table-header-tool icon='reload' :disabled='changing' name='刷新' @click='fetchRows' />
              <table-header-tool icon='clear' name='重置视图' @click='reset' confirm='确定重置视图吗？重置后将丢失所有更改！' />
              <table-header-tool v-model:selected='showNumber' icon='number' name='启用序号' />
              <table-header-tool v-model:selected='showComment' icon='comment' name='显示字段备注' />
              <table-header-tool :current='size' :menus='sizes' icon='column-height' name='密度' @click='handleSize' />
              <table-header-tool v-model:selected='showSearch' :disabled='changing' icon='filter' name='高级搜索' />
              <table-header-tool icon='setting' name='列设置' popover :disabled='changing'>
                <template #title>
                  <div class='data-table-column-selector-title'>
                    <a-checkbox :checked='allColumn' @change='handleColumn'>列展示</a-checkbox>
                    <a @click='handleColumn(true)'>重置</a>
                  </div>
                </template>
                <template #content>
                  <div class='data-table-column-selector-list'>
                    <a-tree :checked-keys='selectedColumns' :show-icon='false' :tree-data='columnOptions'
                            checkable @check='handleColumn' />
                  </div>
                </template>
              </table-header-tool>
              <table-header-tool icon='close' name='关闭' @click='$emit("close")' />
            </a-space>
          </a-space>
        </div>
        <table-search-bar ref='searchBar' :columns='columns' :visible='showSearch' @search='doSearch' />
      </template>
      <template #cellEditor='props'>
        <table-cell-editor v-bind='props' @save='edit' />
      </template>
      <template #bodyCell='props'>
        <table-cell-render v-bind='props' />
      </template>
    </s-table>
    <div class='status-bar'>
      <template v-if='selectedRowKeys.length'>
        <span>
        已选中 <a>{{selectedRowKeys.length}}</a> 行
        <a class='clear' @click='clearSelect'>清空选择</a>
      </span>
        <a-divider type='vertical' />
      </template>
      <a :class='{ edit: editing, read: !editing }'>
        <check-circle-filled /> {{editing ? '编辑模式' : '只读模式'}}
      </a>
    </div>
  </div>
</template>

<script>
import { distinctBy, isEmpty } from '@/utils/utils';
import TableHeaderTool from './components/TableHeaderTool';
import TableSearchBar from './components/TableSearchBar';
import TableCellEditor from './components/TableCellEditor';
import TableCellRender from './components/TableCellRender';
import { TableOutlined, CheckCircleFilled } from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { computed, onMounted, ref, toRefs, watch } from 'vue';
import useClientStore from '@/modules/auth/store/client.js'

const defaultPage = () => ({
  current: 1,
  pageSize: 1000,
  showTotal(total, [start, end] = []) {
    return `当前数据区间 ${start} - ${end}，共${total}条`;
  },
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['100', '500', '1000', '2000', '5000']
});

// 表格大小
const sizes = [{ key: 'default', name: '默认' }, { key: 'middle', name: '中等' }, { key: 'small', name: '紧凑' }]

// 表格区域外空白高度
const otherHeights = {
  default: '168px',
  middle: '151px',
  small: '143px'
}

/**
 * 高级数据表格
 * @author wangyu
 * 需要前后端搭配使用
 */
export default {
  name: 'DataTable',
  components: { TableCellEditor, TableCellRender, TableSearchBar, TableHeaderTool, TableOutlined, CheckCircleFilled },
  props: {
    columns: {
      type: Array,
      default: () => []
    },
    loadData: {
      type: [Function, Array]
    }
  },
  emits: ['save', 'delete', 'close'],
  expose: ['reset', 'fetchRows'],
  setup(props, { emit }) {
    const client = useClientStore();
    const { columns, loadData } = toRefs(props);
    // 响应式监听列变动，取得key
    const columnKeys = computed(() => columns.value.map(({ dataIndex }) => dataIndex));
    // 搜索栏
    const searchBar = ref();
    // 表格实例
    const table = ref();
    // 加载状态
    const loading = ref(false);
    // 修改标记
    const editing = ref(false);
    // 数据
    const data = ref([]);
    // 修改中数据
    const temp = ref({});
    // 排序字段和字段规则
    const sorts = ref({});
    // 列大小
    const widths = ref({});
    // 筛选条件 (key: [ value ])
    const filters = ref({});
    // 查询项
    const filterItems = ref({});
    // 查询条件
    const search = ref([]);
    // 初始化
    const initialized = ref(false);
    // 显示搜索框
    const showSearch = ref(false);
    // 显示序号列
    const showNumber = ref(false);
    // 显示字段备注
    const showComment = ref(true);
    // 滚动设置
    const scroll = computed(() => ({ y: `calc(100vh - ${otherHeights[size.value]})` }));
    // 当前大小
    const size = ref('middle');
    // 选中的列
    const selectedColumns = ref(columnKeys.value);
    // 全部列
    const allColumn = computed(() => selectedColumns.value.length === columns.value.length);
    // 已选中的行键
    const selectedRowKeys = ref([]);
    // 已选中行数据
    const selectedRows = ref([]);
    // 选中行逻辑
    const rowSelection = computed(() => editing.value ? {
      // 翻页保留选中项
      preserveSelectedRowKeys: true,
      fixed: true,
      selectedRowKeys: selectedRowKeys.value,
      onChange: (keys, rows) => {
        selectedRowKeys.value = keys;
        selectedRows.value = rows;
      },
      getCheckboxProps: (record, index) => ({
        disabled: temp.value[index]?.create,
      }),
    } : null);
    // 清空选中
    const clearSelect = () => {
      selectedRows.value = [];
      selectedRowKeys.value = [];
    }
    // 已映射的列
    const mappedColumns = computed(() => {
      const editingValue = editing.value;
      const filtersValue = filters.value;
      const filterItemsValue = filterItems.value;
      const widthsValue = widths.value;
      const sortsValue = sorts.value;
      const showCommentValue = showComment.value;
      const merged = columns.value
        .filter(({ dataIndex }) => selectedColumns.value.includes(dataIndex))
        .map(({ dataIndex, title, dbType }) => ({
          dataIndex,
          title: showCommentValue ? `${title}(${dataIndex})` : dataIndex,
          dbType,
          key: dataIndex,
          filteredValue: !editingValue ? filtersValue[dataIndex] : [],
          filters: !editingValue ? filterItemsValue[dataIndex] : false,
          sorter: !editingValue,
          resizable: true,
          width: widthsValue[dataIndex] || 200,
          sortOrder: sortsValue[dataIndex] || false,
          ellipsis: true,
          editable: editingValue ? 'cellEditorSlot' : false,
          valueChange,
        }));
      if (showNumber.value) {
        merged.unshift({
          title: '#',
          key: '#',
          width: 60,
          resizable: true,
          align: 'center',
          customRender: ({ text, record, index }) => getIndex(index),
        })
      }
      return merged;
    })
    // 列选项
    const columnOptions = computed(() => {
      return columns.value.map(({ title, dataIndex }) => ({
        title,
        class: 'no-switcher',
        key: dataIndex,
        selectable: false,
        disabled: dataIndex === 'id'
      }));
    })
    // 分页
    const pagination = ref(defaultPage());
    // 查询数据
    const fetchRows = () => {
      loading.value = true;
      fetchData().finally(() => loading.value = false);
    }
    // 请求底层
    const fetchData = async () => {
      if (loadData.value) {
        const { current: page, pageSize: size } = pagination.value;
        const body = {
          search: search.value,
          page,
          size,
          filters: filters.value,
          sorts: sorts.value,
          columns: selectedColumns.value
        }
        const { current, size: pageSize, total, records = [] } = await loadData.value(body) || {};
        pagination.value = {
          ...pagination.value,
          current,
          total,
          pageSize
        };
        if (!initialized.value) {
          applyFilterItems(records);
          initialized.value = true;
        }
        data.value = records;
      }
    }
    // 获取下标
    const getIndex = index => {
      const { current, pageSize } = pagination.value;
      return (current - 1) * pageSize + index + 1;
    }
    // 赋值筛选项
    const applyFilterItems = data => {
      filterItems.value = columns.value.reduce((result, { dataIndex }) => {
        result[dataIndex] = distinctBy(data, dataIndex)
          .map(item => item[dataIndex])
          .filter(item => item)
          .map(item => ({ text: item, value: item }));
        return result;
      }, {});
    };
    // 单元格值变更
    const valueChange = (e, { column, record, oldValue, newValue, recordIndexs: recordIndexes }) => {
      const [index] = recordIndexes;
      const tempValue = temp.value;
      const key = column.key;
      const { [index]: changed = { record: { ...record }, change: {} } } = tempValue;
      const origin = changed.record[key];
      // 判断相对原数据是否修改
      if (newValue !== origin && (newValue || origin)) {
        // 更新临时存储
        changed.change[key] = newValue;
      } else {
        // 清除变更存储（如果有）
        delete changed.change[key];
      }
      if (isEmpty(changed.change)) {
        // 没有值，则删除
        delete tempValue[index];
      } else {
        // 设置临时缓存
        tempValue[index] = changed;
      }
      // 更新当前数据
      record[key] = newValue;
    }
    // 自动加载
    onMounted(fetchRows)
    // 监听列元变动，重置选中列
    watch(columns, () => selectedColumns.value = columnKeys.value)
    return {
      client,
      data: computed(() => {
        const pageSize = pagination.value.pageSize;
        if (data.value.length <= pageSize) {
          return data.value;
        }
        return data.value.slice(0, pageSize);
      }),
      loading,
      editing,
      changed: computed(() => !isEmpty(temp.value)),
      changing: computed(() => editing.value && !isEmpty(temp.value)),
      pagination,
      size,
      sizes,
      scroll,
      showSearch,
      showNumber,
      showComment,
      allColumn,
      selectedColumns,
      selectedRowKeys,
      mappedColumns,
      columnOptions,
      rowSelection,
      searchBar,
      table,
      // 方法区
      rowKey: (record, index) => getIndex(index),
      fetchRows,
      clearSelect,
      handleEdit(value) {
        if (!value) clearSelect();
        editing.value = value;
      },
      // 样式定义
      rowClassName(record, index) {
        const { [index]: changed } = temp.value;
        return changed?.create ? 'editing' : '';
      },
      // 自定义单元格
      customCell({ record, rowIndex, column }) {
        const { [rowIndex]: changed } = temp.value;
        return changed?.change?.[column.key] ? { 'class': 'editing' } : {}
      },
      // 表格改变的回调
      handleTableChange(paginationValue, filtersValue, sorter) {
        // console.log('Various parameters', pagination, filters, sorter);
        pagination.value = { ...pagination.value, ...paginationValue };
        filters.value = filtersValue;
        const { field, order } = sorter;
        // 当且仅当field字段存在，才改变排序信息 todo table组件只支持单排序
        if (field) {
          if (order) {
            // sorts.value = { ...this.sorts, [field]: order };
            sorts.value = { [field]: order };
          } else {
            // delete sorts.value[field];
            // sorts.value = { ...this.sorts };
            sorts.value = {};
          }
        }
        // 发起请求
        fetchRows();
      },
      // 大小调整
      handleSize(key) {
        size.value = key;
      },
      // 列拖动
      handleResize(w, col) {
        widths.value[col.key] = w;
      },
      // 筛选
      doSearch(text) {
        search.value = text;
        fetchRows();
      },
      // 列全选
      handleColumn(checkedKeys) {
        // 重置
        if (true === checkedKeys) {
          selectedColumns.value = columnKeys.value;
        }
        // 处理全选和反选
        else if (checkedKeys.target) {
          selectedColumns.value = checkedKeys.target.checked ? columnKeys.value : ['id'];
        }
        // 处理双向绑定
        else {
          selectedColumns.value = checkedKeys || [];
        }
        // 不为空，重新拉取数据
        if (selectedColumns.value.length) {
          fetchRows();
        }
      },
      // 添加行
      add() {
        table.value.scrollTo(0, 'smooth');
        temp.value[0] = { record: {}, change: {}, create: true }
        data.value = [{}].concat(data.value);
      },
      // 删除行
      remove() {
        emit('delete', {
          data: selectedRows.value,
          set loading(val) {
            loading.value = val;
          },
          success: result => {
            clearSelect();
            message.success(`删除成功！共删除${result}条记录！`);
            return fetchRows();
          },
          error: e => message.error(`删除失败！${e}`)
        })
      },
      // 修改
      edit() {
        emit('save', {
          data: temp.value,
          set loading(val) {
            loading.value = val;
          },
          success: result => {
            temp.value = {};
            message.success(`保存成功！共保存${result}条记录！`);
            return fetchRows();
          },
          error: e => message.error(`保存失败！${e}`)
        });
      },
      // 撤销
      revoke() {
        const tempValue = temp.value;
        const dataValue = data.value;
        // 还愿属性
        Object.keys(tempValue)
          .forEach(index => {
            const { record, create } = tempValue[index];
            // 创建会额外添加行，所以需要删除创建的行
            if (create) {
              dataValue.splice(index, 1);
            } else {
              dataValue[index] = record;
            }
          })
        data.value = [...dataValue];
        temp.value = {};
      },
      // 重置
      reset(lazy) {
        initialized.value = false;
        size.value = 'middle';
        selectedColumns.value = columnKeys.value;
        sorts.value = {};
        widths.value = {};
        filters.value = {};
        search.value = [];
        showSearch.value = false;
        showNumber.value = false;
        editing.value = false;
        temp.value = {};
        pagination.value = defaultPage();
        searchBar.value.reset();
        clearSelect();
        if (lazy !== true) {
          return fetchRows();
        }
      }
    }
  },
};
</script>

<style lang='less' scoped>
.data-table {
  position: relative;
  height: 100%;
  overflow: hidden;

  .data-table-header {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: space-between;

    .data-table-header-left {
      display: flex;
      align-items: center;
      justify-content: flex-start;

      .title {
        display: flex;
        align-items: center;
        justify-content: flex-start;
        color: rgba(0, 0, 0, .85);
        white-space: nowrap;
        font-weight: 500;
        font-size: 16px;
      }
    }

    .data-table-header-right {
      display: flex;
      justify-content: flex-end;
      flex-flow: wrap;
      .active {
        background-color: #a5ff96;
        border-radius: 20px;
        padding: 0 10px;
      }
    }
  }

  .status-bar {
    position: absolute;
    bottom: 0;
    left: 20px;
    line-height: 45px;
    a {
      font-size: 13px;
      font-weight: bold;
      margin: 0 2px;
      &.clear {
        margin-left: 10px;
      }
      &.edit {
        color: #33a204;
      }
      &.read {
        color: gray;
      }
    }
  }
}

.data-table-column-selector-title {
  display: flex;
  align-items: center;
  margin-left: 2px;
  justify-content: space-between;
  height: 32px;
}
</style>
<style lang='less'>
.no-switcher {
  .ant-tree-switcher {
    width: 0 !important;
  }
}

.data-table {
  .ant-pagination {
    border-top: 1px solid #f0f0f0;
    margin: 0 !important;
    padding: 10px;
  }
  .editing {
    background-color: #d2f4c2;
  }
}
</style>
