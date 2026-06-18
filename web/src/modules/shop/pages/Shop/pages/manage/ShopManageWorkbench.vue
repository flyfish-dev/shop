<script setup>
import { computed, onMounted } from 'vue';
import RouterLink from '@/components/RouterLink/index.vue';
import {
  ApiOutlined,
  AppstoreOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  GiftOutlined,
  ReloadOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import { useShopManageWorkbench } from '../../hooks/useShopManageWorkbench.js';

const {
  loading,
  overviewLoading,
  insightLoading,
  refreshing,
  error,
  summary,
  loadWorkbench
} = useShopManageWorkbench();

const formatMoney = value => `¥${Number(value || 0).toFixed(2)}`;

const statCards = computed(() => [
  {
    title: '今日成交',
    value: formatMoney(summary.value.todayRevenueAmount),
    meta: `${summary.value.todayOrderCount} 笔订单`,
    icon: DashboardOutlined,
    tone: 'green',
    loading: overviewLoading.value
  },
  {
    title: '累计成交',
    value: formatMoney(summary.value.revenueAmount),
    meta: `${summary.value.paidOrderCount} / ${summary.value.orderTotal} 单`,
    icon: ShoppingCartOutlined,
    tone: 'blue',
    loading: overviewLoading.value
  },
  {
    title: '商品运营',
    value: summary.value.enabledItemCount,
    meta: `共 ${summary.value.itemTotal} 个 · 推荐 ${summary.value.recommendedItemCount}`,
    icon: AppstoreOutlined,
    tone: 'cyan',
    loading: overviewLoading.value
  },
  {
    title: '待处理',
    value: summary.value.activeTicketCount + summary.value.waitingDeliveryCount,
    meta: `工单 ${summary.value.activeTicketCount} · 交付 ${summary.value.waitingDeliveryCount}`,
    icon: FileTextOutlined,
    tone: 'orange',
    loading: overviewLoading.value
  }
]);

const insightCards = computed(() => [
  { label: '商品分组', value: summary.value.groupCount, loading: insightLoading.value },
  { label: '置顶商品', value: summary.value.pinnedItemCount, loading: overviewLoading.value },
  { label: '优惠券', value: `${summary.value.enabledCouponCount}/${summary.value.couponCount}`, loading: insightLoading.value },
  { label: '代码仓库', value: `${summary.value.enabledRepositoryCount}/${summary.value.repositoryCount}`, loading: insightLoading.value },
  { label: 'API Token', value: `${summary.value.enabledTokenCount}/${summary.value.tokenCount}`, loading: insightLoading.value },
  { label: '注册用户', value: summary.value.userCount, loading: insightLoading.value },
  { label: '待支付订单', value: summary.value.pendingPaymentCount, loading: overviewLoading.value },
  { label: '工单总数', value: summary.value.ticketTotal, loading: overviewLoading.value }
]);

const modules = [
  {
    title: '店铺管理',
    path: '/shop/manage/shops',
    icon: ShopOutlined,
    stat: '资料'
  },
  {
    title: '分组管理',
    path: '/shop/manage/groups',
    icon: TagsOutlined,
    stat: '分类'
  },
  {
    title: '商品管理',
    path: '/shop/manage/items',
    icon: AppstoreOutlined,
    stat: '上架'
  },
  {
    title: '仓库管理',
    path: '/shop/manage/repositories',
    icon: DatabaseOutlined,
    stat: '开通'
  },
  {
    title: '订单管理',
    path: '/shop/manage/orders',
    icon: ShoppingCartOutlined,
    stat: '履约'
  },
  {
    title: '用户管理',
    path: '/shop/manage/users',
    icon: UserOutlined,
    stat: '客户'
  },
  {
    title: '优惠券管理',
    path: '/shop/manage/coupons',
    icon: GiftOutlined,
    stat: '营销'
  },
  {
    title: '合同管理',
    path: '/shop/manage/contracts',
    icon: FileDoneOutlined,
    stat: '签署'
  },
  {
    title: '工单管理',
    path: '/shop/manage/tickets',
    icon: TeamOutlined,
    stat: '服务'
  }
];

onMounted(loadWorkbench);
</script>

<template>
  <div class="shop-manage-workbench">
    <section class="workbench-head">
      <div class="head-title">
        <span class="head-kicker">小铺工作台</span>
        <h1>{{ summary.shopName }}</h1>
      </div>
      <div class="head-actions">
        <router-link href="/shop/item-list">
          <a-button>商品列表</a-button>
        </router-link>
        <a-button type="primary" :loading="loading || refreshing" @click="loadWorkbench">
          <template #icon><reload-outlined /></template>
          刷新
        </a-button>
      </div>
    </section>

    <a-alert
      v-if="error"
      class="workbench-alert"
      type="warning"
      show-icon
      :message="error"
      closable
    />

    <section class="stats-grid">
      <div
        v-for="card in statCards"
        :key="card.title"
        class="stat-card"
        :class="[`stat-card-${card.tone}`, { 'is-loading': card.loading }]"
      >
        <span class="stat-icon">
          <component :is="card.icon" />
        </span>
        <span class="stat-title">{{ card.title }}</span>
        <strong :class="{ 'loading-value': card.loading }">
          {{ card.loading ? '...' : card.value }}
        </strong>
        <span class="stat-meta">
          {{ card.loading ? '统计加载中' : card.meta }}
        </span>
      </div>
    </section>

    <section class="insight-grid">
      <div
        v-for="item in insightCards"
        :key="item.label"
        class="insight-item"
        :class="{ 'is-loading': item.loading }"
      >
        <span>{{ item.label }}</span>
        <strong :class="{ 'loading-value': item.loading }">
          {{ item.loading ? '...' : item.value }}
        </strong>
      </div>
    </section>

    <section class="module-section">
      <div class="section-title">
        <api-outlined />
        <span>管理模块</span>
      </div>
      <div class="module-grid">
        <router-link
          v-for="module in modules"
          :key="module.path"
          class="module-card"
          :href="module.path"
        >
          <span class="module-icon">
            <component :is="module.icon" />
          </span>
          <span class="module-main">
            <strong>{{ module.title }}</strong>
            <em>{{ module.stat }}</em>
          </span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped lang="less">
.shop-manage-workbench {
  min-width: 0;
  padding: 20px;
}

.workbench-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.head-title {
  min-width: 0;

  h1 {
    margin: 4px 0 0;
    overflow: hidden;
    color: #183725;
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
    letter-spacing: 0;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.head-kicker {
  color: #4f7c62;
  font-size: 13px;
  font-weight: 600;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.workbench-alert {
  margin-bottom: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.stat-card {
  position: relative;
  display: flex;
  min-height: 138px;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
  padding: 18px;
  overflow: hidden;
  border: 1px solid rgba(35, 86, 60, .08);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 30px rgba(24, 55, 37, .06);

  &::after {
    content: '';
    position: absolute;
    inset: auto -28px -42px auto;
    width: 120px;
    height: 120px;
    border-radius: 50%;
    opacity: .12;
    background: currentColor;
  }

  strong {
    overflow: hidden;
    color: #14291e;
    font-size: 30px;
    font-weight: 760;
    line-height: 1.05;
    letter-spacing: 0;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.is-loading {
  cursor: progress;
}

.loading-value {
  color: transparent !important;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(33, 150, 83, .12), rgba(33, 150, 83, .22), rgba(33, 150, 83, .12));
  background-size: 200% 100%;
  animation: workbench-loading 1.1s ease-in-out infinite;
}

.stat-card-green {
  color: #219653;
}

.stat-card-blue {
  color: #1677ff;
}

.stat-card-cyan {
  color: #08979c;
}

.stat-card-orange {
  color: #d46b08;
}

.stat-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: color-mix(in srgb, currentColor 12%, white);
  font-size: 20px;
}

.stat-title {
  color: #607466;
  font-size: 13px;
  font-weight: 600;
}

.stat-meta {
  color: #809086;
  font-size: 13px;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 24px;
}

.insight-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  padding: 14px;
  border: 1px solid rgba(35, 86, 60, .08);
  border-radius: 8px;
  background: rgba(255, 255, 255, .76);

  span {
    overflow: hidden;
    color: #728176;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    overflow: hidden;
    color: #21382a;
    font-size: 18px;
    line-height: 1.1;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.module-section {
  padding-top: 2px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #1c3827;
  font-size: 18px;
  font-weight: 700;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.module-card {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
  padding: 18px;
  border: 1px solid rgba(35, 86, 60, .08);
  border-radius: 8px;
  color: inherit;
  background: #fff;
  box-shadow: 0 12px 28px rgba(24, 55, 37, .055);
  text-decoration: none;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;

  &:hover {
    border-color: rgba(51, 162, 4, .36);
    box-shadow: 0 16px 34px rgba(24, 55, 37, .09);
    transform: translateY(-2px);
  }
}

.module-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 8px;
  color: #267d4a;
  background: #eff8f1;
  font-size: 21px;
}

.module-main {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  strong {
    overflow: hidden;
    color: #183725;
    font-size: 16px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    flex: none;
    padding: 2px 8px;
    border-radius: 999px;
    color: #2b7d4c;
    background: #f0f8f2;
    font-size: 12px;
    font-style: normal;
  }
}

@keyframes workbench-loading {
  0% {
    background-position: 100% 0;
  }

  100% {
    background-position: -100% 0;
  }
}

@media only screen and (max-width: 1180px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insight-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .module-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media only screen and (max-width: 640px) {
  .shop-manage-workbench {
    padding: 0;
  }

  .workbench-head {
    align-items: stretch;
    flex-direction: column;
  }

  .head-title h1 {
    font-size: 24px;
    white-space: normal;
  }

  .head-actions {
    justify-content: flex-start;
  }

  .stats-grid,
  .insight-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }

  .stat-card {
    min-height: 126px;
  }
}
</style>
