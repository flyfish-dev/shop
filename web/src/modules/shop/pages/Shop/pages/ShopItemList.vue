<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ArrowLeftOutlined, SearchOutlined } from '@ant-design/icons-vue';
import { getShopItemGroups, getShopItemPage } from '../apis/api.js';
import { useRoute, useRouter } from '@/router/use.js';
import { resolveLocalizedShopGroup } from '@/modules/shop/utils/shopGroups.js';
import ShopProductCard from '../components/ShopProductCard.vue';
import ShopSupportEntry from '../components/ShopSupportEntry.vue';

const router = useRouter();
const route = useRoute();
const { t } = useI18n();

const firstQueryValue = value => Array.isArray(value) ? value[0] : value;
const parseGroupId = value => {
  const id = Number(firstQueryValue(value));
  return Number.isFinite(id) && id > 0 ? id : undefined;
};

const groups = ref([]);
const data = ref([]);
const loading = ref(false);
const loadingMore = ref(false);
const loadError = ref('');
const listSentinel = ref(null);
const selectedGroupId = ref(parseGroupId(route.query.groupId));
const searchKeyword = ref(String(firstQueryValue(route.query.keyword) || ''));

const pagination = reactive({
  page: 0,
  size: 10,
  total: 0,
  finished: false
});

let observer;
let requestSeq = 0;
let searchTimer;

const hasMore = computed(() => !pagination.finished);
const normalizedKeyword = computed(() => searchKeyword.value.trim());
const isSearching = computed(() => normalizedKeyword.value.length > 0);
const localizedGroups = computed(() => [...groups.value]
  .filter(group => group.enabled !== false)
  .sort((left, right) => Number(left.sort || 0) - Number(right.sort || 0))
  .map(group => resolveLocalizedShopGroup(group, t)));

const syncRoute = () => router.replace('/shop/item-list', {
  groupId: selectedGroupId.value,
  keyword: normalizedKeyword.value || undefined
});

const selectGroup = id => {
  selectedGroupId.value = selectedGroupId.value === id ? undefined : id;
  syncRoute();
  loadData(true);
};

const toDetail = id => router.push(`/shop/detail/${id}`);

const loadGroups = async () => {
  groups.value = await getShopItemGroups();
};

const loadData = async reset => {
  if ((loading.value || loadingMore.value) && !reset) {
    return;
  }
  if (reset) {
    data.value = [];
    pagination.page = 0;
    pagination.total = 0;
    pagination.finished = false;
  }
  if (!reset && data.value.length && !hasMore.value) {
    return;
  }
  const currentRequest = ++requestSeq;
  const pageToLoad = reset || !data.value.length ? 0 : pagination.page + 1;
  loading.value = reset || !data.value.length;
  loadingMore.value = !loading.value;
  loadError.value = '';
  try {
    const { records, page } = await getShopItemPage({
      page: pageToLoad,
      size: pagination.size,
      groupId: selectedGroupId.value,
      keyword: normalizedKeyword.value || undefined
    });
    if (currentRequest !== requestSeq) {
      return;
    }
    const mergedItems = reset || !data.value.length ? records : [...data.value, ...records];
    data.value = Array.from(new Map(mergedItems.map(item => [item.id, item])).values());
    pagination.page = page?.page ?? pageToLoad;
    pagination.size = page?.size ?? pagination.size;
    pagination.total = page?.total ?? data.value.length;
    pagination.finished = page?.total != null
      ? data.value.length >= Number(page.total)
      : records.length < pagination.size;
  } catch (error) {
    if (currentRequest === requestSeq) {
      loadError.value = error.message || t('shop.catalog.loadFailed');
    }
  } finally {
    if (currentRequest === requestSeq) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
};

const handleSearch = () => {
  window.clearTimeout(searchTimer);
  syncRoute();
  loadData(true);
};

watch(searchKeyword, () => {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(handleSearch, 260);
});

const handleReachBottom = () => {
  const distanceToBottom = document.documentElement.scrollHeight - window.innerHeight - window.scrollY;
  if (distanceToBottom < 360 && hasMore.value && !loading.value && !loadingMore.value) {
    loadData(false);
  }
};

const setupLoadMoreObserver = async () => {
  await nextTick();
  if (!listSentinel.value) {
    return;
  }
  if ('IntersectionObserver' in window) {
    observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting) && hasMore.value && !loading.value && !loadingMore.value) {
        loadData(false);
      }
    }, {
      root: null,
      rootMargin: '360px 0px',
      threshold: 0.01
    });
    observer.observe(listSentinel.value);
  }
  window.addEventListener('scroll', handleReachBottom, { passive: true });
};

onMounted(async () => {
  await Promise.allSettled([loadGroups(), loadData()]);
  await setupLoadMoreObserver();
});

onBeforeUnmount(() => {
  observer?.disconnect();
  window.clearTimeout(searchTimer);
  window.removeEventListener('scroll', handleReachBottom);
});
</script>

<template>
  <main class='items-container'>
    <header class='catalog-heading'>
      <div class='catalog-title'>
        <router-link href='/shop' class='back-store'>
          <arrow-left-outlined />
          {{ t('shop.catalog.backHome') }}
        </router-link>
        <h1>{{ t('shop.catalog.title') }}</h1>
        <span v-if='pagination.total'>{{ t('shop.home.productCount', { count: pagination.total }) }}</span>
      </div>
    </header>

    <div class='catalog-toolbar'>
      <div class='item-group'>
        <button type='button' :class='{ selected: selectedGroupId === undefined }' @click='selectGroup()'>
          {{ t('common.all') }}
        </button>
        <button
          v-for='group in localizedGroups'
          :key='group.id'
          type='button'
          :class='{ selected: selectedGroupId === group.id }'
          @click='selectGroup(group.id)'
        >
          {{ group.name }}
        </button>
      </div>
      <a-input
        v-model:value='searchKeyword'
        class='item-search'
        :placeholder='t("shop.searchPlaceholder")'
        allow-clear
        @pressEnter='handleSearch'
      >
        <template #prefix><search-outlined /></template>
      </a-input>
    </div>

    <a-alert
      v-if='loadError'
      class='catalog-error'
      type='warning'
      show-icon
      :message='loadError'
    >
      <template #action>
        <a-button size='small' @click='loadData(true)'>{{ t('common.retry') }}</a-button>
      </template>
    </a-alert>

    <section class='item-list'>
      <a-spin :spinning='loading'>
        <a-empty
          v-if='!data.length && !loading'
          class='item-empty'
          :description='isSearching ? t("shop.noSearchResults") : t("common.noData")'
        />
        <div v-else class='item-grid'>
          <div v-for='item in data' :key='item.id' class='item-grid-cell'>
            <shop-product-card :item='item' variant='catalog' @select='toDetail' />
          </div>
        </div>
      </a-spin>
      <div ref='listSentinel' class='load-more-state'>
        <a-spin v-if='loadingMore' size='small' />
        <span v-else-if='hasMore'>{{ t('shop.loadMore') }}</span>
        <span v-else-if='data.length'>{{ t('shop.noMore') }}</span>
      </div>
    </section>

    <shop-support-entry variant='banner' class='catalog-support' />
  </main>
</template>

<style scoped lang='less'>
.items-container {
  padding: 22px 18px 24px;
}

.catalog-heading {
  display: flex;
  min-height: 74px;
  margin-bottom: 18px;
  align-items: center;
  border-bottom: 1px solid rgba(43, 137, 95, .12);
}

.catalog-title {
  display: grid;
  grid-template-columns: minmax(0, auto) auto;
  align-items: end;
  gap: 4px 12px;

  .back-store {
    grid-column: 1 / -1;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: #357b4d;
    font-size: 13px;
    text-decoration: none;
  }

  h1 {
    margin: 0;
    color: #203626;
    font-size: 26px;
    font-weight: 740;
    line-height: 1.2;
  }

  > span {
    padding-bottom: 2px;
    color: #7b8980;
    font-size: 13px;
  }
}

.catalog-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 320px);
  margin-bottom: 18px;
  align-items: start;
  gap: 12px;
}

.item-group {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;

  button {
    cursor: pointer;
    display: inline-flex;
    min-height: 32px;
    padding: 0 12px;
    align-items: center;
    border: 1px solid rgba(51, 162, 4, .18);
    border-radius: 8px;
    background: rgba(255, 255, 255, .82);
    color: #476252;
    font-size: 14px;
    line-height: 1;
    transition: border-color .2s ease, color .2s ease, background .2s ease;

    &.selected {
      border-color: rgba(51, 162, 4, .45);
      background: #eff9ef;
      color: #267d4a;
    }

    &:hover {
      border-color: rgba(51, 162, 4, .45);
      color: #267d4a;
    }
  }
}

.item-search {
  width: 100%;
  border-color: rgba(51, 162, 4, .18);
  border-radius: 8px;
  background: rgba(255, 255, 255, .9);

  :deep(.ant-input-prefix) {
    color: #6f8175;
  }
}

.catalog-error {
  margin-bottom: 16px;
}

.item-list {
  --catalog-card-min-width: 264px;
  --catalog-grid-gap: 18px;

  container-type: inline-size;
  padding-bottom: 20px;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(var(--catalog-card-min-width), 1fr));
  align-items: stretch;
  justify-content: center;
  gap: 20px var(--catalog-grid-gap);
}

.item-grid-cell {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
  justify-content: center;
}

.item-empty {
  padding: 46px 0;
  border: 1px dashed rgba(51, 162, 4, .16);
  border-radius: 8px;
  background: rgba(255, 255, 255, .72);
}

.load-more-state {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  color: #7c8b82;
  font-size: 13px;
}

.catalog-support {
  margin-top: 12px;
}

@media only screen and (max-width: 640px) {
  .items-container {
    padding: 16px 0 20px;
  }

  .catalog-heading {
    min-height: 66px;
  }

  .catalog-title h1 {
    font-size: 23px;
  }

  .catalog-toolbar {
    grid-template-columns: minmax(0, 1fr);
    gap: 10px;
  }

  .item-group {
    flex-wrap: nowrap;
    padding-bottom: 2px;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }

    button {
      flex: none;
    }
  }

  .item-list {
    --catalog-card-min-width: 0px;
    --catalog-grid-gap: 0px;
  }

  .item-grid {
    grid-template-columns: minmax(0, 1fr);
    justify-content: stretch;
  }
}
</style>
