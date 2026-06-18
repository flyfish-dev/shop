<script setup>
import { computed, useSlots } from 'vue';

const props = defineProps({
  value: {
    type: String,
    default: ''
  },
  max: {
    type: Number,
    default: 10
  }
});

const getLength = (node, length = 0) => {
  const { children } = node;
  if (children) {
    if (Array.isArray(children)) {
      return children.reduce((res, node) => res + getLength(node), length);
    } else if (typeof children === 'string') {
      return children.length;
    }
  }
  return 0;
};

const slots = useSlots();

const display = computed(() => {
  return props.value.substring(0, props.max);
});
const length = computed(() => {
  return props.value.length || getLength({ children: slots.default() });
});
</script>

<template>
  <span v-if='length <= max'>
      <template v-if='value'>{{ value }}</template>
      <slot v-else></slot>
  </span>
  <a-tooltip v-else :title='value'>
    <span class='overflow'>
      <template v-if='value'>{{ value }}</template>
      <slot v-else></slot>
    </span>
  </a-tooltip>
</template>

<style scoped lang='less'>
.overflow {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
