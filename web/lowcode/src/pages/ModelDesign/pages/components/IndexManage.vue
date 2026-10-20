<template>
  <div class="field-manage">
    <h3 class="field-opt-group">
      <a-space :size="20">
        <a class="field-opt" @click="create"><plus-outlined/> 新建索引</a>
        <a class="field-opt color-red" v-if="rowSelection.selectedRowKeys.length"
           @click="removeAll"><plus-outlined/>删除选中索引</a>
      </a-space>
    </h3>
    <a-table size="small" class="indexes-table" :row-selection="rowSelection" bordered :data-source="model?.indexes"
             :columns="columns" :pagination="false" row-key="_id">
      <template #bodyCell="{ column, text, record, index }">
        <template v-if="column.dataIndex === 'fields'">
          <a-select class="full-input" mode="multiple" v-model:value="record[column.dataIndex]" :options="fields" />
        </template>
        <template v-else-if="column.dataIndex === 'type'">
          <a-select class="full-input" :disabled="!typeNames.includes(record.type)"
                    :value="record.type" @update:value="e => changeType(record, e)" :options="types" />
        </template>
        <template v-else-if="column.dataIndex === 'operation'">
          <a-popconfirm ok-text="删除" cancel-text="取消" title="确定删除吗？" @confirm="remove(index)">
            <a style="margin-left: 8px" class="color-red"><delete-filled/></a>
          </a-popconfirm>
        </template>
        <template v-else>
          <a-input v-model:value="record[column.dataIndex]" :default-value="text" />
        </template>
      </template>
    </a-table>
    <debug-info :data="model?.indexes" />
  </div>
</template>

<script>
import { PlusOutlined, DeleteFilled } from '@ant-design/icons-vue';
import { computed, ref, toRefs } from 'vue';
import draggable from '@/mixins/draggable';
import { Modal } from 'ant-design-vue';
import DebugInfo from '@/components/DebugInfo';

const columns = [
  {
    title: '索引名',
    dataIndex: 'name',
  },
  {
    title: '索引字段',
    dataIndex: 'fields',
  },
  {
    title: '索引类型',
    dataIndex: 'type',
  },
  {
    title: '索引备注',
    dataIndex: 'comment'
  },
  {
    title: '操作',
    dataIndex: 'operation',
    align: 'center',
  }
];

const types = [
  {
    label: '默认索引',
    value: '',
  },
  {
    label: 'BTree索引',
    value: 'BTREE',
  },
  {
    label: '全文索引',
    value: 'FULLTEXT',
  },
  {
    label: '唯一索引',
    value: 'UNIQUE',
  },
]

export default {
  name: 'IndexManage',
  components: {
    DebugInfo,
    PlusOutlined,
    DeleteFilled,
  },
  mixins: [ draggable ],
  props: {
    model: {
      type: Object,
      description: '模型数据'
    }
  },
  setup(props) {
    const { model } = toRefs(props)
    const rowSelection = ref({
      selectedRowKeys: [],
      onChange: keys => rowSelection.value.selectedRowKeys = keys,
    });
    return {
      columns,
      types,
      selector: '.indexes-table .ant-table-tbody',
      draggedList: computed(() => model.value.indexes),
      typeNames: computed(() => types.map(type => type.value)),
      fields: computed(() => model.value.fields.map(field => ({ value: field.name, label: field.name }))),
      rowSelection,
      lengths: types.reduce((res, item) => {
        if (item.length) {
          res[item.value] = item.length;
        }
        return res;
      }, {}),
      create: () => {
        const { indexes } = model.value;
        indexes.push({ _id: Date.now(), type: 'BTREE' });
      },
      remove: index => {
        const { indexes } = model.value;
        indexes.splice(index, 1);
      },
      removeAll: () => {
        Modal.confirm({
          title: '确定要删除选中索引吗？',
          content: '您的操作不可撤销，请谨慎操作',
          okText: '全部删除',
          okType: 'danger',
          cancelText: '再想想',
          onOk: () => {
            const keys = rowSelection.value.selectedRowKeys;
            if (keys.length) {
              model.value.indexes = model.value.indexes.filter(field => !keys.includes(field._id));
              rowSelection.value.selectedRowKeys = [];
            }
          }
        })
      },
      // 改变类型，需要顺便清空精度数据
      changeType: (record, value) => {
        record.type = value;
        const found = types.find(item => item.value === value);
        const { length } = found || {};
        if (Array.isArray(length)) {
          [ record.length, record.precision ] = length;
        } else if (length) {
          record.length = length
          record.precision = 0;
        } else {
          record.length = 0;
          record.precision = 0;
        }
      },
    }
  }
}
</script>

<style lang="less">
.full-input.ant-select {
  width: 100% !important;
}
</style>

<style scoped lang="less">
.indexes-table {
  :deep(.ant-table-row) {
    cursor: move;
    .ant-input {
      font-size: 13px;
    }
  }
  label {
    font-size: 12px;
    .precision-input {
      margin-left: 2px;
      max-width: 55px;
    }
    .length-input {
      margin-left: 2px;
      max-width: 90px;
    }
  }
}
</style>
