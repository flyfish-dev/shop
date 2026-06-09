<script setup>
import { MdPreview } from 'md-editor-v3';
import 'md-editor-v3/lib/preview.css';

defineOptions({
  inheritAttrs: false,
});
</script>

<template>
  <MdPreview v-bind="$attrs" />
</template>
