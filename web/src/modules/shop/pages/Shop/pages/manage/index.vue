<script setup>
import { onMounted, watch } from 'vue';
import { useRouter } from '@/router/use.js';

const router = useRouter();

const redirectManageRoot = () => {
  if (router.currentRoute.value.replace(/\/$/, '') === '/shop/manage') {
    router.replace('/shop/manage/workbench');
  }
};

onMounted(redirectManageRoot);
watch(() => router.currentRoute.value, redirectManageRoot);
</script>

<template>
  <div class="shop-manage-layout">
    <div class="manage-content">
      <router-view />
    </div>
  </div>
</template>

<style scoped lang="less">
.shop-manage-layout {
  min-height: calc(100vh - 200px);

  .manage-content {
    background: #fafafa;
    border-radius: 4px;
  }
}
</style>
