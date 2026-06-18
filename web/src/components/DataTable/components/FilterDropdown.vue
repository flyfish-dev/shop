<template>
  <a-dropdown trigger="click" placement="bottomRight" :visible="visible" @visibleChange="handleVisibleChange">
    <filter-filled />
    <a-menu slot="overlay">
      <a-menu-item>
        <a href="javascript:;">1st menu item</a>
      </a-menu-item>
      <a-menu-item>
        <a href="javascript:;">2nd menu item</a>
      </a-menu-item>
      <a-menu-item>
        <a href="javascript:;">3rd menu item</a>
      </a-menu-item>
    </a-menu>
  </a-dropdown>
</template>

<script setup>
import { FilterFilled } from '@ant-design/icons-vue'
import { ref } from 'vue';
const visible = ref(false);
const handleVisibleChange = value => visible.value = value;
</script>

<style scoped>

</style>
