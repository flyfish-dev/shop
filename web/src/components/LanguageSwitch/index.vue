<script setup>
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { setLocale } from '@/i18n/index.js';

const { locale, t } = useI18n();

const options = computed(() => [
  {
    key: 'zh-CN',
    name: t('common.chinese'),
    shortName: '中'
  },
  {
    key: 'en-US',
    name: t('common.english'),
    shortName: 'EN'
  }
]);

const currentLocale = computed({
  get: () => locale.value,
  set: value => setLocale(value)
});

const switchLocale = key => {
  currentLocale.value = key;
};
</script>

<template>
  <div
    class="language-switch"
    role="group"
    :aria-label="t('common.languageSwitch')"
  >
    <button
      v-for="item in options"
      :key="item.key"
      class="language-option"
      :class="{ active: item.key === currentLocale }"
      type="button"
      :aria-pressed="item.key === currentLocale"
      :aria-label="item.name"
      @click="switchLocale(item.key)"
    >
      {{ item.shortName }}
    </button>
  </div>
</template>

<style scoped lang="less">
.language-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 30px;
  padding: 2px;
  border: 1px solid rgba(90, 113, 138, .14);
  border-radius: 999px;
  color: #57708c;
  background: rgba(255, 255, 255, .72);
  box-shadow: 0 8px 20px rgba(28, 73, 118, .06);
  line-height: 1;
  backdrop-filter: blur(14px);
  transition: border-color .18s ease, background .18s ease, box-shadow .18s ease, opacity .18s ease;

  &:hover,
  &:focus {
    border-color: rgba(22, 119, 255, .2);
    background: rgba(255, 255, 255, .9);
    box-shadow: 0 10px 24px rgba(28, 73, 118, .1);
  }
}

.language-option {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 24px;
  padding: 0 7px;
  border: 0;
  border-radius: 999px;
  color: #6c7f94;
  background: transparent;
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: color .18s ease, background .18s ease, box-shadow .18s ease;

  &:hover {
    color: #1677ff;
  }

  &.active {
    color: #173858;
    background: #fff;
    box-shadow: 0 5px 14px rgba(30, 72, 112, .12);
  }
}

@media only screen and (max-width: 640px) {
  .language-switch {
    height: 28px;
    padding: 2px;
  }

  .language-option {
    min-width: 28px;
    height: 22px;
    padding: 0 6px;
    font-size: 12px;
  }
}
</style>
