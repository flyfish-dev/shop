<script setup>
import { computed } from 'vue';
import { CheckCircleOutlined, FileDoneOutlined } from '@ant-design/icons-vue';

const props = defineProps({
  agreement: {
    type: Object,
    required: true
  }
});

const open = computed({
  get: () => props.agreement.visible.value,
  set: value => {
    if (!value) {
      props.agreement.cancelAgreement();
    }
  }
});

const activeKey = computed({
  get: () => props.agreement.activeKey.value,
  set: value => {
    props.agreement.activeKey.value = value;
  }
});

const progressPercent = computed(() => {
  if (!props.agreement.totalCount.value) {
    return 0;
  }
  return Math.round((props.agreement.agreedCount.value / props.agreement.totalCount.value) * 100);
});

const shortName = name => {
  if (!name) {
    return '合同文件';
  }
  return name.length > 18 ? `${name.slice(0, 18)}...` : name;
};
</script>

<template>
  <a-modal
    v-model:open="open"
    class="contract-agreement-modal"
    wrap-class-name="contract-agreement-modal-wrap"
    width="100vw"
    :style="{ top: 0, paddingBottom: 0 }"
    :body-style="{ padding: 0, overflow: 'hidden' }"
    :maskClosable="false"
    destroy-on-close
    title="合同确认"
  >
    <template #footer>
      <div class="contract-action-bar">
        <div class="read-progress">
          <span>阅读进度 {{ agreement.readPercent.value }}%</span>
          <a-tag v-if="agreement.readToEnd.value" color="green">已到底部</a-tag>
          <a-tag v-else color="orange">需阅读到底部</a-tag>
        </div>
        <a-checkbox
          v-model:checked="agreement.agreedCurrent.value"
          :disabled="agreement.loading.value || !agreement.readToEnd.value"
        >
          我已阅读并同意
        </a-checkbox>
        <div class="contract-action-buttons">
          <a-button @click="agreement.cancelAgreement">
            取消
          </a-button>
          <a-button
            type="primary"
            :loading="agreement.signing.value"
            :disabled="agreement.loading.value || !agreement.readToEnd.value || !agreement.agreedCurrent.value"
            @click="agreement.agreeCurrentFile"
          >
            {{ agreement.activeIndex.value + 1 >= agreement.totalCount.value ? '完成并继续付款' : '同意并阅读下一份' }}
          </a-button>
        </div>
      </div>
    </template>
    <a-spin :spinning="agreement.loading.value">
      <div class="contract-shell">
        <div class="contract-progress">
          <div>
            <strong>{{ agreement.currentFile.value?.contractName || '合同文件' }}</strong>
            <span>{{ agreement.agreedCount.value }}/{{ agreement.totalCount.value }}</span>
          </div>
          <a-progress :percent="progressPercent" size="small" :show-info="false" />
        </div>

        <a-tabs v-model:activeKey="activeKey" size="small" class="contract-tabs">
          <a-tab-pane
            v-for="(file, index) in agreement.files.value"
            :key="String(index)"
          >
            <template #tab>
              <span class="contract-tab">
                <check-circle-outlined v-if="agreement.agreedFileIds?.value?.has(file.id)" />
                <file-done-outlined v-else />
                {{ shortName(file.fileName) }}
              </span>
            </template>
          </a-tab-pane>
        </a-tabs>

        <div
          class="contract-preview-scroll"
        >
          <iframe
            v-if="agreement.currentFile.value"
            :src="agreement.viewerUrl(agreement.currentFile.value)"
            title="合同预览"
            loading="lazy"
            @load="agreement.handleViewerFrameLoad"
          />
        </div>
      </div>
    </a-spin>
  </a-modal>
</template>

<style lang="less">
.contract-agreement-modal-wrap {
  overflow: hidden;

  .ant-modal {
    top: 0 !important;
    max-width: 100vw;
    height: 100vh;
    margin: 0;
    padding-bottom: 0;
  }

  .ant-modal-content {
    display: flex;
    height: 100vh;
    overflow: hidden;
    flex-direction: column;
    border-radius: 0;
  }

  .ant-modal-header {
    flex: 0 0 auto;
    padding: 14px 20px;
    border-bottom: 1px solid #edf2f7;
  }

  .ant-modal-close {
    top: 10px;
  }

  .ant-modal-body {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  .ant-modal-footer {
    flex: 0 0 auto;
    margin-top: 0;
    padding: 0;
    border-top: 0;
  }

  .ant-spin-nested-loading,
  .ant-spin-container {
    height: 100%;
    min-height: 0;
  }
}
</style>

<style scoped lang="less">
.contract-shell {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  background: #fff;
}

.contract-progress {
  display: grid;
  gap: 8px;
  padding: 12px 20px 10px;
  border-bottom: 1px solid #f0f4f7;

  > div {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    color: #203626;

    strong {
      min-width: 0;
      overflow: hidden;
      font-size: 16px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    span {
      color: #7a8b80;
      font-size: 13px;
      white-space: nowrap;
    }
  }
}

.contract-tabs {
  min-height: 0;
  padding: 0 20px;
  border-bottom: 1px solid #edf2f7;

  :deep(.ant-tabs-nav) {
    margin: 0;
  }

  :deep(.ant-tabs-content-holder) {
    display: none;
  }
}

.contract-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 180px;
}

.contract-preview-scroll {
  min-height: 0;
  overflow: hidden;
  background: #eef3f7;

  iframe {
    display: block;
    width: 100%;
    height: 100%;
    border: 0;
    background: #eef5f1;
  }
}

.contract-action-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 20px max(12px, env(safe-area-inset-bottom));
  border-top: 1px solid #edf2f7;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 -8px 24px rgba(31, 54, 38, .06);
}

.read-progress {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #607268;
  font-size: 13px;
}

.contract-action-buttons {
  display: inline-flex;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;
}

@media only screen and (max-width: 720px) {
  .contract-action-bar {
    grid-template-columns: 1fr;
    align-items: stretch;
    padding: 10px 12px;
  }

  .contract-action-buttons {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  }

  .contract-progress {
    padding: 10px 12px;
  }

  .contract-tabs {
    padding: 0 12px;
  }
}
</style>
