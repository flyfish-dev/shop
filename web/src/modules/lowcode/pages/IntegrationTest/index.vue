<template>
  <a-card class="integration-page">
    <template #title>
      <navigator-bar>集成测试</navigator-bar>
    </template>

    <div class="test-toolbar">
      <a-select
        v-model:value="source"
        class="source-select"
        :options="dataSourceOptions"
        allow-clear
      />
      <a-button @click="addCase">
        <template #icon><plus-outlined /></template>
        添加用例
      </a-button>
      <a-button type="primary" :loading="running" @click="run">
        <template #icon><play-circle-outlined /></template>
        运行测试
      </a-button>
    </div>

    <div class="test-layout">
      <section class="case-list">
        <div v-for="(item, index) in cases" :key="item.id" class="case-card">
          <div class="case-card-head">
            <a-input v-model:value="item.name" class="case-name" />
            <a-button type="text" danger @click="removeCase(index)">
              <template #icon><delete-outlined /></template>
            </a-button>
          </div>
          <a-textarea
            v-model:value="item.sql"
            class="sql-editor"
            :rows="5"
            spellcheck="false"
          />
          <div class="assertion-row">
            <a-select
              v-model:value="item.assertionType"
              class="assertion-select"
              :options="assertionOptions"
            />
            <a-input
              v-if="needExpected(item.assertionType)"
              v-model:value="item.expectedValue"
              class="expected-input"
            />
          </div>
        </div>
      </section>

      <section class="run-result">
        <div class="summary-row">
          <a-statistic title="总数" :value="summary.total" />
          <a-statistic title="通过" :value="summary.passed" />
          <a-statistic title="失败" :value="summary.failed" />
        </div>
        <a-table
          size="small"
          :columns="resultColumns"
          :data-source="resultRows"
          :pagination="{ pageSize: 8 }"
          row-key="name"
        >
          <template #bodyCell="{ column, record }">
            <a-tag
              v-if="column.key === 'passed'"
              :color="record.passed ? 'green' : 'red'"
            >
              {{ record.passed ? '通过' : '失败' }}
            </a-tag>
          </template>
        </a-table>
      </section>
    </div>
  </a-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { message as $message } from 'ant-design-vue';
import { DeleteOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons-vue';
import useStore from '@/modules/lowcode/store/datasource.js';
import { runIntegrationTests } from './apis';
import { dataSourceOptionLabel } from '@/utils/dataSource';

const store = useStore();
const source = ref();
const running = ref(false);
const cases = ref([
  createCase('连通性检查', 'select 1 as result', 'FIRST_CELL_EQUALS', '1'),
]);
const runResult = ref(null);

const assertionOptions = [
  { value: 'NO_ERROR', label: '无异常' },
  { value: 'HAS_ROWS', label: '存在结果' },
  { value: 'EMPTY', label: '结果为空' },
  { value: 'ROW_COUNT_EQUALS', label: '行数等于' },
  { value: 'FIRST_CELL_EQUALS', label: '首列等于' },
];
const resultColumns = [
  { title: '用例', dataIndex: 'name', key: 'name' },
  { title: '断言', dataIndex: 'assertionType', key: 'assertionType' },
  { title: '结果', dataIndex: 'passed', key: 'passed' },
  { title: '耗时', dataIndex: 'durationMs', key: 'durationMs' },
  { title: '信息', dataIndex: 'message', key: 'message', ellipsis: true },
];

const dataSourceOptions = computed(() => store.dataSources.map(item => ({
  value: item.key,
  label: dataSourceOptionLabel(item),
})));
const summary = computed(() => runResult.value || { total: 0, passed: 0, failed: 0 });
const resultRows = computed(() => runResult.value?.cases || []);

onMounted(async () => {
  await store.loadDataSources();
  source.value = store.dataSources[0]?.key;
});

function createCase(name, sql, assertionType = 'NO_ERROR', expectedValue = '') {
  return {
    id: `${Date.now()}-${Math.random()}`,
    name,
    sql,
    assertionType,
    expectedValue,
  };
}

function needExpected(type) {
  return ['ROW_COUNT_EQUALS', 'FIRST_CELL_EQUALS'].includes(type);
}

function addCase() {
  cases.value.push(createCase('新建用例', 'select 1'));
}

function removeCase(index) {
  if (cases.value.length === 1) {
    $message.warning('至少保留一个用例');
    return;
  }
  cases.value.splice(index, 1);
}

async function run() {
  if (!source.value) {
    $message.warning('请选择数据源');
    return;
  }
  running.value = true;
  try {
    runResult.value = await runIntegrationTests(source.value, cases.value.map(item => ({
      name: item.name,
      sql: item.sql,
      assertionType: item.assertionType,
      expectedValue: item.expectedValue,
    })));
  } catch (e) {
    $message.error(e.message);
  } finally {
    running.value = false;
  }
}
</script>

<style scoped lang="less">
.integration-page {
  :deep(.ant-card-body) {
    max-height: calc(100vh - 160px);
    overflow: auto;
  }
}

.test-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.source-select {
  flex: 1;
  min-width: 0;
}

.test-layout {
  display: grid;
  grid-template-columns: minmax(320px, 460px) minmax(0, 1fr);
  gap: 20px;
}

.case-list,
.run-result {
  min-width: 0;
}

.case-card {
  padding: 14px 0;
  border-bottom: 1px solid #eef2f7;
}

.case-card:first-child {
  padding-top: 0;
}

.case-card-head,
.assertion-row,
.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.case-card-head {
  margin-bottom: 10px;
}

.case-name {
  flex: 1;
}

.sql-editor {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  margin-bottom: 10px;
}

.assertion-select {
  width: 160px;
}

.expected-input {
  flex: 1;
}

.summary-row {
  justify-content: space-around;
  margin-bottom: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #eef2f7;
}

@media (max-width: 900px) {
  .test-toolbar,
  .test-layout {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
