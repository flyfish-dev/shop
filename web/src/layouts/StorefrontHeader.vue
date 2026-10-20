<script setup>
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { ArrowUpOutlined, MenuOutlined, CloseOutlined } from '@ant-design/icons-vue';
import UserInfo from '@/components/UserInfo/index.vue';
import LanguageSwitch from '@/components/LanguageSwitch/index.vue';
import { useRoute } from '@/router/use.js';
import { lowcodeSiteUrl } from '@/runtime/application.js';
import logo from '@/assets/logo.png';

const { t } = useI18n();
const route = useRoute();
const menuOpen = ref(false);
const onHome = computed(() => ['/', '/shop', '/shop/'].includes(route.path));
watch(() => route.fullPath, () => { menuOpen.value = false; });
</script>

<template>
  <header class="store-header" @keydown.esc="menuOpen = false">
    <div class="store-header-inner">
      <router-link href="/" class="store-brand" :aria-label="t('storefront.title')">
        <img :src="logo" alt="" width="38" height="38">
        <span>{{ t('storefront.title') }}</span>
      </router-link>
      <nav id="store-navigation" class="store-nav" :class="{ open: menuOpen }" :aria-label="t('nav.commonEntries')">
        <router-link href="/" :class="{ active: onHome }" :aria-current="onHome ? 'page' : undefined">{{ t('storefront.discover') }}</router-link>
        <router-link href="/shop/item-list" :class="{ active: route.path === '/shop/item-list' }">{{ t('shop.home.allProducts') }}</router-link>
        <a href="https://viewer.flyfish.dev" target="_blank" rel="noopener noreferrer">{{ t('nav.filePreview') }}<arrow-up-outlined class="external-arrow" /></a>
        <a :href="lowcodeSiteUrl">{{ t('storefront.workspace') }}<arrow-up-outlined class="external-arrow" /></a>
      </nav>
      <div class="store-tools">
        <slot name="actions" />
        <language-switch />
        <user-info compact appearance="storefront" />
        <button class="mobile-menu" type="button" aria-controls="store-navigation" :aria-label="menuOpen ? t('storefront.closeMenu') : t('storefront.menu')" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
          <close-outlined v-if="menuOpen" /><menu-outlined v-else />
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped lang="less">
.store-header { position: sticky; top: 0; z-index: 25; background: rgba(255,255,255,.95); backdrop-filter: blur(12px); border-bottom: 1px solid #e9ecea; }
.store-header-inner { display: flex; align-items: center; gap: 34px; max-width: 1336px; min-height: 76px; margin: auto; padding: 0 28px; }
.store-brand { display: inline-flex; align-items: center; gap: 10px; flex-shrink: 0; color: #172b24; font-size: 21px; font-weight: 750; text-decoration: none; white-space: nowrap; }
.store-brand img { object-fit: contain; }
.store-nav { display: flex; align-items: center; gap: 26px; flex: 1; }
.store-nav a { position: relative; display: inline-flex; align-items: center; gap: 5px; padding: 26px 0; color: #66716c; white-space: nowrap; font-size: 13px; text-decoration: none; transition: color .2s; }
.store-nav a::after { position: absolute; bottom: 16px; height: 2px; left: 0; right: 0; content: ''; background: #17825c; transform: scaleX(0); transform-origin: left; transition: transform .24s; }
.store-nav a:hover, .store-nav a.active { color: #147853; }
.store-nav a:hover::after, .store-nav a.active::after { transform: scaleX(1); }
.external-arrow { transform: rotate(45deg); font-size: 10px; opacity: .55; }
.store-tools { display: flex; align-items: center; margin-left: auto; gap: 14px; }
.mobile-menu { display: none; border: 0; background: transparent; color: #273d33; width: 36px; height: 36px; align-items: center; justify-content: center; font-size: 18px; cursor: pointer; }
@media (max-width: 1100px) {
  .store-header-inner { gap: 20px; }
  .store-nav { gap: 16px; }
  .store-nav a { font-size: 12px; }
  .store-tools { gap: 8px; }
}
@media (max-width: 960px) {
  .store-header-inner { min-height: 64px; padding: 0 20px; }
  .mobile-menu { display: inline-flex; }
  .store-nav { display: none; position: absolute; top: 100%; left: 0; right: 0; padding: 8px 24px 18px; border-bottom: 1px solid #e9ecea; background: #fff; }
  .store-nav.open { display: grid; grid-template-columns: 1fr 1fr; gap: 0 20px; }
  .store-nav a { padding: 14px 0; font-size: 14px; }
  .store-nav a::after { bottom: 6px; }
}
@media (max-width: 480px) {
  .store-header-inner { gap: 8px; padding: 0 12px; }
  .store-brand { font-size: 17px; gap: 5px; }
  .store-brand img { width: 30px; height: 30px; }
  .store-tools { gap: 4px; }
  .store-tools :deep(.language-switch) { transform: scale(.94); }
}
@media (prefers-reduced-motion: reduce) { .store-nav a, .store-nav a::after { transition: none; } }
@media (max-width: 360px) {
  .store-brand { font-size: 15px; }
  .store-brand img { width: 24px; height: 24px; }
  .store-header-inner { padding-inline: 8px; gap: 4px; }
  .store-tools { gap: 2px; }
  .mobile-menu { width: 30px; }
}
</style>
