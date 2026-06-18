<script setup>
import MarkdownPreview from '@/components/Markdown/MarkdownPreview.vue';

defineOptions({
  inheritAttrs: false,
});
</script>

<template>
  <MarkdownPreview v-bind="$attrs" />
</template>
