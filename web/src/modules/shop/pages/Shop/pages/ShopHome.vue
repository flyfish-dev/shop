<script setup>
import { onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { Skeleton } from 'ant-design-vue';
import {
  AppstoreOutlined, ArrowRightOutlined, CodeOutlined, EditOutlined,
  CustomerServiceOutlined, FileTextOutlined, SafetyCertificateOutlined,
  BorderOuterOutlined, CodeSandboxOutlined, FileSearchOutlined
} from '@ant-design/icons-vue';
import StorefrontHero from '../components/StorefrontHero.vue';
import ShopProductCard from '../components/ShopProductCard.vue';
import ShopSupportEntry from '../components/ShopSupportEntry.vue';
import { useShopHome } from '../hooks/useShopHome.js';
import { useStorefrontMotion } from '../hooks/useStorefrontMotion.js';

const { t } = useI18n();
const { vReveal } = useStorefrontMotion();
const {
  activeCollection, collections, selectedCollection, visibleItems, itemsLoading, loadError, loadHome,
  search, searchKeyword, toCatalog, toDetail, totalProducts
} = useShopHome();
const collectionIcons = {
  documents: FileTextOutlined, editing: EditOutlined, cad: BorderOuterOutlined, models: CodeSandboxOutlined
};
onMounted(loadHome);
</script>

<template>
  <main class="shop-home">
    <storefront-hero v-model="searchKeyword" @search="search" />

    <div class="storefront-content">
      <nav class="collection-nav" :aria-label="t('storefront.explore')">
        <button type="button" :class="{ selected: !activeCollection }" :aria-pressed="!activeCollection" aria-controls="storefront-products" @click="activeCollection = ''">
          <appstore-outlined aria-hidden="true" />{{ t('storefront.all') }}
        </button>
        <button v-for="collection in collections" :key="collection.key" type="button" :class="{ selected: activeCollection === collection.key }" :aria-pressed="activeCollection === collection.key" aria-controls="storefront-products" @click="activeCollection = collection.key">
          <component :is="collectionIcons[collection.key]" aria-hidden="true" />{{ collection.name }}
        </button>
        <span v-if="totalProducts" class="collection-total">{{ t('storefront.products', { count: totalProducts }) }}</span>
      </nav>

      <a-alert v-if="loadError" class="home-load-error" type="warning" show-icon :message="loadError">
        <template #action><a-button size="small" @click="loadHome">{{ t('common.retry') }}</a-button></template>
      </a-alert>

      <section v-if="visibleItems.length || itemsLoading" id="storefront-products" class="home-section featured-section" aria-labelledby="featured-title" :aria-busy="itemsLoading">
        <div class="section-heading">
          <div><span class="section-kicker">{{ t('storefront.selectionNote') }}</span><h2 id="featured-title" aria-live="polite">{{ selectedCollection?.name || t('storefront.selection') }}</h2></div>
          <button type="button" class="section-link" @click="toCatalog()">{{ t('shop.home.allProducts') }}<arrow-right-outlined /></button>
        </div>
        <div v-if="itemsLoading && !visibleItems.length" class="product-skeletons" aria-hidden="true"><Skeleton v-for="index in 4" :key="index" active /></div>
        <transition-group v-else :key="activeCollection" name="collection" tag="div" class="product-row">
          <shop-product-card v-for="(item, index) in visibleItems" :key="item.id" v-reveal :style="{ '--reveal-delay': (index * 55) + 'ms' }" :item="item" variant="home" @select="toDetail" />
        </transition-group>
      </section>

      <a-empty v-if="!itemsLoading && !visibleItems.length && !loadError" class="home-empty" :description="t('storefront.noProducts')" />

      <section v-reveal class="catalog-band">
        <file-search-outlined class="catalog-mark" />
        <div><h2>{{ t('storefront.catalogAction') }}</h2><p>{{ t('storefront.catalogNote') }}</p></div>
        <a-button @click="toCatalog()">{{ t('shop.home.allProducts') }}<arrow-right-outlined /></a-button>
      </section>
    </div>

    <section class="store-service-band" :aria-label="t('storefront.delivery')">
      <div class="service-grid">
        <div v-reveal class="service-item"><code-outlined /><h3>{{ t('storefront.source') }}</h3><p>{{ t('storefront.sourceNote') }}</p></div>
        <div v-reveal class="service-item"><safety-certificate-outlined /><h3>{{ t('storefront.license') }}</h3><p>{{ t('storefront.licenseNote') }}</p></div>
        <div v-reveal class="service-item"><customer-service-outlined /><h3>{{ t('storefront.support') }}</h3><p>{{ t('storefront.supportNote') }}</p></div>
      </div>
    </section>
    <section v-reveal class="store-help">
      <div><h2>{{ t('storefront.helpTitle') }}</h2><p>{{ t('storefront.helpNote') }}</p></div>
      <shop-support-entry />
    </section>
  </main>
</template>

<style scoped lang="less">
.shop-home { color: #23362c; text-align: left; letter-spacing: 0; }
.storefront-content { max-width: 1336px; padding: 0 28px; margin: auto; }
.collection-nav { display: flex; align-items: center; gap: 28px; min-height: 76px; border-bottom: 1px solid #e9eeea; overflow-x: auto; overflow-y: hidden; scrollbar-width: none; }
.collection-nav::-webkit-scrollbar { display: none; }
.collection-nav button { position: relative; display: inline-flex; align-items: center; gap: 8px; min-height: 76px; flex: none; padding: 0; border: 0; color: #7c877f; background: transparent; font-size: 14px; cursor: pointer; transition: color .2s; }
.collection-nav button::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 2px; background: #17825c; transform: scaleX(0); transition: transform .25s; }
.collection-nav button.selected { color: #157753; font-weight: 650; }
.collection-nav button.selected::after { transform: scaleX(1); }
.collection-nav button:hover { color: #157753; }
.collection-total { margin-left: auto; white-space: nowrap; font-size: 12px; color: #99a39c; }
.home-load-error { margin-top: 20px; }
.home-section { padding: 38px 0 44px; border-bottom: 1px solid #edf0ee; transition: opacity .6s, transform .6s; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.section-heading h2 { margin: 0; font-size: 24px; line-height: 1.35; font-weight: 650; }
.section-kicker { display: block; margin-bottom: 6px; color: #8a968e; font-size: 12px; }
.section-link { display: inline-flex; align-items: center; gap: 8px; flex: none; border: 0; background: transparent; color: #577163; font-size: 13px; padding: 10px 0; cursor: pointer; }
.section-link .anticon { transition: transform .2s; }
.section-link:hover { color: #17825c; }
.section-link:hover .anticon { transform: translateX(4px); }
.product-row,
.product-skeletons {
  --home-card-min-width: 250px;
  --home-card-max-width: 310px;
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: stretch;
  justify-content: center;
  > * { width: 100%; max-width: var(--home-card-max-width); min-width: 0; flex: 1 1 var(--home-card-min-width); }
}
.product-skeletons {
  min-height: 330px;
  > * { padding: 80px 20px 20px; background: #f6f8f7; }
}
.product-row > * { transition: opacity .6s var(--reveal-delay, 0ms), transform .45s, box-shadow .25s; }
.catalog-band { display: flex; align-items: center; gap: 18px; padding: 28px 0; transition: opacity .6s, transform .6s; }
.catalog-mark { color: #59806b; font-size: 28px; }
.catalog-band h2, .store-help h2 { margin: 0 0 6px; font-size: 20px; font-weight: 650; }
.catalog-band p, .store-help p { color: #849088; font-size: 13px; margin: 0; }
.catalog-band .ant-btn { margin-left: auto; display: inline-flex; align-items: center; gap: 8px; height: 38px; color: #466352; border-color: #dbe5dd; }
.store-service-band { border-block: 1px solid #e9edea; background: #f7f9f8; }
.service-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); max-width: 1280px; margin: auto; }
.service-item { padding: 32px 28px; transition: opacity .6s, transform .6s; }
.service-item + .service-item { border-left: 1px solid #e8ede9; }
.service-item > .anticon { color: #517860; font-size: 23px; margin-bottom: 18px; }
.service-item:nth-child(2) > .anticon { color: #5478a7; }
.service-item:nth-child(3) > .anticon { color: #b98a39; }
.service-item h3 { font-size: 16px; margin: 0 0 8px; font-weight: 650; }
.service-item p { margin: 0; color: #839087; font-size: 13px; line-height: 1.8; }
.store-help { display: flex; justify-content: space-between; gap: 24px; align-items: center; max-width: 1336px; margin: auto; padding: 42px 28px; transition: opacity .6s, transform .6s; }
.store-help :deep(.support-entry-button) { background: #17825c; border-color: #17825c; box-shadow: none; }
.reveal-pending { opacity: 0; transform: translateY(18px); }
.collection-enter-active { transition: opacity .25s, transform .25s; }
.collection-enter-from { opacity: 0; transform: translateY(8px); }
.collection-leave-active { display: none; }
.home-empty { padding: 70px 0; }
button:focus-visible { outline: 2px solid #17825c; outline-offset: 3px; }
@media (min-width: 1800px) { .storefront-content, .store-help { max-width: 1516px; } }
@media (max-width: 1080px) {
  .product-row, .product-skeletons { flex-wrap: nowrap; justify-content: flex-start; overflow-x: auto; overflow-y: hidden; padding: 4px 0 14px; scroll-snap-type: x proximity; overscroll-behavior-x: contain; scrollbar-width: none; }
  .product-row::-webkit-scrollbar, .product-skeletons::-webkit-scrollbar { display: none; }
  .product-row > *, .product-skeletons > * { flex: 0 0 284px; scroll-snap-align: start; }
  .collection-total { display: none; }
}
@media (max-width: 640px) {
  .storefront-content { padding: 0 16px; }
  .collection-nav { min-height: 62px; gap: 22px; }
  .collection-nav button { min-height: 62px; font-size: 13px; }
  .home-section { padding: 28px 0; }
  .section-heading { gap: 10px; margin-bottom: 16px; align-items: end; }
  .section-heading h2 { font-size: 20px; }
  .section-link { font-size: 12px; padding-bottom: 2px; }
  .product-row, .product-skeletons { gap: 12px; }
  .product-row > *, .product-skeletons > * { flex-basis: 264px; }
  .catalog-band { flex-wrap: wrap; padding: 24px 0; gap: 12px; }
  .catalog-band h2, .store-help h2 { font-size: 18px; }
  .catalog-band .ant-btn { margin-left: 40px; }
  .service-grid { grid-template-columns: 1fr; padding: 8px 20px; }
  .service-item { position: relative; padding: 22px 0 22px 40px; }
  .service-item + .service-item { border-left: 0; border-top: 1px solid #e8ede9; }
  .service-item > .anticon { position: absolute; top: 24px; left: 0; font-size: 20px; }
  .service-item h3 { font-size: 15px; }
  .service-item p { font-size: 12px; }
  .store-help { align-items: flex-start; padding: 30px 20px; gap: 16px; }
  .store-help p { line-height: 1.7; }
  .store-help :deep(.support-entry-button) { flex: none; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; scroll-behavior: auto !important; }
  .reveal-pending { opacity: 1; transform: none; }
}
</style>
