<script setup>
import TopBar from '@/layouts/TopBar.vue';
import FooterBar from '@/layouts/FooterBar.vue';
import useClientStore from '@/modules/auth/store/client.js';
import { storeToRefs } from 'pinia';

const { collapsed } = storeToRefs(useClientStore());
</script>

<template>
  <div class='app-wrapper' :class='{ collapsed }'>
    <top-bar />
    <div class="main-page">
      <div class="module-page">
        <router-view />
      </div>
    </div>
    <footer-bar />
  </div>
</template>

<style>
.color-red {
  color: red;
}
</style>
<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped lang="less">
.app-wrapper {
  display: flex;
  flex-direction: column;
  background: #f6f6f6;
  min-height: 100vh;

  .main-page {
    flex: 1 0 auto;

    .module-page {
      text-align: left;
      margin: 20px 0;
    }
  }
}
</style>
