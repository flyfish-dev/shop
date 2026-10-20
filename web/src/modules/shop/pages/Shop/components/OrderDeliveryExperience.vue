<script setup>
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  ArrowRightOutlined,
  CodeOutlined,
  CustomerServiceOutlined,
  DownloadOutlined,
  LinkOutlined,
  ReadOutlined,
  RocketOutlined
} from '@ant-design/icons-vue';

const props = defineProps({
  experience: {
    type: Object,
    default: null
  }
});

const { locale, t } = useI18n();
const english = computed(() => String(locale.value || '').toLowerCase().startsWith('en'));
const localizedText = (chinese, englishText) => english.value
  ? (englishText || chinese || '')
  : (chinese || englishText || '');

const title = computed(() => localizedText(props.experience?.title, props.experience?.titleEn));
const description = computed(() => localizedText(
  props.experience?.description,
  props.experience?.descriptionEn
));
const links = computed(() => (props.experience?.links || [])
  .map(link => ({
    ...link,
    displayTitle: localizedText(link.title, link.titleEn),
    displayDescription: localizedText(link.description, link.descriptionEn)
  }))
  .sort((left, right) => Number(right.primary) - Number(left.primary)));

const kindIcon = kind => ({
  REPOSITORY: CodeOutlined,
  DEMO: RocketOutlined,
  DOCUMENTATION: ReadOutlined,
  SUPPORT: CustomerServiceOutlined,
  DOWNLOAD: DownloadOutlined,
  LINK: LinkOutlined
})[kind] || LinkOutlined;
</script>

<template>
  <section v-if="links.length" class="delivery-experience">
    <header class="delivery-experience__head">
      <div class="delivery-experience__mark">
        <rocket-outlined />
      </div>
      <div>
        <h4>{{ title || t('orders.deliveryExperienceTitle') }}</h4>
        <p v-if="description">{{ description }}</p>
      </div>
    </header>

    <div class="delivery-experience__links">
      <article
        v-for="link in links"
        :key="`${link.kind}-${link.url}`"
        class="delivery-experience-link"
        :class="{ primary: link.primary }"
      >
        <div class="delivery-experience-link__icon">
          <component :is="kindIcon(link.kind)" />
        </div>
        <div class="delivery-experience-link__copy">
          <strong>{{ link.displayTitle }}</strong>
          <span v-if="link.displayDescription">{{ link.displayDescription }}</span>
        </div>
        <a-button
          :type="link.primary ? 'primary' : 'default'"
          :href="link.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ link.primary ? t('orders.openAndRun') : t('orders.openLink') }}
          <template #icon><arrow-right-outlined /></template>
        </a-button>
      </article>
    </div>
  </section>
</template>

<style scoped lang="less">
.delivery-experience {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  padding: 18px;
  border: 1px solid #cfe6d3;
  border-radius: 8px;
  background: #f7fcf8;
}

.delivery-experience__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;

  h4 {
    margin: 1px 0 4px;
    color: #1d3827;
    font-size: 17px;
    line-height: 1.35;
  }

  p {
    margin: 0;
    color: #617267;
    line-height: 1.65;
  }
}

.delivery-experience__mark,
.delivery-experience-link__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #e3f5e7;
  color: #24903e;
  font-size: 18px;
}

.delivery-experience__links {
  display: grid;
  gap: 10px;
}

.delivery-experience-link {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid #e0e9e2;
  border-radius: 8px;
  background: #fff;

  &.primary {
    border-color: #afd8b8;
    box-shadow: 0 8px 20px rgb(34 139 62 / 8%);
  }
}

.delivery-experience-link__copy {
  display: grid;
  gap: 3px;
  min-width: 0;

  strong {
    overflow-wrap: anywhere;
    color: #24372a;
  }

  span {
    color: #728078;
    font-size: 13px;
    line-height: 1.5;
  }
}

@media (max-width: 640px) {
  .delivery-experience {
    padding: 14px;
  }

  .delivery-experience-link {
    grid-template-columns: 34px minmax(0, 1fr);

    :deep(.ant-btn) {
      grid-column: 1 / -1;
      width: 100%;
    }
  }
}
</style>
