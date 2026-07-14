<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { getShopItemGroups, getShopItemPage, getShopItems } from '../apis/api.js';
import { useRouter } from '@/router/use';
import { isPureDonationType, resolveShopItemCover, setShopImageFallback } from '@/modules/shop/utils/shopCovers.js';
import { deliveryModeColor, deliveryModeText, shopItemTypeText } from '@/modules/shop/utils/shopDelivery.js';
import { getShopItemHighlight } from '@/modules/shop/utils/shopItemEffects.js';
import ShopSupportEntry from '../components/ShopSupportEntry.vue';
import {
  CrownOutlined,
  FireOutlined,
  PushpinFilled,
  SafetyCertificateOutlined,
  SearchOutlined,
  StarFilled,
  ThunderboltOutlined
} from '@ant-design/icons-vue';
import { resolveShopItemPromotion } from '../hooks/useShopItemPromotion.js';
import { resolveLocalizedShopItem } from '@/modules/shop/utils/shopI18n.js';

const router = useRouter();
const { locale, t } = useI18n();

const groups = ref([]);
const data = ref([]);
const recommendedItems = ref([]);
const loading = ref(false);
const loadingMore = ref(false);
const recommendedLoading = ref(false);
const listSentinel = ref(null);

const pagination = reactive({
  page: 0,
  size: 10,
  total: 0,
  finished: false,
});

let observer;
let requestSeq = 0;
let searchTimer;

const highlightIcons = {
  crown: CrownOutlined,
  badge: SafetyCertificateOutlined,
  spark: ThunderboltOutlined,
  fire: FireOutlined
};

const selectedGroupId = ref();
const searchKeyword = ref('');
const hasMore = computed(() => !pagination.finished);
const normalizedKeyword = computed(() => searchKeyword.value.trim());
const isSearching = computed(() => normalizedKeyword.value.length > 0);
const localizedItems = computed(() => data.value.map(item => resolveLocalizedShopItem(item, locale.value)));
const localizedRecommendedItems = computed(() => recommendedItems.value.map(item => resolveLocalizedShopItem(item, locale.value)));
const GROUP_NAME_KEYS = {
  '开源仓库': 'repositories',
  '数字资料': 'digitalResources',
  '服务套餐': 'services',
  '授权许可': 'licenses',
  '商业套餐': 'commercial'
};
const localizedGroups = computed(() => groups.value.map(group => ({
  ...group,
  name: GROUP_NAME_KEYS[group.name] ? t(`shop.groups.${GROUP_NAME_KEYS[group.name]}`) : group.name
})));

// 选择分组
const selectGroup = id => {
  if (selectedGroupId.value === id) {
    selectedGroupId.value = undefined;
  } else {
    selectedGroupId.value = id;
  }
  // 刷新数据
  loadData(true);
}

const toDetail = id => router.push(`/shop/detail/${id}`)

const itemTypeText = item => shopItemTypeText(item?.type, t);
const itemDeliveryText = item => deliveryModeText(item?.deliveryMode, t);

const displayTags = item => {
  const used = new Set([
    item.typeName,
    item.deliveryModeName,
    itemTypeText(item),
    itemDeliveryText(item)
  ].filter(Boolean));
  return (item.tags || []).filter(tag => {
    if (!tag || used.has(tag)) {
      return false;
    }
    used.add(tag);
    return true;
  });
}

const itemCardClass = item => {
  const highlight = getShopItemHighlight(item);
  return ['item-card', highlight.style?.className, { 'item-highlighted': highlight.active }];
};

const itemHighlightIcon = item => highlightIcons[getShopItemHighlight(item).icon];

const itemHighlightLabel = item => getShopItemHighlight(item, t).style?.label;

const isUsdDisplay = () => String(locale.value || '').toLowerCase().startsWith('en');
const formatUsdAmount = value => {
  const amount = Number(value || 0);
  return Number.isFinite(amount) ? amount.toFixed(2).replace(/\.00$/, '') : '0';
};
const itemUsdAmount = item => item.minEffectiveUsdPrice || item.effectiveUsdPrice || 0;
const itemPromotion = item => isUsdDisplay()
  ? {
      active: false,
      originalAmount: itemUsdAmount(item),
      payableAmount: itemUsdAmount(item),
      discountAmount: '0.00',
      rangeSuffix: ''
    }
  : resolveShopItemPromotion(item);
const itemPriceText = item => {
  if (isPureDonationType(item?.type)) {
    return t('shop.donationStart', {
      symbol: isUsdDisplay() ? '$' : '¥',
      amount: isUsdDisplay()
        ? formatUsdAmount(Math.max(1, Number(itemUsdAmount(item))))
        : itemPromotion(item).payableAmount
    });
  }
  if (isUsdDisplay()) {
    const price = `$${formatUsdAmount(itemUsdAmount(item))}`;
    const hasRange = Number(item.maxEffectiveUsdPrice || itemUsdAmount(item))
      > Number(itemUsdAmount(item));
    return hasRange ? `${t('shop.priceStartSuffix')} ${price}` : price;
  }
  const price = `¥${itemPromotion(item).payableAmount}`;
  if (!itemPromotion(item).rangeSuffix) {
    return price;
  }
  return String(locale.value || '').toLowerCase().startsWith('en')
    ? `${t('shop.priceStartSuffix')} ${price}`
    : `${price}${itemPromotion(item).rangeSuffix}`;
};

const loadGroups = async () => {
  groups.value = await getShopItemGroups();
}

const loadRecommendedItems = async () => {
  recommendedLoading.value = true;
  try {
    recommendedItems.value = await getShopItems({
      page: 0,
      size: 6,
      recommended: true
    });
  } finally {
    recommendedLoading.value = false;
  }
}

// 加载数据
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
  try {
    const { records, page } = await getShopItemPage({
      page: pageToLoad,
      size: pagination.size,
      groupId: selectedGroupId.value,
      keyword: normalizedKeyword.value || undefined,
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
  } finally {
    if (currentRequest === requestSeq) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
}

const handleSearch = () => {
  window.clearTimeout(searchTimer);
  loadData(true);
};

watch(searchKeyword, () => {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => loadData(true), 260);
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
  if (!('IntersectionObserver' in window)) {
    window.addEventListener('scroll', handleReachBottom, { passive: true });
    return;
  }
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
  window.addEventListener('scroll', handleReachBottom, { passive: true });
};

onMounted(async () => {
  await Promise.all([loadGroups(), loadData(), loadRecommendedItems()]);
  await setupLoadMoreObserver();
});

onBeforeUnmount(() => {
  observer?.disconnect();
  window.clearTimeout(searchTimer);
  window.removeEventListener('scroll', handleReachBottom);
});
</script>

<template>
  <div class='items-container'>
    <shop-support-entry variant='banner' />
    <section v-if='!isSearching && (recommendedItems.length || recommendedLoading)' class='recommended-section'>
      <div class='section-head'>
        <h2>{{ t('shop.recommendedItems') }}</h2>
      </div>
      <a-spin :spinning='recommendedLoading'>
        <div class='recommended-list'>
          <button
            v-for='item in localizedRecommendedItems'
            :key='item.id'
            type='button'
            class='recommended-card'
            @click='toDetail(item.id)'
          >
            <img :src='resolveShopItemCover(item)' alt='' @error='event => setShopImageFallback(event, item.type)' />
            <span class='recommended-info'>
              <strong>{{ item.name }}</strong>
              <span class='recommended-price' :class='{ discounted: itemPromotion(item).active }'>
                <del v-if='itemPromotion(item).active'>¥{{ itemPromotion(item).originalAmount }}</del>
                <em>{{ itemPriceText(item) }}</em>
              </span>
            </span>
          </button>
        </div>
      </a-spin>
    </section>
    <div class='catalog-toolbar'>
      <div class='item-group'>
        <button type='button' :class='{selected: selectedGroupId === undefined}' @click='selectGroup()'>{{ t('common.all') }}</button>
        <div v-for='{id, name} in localizedGroups' :key='id'>
          <button type='button' @click='selectGroup(id)' :class='{selected: selectedGroupId === id}'>{{name}}</button>
        </div>
      </div>
      <a-input
        v-model:value='searchKeyword'
        class='item-search'
        :placeholder="t('shop.searchPlaceholder')"
        allow-clear
        @pressEnter='handleSearch'
      >
        <template #prefix>
          <search-outlined />
        </template>
      </a-input>
    </div>
    <div class='item-list'>
      <a-spin :spinning='loading'>
        <a-empty
          v-if='!localizedItems.length && !loading'
          class='item-empty'
          :description='isSearching ? t("shop.noSearchResults") : t("common.noData")'
        />
        <div v-else class='item-grid'>
          <div v-for='item in localizedItems' :key='item.id' class='item-grid-cell'>
            <a-card hoverable :bordered='false' :class='itemCardClass(item)' @click='toDetail(item.id)'>
              <template #cover>
                <div class='cover-box'>
                  <div v-if='item.pinned || item.recommended' class='status-badges'>
                    <a-tooltip v-if='item.pinned' :title='t("shop.pinned")'>
                      <span class='status-icon status-icon-pinned' role='img' :aria-label='t("shop.pinned")'>
                        <pushpin-filled />
                      </span>
                    </a-tooltip>
                    <a-tooltip v-if='item.recommended' :title='t("shop.recommended")'>
                      <span class='status-icon status-icon-recommended' role='img' :aria-label='t("shop.recommended")'>
                        <star-filled />
                      </span>
                    </a-tooltip>
                  </div>
                  <div v-if='itemHighlightLabel(item) || itemHighlightIcon(item)' class='highlight-badge'>
                    <component v-if='itemHighlightIcon(item)' :is='itemHighlightIcon(item)' />
                    <span v-if='itemHighlightLabel(item)'>{{ itemHighlightLabel(item) }}</span>
                  </div>
                  <img alt='' :src='resolveShopItemCover(item)' @error='event => setShopImageFallback(event, item.type)' />
                </div>
              </template>
              <div class='card-body'>
                <div class='card-content'>
                  <div class='item-name' :title='item.name'>{{item.name}}</div>
                  <div class='tag-list'>
                    <a-tag v-if='item.type' color='blue'>{{ itemTypeText(item) }}</a-tag>
                    <a-tag :color='deliveryModeColor(item.deliveryMode)'>{{ itemDeliveryText(item) }}</a-tag>
                    <a-tag color='green' v-for='tag in displayTags(item)' :key='tag'>{{tag}}</a-tag>
                  </div>
                </div>
                <div class='price-box'>
                  <span class='price-main' :class='{ discounted: itemPromotion(item).active }'>
                    <span v-if='itemPromotion(item).active' class='original-price'>¥{{ itemPromotion(item).originalAmount }}</span>
                    <span class='price'>{{ itemPriceText(item) }}</span>
                    <a-tag v-if='itemPromotion(item).active' color='red' class='coupon-tag'>{{ t('shop.postDiscount') }}</a-tag>
                  </span>
                  <span class='count'>{{ t('shop.soldCount', { count: item.buyCount ?? 0 }) }}</span>
                </div>
              </div>
            </a-card>
          </div>
        </div>
      </a-spin>
      <div ref='listSentinel' class='load-more-state'>
        <a-spin v-if='loadingMore' size='small' />
        <span v-else-if='hasMore'>{{ t('shop.loadMore') }}</span>
        <span v-else-if='data.length'>{{ t('shop.noMore') }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang='less'>
.items-container {
  padding: 22px 18px 24px;

  .recommended-section {
    margin-bottom: 18px;

    .section-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;

      h2 {
        margin: 0;
        color: #203626;
        font-size: 18px;
        font-weight: 700;
        line-height: 1.2;
      }
    }

    .recommended-list {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 14px;
    }

    .recommended-card {
      cursor: pointer;
      display: grid;
      grid-template-columns: 108px minmax(0, 1fr);
      gap: 12px;
      align-items: center;
      min-width: 0;
      min-height: 92px;
      padding: 10px;
      border: 1px solid rgba(31, 122, 54, .08);
      border-radius: 8px;
      background: rgba(255, 255, 255, .92);
      box-shadow: 0 8px 20px rgba(32, 54, 38, .055);
      text-align: left;
      transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;

      &:hover {
        transform: translateY(-1px);
        border-color: rgba(51, 162, 4, .18);
        box-shadow: 0 12px 26px rgba(32, 54, 38, .09);
      }

      img {
        width: 108px;
        height: 72px;
        border-radius: 6px;
        background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
        object-fit: cover;
      }

      .recommended-info {
        display: grid;
        gap: 8px;
        min-width: 0;

        strong {
          display: -webkit-box;
          overflow: hidden;
          color: #203626;
          font-size: 14px;
          font-weight: 650;
          line-height: 1.3;
          text-overflow: ellipsis;
          -webkit-box-orient: vertical;
          -webkit-line-clamp: 2;
        }

        .recommended-price {
          display: flex;
          min-width: 0;
          flex-wrap: wrap;
          align-items: baseline;
          gap: 5px 7px;
          color: #e5483d;
          font-weight: 700;
          line-height: 1;

          del {
            color: #9aa59e;
            font-size: 12px;
            font-weight: 500;
          }

          em {
            font-style: normal;
            font-size: 16px;
          }
        }
      }
    }
  }

  .catalog-toolbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(220px, 320px);
    gap: 12px;
    align-items: start;
    margin-bottom: 14px;

    .item-group {
      display: flex;
      min-width: 0;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;

      button {
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        min-height: 32px;
        padding: 0 12px;
        border: 1px solid rgba(51, 162, 4, .18);
        border-radius: 8px;
        background: rgba(255, 255, 255, .82);
        color: #476252;
        font-size: 14px;
        line-height: 1;
        transition: border-color .2s ease, color .2s ease, background .2s ease, box-shadow .2s ease;

        &.selected {
          border-color: rgba(51, 162, 4, .45);
          background: #eff9ef;
          color: #267d4a;
          box-shadow: 0 8px 18px rgba(51, 162, 4, .1);
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
  }

  .item-list {
    --catalog-card-min-width: 264px;
    --catalog-card-max-width: 268px;
    --catalog-grid-gap: 18px;

    container-type: inline-size;
    padding: 0 0 20px;

    .load-more-state {
      display: flex;
      min-height: 42px;
      align-items: center;
      justify-content: center;
      color: #7c8b82;
      font-size: 13px;
    }

    .item-empty {
      padding: 46px 0;
      border: 1px dashed rgba(51, 162, 4, .16);
      border-radius: 8px;
      background: rgba(255, 255, 255, .72);
    }

    .item-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(var(--catalog-card-min-width), 1fr));
      gap: 20px var(--catalog-grid-gap);
      justify-content: center;
      align-items: stretch;
    }

    .item-grid-cell {
      display: flex;
      min-width: 0;
      width: 100%;
      height: 100%;
      justify-content: center;
    }

    .item-card {
      width: 100%;
      height: 100%;
      min-width: 0;
      max-width: var(--catalog-card-max-width);
      overflow: hidden;
      border: 1px solid rgba(31, 122, 54, .08);
      border-radius: 8px;
      background: white;
      box-shadow: 0 8px 20px rgba(32, 54, 38, .055);
      transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;

      :deep(.ant-card-cover) {
        overflow: hidden;
      }

      :deep(.ant-card-body) {
        display: block;
        padding: 12px 12px 13px;
      }

      &:hover {
        transform: translateY(-2px);
        border-color: rgba(51, 162, 4, .18);
        box-shadow: 0 14px 30px rgba(32, 54, 38, .095);
      }

      &.item-highlighted {
        border-color: rgba(226, 167, 35, .32);
        box-shadow: 0 12px 28px rgba(170, 120, 25, .12);
      }

      &.item-highlight-commercial {
        background: linear-gradient(180deg, rgba(255, 251, 235, .96), #fff 45%);
      }

      &.item-highlight-enterprise {
        border-color: rgba(96, 75, 190, .32);
        background: linear-gradient(180deg, rgba(245, 243, 255, .96), #fff 45%);
        box-shadow: 0 12px 30px rgba(83, 65, 170, .13);
      }

      &.item-highlight-hot {
        border-color: rgba(229, 72, 61, .3);
        background: linear-gradient(180deg, rgba(255, 241, 240, .95), #fff 45%);
      }

      .cover-box {
        position: relative;
        display: grid;
        place-items: center;
        width: 100%;
        aspect-ratio: 1.32 / 1;
        background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
        color: #267d4a;
        font-size: 36px;
        font-weight: 700;

        img {
          width: 100%;
          height: 100%;
          background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
          object-fit: cover;
          color: transparent;
          font-size: 0;
        }
      }

      .status-badges {
        position: absolute;
        z-index: 1;
        top: 8px;
        left: 8px;
        display: flex;
        max-width: calc(100% - 72px);
        flex-wrap: nowrap;
        gap: 5px;

        .status-icon {
          display: inline-grid;
          width: 26px;
          height: 26px;
          flex: 0 0 26px;
          place-items: center;
          border: 1px solid rgba(255, 255, 255, .82);
          border-radius: 50%;
          color: #fff;
          font-size: 13px;
          box-shadow: 0 6px 16px rgba(24, 55, 37, .16);
          backdrop-filter: blur(8px);
        }

        .status-icon-pinned {
          background: rgba(218, 54, 51, .9);
        }

        .status-icon-recommended {
          background: rgba(217, 119, 6, .9);
        }
      }

      .highlight-badge {
        position: absolute;
        z-index: 2;
        bottom: 8px;
        right: 8px;
        display: inline-flex;
        align-items: center;
        gap: 4px;
        max-width: calc(100% - 16px);
        min-height: 24px;
        padding: 0 8px;
        overflow: hidden;
        border: 1px solid rgba(255, 255, 255, .76);
        border-radius: 999px;
        background: rgba(24, 55, 37, .86);
        color: #fff;
        font-size: 12px;
        font-weight: 650;
        line-height: 1;
        white-space: nowrap;
        box-shadow: 0 8px 18px rgba(24, 55, 37, .16);
        backdrop-filter: blur(8px);

        span {
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      &.item-highlight-commercial .highlight-badge {
        background: rgba(217, 119, 6, .88);
        box-shadow: 0 8px 18px rgba(217, 119, 6, .16);
      }

      &.item-highlight-enterprise .highlight-badge {
        background: rgba(91, 68, 166, .9);
        box-shadow: 0 8px 18px rgba(91, 68, 166, .18);
      }

      &.item-highlight-hot .highlight-badge {
        background: rgba(218, 54, 51, .88);
        box-shadow: 0 8px 18px rgba(218, 54, 51, .16);
      }

      .card-body {
        display: grid;
        grid-template-rows: minmax(104px, 1fr) minmax(34px, auto);
        min-height: 150px;
        gap: 10px;
        min-width: 0;
        text-align: left;
      }

      .card-content {
        display: flex;
        min-width: 0;
        min-height: 0;
        overflow: hidden;
        flex-direction: column;
        gap: 8px;
        align-self: start;
      }

      .item-name {
        display: -webkit-box;
        min-height: 0;
        overflow: hidden;
        color: #203626;
        font-size: 16px;
        font-weight: 650;
        line-height: 1.3;
        text-overflow: ellipsis;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 3;
      }

      .tag-list {
        display: flex;
        max-height: 58px;
        overflow: hidden;
        flex-wrap: wrap;
        align-content: flex-start;
        gap: 6px 5px;

        :deep(.ant-tag) {
          display: inline-flex;
          align-items: center;
          max-width: calc(100% - 2px);
          min-height: 22px;
          padding: 0 8px;
          margin-inline-end: 0;
          overflow: hidden;
          border-radius: 6px;
          font-size: 12px;
          line-height: 20px;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .price-box {
        display: flex;
        min-height: 34px;
        justify-content: space-between;
        align-items: flex-end;
        align-self: end;
        gap: 10px;
        line-height: 1;

        .price-main {
          display: inline-flex;
          min-width: 0;
          flex-wrap: wrap;
          align-items: baseline;
          gap: 5px 7px;
        }

        .original-price {
          flex: 0 1 auto;
          overflow: hidden;
          color: #9aa59e;
          font-size: 12px;
          text-decoration: line-through;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .price {
          color: #e5483d;
          font-size: 21px;
          font-weight: 700;
        }

        .coupon-tag {
          flex: 0 0 auto;
          min-height: 20px;
          margin-inline-end: 0;
          border-radius: 999px;
          font-size: 12px;
          line-height: 18px;
        }

        .count {
          flex: 0 0 auto;
          color: #8a958e;
          font-size: 12px;
          white-space: nowrap;
        }
      }
    }
  }
}

@media only screen and (max-width: 960px) {
  .items-container {
    padding-inline: 12px;

    .recommended-section {
      .recommended-list {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

  }
}

@media only screen and (max-width: 640px) {
  .items-container {
    padding: 16px 10px 20px;

    .recommended-section {
      .recommended-list {
        grid-template-columns: 1fr;
      }

      .recommended-card {
        grid-template-columns: 76px minmax(0, 1fr);
        min-height: 74px;
        padding: 8px;

        img {
          width: 76px;
          height: 54px;
        }
      }
    }

    .catalog-toolbar {
      grid-template-columns: minmax(0, 1fr);
      gap: 10px;

      .item-search {
        min-height: 34px;
      }
    }

    .item-list {
      --catalog-card-min-width: 0px;
      --catalog-grid-gap: 0px;

      .item-grid {
        grid-template-columns: minmax(0, 1fr);
        justify-content: stretch;
      }

      .item-card {
        max-width: none;
        width: 100%;
      }
    }
  }
}
</style>
