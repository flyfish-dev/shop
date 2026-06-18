<template>
  <router-link style="margin-right: 20px; float: left"><left-outlined /> 返回</router-link>
  <div style="margin: 0 auto; width: fit-content">
    <slot></slot>
  </div>
</template>

<script>
import { LeftOutlined } from '@ant-design/icons-vue';

export default {
  name: 'NavigatorBar',
  components: {
    LeftOutlined
  }
}
</script>

<style scoped>

</style>
