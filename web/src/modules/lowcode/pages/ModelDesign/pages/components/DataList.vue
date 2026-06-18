<template>
  <a-drawer
    :mask-closable="false"
    :visible="visible"
    cancel-text="关闭"
    :closable='false'
    root-class-name="data-list"
    :body-style='{padding: 0}'
    @close="close"
  >
    <data-table
      v-if='tableFields.length'
      ref="table"
      :columns="tableFields"
      :load-data="fetch"
      @close="close"
      @save="save"
      @delete="remove"
    />
  </a-drawer>
</template>

<script setup>
import DataTable from '@/components/DataTable/DataTable';
import { nextTick, ref, inject } from 'vue';
import { deleteTableData, fetchTableData, getTableDetail, saveTableData } from '@/modules/lowcode/pages/ModelDesign/apis';

const tableName = ref();
const source = inject('dataSource');
const emit = defineEmits(['close'])
const table = ref();
const visible = ref(false)
const tableFields = ref([]);

const close = () => {
  emit('close');
  table.value?.reset(true);
  visible.value = false;
}

const mapType = (() => {
  const mappings = {
    number: ['int', 'bigint', 'decimal', 'double', 'float', 'tinyint'],
    string: ['varchar', 'text', 'longtext'],
    date : ['datetime', 'timestamp'],
    boolean: ['bit']
  }
  return type => Object.keys(mappings).find(key => mappings[key].includes(type)) || 'default';
})();

const show = async record => {
  if (!record) return;
  visible.value = true;
  tableName.value = record.name;
  const detail = await getTableDetail(source.value, tableName.value);
  tableFields.value = detail.columns.map(({ name, comment, type }) => ({
    dataIndex: name,
    title: comment,
    dbType: mapType(type)
  }));
  return nextTick(() => table.value.fetchRows());
}

const fetch = (params = {}) => {
  return fetchTableData(source.value, tableName.value, params)
}

const save = async ctx => {
  const { data, success, error } = ctx;
  try {
    ctx.loading = true;
    const count = await saveTableData(source.value, tableName.value, data)
    if (count > 0) {
      success(count);
    } else {
      error('没有行被更新！')
    }
  } catch (e) {
    error(e.message);
  } finally {
    ctx.loading = false
  }
}

const remove = async ctx => {
  const { data, success, error } = ctx;
  try {
    ctx.loading = true;
    const count = await deleteTableData(source.value, tableName.value, data)
    if (count > 0) {
      success(count);
    } else {
      error('没有行被删除！')
    }
  } catch (e) {
    error(e.message);
  } finally {
    ctx.loading = false
  }
}

defineExpose({
  show
});
</script>
<style lang="less">
.list-input {
  width: calc(50% - 16px);
  margin-bottom: 10px;
}

@media screen and (max-width: 950px) {
  .data-list {
    .ant-drawer-content-wrapper {
      width: 100% !important;
    }
  }

}

@media screen and (min-width: 950px) {
  .data-list {
    .ant-drawer-content-wrapper {
      width: calc(100% - 40px) !important;
    }
  }

}

.demo-loadmore-list {
  min-height: 350px;
}
</style>
