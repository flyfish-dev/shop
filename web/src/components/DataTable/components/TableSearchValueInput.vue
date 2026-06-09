<template>
  <a-date-picker
    v-if="computedType === 'date'"
    :ref="rawRef"
    v-bind="$attrs"
    @change='handleDateChange'
    :value="computedValue"
    format="YYYY-MM-DD HH:mm:ss"
    show-time
  />
  <a-input-number
    v-else-if="computedType === 'number'"
    :ref="rawRef"
    v-bind="$attrs"
    :value="value"
    @change="handleChange"
  />
  <a-select v-else-if="computedType === 'boolean'" :ref="rawRef" :value='value' @change='handleChange'>
    <a-select-option :value="1">是</a-select-option>
    <a-select-option :value="0">否</a-select-option>
  </a-select>
  <a-input
    v-else
    :ref="rawRef"
    v-bind="$attrs"
    @change="handleChange"
    :value="value"
  />
</template>

<script setup>
import { computed } from 'vue';
import dayjs from 'dayjs';

const props = defineProps({
  type: {
    type: String,
    description: '支持string, number, date',
  },
  opt: {
    type: String,
  },
  value: {
    type: null,
    description: '值',
  },
  rawRef: {
    type: null,
    description: '裸引用'
  },
})

const valueEvent = 'update:value';
const emit = defineEmits([valueEvent])

const handleChange = value => emit(valueEvent, value?.target ? value.target.value : value)

const handleDateChange = (date, value) => handleChange(value)

const computedValue = computed(() => {
  const { type, value } = props;
  if (type === 'date') {
    return value && dayjs(value) || undefined;
  }
  return value;
})

// 计算后的类型，对于某些特殊操作，需要文本输入框支撑
const computedType = computed(() => {
  const { type, opt } = props;
  if (['IN', 'NOT_IN', 'CUSTOM'].includes(props.opt)) {
    return 'string';
  }
  return type;
})
</script>

<style scoped>

</style>
