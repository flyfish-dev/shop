<script setup>
import TopBar from './TopBar.vue';
import { shopSiteUrl } from '@/runtime/application.js';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
</script>
<template>
  <div class="lowcode-site">
    <top-bar />
    <main class="lowcode-content"><router-view /></main>
    <footer><a :href="shopSiteUrl">{{ t('nav.market') }}</a><router-link href="/privacy">{{ t('footer.privacyPolicy') }}</router-link><router-link href="/terms">{{ t('footer.termsOfService') }}</router-link></footer>
  </div>
</template>
<style scoped>
.lowcode-site { min-height: 100vh; background: #f6f7f8; }
.lowcode-content { min-height: 50vh; padding: 20px 0; text-align: left; }
footer { display: flex; flex-wrap: wrap; justify-content: center; gap: 24px; padding: 32px 16px; border-top: 1px solid #e4e8e6; background: #fff; }
</style>
