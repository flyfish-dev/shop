<template>
  <a-spin :spinning="loading">
    <div id="workbench" class="workbench-panel">
      <div class="overview">
        <h2>开发工作台</h2>
        <a-space wrap>
          <a-tag color="blue">{{ userName }}</a-tag>
          <a-tag color="green">数据源 {{ workbench?.dataSources?.total ?? 0 }}</a-tag>
          <a-tag
            v-for="tag in extensionTags"
            :key="tag.key"
            :color="tag.color"
          >
            {{ tag.label }} {{ tag.value }}
          </a-tag>
        </a-space>
      </div>

      <div class="workbench-grid">
        <section class="recent-sources">
          <h3>最近数据源</h3>
          <a-empty v-if="!recentSources.length" description="暂无数据源" />
          <a-list v-else :data-source="recentSources" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta :title="item.name" :description="sourceAddress(item)" />
                <a-button type="link" @click="router.push('/model-design/select-data-table', { source: item.key })">
                  建模
                </a-button>
              </a-list-item>
            </template>
          </a-list>
        </section>

        <section class="quick-actions">
          <h3>快捷操作</h3>
          <a class="product-suite-link" href="https://product.example.com" target="_blank" rel="noreferrer">
            <span>
              office预览套件
              <em>HOT</em>
            </span>
          </a>
          <a-space direction="vertical">
            <a-button
              v-for="action in actions"
              :key="action.path"
              block
              :type="action.path === '/model-design' ? 'primary' : 'default'"
              @click="goAction(action)"
            >
              {{ action.name }}
            </a-button>
          </a-space>
        </section>
      </div>
    </div>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import useClientStore from '@/modules/auth/store/client.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';
import { useRouter } from '@/router/use';
import { dataSourceAddress } from '@/utils/dataSource';
import { getPortalWorkbench } from '@/modules/lowcode/api/workbench.js';

const baseSummaryKeys = new Set(['user', 'dataSources', 'actions']);
const extensionColors = ['purple', 'orange', 'cyan', 'geekblue', 'magenta'];

const router = useRouter();
const store = useClientStore();
const { capabilityByCode, loadPortalCapabilities } = usePortalCapabilities();
const loading = ref(false);
const workbench = ref(null);

const userName = computed(() => {
  const user = workbench.value?.user || store.user;
  return user?.id > 0 ? user.username : '游客模式';
});
const recentSources = computed(() => workbench.value?.dataSources?.recent || []);
const actions = computed(() => workbench.value?.actions || []);
const metricLabel = metric => metric.replace(/([A-Z])/g, ' $1').trim();
const extensionMetricItems = computed(() => {
  return Array.isArray(workbench.value?.extensionMetrics) ? workbench.value.extensionMetrics : [];
});
const extensionTags = computed(() => {
  if (extensionMetricItems.value.length > 0) {
    return extensionMetricItems.value
      .filter(item => item && Number.isFinite(Number(item.value)))
      .map((item, index) => {
        const capability = capabilityByCode.value.get(item.capability);
        const capabilityName = capability?.name || item.capability;
        return {
          key: `${item.capability}.${item.name}`,
          label: `${capabilityName} ${item.label || metricLabel(item.name || '')}`.trim(),
          value: item.value,
          color: extensionColors[index % extensionColors.length]
        };
      });
  }
  return Object.entries(workbench.value || {})
    .filter(([code, metrics]) => !baseSummaryKeys.has(code)
      && metrics
      && typeof metrics === 'object'
      && !Array.isArray(metrics))
    .flatMap(([code, metrics], summaryIndex) => {
      const capability = capabilityByCode.value.get(code);
      const capabilityName = capability?.name || code;
      return Object.entries(metrics)
        .filter(([, value]) => Number.isFinite(Number(value)))
        .map(([metric, value], metricIndex) => ({
          key: `${code}.${metric}`,
          label: `${capabilityName} ${metricLabel(metric)}`,
          value,
          color: extensionColors[(summaryIndex + metricIndex) % extensionColors.length]
        }));
    });
});

const sourceAddress = dataSourceAddress;

const loadWorkbench = async () => {
  loading.value = true;
  try {
    await loadPortalCapabilities();
    workbench.value = await getPortalWorkbench();
  } catch (e) {
    message.error(e.message || '加载首页失败');
  } finally {
    loading.value = false;
  }
};

const goAction = action => {
  if (action.status === 'later') {
    message.info('二期开放');
    return;
  }
  router.push(action.path);
};

onMounted(() => {
  loadWorkbench();
});
</script>

<style scoped lang="less">
.workbench-panel {
  width: min(960px, 100%);
  max-width: 100%;
  box-sizing: border-box;
  margin: 0 auto 40px;
  padding: 24px;
  border: 1px solid #d9eef5;
  border-radius: 8px;
  background: #fbfdff;
}

.overview {
  h2 {
    margin: 0 0 12px;
  }
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(200px, 240px);
  gap: 24px;
  margin-top: 24px;

  h3 {
    margin-bottom: 12px;
  }
}

.recent-sources,
.quick-actions {
  min-width: 0;
}

.recent-sources {
  :deep(.ant-list-item) {
    gap: 12px;
  }

  :deep(.ant-list-item-meta) {
    min-width: 0;
  }

  :deep(.ant-list-item-meta-title),
  :deep(.ant-list-item-meta-description) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.quick-actions {
  :deep(.ant-space) {
    width: 100%;
  }
}

.product-suite-link {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 48px;
  margin-bottom: 12px;
  padding: 0 14px;
  overflow: hidden;
  border: 1px solid rgba(255, 107, 53, .28);
  border-radius: 8px;
  color: #21334a;
  background:
    linear-gradient(90deg, rgba(255, 247, 237, .96), rgba(239, 255, 245, .92)),
    #fff;
  box-shadow: 0 10px 26px rgba(255, 107, 53, .08);
  font-weight: 700;
  text-decoration: none;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(110deg, transparent, rgba(255, 255, 255, .7), transparent);
    transform: translateX(-120%);
    animation: hotSweep 2.4s ease-in-out infinite;
  }

  span {
    position: relative;
    z-index: 1;
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  em {
    display: inline-flex;
    align-items: center;
    height: 18px;
    padding: 0 7px;
    border-radius: 999px;
    color: #fff;
    background: #ff4d4f;
    font-size: 11px;
    font-style: normal;
    line-height: 18px;
    box-shadow: 0 6px 14px rgba(255, 77, 79, .24);
  }
}

@keyframes hotSweep {
  0% {
    transform: translateX(-120%);
  }
  54%,
  100% {
    transform: translateX(120%);
  }
}

@media only screen and (max-width: 820px) {
  .workbench-panel {
    padding: 18px;
  }

  .workbench-grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }
}

@media only screen and (max-width: 480px) {
  .workbench-panel {
    padding: 16px;
  }

  .recent-sources {
    :deep(.ant-list-item) {
      align-items: flex-start;
      flex-direction: column;
    }

    :deep(.ant-list-item-action) {
      margin-left: 0;
    }
  }
}
</style>
