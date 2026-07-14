<template>
  <a-card>
    <template #title>
      <navigator-bar>404 NOT FOUND</navigator-bar>
    </template>
    <h1>{{ t('home.notFoundTitle') }}<router-link>{{ t('home.backHome') }}</router-link></h1>
  </a-card>
</template>

<script setup>
import { useI18n } from 'vue-i18n';

defineOptions({
  name: 'NotFound'
});

const { t } = useI18n();
</script>

<style scoped>
h1 {
  text-align: center;
  line-height: 300px;
}
</style>
