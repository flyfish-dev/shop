<template>
  <span>
    <span :key="i" v-for="(part, i) in parts">
      <span v-if="part === true" :style="{background: color}">
        {{keyword}}
      </span>
      <span v-else>
        {{part}}
      </span>
    </span>
  </span>
</template>

<script setup>
/**
 * 关键字高亮组件
 */
import { computed, toRefs } from 'vue';

const props = defineProps({
  keyword: {
    type: String,
    default: '',
  },
  content: {
    type: String,
    default: '',
  },
  color: {
    type: String,
    default: '#ffdb3d'
  }
})

const { keyword, content } = toRefs(props);
const parts = computed(() => {
  if (!keyword.value) {
    return [content.value];
  }
  const parts = content.value.split(keyword.value);
  const length = parts.length;
  const result = []
  for (let i = 0; i < length; i ++) {
    result.push(parts[i])
    if (i !== length - 1) {
      result.push(true)
    }
  }
  return result;
});
</script>

<style scoped>

</style>
