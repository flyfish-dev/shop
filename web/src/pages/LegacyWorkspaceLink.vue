<script setup>
import { onMounted } from 'vue';
import { useRoute } from '@/router/use.js';
import { lowcodeLink } from '@/runtime/application.js';
const route = useRoute();
onMounted(() => window.location.replace(lowcodeLink(route.fullPath)));
</script>

<template><div class="workspace-redirect"><a-spin /></div></template>
<style scoped>.workspace-redirect { padding: 80px; text-align: center; }</style>
