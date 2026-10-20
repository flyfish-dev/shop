<script setup>
import { useI18n } from 'vue-i18n';
import { ArrowRightOutlined, ArrowUpOutlined, SearchOutlined } from '@ant-design/icons-vue';
import heroImage from '@/assets/shop/storefront-hero.webp';

const { t } = useI18n();
const keyword = defineModel({ type: String, default: '' });
const emit = defineEmits(['search']);
</script>

<template>
  <section class="storefront-intro" aria-labelledby="storefront-title">
    <img class="hero-art" :src="heroImage" alt="" width="1920" height="640" fetchpriority="high" decoding="async">
    <div class="hero-inner">
      <div class="intro-inner">
        <span class="storefront-kicker">{{ t('storefront.heroCategories') }}</span>
        <h1 id="storefront-title">{{ t('storefront.title') }}</h1>
        <p class="storefront-tagline">{{ t('storefront.tagline') }}</p>
        <p class="storefront-description">{{ t('storefront.description') }}</p>
        <form class="storefront-search" role="search" @submit.prevent="emit('search')">
          <search-outlined aria-hidden="true" />
          <input v-model="keyword" type="search" :placeholder="t('storefront.search')" :aria-label="t('storefront.search')" maxlength="100">
          <button type="submit" :aria-label="t('storefront.searchAction')"><arrow-right-outlined /></button>
        </form>
        <a class="hero-demo" href="https://viewer.flyfish.dev/" target="_blank" rel="noopener noreferrer">
          {{ t('storefront.tryDemo') }}<arrow-up-outlined aria-hidden="true" />
        </a>
      </div>
    </div>
  </section>
</template>

<style scoped lang="less">
.storefront-intro { position: relative; isolation: isolate; overflow: hidden; border-bottom: 1px solid #edf0ee; background: #eef3f8; }
.hero-art { position: absolute; z-index: -1; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center; animation: hero-arrive .8s ease both; }
.hero-inner { display: flex; align-items: center; max-width: 1336px; min-height: 420px; margin: auto; padding: 40px 28px; }
.intro-inner { width: 44%; max-width: 540px; text-align: left; animation: intro-arrive .65s ease both; }
.storefront-kicker { color: #557367; font-size: 11px; font-weight: 650; }
h1 { margin: 10px 0 12px; font-size: 44px; line-height: 1.15; font-weight: 750; color: #183b2b; }
.storefront-tagline { margin: 0 0 8px; color: #304e40; font-size: 20px; font-weight: 550; line-height: 1.5; }
.storefront-description { color: #67786f; margin: 0; font-size: 13px; line-height: 1.7; }
.storefront-search { display: flex; align-items: center; gap: 12px; width: min(100%, 440px); height: 48px; margin: 24px 0 0; padding: 5px 6px 5px 17px; border: 1px solid #dce5df; border-radius: 8px; background: #fff; box-shadow: 0 4px 14px #223c2d05; transition: border-color .2s, box-shadow .2s; }
.storefront-search:focus-within { border-color: #24916a; box-shadow: 0 0 0 3px #17825c10; }
.storefront-search > .anticon { color: #8c9a91; font-size: 17px; }
.storefront-search input { min-width: 0; width: 100%; border: 0; outline: 0; color: #263f30; background: transparent; font-size: 14px; }
.storefront-search input::placeholder { color: #91a097; }
.storefront-search button { display: grid; width: 36px; height: 36px; flex: none; place-items: center; border: 0; border-radius: 5px; color: #fff; background: #17825c; font-size: 18px; cursor: pointer; transition: background .2s; }
.storefront-search button:hover { background: #126846; }
.hero-demo { display: inline-flex; align-items: center; gap: 8px; margin-top: 16px; color: #157753; font-size: 13px; font-weight: 600; }
.hero-demo .anticon { transform: rotate(45deg); transition: transform .2s; }
.hero-demo:hover .anticon { transform: translate(2px, -2px) rotate(45deg); }
button:focus-visible, a:focus-visible { outline: 2px solid #17825c; outline-offset: 3px; }
@keyframes intro-arrive { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@keyframes hero-arrive { from { opacity: .5; } to { opacity: 1; } }
@media (min-width: 1800px) { .hero-inner { max-width: 1516px; min-height: 480px; } }
@media (max-width: 1080px) {
  .hero-inner { min-height: 390px; padding-block: 32px; }
  .hero-art { object-position: 60% center; }
  .intro-inner { width: 44%; }
}
@media (max-width: 640px) {
  .storefront-intro { background: #f3f7fb; }
  .hero-inner { min-height: 0; padding: 24px 20px 210px; }
  .intro-inner { width: 100%; max-width: none; }
  .hero-art { inset: auto 0 0; width: 100%; height: 190px; object-fit: cover; object-position: right center; }
  h1 { font-size: 34px; margin: 8px 0 10px; }
  .storefront-tagline { font-size: 17px; }
  .storefront-description { font-size: 12px; max-width: 340px; }
  .storefront-search { margin-top: 18px; height: 46px; }
  .hero-demo { margin-top: 12px; }
}
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; }
}
</style>
