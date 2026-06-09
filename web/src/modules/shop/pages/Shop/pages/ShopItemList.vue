<script setup>
import { onMounted, reactive, ref } from 'vue';
import { getShopItemGroups, getShopItems } from '../apis/api.js';
import { useRouter } from '@/router/use';
import { resolveShopItemCover, setShopImageFallback } from '@/modules/shop/utils/shopCovers.js';
import { deliveryModeColor, deliveryModeText } from '@/modules/shop/utils/shopDelivery.js';
import ShopSupportEntry from '../components/ShopSupportEntry.vue';

const router = useRouter();

const groups = ref([]);
const data = ref([]);
const recommendedItems = ref([]);
const loading = ref(false);
const recommendedLoading = ref(false);

const pagination = reactive({
  page: 0,
  size: 10,
  total: 10,
});

// 商品栅格布局
const grid = { gutter: 14, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 5 };

const selectedGroupId = ref();

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

const displayTags = item => {
  const used = new Set([item.typeName, item.deliveryModeName, deliveryModeText(item.deliveryMode)].filter(Boolean));
  return (item.tags || []).filter(tag => {
    if (!tag || used.has(tag)) {
      return false;
    }
    used.add(tag);
    return true;
  });
}

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
  if (reset) {
    data.value = [];
    pagination.page = 0;
  }
  loading.value = true;
  try {
    data.value = await getShopItems({
      ...pagination,
      groupId: selectedGroupId.value,
    });
    pagination.total = data.value.page?.total ?? data.value.length;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadGroups(), loadData(), loadRecommendedItems()]);
})
</script>

<template>
	  <div class='items-container'>
      <shop-support-entry variant='banner' />
      <section v-if='recommendedItems.length || recommendedLoading' class='recommended-section'>
        <div class='section-head'>
          <h2>推荐商品</h2>
        </div>
        <a-spin :spinning='recommendedLoading'>
          <div class='recommended-list'>
            <button
              v-for='item in recommendedItems'
              :key='item.id'
              type='button'
              class='recommended-card'
              @click='toDetail(item.id)'
            >
              <img :src='resolveShopItemCover(item)' alt='' @error='event => setShopImageFallback(event, item.type)' />
              <span class='recommended-info'>
                <strong>{{ item.name }}</strong>
                <span>¥{{ item.price }}</span>
              </span>
            </button>
          </div>
        </a-spin>
      </section>
	    <div class='item-group'>
	      <button type='button' :class='{selected: selectedGroupId === undefined}' @click='selectGroup()'>全部</button>
	      <div v-for='{id, name} in groups' :key='id'>
	        <button type='button' @click='selectGroup(id)' :class='{selected: selectedGroupId === id}'>{{name}}</button>
	      </div>
	    </div>
	    <div class='item-list'>
	      <a-list :grid='grid' :data-source='data' row-key='id' :loading='loading'>
	        <template #renderItem='{ item }'>
	          <a-list-item>
	            <a-card hoverable :bordered='false' class='item-card' @click='toDetail(item.id)'>
	              <template #cover>
	                <div class='cover-box'>
                    <div v-if='item.pinned || item.recommended' class='status-badges'>
                      <a-tag v-if='item.pinned' color='red'>置顶</a-tag>
                      <a-tag v-if='item.recommended' color='gold'>推荐</a-tag>
                    </div>
	                  <img alt='' :src="resolveShopItemCover(item)" @error='event => setShopImageFallback(event, item.type)' />
	                </div>
	              </template>
              <div class='card-body'>
                <div class='item-name' :title='item.name'>{{item.name}}</div>
                <div class='tag-list'>
                  <a-tag v-if='item.typeName' color='blue'>{{item.typeName}}</a-tag>
                  <a-tag :color='deliveryModeColor(item.deliveryMode)'>{{ item.deliveryModeName || deliveryModeText(item.deliveryMode) }}</a-tag>
                  <a-tag color='green' v-for='tag in displayTags(item)' :key='tag'>{{tag}}</a-tag>
                </div>
                <div class='price-box'>
                  <span class='price'>{{item.price}}</span>
                  <span class='count'>{{item.buyCount ?? 0}}人购买</span>
                </div>
              </div>
            </a-card>
          </a-list-item>
        </template>
      </a-list>
    </div>
  </div>
</template>

<style scoped lang='less'>
.items-container {
  padding: 18px 12px 20px;

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
      gap: 10px;
    }

    .recommended-card {
      cursor: pointer;
      display: grid;
      grid-template-columns: 84px minmax(0, 1fr);
      gap: 10px;
      align-items: center;
      min-width: 0;
      min-height: 74px;
      padding: 8px;
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
        width: 84px;
        height: 58px;
        border-radius: 6px;
        background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
        object-fit: cover;
      }

      .recommended-info {
        display: grid;
        gap: 8px;
        min-width: 0;

        strong {
          overflow: hidden;
          color: #203626;
          font-size: 14px;
          font-weight: 650;
          line-height: 1.3;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        span {
          color: #e5483d;
          font-size: 16px;
          font-weight: 700;
          line-height: 1;
        }
      }
    }
  }

  .item-group {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-bottom: 14px;

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

  .item-list {
    padding: 0 0 20px;

    :deep(.ant-list-grid .ant-col) {
      display: flex;
    }

    :deep(.ant-list-item) {
      display: flex;
      width: 100%;
      height: 100%;
      margin-bottom: 14px;
    }

    .item-card {
      width: 100%;
      min-width: 0;
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

      .cover-box {
        position: relative;
        display: grid;
        place-items: center;
        width: 100%;
        aspect-ratio: 1.22 / 1;
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
        max-width: calc(100% - 16px);
        flex-wrap: wrap;
        gap: 5px;

        :deep(.ant-tag) {
          min-height: 22px;
          margin-inline-end: 0;
          border-radius: 999px;
          font-size: 12px;
          line-height: 20px;
        }
      }

      .card-body {
        display: grid;
        grid-template-rows: 42px 50px 30px;
        row-gap: 7px;
        min-width: 0;
        text-align: left;
      }

      .item-name {
        display: -webkit-box;
        min-height: 42px;
        overflow: hidden;
        color: #203626;
        font-size: 16px;
        font-weight: 650;
        line-height: 1.32;
        text-overflow: ellipsis;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
      }

      .tag-list {
        display: flex;
        max-height: 50px;
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
        min-height: 30px;
        justify-content: space-between;
        align-items: baseline;
        gap: 8px;
        line-height: 1;

        .price {
          color: #e5483d;
          font-size: 19px;
          font-weight: 700;

          &::before {
            content: '¥';
            margin-right: 2px;
            font-size: 14px;
            font-weight: 600;
          }
        }

        .count {
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

        img {
          width: 76px;
          height: 54px;
        }
      }
    }

    .item-list {
      .item-card {
        width: 100%;
      }
    }
  }
}
</style>
