<script setup>
import { computed, inject, ref } from 'vue';
import { ConsoleSqlOutlined, PlayCircleOutlined, LineChartOutlined } from '@ant-design/icons-vue'
import { executeSql } from '@/modules/lowcode/pages/ModelDesign/apis';
import { computeWidth } from '@/utils/utils';
import TableCellRender from '@/components/DataTable/components/TableCellRender';

const emit = defineEmits(['update:visible']);
const props = defineProps({
  visible: Boolean
});

const source = inject('dataSource')
const sql = ref('');
const loading = ref(false);
const error = ref(false);
const message = ref('');
const result = ref({});

const close = () => {
  emit('update:visible', false);
};

const alertType = computed(() => {
  return error.value ? 'error' : 'success';
});

const maxWidth = col => {
  const { rows = [] } = result.value;
  const truncated = rows.slice(0, 20);
  const lengths = truncated.map(row => row[col] ?? '').concat(col).map(computeWidth);
  return lengths.length ? (Math.max(...lengths)) + 16 : 70;
}

const columns = computed(() => {
  const { columns: cols = [] } = result.value;
  return cols.map(name => {
    const width = maxWidth(name);
    return {
      dataIndex: name,
      title: name,
      width: width > 500 ? 500 : width < 70 ? 70 : width
    }
  });
});

const execute = async () => {
  loading.value = true;
  try {
    result.value = await executeSql(source.value, sql.value)
    error.value = false;
    message.value = `请求成功，返回 ${result.value?.total ?? result.value?.rows?.length ?? 0} 行`;
  } catch (e) {
    error.value = true;
    message.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <a-drawer :open='visible' @update:open='close' width='1000px'>
    <template #title>
      <console-sql-outlined /> 运行SQL查询
    </template>
    <template #extra>
      <a-space>
        <a-button type='primary' :disabled='!sql' @click='execute' :loading='loading'>
          <play-circle-outlined/>运行当前SQL
        </a-button>
        <a-button><line-chart-outlined />执行分析</a-button>
      </a-space>
    </template>
    <a-alert v-if='message' :message="message" style='margin-bottom: 20px' :type="alertType" show-icon />
    <a-textarea v-model:value='sql' class='sql-input' />
    <template v-if='result.columns?.length'>
      <a-divider>查询结果</a-divider>
      <a-table size='small' :scroll="{ y: 400 }" :columns='columns' :data-source='result.rows'>
        <template #bodyCell='props'>
          <table-cell-render v-bind='props' />
        </template>
      </a-table>
    </template>
  </a-drawer>
</template>

<style scoped lang='less'>
.sql-input {
  height: 300px;
}
</style>
