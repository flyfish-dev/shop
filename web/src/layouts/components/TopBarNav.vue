<script setup>
import { BookFilled, FileTextFilled, ShopFilled } from '@ant-design/icons-vue';
import { onMounted } from 'vue';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

defineProps({
  interactive: {
    type: Boolean,
    default: false
  }
});

const { hasShop, shopEntryName, shopEntryPath, loadPortalCapabilities } = usePortalCapabilities();

onMounted(() => {
  loadPortalCapabilities().catch(() => {});
});
</script>

<template>
  <nav class='nav-items' :class='{ interactive }' aria-label='常用入口'>
    <router-link v-if='hasShop' :href="shopEntryPath" class='nav-link shop-nav-link'>
      <ShopFilled />
      <span>{{ shopEntryName }}</span>
    </router-link>
    <a class='nav-link' href='https://blog.flyfish.dev' target='_blank' rel='noreferrer'>
      <BookFilled />
      <span>博客</span>
    </a>
    <a class='nav-link' href='https://viewer.flyfish.dev' target='_blank' rel='noreferrer'>
      <FileTextFilled />
      <span>文件预览</span>
    </a>
  </nav>
</template>

<style scoped lang='less'>
.nav-items {
  position: absolute;
  top: var(--nav-top);
  right: var(--nav-right);
  display: flex;
  justify-content: right;
  gap: 8px;
  opacity: var(--nav-opacity);
  pointer-events: none;
  transform: scale(var(--nav-scale));
  transform-origin: right center;

  &.interactive {
    pointer-events: auto;
  }
}

.nav-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(93, 125, 155, .14);
  border-radius: 999px;
  color: #365574;
  background: rgba(255, 255, 255, .82);
  box-shadow: 0 8px 24px rgba(28, 73, 118, .08);
  font-size: 14px;
  line-height: 34px;
  text-decoration: none;
  backdrop-filter: blur(10px);
  transition: color .18s ease, border-color .18s ease, background .18s ease, box-shadow .18s ease;

  &:hover {
    color: #1677ff;
    border-color: rgba(22, 119, 255, .28);
    background: #fff;
    box-shadow: 0 10px 26px rgba(22, 119, 255, .12);
  }

  :deep(.anticon) {
    font-size: 15px;
  }
}

.shop-nav-link {
  border-color: rgba(36, 128, 84, .22);
  color: #fff;
  background: linear-gradient(135deg, #15915b, #1677ff);
  box-shadow: 0 12px 28px rgba(21, 145, 91, .18);

  &:hover {
    color: #fff;
    border-color: rgba(36, 128, 84, .34);
    background: linear-gradient(135deg, #128452, #116de8);
  }
}

@media only screen and (max-width: 640px) {
  .nav-items {
    right: 0;
    left: 0;
    justify-content: center;
    gap: 6px;
  }

  .nav-link {
    height: 30px;
    padding: 0 9px;
    font-size: 13px;
    line-height: 30px;
  }
}
</style>
