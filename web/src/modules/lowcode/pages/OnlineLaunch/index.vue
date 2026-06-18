<template>
  <a-card class="online-page">
    <template #title>
      <navigator-bar>在线运行</navigator-bar>
    </template>

    <div class="online-layout">
      <section class="control-panel">
        <a-form layout="vertical">
          <a-form-item label="数据源">
            <a-select
              v-model:value="source"
              :options="dataSourceOptions"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="SQL">
            <a-textarea
              v-model:value="sql"
              :rows="12"
              class="sql-editor"
              spellcheck="false"
            />
          </a-form-item>
          <div class="run-bar">
            <a-checkbox v-model:checked="allowMutation">允许变更</a-checkbox>
            <a-button type="primary" :loading="running" @click="run">
              <template #icon><play-circle-outlined /></template>
              运行
            </a-button>
          </div>
        </a-form>
      </section>

      <section class="result-panel">
        <div class="result-head">
          <a-space>
            <a-tag :color="resultTag.color">{{ resultTag.text }}</a-tag>
            <span v-if="lastResult?.durationMs" class="duration">{{ lastResult.durationMs }}ms</span>
          </a-space>
        </div>
        <a-alert
          v-if="error"
          type="error"
          :message="error"
          show-icon
        />
        <a-table
          v-else-if="columns.length"
          size="small"
          :columns="columns"
          :data-source="rows"
          :pagination="{ pageSize: 12 }"
          :scroll="{ x: 'max-content' }"
          row-key="__key"
        />
        <a-empty v-else class="empty-result" />
      </section>
    </div>
  </a-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { message as $message } from 'ant-design-vue';
import { PlayCircleOutlined } from '@ant-design/icons-vue';
import useStore from '@/modules/lowcode/store/datasource.js';
import { runOnlineSql } from './apis';
import { dataSourceOptionLabel } from '@/utils/dataSource';

const store = useStore();
const source = ref();
const sql = ref('select 1 as result');
const allowMutation = ref(false);
const running = ref(false);
const lastResult = ref(null);
const error = ref('');

const dataSourceOptions = computed(() => store.dataSources.map(item => ({
  value: item.key,
  label: dataSourceOptionLabel(item),
})));

const queryResult = computed(() => lastResult.value?.result || {});
const columns = computed(() => (queryResult.value.columns || []).map(name => ({
  title: name,
  dataIndex: name,
  key: name,
  ellipsis: true,
})));
const rows = computed(() => (queryResult.value.rows || []).map((row, index) => ({
  __key: index,
  ...row,
})));
const resultTag = computed(() => {
  if (error.value) {
    return { color: 'red', text: '失败' };
  }
  if (lastResult.value) {
    return { color: 'green', text: '成功' };
  }
  return { color: 'blue', text: '待运行' };
});

onMounted(async () => {
  await store.loadDataSources();
  source.value = store.dataSources[0]?.key;
});

async function run() {
  if (!source.value) {
    $message.warning('请选择数据源');
    return;
  }
  running.value = true;
  error.value = '';
  try {
    lastResult.value = await runOnlineSql(source.value, {
      sql: sql.value,
      allowMutation: allowMutation.value,
    });
  } catch (e) {
    lastResult.value = null;
    error.value = e.message;
  } finally {
    running.value = false;
  }
}
</script>

<style scoped lang="less">
.online-page {
  :deep(.ant-card-body) {
    max-height: calc(100vh - 160px);
    overflow: auto;
  }
}

.online-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 20px;
}

.control-panel,
.result-panel {
  min-width: 0;
}

.sql-editor {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.run-bar,
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.duration {
  color: #64748b;
  font-size: 13px;
}

.empty-result {
  padding: 80px 0;
}

@media (max-width: 900px) {
  .online-layout {
    grid-template-columns: 1fr;
  }
}
</style>
