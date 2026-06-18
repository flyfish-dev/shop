<template>
  <a-card>
    <template #title>
      <navigator-bar>404 NOT FOUND</navigator-bar>
    </template>
    <h1>您访问的页面不存在！<router-link>返回首页</router-link></h1>
  </a-card>
</template>

<script>
export default {
  name: 'NotFound',
}
</script>

<style scoped>
h1 {
  text-align: center;
  line-height: 300px;
}
</style>
