<script setup>
import { DeleteOutlined, LinkOutlined, PlusOutlined } from '@ant-design/icons-vue';
import {
  defaultDeliveryExperience,
  defaultDeliveryExperienceLink,
  DELIVERY_EXPERIENCE_KIND_OPTIONS
} from '../hooks/useDeliveryExperienceConfiguration.js';

const experience = defineModel('value', {
  type: Object,
  default: defaultDeliveryExperience
});

const addLink = () => {
  const links = [...(experience.value?.links || [])];
  links.push({
    ...defaultDeliveryExperienceLink(),
    primary: links.length === 0
  });
  experience.value = { ...defaultDeliveryExperience(), ...experience.value, links };
};

const removeLink = index => {
  const links = [...(experience.value?.links || [])];
  const removedPrimary = links[index]?.primary === true;
  links.splice(index, 1);
  if (removedPrimary && links.length) {
    links[0] = { ...links[0], primary: true };
  }
  experience.value = { ...experience.value, links };
};

const selectPrimary = index => {
  experience.value = {
    ...experience.value,
    links: (experience.value?.links || []).map((link, linkIndex) => ({
      ...link,
      primary: linkIndex === index
    }))
  };
};
</script>

<template>
  <section class="delivery-experience-editor">
    <div class="delivery-experience-toolbar">
      <div>
        <strong>交付后体验指引</strong>
        <span>在订单交付内容中提供仓库、Demo 或文档入口</span>
      </div>
      <a-switch
        v-model:checked="experience.enabled"
        :checked-value="true"
        :un-checked-value="false"
        checked-children="启用"
        un-checked-children="关闭"
      />
    </div>

    <template v-if="experience.enabled">
      <a-row :gutter="12">
        <a-col :xs="24" :md="12">
          <a-form-item label="中文标题">
            <a-input v-model:value="experience.title" :maxlength="128" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="英文标题">
            <a-input v-model:value="experience.titleEn" :maxlength="128" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="12">
        <a-col :xs="24" :md="12">
          <a-form-item label="中文说明">
            <a-textarea v-model:value="experience.description" :maxlength="512" :auto-size="{ minRows: 2, maxRows: 4 }" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="英文说明">
            <a-textarea v-model:value="experience.descriptionEn" :maxlength="512" :auto-size="{ minRows: 2, maxRows: 4 }" />
          </a-form-item>
        </a-col>
      </a-row>

      <div class="delivery-link-heading">
        <strong>体验入口</strong>
        <a-button size="small" type="primary" ghost :disabled="experience.links?.length >= 8" @click="addLink">
          <template #icon><plus-outlined /></template>
          添加入口
        </a-button>
      </div>

      <a-empty v-if="!experience.links?.length" class="delivery-link-empty" description="尚未配置体验入口" />
      <article
        v-for="(link, index) in experience.links"
        :key="`delivery-link-${index}`"
        class="delivery-link-editor"
      >
        <div class="delivery-link-editor__head">
          <div>
            <link-outlined />
            <strong>{{ link.title || link.titleEn || `入口 ${index + 1}` }}</strong>
          </div>
          <a-space>
            <a-radio :checked="link.primary" @change="selectPrimary(index)">首要入口</a-radio>
            <a-button size="small" danger title="删除入口" @click="removeLink(index)">
              <template #icon><delete-outlined /></template>
            </a-button>
          </a-space>
        </div>
        <a-row :gutter="12">
          <a-col :xs="24" :md="8">
            <a-form-item label="入口类型">
              <a-select v-model:value="link.kind" :options="DELIVERY_EXPERIENCE_KIND_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="16">
            <a-form-item label="HTTPS 地址" required>
              <a-input v-model:value="link.url" :maxlength="512" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :xs="24" :md="12">
            <a-form-item label="中文名称" required>
              <a-input v-model:value="link.title" :maxlength="128" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item label="英文名称">
              <a-input v-model:value="link.titleEn" :maxlength="128" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :xs="24" :md="12">
            <a-form-item label="中文说明">
              <a-input v-model:value="link.description" :maxlength="512" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="12">
            <a-form-item label="英文说明">
              <a-input v-model:value="link.descriptionEn" :maxlength="512" />
            </a-form-item>
          </a-col>
        </a-row>
      </article>
    </template>
  </section>
</template>

<style scoped lang="less">
.delivery-experience-editor {
  display: grid;
  gap: 16px;
}

.delivery-experience-toolbar,
.delivery-link-heading,
.delivery-link-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.delivery-experience-toolbar {
  padding: 14px 16px;
  border: 1px solid #d9e8dc;
  border-radius: 8px;
  background: #f8fcf8;

  > div {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong {
    color: #1f3a2b;
  }

  span {
    color: #6a7d70;
    font-size: 13px;
  }
}

.delivery-link-editor {
  padding: 14px;
  border: 1px solid #e3e8e4;
  border-radius: 8px;
  background: #fff;
}

.delivery-link-editor__head {
  margin-bottom: 12px;

  > div {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
}

.delivery-link-empty {
  margin: 4px 0;
  padding: 18px;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
}

:deep(.ant-form-item) {
  margin-bottom: 12px;
}

@media (max-width: 640px) {
  .delivery-experience-toolbar,
  .delivery-link-editor__head {
    align-items: flex-start;
  }

  .delivery-link-editor__head {
    flex-direction: column;
  }
}
</style>
