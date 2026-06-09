<script setup>
import { HomeFilled } from '@ant-design/icons-vue';
import RouterLink from '@/components/RouterLink/index.vue';
</script>

<template>
  <router-link href="/" class="shop-home-link" aria-label="返回首页">
    <HomeFilled />
    <span class="shop-home-link-text">首页</span>
  </router-link>
</template>

<style scoped lang="less">
.shop-home-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(43, 137, 95, .16);
  border-radius: 8px;
  color: #2f5f46;
  background: rgba(255, 255, 255, .74);
  box-shadow: 0 6px 18px rgba(38, 125, 74, .07);
  font-size: 14px;
  line-height: 34px;
  text-decoration: none;
  white-space: nowrap;
  transition: color .18s ease, border-color .18s ease, background .18s ease, box-shadow .18s ease;

  &:hover {
    color: #1f7a36;
    border-color: rgba(51, 162, 4, .34);
    background: #fff;
    box-shadow: 0 8px 22px rgba(38, 125, 74, .1);
  }

  :deep(.anticon) {
    font-size: 15px;
  }
}

@media only screen and (max-width: 640px) {
  .shop-home-link {
    width: 34px;
    padding: 0;

    .shop-home-link-text {
      display: none;
    }
  }
}
</style>
