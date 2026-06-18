import { computed, nextTick, ref, watch } from 'vue';
import { isGitRepositoryAccessType } from '@/modules/shop/utils/shopCovers.js';
import { isDigitalDownloadType } from '@/modules/shop/utils/shopDelivery.js';

const fieldKey = field => (Array.isArray(field) ? field.join('.') : String(field || ''));

const firstFieldKey = error => fieldKey(error?.name);

export function useShopItemModalSections(formData, licenseDeliveryEnabled) {
  const activeSection = ref('basic');

  const sections = computed(() => ([
    {
      key: 'basic',
      label: '基础信息',
      fields: ['name', 'groupId', 'type']
    },
    {
      key: 'sales',
      label: '销售优惠',
      fields: ['price', 'tags', 'defaultCouponEnabled', 'defaultCouponCode']
    },
    {
      key: 'delivery',
      label: '交付规则',
      fields: ['deliveryMode', 'deliveryActions']
    },
    {
      key: 'git',
      label: '代码仓库',
      fields: ['repositoryKeys'],
      visible: isGitRepositoryAccessType(formData.value.type)
    },
    {
      key: 'digital',
      label: '数字提货',
      fields: [
        ['digitalDelivery', 'title'],
        ['digitalDelivery', 'content'],
        ['digitalDelivery', 'attachments']
      ],
      visible: isDigitalDownloadType(formData.value.type)
    },
    {
      key: 'license',
      label: '授权许可',
      fields: [
        ['licenseDelivery', 'licenseName'],
        ['licenseDelivery', 'scope'],
        ['licenseDelivery', 'edition'],
        ['licenseDelivery', 'holder'],
        ['licenseDelivery', 'allowedOrigins'],
        ['licenseDelivery', 'features'],
        ['licenseDelivery', 'maxDeployments'],
        ['licenseDelivery', 'commercialUse'],
        ['licenseDelivery', 'validDays'],
        ['licenseDelivery', 'remark']
      ],
      visible: licenseDeliveryEnabled.value
    },
    {
      key: 'contract',
      label: '签署合同',
      fields: ['contractIds']
    },
    {
      key: 'display',
      label: '展示装修',
      fields: ['enabled', 'pinned', 'recommended', 'sort', 'highlightStyle', 'highlightIcon', 'fileList']
    },
    {
      key: 'content',
      label: '图文详情',
      fields: ['description']
    }
  ].filter(section => section.visible !== false)));

  const fieldSectionMap = computed(() => {
    const map = new Map();
    sections.value.forEach(section => {
      section.fields.forEach(field => {
        map.set(fieldKey(field), section.key);
      });
    });
    return map;
  });

  const activateSectionByField = async field => {
    const sectionKey = fieldSectionMap.value.get(fieldKey(field));
    if (sectionKey) {
      activeSection.value = sectionKey;
      await nextTick();
    }
    return sectionKey;
  };

  const focusValidationError = async (error, form) => {
    const firstError = error?.errorFields?.[0];
    const name = firstError?.name;
    await activateSectionByField(name);
    await nextTick();
    form?.scrollToField?.(name, { block: 'center', behavior: 'smooth' });
    return firstFieldKey(firstError);
  };

  watch(
    () => sections.value.map(section => section.key),
    keys => {
      if (!keys.includes(activeSection.value)) {
        activeSection.value = keys[0] || 'basic';
      }
    },
    { immediate: true }
  );

  return {
    activeSection,
    sections,
    focusValidationError
  };
}
