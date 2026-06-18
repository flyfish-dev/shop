<script setup>
import { MdEditor } from 'md-editor-v3';
import 'md-editor-v3/lib/style.css';

defineOptions({
  inheritAttrs: false,
});
</script>

<template>
  <MdEditor v-bind="$attrs" />
</template>
