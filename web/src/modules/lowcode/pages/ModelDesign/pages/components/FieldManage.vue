<template>
  <div class="field-manage">
    <a-affix>
      <h3 class="field-opt-group">
        <a-space :size="20">
          <a class="field-opt" @click="create"><plus-outlined/> 新建字段</a>
          <a v-if="rowSelection.selectedRowKeys.length" class="field-opt" @click="insert"><insert-row-above-outlined/> 插入字段</a>
          <a-dropdown>
            <a class="field-opt"><robot-filled /> 快速生成字段 <down-outlined/></a>
            <template #overlay>
              <a-menu @click="generate">
                <a-menu-item key="standard">
                  <a>标准带时间实体(id, name, *_time, *_by)</a>
                </a-menu-item>
                <a-menu-item key="full">
                  <a>逻辑删除实体(id, name, *_time, *_by, is_delete)</a>
                </a-menu-item>
                <a-menu-item key="minimal">
                  <a>最小化实体(id, name)</a>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a class="field-opt color-red" v-if="rowSelection.selectedRowKeys.length"
             @click="removeAll"><delete-filled/> 删除选中字段</a>
        </a-space>
      </h3>
    </a-affix>
    <a-table size="small" class="fields-table" :row-selection="rowSelection" bordered :data-source="model?.fields"
             :columns="columns" :custom-row="customRow" :pagination="false" row-key="_id">
      <template #bodyCell="{ column, text, record, index }">
        <a-space v-if="column.component === 'a-checkbox'">
          <a-checkbox :checked="record[column.dataIndex]" @update:checked="e => check(column.dataIndex, record, e)"
                      :disabled="disabled(column.dataIndex, record)" />
          <template v-if="column.dataIndex === 'primary'">
            <a-checkbox v-model:checked="record.autoIncrement" :disabled="!record.type?.includes('int')" />
          </template>
        </a-space>
        <template v-else-if="column.component === 'number'">
          <a-input-number v-model:value="record[column.dataIndex]" :disabled="lengthDisable(record, column.dataIndex)" />
        </template>
        <template v-else-if="column.dataIndex === 'type'">
          <a-select class="full-input" :disabled="!typeNames.includes(record.type)"
                    :value="record.type" @update:value="e => changeType(record, e)" :options="types" />
        </template>
        <template v-else-if="column.dataIndex === 'operation'">
          <a @click="properties = record"><setting-filled /></a>
          <a-popconfirm ok-text="删除" cancel-text="取消" title="确定删除吗？" @confirm="remove(index)">
            <a style="margin-left: 8px" class="color-red"><delete-filled/></a>
          </a-popconfirm>
        </template>
        <template v-else>
          <a-input :id="`input${record._id}`" v-model:value="record[column.dataIndex]" :default-value="text" />
        </template>
      </template>
    </a-table>
    <a-drawer v-model:open="visible" title="字段属性设置">
      <a-form :model="properties" v-if="properties">
        <a-form-item label="默认值" name="defaultValue">
          <a-input v-model:value="properties.defaultValue" />
        </a-form-item>
        <a-form-item v-if="isNumeric(properties.type)" label="无符号" name="unsigned">
          <a-checkbox v-model:checked="properties.unsigned" />
        </a-form-item>
      </a-form>
    </a-drawer>
    <debug-info :data="model?.fields" />
  </div>
</template>

<script>
import { PlusOutlined, DeleteFilled, SettingFilled, RobotFilled, DownOutlined, InsertRowAboveOutlined } from '@ant-design/icons-vue';
import { computed, nextTick, ref, toRefs } from 'vue';
import draggable from '@/mixins/draggable';
import { Modal } from 'ant-design-vue';
import templates from '../template/fields';
import DebugInfo from '@/components/DebugInfo';

const columns = [
  {
    title: '字段名',
    dataIndex: 'name',
  },
  {
    title: '类型',
    dataIndex: 'type',
  },
  {
    title: '长度',
    dataIndex: 'length',
    component: 'number',
    width: 70,
  },
  {
    title: '精度',
    dataIndex: 'precision',
    component: 'number',
    width: 70,
  },
  {
    title: '主键/自增',
    dataIndex: 'primary',
    component: 'a-checkbox',
    width: 79,
    align: 'center'
  },
  {
    title: '允许空',
    dataIndex: 'nullable',
    component: 'a-checkbox',
    width: 75,
    align: 'center'
  },
  {
    title: '字段备注',
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
    label: '字符串',
    value: 'varchar',
    javaType: 'String',
    length: 50
  },
  {
    label: '整型',
    value: 'int',
    javaType: 'Integer',
    length: 10
  },
  {
    label: '长整型',
    value: 'bigint',
    javaType: 'Long',
    length: 19
  },
  {
    label: '定点型',
    value: 'decimal',
    javaType: 'BigDecimal',
    length: [10, 2],
  },
  {
    label: '浮点型',
    value: 'double',
    javaType: 'Double',
    length: [10, 2],
  },
  {
    label: '枚举型',
    value: 'tinyint',
    javaType: 'Integer',
    length: 1,
  },
  {
    label: '日期时间',
    value: 'datetime',
    javaType: 'Date',
    length: 6,
  },
  {
    label: '长文本',
    value: 'text',
    javaType: 'String',
    length: 10000,
  },
  {
    label: '中长文本',
    value: 'mediumtext',
    javaType: 'String',
    length: 65535,
  },
  {
    label: '布尔型',
    value: 'bit',
    javaType: 'Boolean',
    length: 1,
  }
]

export default {
  name: 'FieldManage',
  components: {
    DebugInfo,
    PlusOutlined,
    DeleteFilled,
    SettingFilled,
    RobotFilled,
    DownOutlined,
    InsertRowAboveOutlined,
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
    const properties = ref(null);
    const rowSelection = ref({
      getCheckboxProps: record => ({ name: record.name }),
      selectedRowKeys: [],
      onChange: keys => rowSelection.value.selectedRowKeys = keys,
    });
    const typeGroups = {
      double: ['decimal', 'double'],
      single: ['varchar']
    }
    const customRow = record => {
      return {
        onClick: e => {
          if (e.target.tagName === 'TD') {
            const { selectedRowKeys } = rowSelection.value;
            if (selectedRowKeys.includes(record._id)) {
              rowSelection.value.selectedRowKeys = selectedRowKeys.filter(key => key !== record._id);
            } else {
              rowSelection.value.selectedRowKeys.push(record._id);
            }
          }
        },
      };
    };
    return {
      columns,
      customRow,
      types,
      typeGroups,
      selector: '.fields-table .ant-table-tbody',
      draggedList: computed(() => model.value.fields),
      properties,
      visible: computed({
        get: () => !!properties.value,
        set: e => {
          if (!e) {
            properties.value = null;
          }
        }
      }),
      typeNames: computed(() => types.map(type => type.value)),
      rowSelection,
      lengths: types.reduce((res, item) => {
        if (item.length) {
          res[item.value] = item.length;
        }
        return res;
      }, {}),
      generate: ({ key }) => {
        if (model.value.fields.length) {
          Modal.confirm({
            title: '生成字段',
            content: '清空当前字段并生成默认字段？',
            okText: '清除并生成',
            cancelText: '取消',
            onOk: close => {
              model.value.fields = templates[key]();
              close();
            },
          })
        } else {
          model.value.fields = templates[key]();
        }
      },
      create: async () => {
        const { fields } = model.value;
        const id = Date.now();
        fields.push({ _id: id, type: 'varchar', length: 20 });
        await nextTick()
        const input = document.getElementById(`input${id}`)
        input.scrollIntoView({ behavior: 'smooth' })
        setTimeout(() => input.focus(), 500)
      },
      insert: () => {
        const keys = rowSelection.value.selectedRowKeys;
        const fields = model.value.fields.map(item => item._id);
        // 按照找到最大下标
        const max = Math.max(...keys.map(key => fields.indexOf(key)));
        // 插入到这个位置，后面的往后排
        model.value.fields.splice(max, 0, { _id: Date.now(), type: 'varchar', length: 20 })
      },
      remove: index => {
        const { fields } = model.value;
        fields.splice(index, 1);
      },
      removeAll: () => {
        Modal.confirm({
          title: '确定要删除选中字段吗？',
          content: '您的操作不可撤销，请谨慎操作',
          okText: '全部删除',
          okType: 'danger',
          cancelText: '再想想',
          onOk: () => {
            const keys = rowSelection.value.selectedRowKeys;
            if (keys.length) {
              model.value.fields = model.value.fields.filter(field => !keys.includes(field._id));
              rowSelection.value.selectedRowKeys = [];
            }
          }
        })
      },
      // 判断禁用情况
      disabled: (key, record) => {
        return key === 'nullable' && record.primary;
      },
      // 改变类型，需要顺便清空精度数据
      changeType: (record, value) => {
        record.type = value;
        const found = types.find(item => item.value === value);
        const { length } = found || {};
        if (Array.isArray(length)) {
          [record.length, record.precision] = length;
        } else if (length) {
          record.length = length
          record.precision = 0;
        } else {
          record.length = 0;
          record.precision = 0;
        }
      },
      check: (key, record, checked) => {
        record[key] = checked;
        // 主键，必须不为空
        if (key === 'primary' && checked) {
          record.nullable = false;
        }
      },
      isNumeric: type => {
        return ['int', 'bigint', 'decimal', 'double', 'tinyint'].includes(type);
      },
      // 判断禁用状态
      lengthDisable: (record, key) => {
        if (key === 'length') {
          return !typeGroups.single.includes(record.type) && !typeGroups.double.includes(record.type)
        }
        if (key === 'precision') {
          return !typeGroups.double.includes(record.type);
        }
        return false;
      },
    };
  }
}
</script>

<style lang="less">
.full-input.ant-select {
  width: 100% !important;
}
</style>

<style scoped lang="less">
.field-manage {
  :deep(.ant-affix) {
    h3.field-opt-group {
      background-color: white;
      border-bottom: 1px solid #e1e1e1;
      line-height: 50px;
    }
  }
  .fields-table {
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
}
</style>
