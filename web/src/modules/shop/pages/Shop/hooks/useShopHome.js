import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from '@/router/use.js';
import { getShopItems } from '../apis/api.js';
import { buildStorefrontCollections, selectStorefrontHighlights } from '@/modules/shop/utils/storefrontCollections.js';

const HOME_PAGE_SIZE = 100;

export function useShopHome() {
  const router = useRouter();
  const { t } = useI18n();
  const items = ref([]);
  const searchKeyword = ref('');
  const itemsLoading = ref(false);
  const loadError = ref('');
  const activeCollection = ref('');
  const collections = computed(() => buildStorefrontCollections(items.value).map(collection => ({
    ...collection,
    name: t(`storefront.collections.${collection.key}`)
  })));
  const featuredItems = computed(() => selectStorefrontHighlights(items.value));
  const selectedCollection = computed(() => collections.value.find(collection => collection.key === activeCollection.value));
  const visibleItems = computed(() => selectedCollection.value?.items || featuredItems.value);
  const totalProducts = computed(() => items.value.page?.total ?? items.value.length);

  const loadItems = async () => {
    itemsLoading.value = true;
    try {
      items.value = await getShopItems({ page: 0, size: HOME_PAGE_SIZE });
    } finally {
      itemsLoading.value = false;
    }
  };

  const loadHome = async () => {
    if (itemsLoading.value) return;
    loadError.value = '';
    try {
      await loadItems();
      if (activeCollection.value && !selectedCollection.value) activeCollection.value = '';
    } catch {
      loadError.value = t('shop.home.loadFailed');
    }
  };

  const toCatalog = groupId => router.push('/shop/item-list', {
    groupId: groupId || undefined
  });

  const search = () => {
    const keyword = searchKeyword.value.trim();
    router.push('/shop/item-list', keyword ? { keyword } : undefined);
  };

  const toDetail = id => router.push(`/shop/detail/${id}`);

  return {
    activeCollection,
    collections,
    selectedCollection,
    visibleItems,
    itemsLoading,
    loadError,
    search,
    searchKeyword,
    toCatalog,
    toDetail,
    totalProducts,
    loadHome
  };
}
