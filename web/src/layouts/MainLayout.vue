<script setup>
import StorefrontHeader from './StorefrontHeader.vue';
import FooterBar from './FooterBar.vue';
</script>
<template>
  <div class="main-layout">
    <storefront-header />
    <main><router-view /></main>
    <footer-bar />
  </div>
</template>
<style scoped>
.main-layout { min-height: 100vh; background: #fafbfa; }
main { max-width: 1280px; min-height: 60vh; padding: 24px 16px; margin: auto; text-align: left; }
</style>
