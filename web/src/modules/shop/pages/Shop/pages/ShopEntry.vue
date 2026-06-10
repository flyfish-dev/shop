<script setup>
import { onMounted } from 'vue';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import { isShopMaintainer } from '@/modules/shop/authority.js';
import { useRouter } from '@/router/use.js';

const store = useClientStore();
const { user } = storeToRefs(store);
const router = useRouter();

onMounted(async () => {
  const currentUser = await store.loadUser();
  router.replace(isShopMaintainer(currentUser || user.value)
    ? '/shop/manage/workbench'
    : '/shop/item-list');
});
</script>

<template>
  <div class="shop-entry">
    <a-spin />
  </div>
</template>

<style scoped lang="less">
.shop-entry {
  display: grid;
  min-height: 360px;
  place-items: center;
}
</style>
