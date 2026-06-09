<script setup>
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
