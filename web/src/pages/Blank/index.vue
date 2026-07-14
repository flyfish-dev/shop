<template>
  <a-card>
    <template #title>
      <navigator-bar>{{ t('home.blankTitle') }}</navigator-bar>
    </template>
    <div class="content">{{ t('home.blankContent') }}</div>
  </a-card>
</template>

<script setup>
import { useI18n } from 'vue-i18n';

defineOptions({
  name: 'BlankContent'
});

const { t } = useI18n();
</script>

<style scoped>
.content {
  text-align: center;
}
</style>
