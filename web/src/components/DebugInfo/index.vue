<template>
  <h3 style="margin-top: 20px; cursor: pointer" @click="debug = !debug">
    <a v-if="debug"><up-circle-outlined /> 隐藏调试信息</a>
    <a v-else><down-circle-outlined /> 显示调试信息</a>
  </h3>
  <pre v-if="debug" style="font-size: 10px; padding: 18px; background-color: #e9ffdb; height: 500px; overflow: auto">{{result}}</pre>
</template>

<script>
import { ref, toRefs, computed } from 'vue';
import { DownCircleOutlined, UpCircleOutlined } from '@ant-design/icons-vue';

export default {
  name: 'DebugInfo',
  components: {
    DownCircleOutlined,
    UpCircleOutlined
  },
  props: {
    data: {
      type: [Object, Array, String]
    }
  },
  setup(props) {
    const { data } = toRefs(props);
    return {
      debug: ref(false),
      result: computed(() => JSON.stringify(data.value, null, '  ')),
    }
  },
}
</script>

<style scoped>

</style>
