<script setup>
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { getShopItemGroups, getShopItemDetail } from '../apis/api';
import { message, Upload } from 'ant-design-vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import { getContracts, getGitRepositoryOptions, uploadImage } from '../apis/manage';
import {
  getShopItemDefaultCover,
  isGitRepositoryAccessType,
  isGitRepositoryDonationAccessType
} from '@/modules/shop/utils/shopCovers.js';
import {
  DEFAULT_OFFICE_LICENSE_FEATURES,
  DELIVERY_ACTION,
  DELIVERY_ACTION_OPTIONS,
  OFFICE_LICENSE_KIND,
  OFFICE_LICENSE_KIND_OPTIONS,
  OFFICE_LICENSE_EDITION_OPTIONS,
  OFFICE_LICENSE_FEATURE_OPTIONS,
  SHOP_TYPE_OPTIONS,
  defaultDeliveryActionsForType,
  defaultDeliveryModeForType,
  deliveryModeOptionsForType,
  isDigitalDownloadType,
  isLicenseType,
  normalizeDeliveryActionsForType,
  normalizeDeliveryModeForType
} from '@/modules/shop/utils/shopDelivery.js';
import {
  GIT_REPOSITORY_PROVIDER_OPTIONS,
  defaultGitPermission,
  gitRepositoryValue,
  isGitRepositoryConfigured,
  normalizeGitRepositoryAccess,
  normalizeGitProvider,
  parseGitRepositoryAccessParams,
  toGitRepositoryAccessParams,
  toGitRepositoryKeys
} from '@/modules/shop/utils/gitRepositoryAccess.js';
import { useShopItemModalSections } from '../hooks/useShopItemModalSections.js';

const GIT_REPOSITORY_ACCESS = 'GIT_REPOSITORY_ACCESS';
const ShopMarkdownEditor = defineAsyncComponent(() => import('../components/ShopMarkdownEditor.vue'));
const highlightStyleOptions = [
  { label: '默认', value: '' },
  { label: '商业版', value: 'commercial' },
  { label: '企业版', value: 'enterprise' },
  { label: '热门推荐', value: 'hot' }
];
const highlightIconOptions = [
  { label: '默认', value: '' },
  { label: '皇冠', value: 'crown' },
  { label: '徽章', value: 'badge' },
  { label: '闪光', value: 'spark' },
  { label: '火热', value: 'fire' }
];

const props = defineProps({
  visible: Boolean,
  item: Object,
});

const emit = defineEmits(['update:visible', 'save']);

const form = ref();
const formData = ref({
  fileList: [],
  enabled: false,
  pinned: false,
  recommended: false,
  defaultCouponEnabled: false,
  defaultCouponCode: '',
  highlightStyle: '',
  highlightIcon: '',
  contractIds: [],
  sort: 0,
  type: GIT_REPOSITORY_ACCESS,
  deliveryMode: defaultDeliveryModeForType(GIT_REPOSITORY_ACCESS),
  deliveryActions: defaultDeliveryActionsForType(GIT_REPOSITORY_ACCESS),
  tags: [],
  repositoryAccess: {
    provider: 'github',
    repositories: []
  },
  digitalDelivery: {
    title: '',
    content: '',
    attachments: []
  },
	  licenseDelivery: {
	    licenseKind: OFFICE_LICENSE_KIND.RUNTIME,
	    licenseName: '',
	    scope: '',
	    product: 'license-product',
	    edition: 'commercial',
	    holder: '',
	    allowedOrigins: [],
	    features: [...DEFAULT_OFFICE_LICENSE_FEATURES],
	    maxDeployments: 1,
	    commercialUse: true,
	    validDays: null,
	    remark: ''
  }
});
const loading = ref(false);
const repositoryLoading = ref(false);
const repositoryOptions = ref([]);
const repositoryProvider = ref('github');

const groups = ref([]);
const contractOptions = ref([]);
const contractLoading = ref(false);

const itemTypes = SHOP_TYPE_OPTIONS;
const repositoryProviderOptions = GIT_REPOSITORY_PROVIDER_OPTIONS;

const deliveryModeOptions = computed(() => deliveryModeOptionsForType(formData.value.type));
const deliveryModeLocked = computed(() => deliveryModeOptions.value.length === 1);
const deliveryActionOptions = computed(() => {
  if (isGitRepositoryAccessType(formData.value.type)) {
    return DELIVERY_ACTION_OPTIONS.filter(option => [
      DELIVERY_ACTION.GIT_REPOSITORY_ACCESS,
      DELIVERY_ACTION.LICENSE
    ].includes(option.value));
  }
  if (isLicenseType(formData.value.type)) {
    return DELIVERY_ACTION_OPTIONS.filter(option => option.value === DELIVERY_ACTION.LICENSE);
  }
  if (isDigitalDownloadType(formData.value.type)) {
    return DELIVERY_ACTION_OPTIONS.filter(option => option.value === DELIVERY_ACTION.DIGITAL_DOWNLOAD);
  }
  return [];
});
const licenseDeliveryEnabled = computed(() => (formData.value.deliveryActions || []).includes(DELIVERY_ACTION.LICENSE));
const licenseKindOptions = OFFICE_LICENSE_KIND_OPTIONS;
const licenseEditionOptions = OFFICE_LICENSE_EDITION_OPTIONS;
const licenseFeatureOptions = OFFICE_LICENSE_FEATURE_OPTIONS;
const priceLabel = computed(() => isGitRepositoryDonationAccessType(formData.value.type) ? '最低打赏金额' : '商品价格');
const formRules = {
  name: [{ required: true, message: '请输入商品名称' }],
  price: [{ required: true, message: '请输入商品价格' }],
  groupId: [{ required: true, message: '请选择商品分组' }],
  description: [{ required: true, message: '请输入商品描述' }]
};
const {
  activeSection,
  sections,
  focusValidationError
} = useShopItemModalSections(formData, licenseDeliveryEnabled);
const activeSectionTitle = computed(() => (
  sections.value.find(section => section.key === activeSection.value)?.label || '商品配置'
));

const defaults = () => ({
  fileList: [],
  enabled: false,
  pinned: false,
  recommended: false,
  defaultCouponEnabled: false,
  defaultCouponCode: '',
  highlightStyle: '',
  highlightIcon: '',
  contractIds: [],
  sort: 0,
  type: GIT_REPOSITORY_ACCESS,
  deliveryMode: defaultDeliveryModeForType(GIT_REPOSITORY_ACCESS),
  deliveryActions: defaultDeliveryActionsForType(GIT_REPOSITORY_ACCESS),
  tags: [],
  repositoryAccess: {
    provider: 'github',
    repositories: []
  },
  digitalDelivery: {
    title: '',
    content: '',
    attachments: []
  },
	  licenseDelivery: {
	    licenseKind: OFFICE_LICENSE_KIND.RUNTIME,
	    licenseName: '',
	    scope: '',
	    product: 'license-product',
	    edition: 'commercial',
	    holder: '',
	    allowedOrigins: [],
	    features: [...DEFAULT_OFFICE_LICENSE_FEATURES],
	    maxDeployments: 1,
	    commercialUse: true,
	    validDays: null,
	    remark: ''
  }
});

const parseParams = params => {
  if (!params) {
    return {};
  }
  try {
    const parsed = typeof params === 'string' ? JSON.parse(params) : params;
    return typeof parsed === 'string' ? JSON.parse(parsed) : (parsed || {});
  } catch (e) {
    return {};
  }
};

const parseDigitalDeliveryParams = params => ({
  title: '',
  content: '',
  attachments: [],
  ...parseParams(params)
});

const parseLicenseDeliveryParams = params => ({
  licenseKind: OFFICE_LICENSE_KIND.RUNTIME,
  licenseName: '',
  scope: '',
  product: 'license-product',
  edition: 'commercial',
  holder: '',
  allowedOrigins: [],
  features: [...DEFAULT_OFFICE_LICENSE_FEATURES],
  maxDeployments: 1,
  commercialUse: true,
  validDays: null,
  remark: '',
  ...parseParams(params)
});

const toDigitalDeliveryParams = value => ({
  title: value?.title || '数字商品提货内容',
  content: value?.content || '',
  attachments: value?.attachments || []
});

const toLicenseDeliveryParams = value => ({
  licenseKind: OFFICE_LICENSE_KIND.RUNTIME,
  licenseName: value?.licenseName || formData.value.name || '飞鱼小铺授权许可',
  scope: value?.scope || `product:${formData.value.name || 'flyfish'}`,
  product: value?.product || 'license-product',
  edition: value?.edition || 'commercial',
  holder: value?.holder || value?.licenseName || formData.value.name || '授权用户',
  allowedOrigins: Array.isArray(value?.allowedOrigins)
    ? value.allowedOrigins.map(origin => String(origin || '').trim()).filter(Boolean)
    : [],
  features: Array.isArray(value?.features) && value.features.length
    ? value.features
    : [...DEFAULT_OFFICE_LICENSE_FEATURES],
  maxDeployments: Number(value?.maxDeployments || 1),
  commercialUse: value?.edition === 'personal' ? false : value?.commercialUse !== false,
  validDays: value?.validDays || null,
  remark: value?.remark || ''
});

const parseDeliveryActions = (type, detail, parsedParams) => normalizeDeliveryActionsForType(
  type,
  detail?.deliveryActions || parsedParams?.deliveryActions
);

const toAccessRepository = repo => {
  const provider = normalizeGitProvider(repo?.provider, repositoryProvider.value);
  const owner = repo?.owner || '';
  const repoName = repo?.repo || '';
  return {
    repositoryId: Number(repo?.id || repo?.repositoryId || 0) || null,
    provider,
    owner,
    repo: repoName,
    name: repo?.name || repo?.fullName || (owner && repoName ? `${owner}/${repoName}` : ''),
    permission: repo?.permission || defaultGitPermission(provider)
  };
};

const repositoryFullName = repository => (
  repository.owner && repository.repo ? `${repository.owner}/${repository.repo}` : ''
);

const repositoryDisplayName = repository => (
  repositoryFullName(repository) || repository.name || (repository.repositoryId ? `仓库#${repository.repositoryId}` : '')
);

const toRepositoryOption = repo => {
  const accessRepository = toAccessRepository(repo);
  const value = gitRepositoryValue(accessRepository);
  const fullName = repositoryFullName(accessRepository);
  return {
    value,
    label: `${repo.providerName || accessRepository.provider || 'Git'} · ${repo.name || fullName || value}${repo.description ? ` · ${repo.description}` : ''}`,
    repo: {
      ...repo,
      ...accessRepository,
      id: accessRepository.repositoryId,
      fullName: repo.fullName || fullName
    }
  };
};

const selectedRepositories = computed(() => normalizeGitRepositoryAccess(formData.value.repositoryAccess).repositories);
const activeProviderRepositories = computed(() => selectedRepositories.value
  .filter(repository => normalizeGitProvider(repository.provider) === repositoryProvider.value));
const currentRepositoryOptions = computed(() => repositoryOptions.value
  .filter(option => normalizeGitProvider(option.repo?.provider) === repositoryProvider.value));

const toRepositoryFromValue = value => {
  const selected = repositoryOptions.value.find(option => option.value === value)?.repo;
  if (selected) {
    return toAccessRepository(selected);
  }
  const rawValue = String(value || '');
  if (rawValue.startsWith('id:')) {
    return {
      repositoryId: Number(rawValue.slice(3)) || null,
      provider: repositoryProvider.value,
      permission: defaultGitPermission(repositoryProvider.value)
    };
  }
  const provider = rawValue.includes(':')
    ? normalizeGitProvider(rawValue.split(':')[0], repositoryProvider.value)
    : repositoryProvider.value;
  const fullName = rawValue.includes(':') ? rawValue.split(':').slice(1).join(':') : rawValue;
  const [owner, ...repoParts] = fullName.split('/');
  return {
    provider,
    owner,
    repo: repoParts.join('/'),
    permission: defaultGitPermission(provider)
  };
};

const repositoryKeys = computed({
  get() {
    return toGitRepositoryKeys({ repositories: activeProviderRepositories.value });
  },
  set(values) {
    const activeProvider = repositoryProvider.value;
    const preservedRepositories = selectedRepositories.value
      .filter(repository => normalizeGitProvider(repository.provider) !== activeProvider);
    formData.value.repositoryAccess = normalizeGitRepositoryAccess({
      provider: activeProvider,
      repositories: [
        ...preservedRepositories,
        ...(values || []).map(toRepositoryFromValue)
      ]
    });
  }
});

const repositoryAccessPreview = computed(() => {
  return selectedRepositories.value;
});

const validateRepositoryAccess = async () => {
  if (!isGitRepositoryConfigured(formData.value.repositoryAccess)) {
    return Promise.reject(new Error('请选择 Git 仓库'));
  }
  return Promise.resolve();
};

const validateDefaultCouponCode = async () => {
  if (formData.value.defaultCouponEnabled && !String(formData.value.defaultCouponCode || '').trim()) {
    return Promise.reject(new Error('请输入默认优惠券编码'));
  }
  return Promise.resolve();
};

const mergeSelectedRepositoryDetails = records => {
  const optionByValue = new Map((records || []).map(repo => {
    const option = toRepositoryOption(repo);
    return [option.value, option.repo];
  }));
  const nextRepositories = selectedRepositories.value.map(repository => {
    const matched = optionByValue.get(gitRepositoryValue(repository));
    return matched ? toAccessRepository(matched) : repository;
  });
  if (!nextRepositories.length) {
    return;
  }
  formData.value.repositoryAccess = normalizeGitRepositoryAccess({
    provider: repositoryProvider.value,
    repositories: nextRepositories
  });
  const firstProvider = nextRepositories.map(repository => normalizeGitProvider(repository.provider)).find(Boolean);
  if (firstProvider) {
    repositoryProvider.value = firstProvider;
  }
};

const loadRepositoryOptions = async (keyword = '', options = {}) => {
  if (!isGitRepositoryAccessType(formData.value.type)) {
    return;
  }
  repositoryLoading.value = true;
  try {
    const records = await getGitRepositoryOptions({
      provider: options.includeAllProviders ? undefined : repositoryProvider.value,
      keyword: keyword || undefined
    });
    mergeSelectedRepositoryDetails(records);
    const selectedOptions = selectedRepositories.value.map(repository => toRepositoryOption({
      id: repository.repositoryId,
      provider: repository.provider,
      providerName: repository.provider,
      owner: repository.owner,
      repo: repository.repo,
      fullName: repository.owner && repository.repo ? `${repository.owner}/${repository.repo}` : repository.name,
      name: repository.name,
      permission: repository.permission,
      privateRepo: false
    }));
    const loadedOptions = (records || []).map(toRepositoryOption);
    const optionMap = new Map([...selectedOptions, ...loadedOptions].map(option => [option.value, option]));
    repositoryOptions.value = Array.from(optionMap.values());
  } catch (e) {
    repositoryOptions.value = [];
    message.error(e.message || '仓库加载失败，请先在仓库管理中维护');
  } finally {
    repositoryLoading.value = false;
  }
};

const handleRepositoryProviderChange = provider => {
  repositoryProvider.value = normalizeGitProvider(provider);
  loadRepositoryOptions();
};

const removeRepository = repository => {
  const removeKey = gitRepositoryValue(repository);
  formData.value.repositoryAccess = normalizeGitRepositoryAccess({
    provider: repositoryProvider.value,
    repositories: selectedRepositories.value.filter(item => gitRepositoryValue(item) !== removeKey)
  });
};

watch(() => formData.value.type, type => {
  formData.value.deliveryMode = normalizeDeliveryModeForType(type, formData.value.deliveryMode);
  formData.value.deliveryActions = normalizeDeliveryActionsForType(type, formData.value.deliveryActions);
  if (isGitRepositoryAccessType(type)) {
    loadRepositoryOptions();
  }
});

watch(() => formData.value.licenseDelivery.edition, edition => {
  if (edition === 'personal') {
    formData.value.licenseDelivery.commercialUse = false;
    formData.value.licenseDelivery.maxDeployments = 1;
  } else if (edition === 'commercial') {
    formData.value.licenseDelivery.commercialUse = true;
    formData.value.licenseDelivery.maxDeployments = 1;
  } else if (edition === 'enterprise') {
    formData.value.licenseDelivery.commercialUse = true;
    if (!formData.value.licenseDelivery.maxDeployments || formData.value.licenseDelivery.maxDeployments < 1) {
      formData.value.licenseDelivery.maxDeployments = 1;
    }
  }
});

const loadGroups = async () => {
  groups.value = await getShopItemGroups();
};

const loadContracts = async () => {
  contractLoading.value = true;
  try {
    const records = await getContracts({ enabledOnly: true });
    contractOptions.value = (records || []).map(contract => ({
      label: `${contract.name}${contract.typeName ? ` · ${contract.typeName}` : ''}`,
      value: contract.id
    }));
  } finally {
    contractLoading.value = false;
  }
};

// 加载商品详情
const loadItemDetail = async (id) => {
  loading.value = true;
  try {
    const detail = await getShopItemDetail(id);
    const parsedParams = parseParams(detail.params);
    const nestedLicenseParams = parsedParams.licenseDelivery || parsedParams;
    formData.value = {
      ...detail,
      type: isGitRepositoryAccessType(detail.type) ? GIT_REPOSITORY_ACCESS : detail.type,
      deliveryMode: normalizeDeliveryModeForType(detail.type, detail.deliveryMode),
      deliveryActions: parseDeliveryActions(detail.type, detail, parsedParams),
      enabled: detail.enabled !== false,
      pinned: detail.pinned === true,
      recommended: detail.recommended === true,
      defaultCouponEnabled: detail.defaultCouponEnabled === true,
      defaultCouponCode: detail.defaultCouponCode || '',
      highlightStyle: detail.highlightStyle || '',
      highlightIcon: detail.highlightIcon || '',
      contractIds: detail.contractIds || [],
      sort: detail.sort || 0,
      repositoryAccess: parseGitRepositoryAccessParams(detail.params),
      digitalDelivery: parseDigitalDeliveryParams(detail.params),
      licenseDelivery: parseLicenseDeliveryParams(nestedLicenseParams),
      fileList: detail?.images ? detail.images.map((url, index) => ({
        uid: `-${index}`,
        name: `image-${index}`,
        status: 'done',
        url,
        thumbUrl: url,
      })) : []
    };
    repositoryProvider.value = selectedRepositories.value[0]?.provider || formData.value.repositoryAccess.provider || 'github';
    repositoryOptions.value = selectedRepositories.value.map(repository => toRepositoryOption({
      id: repository.repositoryId,
      provider: repository.provider,
      providerName: repository.provider,
      owner: repository.owner,
        repo: repository.repo,
        fullName: repository.owner && repository.repo ? `${repository.owner}/${repository.repo}` : repository.name,
      name: repository.name,
      permission: repository.permission,
      privateRepo: false
    }));
    await loadRepositoryOptions('', { includeAllProviders: true });
  } catch (e) {
    message.error('获取商品详情失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadGroups();
  loadContracts();
});

// 监听弹窗显示和编辑项变化
watch([() => props.visible, () => props.item], async ([visible, item]) => {
  if (visible) {
    if (item?.id) {
      // 编辑模式，加载详情
      await loadItemDetail(item.id);
    } else {
      // 新增模式，重置表单
      formData.value = defaults();
      repositoryProvider.value = 'github';
    }
  }
}, { immediate: true });

const handleOk = async () => {
  try {
    await form.value.validate();
    const {
      fileList = [],
      repositoryAccess: repositoryAccessParams,
      digitalDelivery,
      licenseDelivery,
      deliveryActions,
      ...rest
    } = formData.value;

    // 处理图片数据
    const images = fileList
      .filter(file => file?.status === 'done')
      .map(file => file.url)
      .filter(Boolean);

    const deliveryMode = normalizeDeliveryModeForType(rest.type, rest.deliveryMode);
    const normalizedActions = normalizeDeliveryActionsForType(rest.type, deliveryActions);
    const repositoryAccess = toGitRepositoryAccessParams({
      ...repositoryAccessParams,
      provider: repositoryProvider.value
    });
    const params = (() => {
      if (isGitRepositoryAccessType(rest.type)) {
        return JSON.stringify({
          ...repositoryAccess,
          deliveryActions: normalizedActions,
          ...(normalizedActions.includes(DELIVERY_ACTION.LICENSE)
            ? { licenseDelivery: toLicenseDeliveryParams(licenseDelivery) }
            : {})
        });
      }
      if (isDigitalDownloadType(rest.type)) {
        return JSON.stringify(toDigitalDeliveryParams(digitalDelivery));
      }
      if (isLicenseType(rest.type)) {
        return JSON.stringify({
          ...toLicenseDeliveryParams(licenseDelivery),
          deliveryActions: normalizedActions
        });
      }
      return null;
    })();

    emit('save', {
      ...rest,
      deliveryMode,
      deliveryActions: normalizedActions,
      params,
      tags: rest.tags || [],
      sort: rest.sort || 0,
      enabled: rest.enabled === true,
      pinned: rest.pinned === true,
      recommended: rest.recommended === true,
      defaultCouponEnabled: rest.defaultCouponEnabled === true,
      defaultCouponCode: rest.defaultCouponEnabled
        ? String(rest.defaultCouponCode || '').trim().toUpperCase() || null
        : null,
      highlightStyle: rest.highlightStyle || null,
      highlightIcon: rest.highlightIcon || null,
      contractIds: rest.contractIds || [],
      images,
      cover: images[0] || null
    });
  } catch (error) {
    const field = await focusValidationError(error, form.value);
    if (field) {
      message.warning('请先完善当前分组中的必填内容');
    }
  }
};

const handleCancel = () => {
  emit('update:visible', false);
};

const beforeUpload = (file) => {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    message.error('只能上传图片文件！');
  }
  const isLt2M = file.size / 1024 / 1024 < 2;
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB!');
  }
  return isImage && isLt2M || Upload.LIST_IGNORE;
};

const handleMarkdownImageUpload = async (files, callback) => {
  try {
    const images = await Promise.all(files.map(async file => {
      const formData = new FormData();
      formData.append('file', file);
      const url = await uploadImage(formData);
      return {
        url,
        alt: file.name,
        title: file.name
      };
    }));
    callback(images);
  } catch (error) {
    message.error('图片上传失败');
  }
};

const handlePreview = async (file) => {
  if (!file.url && !file.preview) {
    file.preview = await new Promise(resolve => {
      const reader = new FileReader();
      reader.readAsDataURL(file.originFileObj);
      reader.onload = () => resolve(reader.result);
    });
  }
  window.open(file.url || file.preview);
};

const handleUpload = async ({ file, onSuccess, onError }) => {
  try {
    const formData = new FormData();
    formData.append('file', file);
    const data = await uploadImage(formData);
    onSuccess({ url: data });
  } catch (error) {
    message.error('上传失败');
    onError(error);
  }
};

const handleChange = ({ file, fileList }) => {
  // 更新文件状态
  const newFileList = fileList.map(f => {
    if (f.uid === file.uid && file.status === 'done' && file.response) {
      // 上传完成，设置 url
      return {
        ...f,
        url: file.response.url,
        thumbUrl: file.response.url,
      };
    }
    return f;
  });
  formData.value.fileList = newFileList;
};
</script>

<template>
  <a-modal
    :open="visible"
    :title="item ? '编辑商品' : '新增商品'"
    :confirmLoading="loading"
    @ok="handleOk"
    @cancel="handleCancel"
    width="1080px"
    class="shop-item-modal"
    wrap-class-name="shop-item-modal-wrap"
  >
    <a-form
      ref="form"
      :model="formData"
      :rules="formRules"
      class="shop-item-form"
      layout="vertical"
    >
      <div class="shop-item-editor">
        <aside class="shop-item-section-tabs">
          <button
            v-for="section in sections"
            :key="section.key"
            type="button"
            class="section-tab"
            :class="{ active: activeSection === section.key }"
            @click="activeSection = section.key"
          >
            {{ section.label }}
          </button>
        </aside>

        <div class="shop-item-section-panel">
          <div class="section-heading">
            <h3>{{ activeSectionTitle }}</h3>
          </div>

      <a-row v-show="activeSection === 'basic'" :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="商品名称" name="name">
            <a-input v-model:value="formData.name" />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="商品分组" name="groupId">
            <a-select v-model:value="formData.groupId">
              <a-select-option
                v-for="group in groups"
                :key="group.id"
                :value="group.id"
              >
                {{ group.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
      </a-row>

      <a-row v-show="activeSection === 'sales'" :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item :label="priceLabel" name="price">
            <a-input-number
              v-model:value="formData.price"
              :min="0"
              :precision="2"
              :step="0.01"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-form-item label="商品标签" name="tags">
            <a-select
              v-model:value="formData.tags"
              mode="tags"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row v-show="['basic', 'delivery'].includes(activeSection)" :gutter="16">
        <a-col v-show="activeSection === 'basic'" :xs="24" :md="12">
          <a-form-item label="商品类型" name="type" :rules="[{ required: true, message: '请选择商品类型' }]">
            <a-select
              v-model:value="formData.type"
            >
              <a-select-option
                v-for="type in itemTypes"
                :key="type.value"
                :value="type.value"
              >
                {{ type.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col v-show="activeSection === 'delivery'" :xs="24" :md="12">
          <a-form-item label="交付方式" name="deliveryMode" :rules="[{ required: true, message: '请选择交付方式' }]">
            <a-select
              v-model:value="formData.deliveryMode"
              :options="deliveryModeOptions"
              :disabled="deliveryModeLocked"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item
        v-if="deliveryActionOptions.length"
        v-show="activeSection === 'delivery'"
        label="自动交付动作"
        name="deliveryActions"
      >
        <a-checkbox-group
          v-model:value="formData.deliveryActions"
          :options="deliveryActionOptions"
        />
      </a-form-item>

      <a-row v-show="activeSection === 'display'" :gutter="16">
        <a-col :xs="24" :sm="8">
          <a-form-item label="上架状态" name="enabled">
            <a-switch
              v-model:checked="formData.enabled"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="上架"
              un-checked-children="下架"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="8">
          <a-form-item label="首页置顶" name="pinned">
            <a-switch
              v-model:checked="formData.pinned"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="是"
              un-checked-children="否"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="8">
          <a-form-item label="推荐商品" name="recommended">
            <a-switch
              v-model:checked="formData.recommended"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="是"
              un-checked-children="否"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row v-show="['display', 'contract'].includes(activeSection)" :gutter="16">
        <a-col v-show="activeSection === 'display'" :xs="24" :sm="8">
          <a-form-item label="醒目样式" name="highlightStyle">
            <a-select
              v-model:value="formData.highlightStyle"
              :options="highlightStyleOptions"
            />
          </a-form-item>
        </a-col>
        <a-col v-show="activeSection === 'display'" :xs="24" :sm="8">
          <a-form-item label="小图标特效" name="highlightIcon">
            <a-select
              v-model:value="formData.highlightIcon"
              :options="highlightIconOptions"
            />
          </a-form-item>
        </a-col>
        <a-col v-show="activeSection === 'contract'" :xs="24" :sm="24">
          <a-form-item label="购买前合同" name="contractIds">
            <a-select
              v-model:value="formData.contractIds"
              mode="multiple"
              allow-clear
              :max-tag-count="'responsive'"
              :loading="contractLoading"
              :options="contractOptions"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row v-show="activeSection === 'sales'" :gutter="16">
        <a-col :xs="24" :sm="8">
          <a-form-item label="默认优惠" name="defaultCouponEnabled">
            <a-switch
              v-model:checked="formData.defaultCouponEnabled"
              :checkedValue="true"
              :unCheckedValue="false"
              checked-children="启用"
              un-checked-children="关闭"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="16">
          <a-form-item
            label="默认优惠券编码"
            name="defaultCouponCode"
            :rules="[{ validator: validateDefaultCouponCode }]"
          >
            <a-input
              v-model:value="formData.defaultCouponCode"
              :disabled="!formData.defaultCouponEnabled"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row v-if="isGitRepositoryAccessType(formData.type)" v-show="activeSection === 'git'" :gutter="16">
        <a-col :xs="24" :sm="8">
          <a-form-item label="仓库平台" required>
            <a-select
              v-model:value="repositoryProvider"
              :options="repositoryProviderOptions"
              @change="handleRepositoryProviderChange"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :sm="16">
          <a-form-item
            label="代码仓库"
            name="repositoryKeys"
            :rules="[{ required: true, validator: validateRepositoryAccess }]"
          >
            <a-select
              v-model:value="repositoryKeys"
              mode="multiple"
              show-search
              allow-clear
              :max-tag-count="'responsive'"
              :filter-option="false"
              :loading="repositoryLoading"
              :options="currentRepositoryOptions"
              @search="loadRepositoryOptions"
              @dropdown-visible-change="open => open && loadRepositoryOptions()"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <div v-if="isGitRepositoryAccessType(formData.type) && repositoryAccessPreview.length" v-show="activeSection === 'git'" class="repo-preview">
        <a-tag
          v-for="repository in repositoryAccessPreview"
          :key="repository.repositoryId || `${repository.owner}/${repository.repo}`"
          color="green"
          closable
          @close="event => { event.preventDefault(); removeRepository(repository); }"
        >
          {{ repository.provider || 'git' }} · {{ repositoryDisplayName(repository) }}
        </a-tag>
      </div>

      <section v-if="isDigitalDownloadType(formData.type)" v-show="activeSection === 'digital'" class="delivery-config">
        <a-form-item label="提货标题" :name="['digitalDelivery', 'title']">
          <a-input v-model:value="formData.digitalDelivery.title" />
        </a-form-item>
        <a-form-item label="提货内容" :name="['digitalDelivery', 'content']">
          <a-textarea
            v-model:value="formData.digitalDelivery.content"
            :maxlength="4096"
            :auto-size="{ minRows: 4, maxRows: 8 }"
          />
        </a-form-item>
        <a-form-item label="提货附件" :name="['digitalDelivery', 'attachments']">
          <attachment-upload
            v-model:value="formData.digitalDelivery.attachments"
            :max-count="8"
          />
        </a-form-item>
      </section>

      <section v-if="licenseDeliveryEnabled" v-show="activeSection === 'license'" class="delivery-config">
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权类型" :name="['licenseDelivery', 'licenseKind']">
              <a-select
                v-model:value="formData.licenseDelivery.licenseKind"
                :options="licenseKindOptions"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权名称" :name="['licenseDelivery', 'licenseName']">
              <a-input v-model:value="formData.licenseDelivery.licenseName" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权范围" :name="['licenseDelivery', 'scope']">
              <a-input v-model:value="formData.licenseDelivery.scope" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权套餐" :name="['licenseDelivery', 'edition']">
              <a-select
                v-model:value="formData.licenseDelivery.edition"
                :options="licenseEditionOptions"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12">
            <a-form-item label="持有人" :name="['licenseDelivery', 'holder']">
              <a-input v-model:value="formData.licenseDelivery.holder" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item
          label="授权域名"
          :name="['licenseDelivery', 'allowedOrigins']"
          required
        >
          <a-select
            v-model:value="formData.licenseDelivery.allowedOrigins"
            mode="tags"
            placeholder="例如 https://demo.example.com"
            :token-separators="[',', '，', '\\n', ' ']"
          />
        </a-form-item>
        <a-form-item label="授权功能" :name="['licenseDelivery', 'features']">
          <a-checkbox-group
            v-model:value="formData.licenseDelivery.features"
            :options="licenseFeatureOptions"
          />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :xs="24" :sm="8">
            <a-form-item label="最大部署数" :name="['licenseDelivery', 'maxDeployments']">
              <a-input-number
                v-model:value="formData.licenseDelivery.maxDeployments"
                :min="1"
                :disabled="formData.licenseDelivery.edition !== 'enterprise'"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="8">
            <a-form-item label="商业使用" :name="['licenseDelivery', 'commercialUse']">
              <a-switch
                v-model:checked="formData.licenseDelivery.commercialUse"
                :checkedValue="true"
                :unCheckedValue="false"
                :disabled="formData.licenseDelivery.edition === 'personal'"
                checked-children="允许"
                un-checked-children="禁止"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="8">
            <a-form-item label="有效天数" :name="['licenseDelivery', 'validDays']">
              <a-input-number
                v-model:value="formData.licenseDelivery.validDays"
                :min="1"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="授权说明" :name="['licenseDelivery', 'remark']">
          <a-input v-model:value="formData.licenseDelivery.remark" />
        </a-form-item>
      </section>

      <a-row v-show="activeSection === 'display'" :gutter="16">
        <a-col :xs="24" :md="12">
          <a-form-item label="排序" name="sort">
            <a-input-number v-model:value="formData.sort" :min="0" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item v-show="activeSection === 'display'" label="商品图片" name="fileList">
        <div class="image-field">
          <a-upload
            :file-list="formData.fileList"
            list-type="picture-card"
            :before-upload="beforeUpload"
            :customRequest="handleUpload"
            @preview="handlePreview"
            @change="handleChange"
            accept="image/*"
          >
            <div v-if="formData?.fileList?.length < 8">
              <plus-outlined />
              <div style="margin-top: 8px">上传图片</div>
            </div>
          </a-upload>
          <div v-if="!formData?.fileList?.length" class="default-cover-preview">
            <img :src="getShopItemDefaultCover(formData.type)" alt="" />
          </div>
        </div>
      </a-form-item>

      <a-form-item v-show="activeSection === 'content'" label="商品描述" name="description">
        <ShopMarkdownEditor
          v-if="visible && activeSection === 'content'"
          v-model="formData.description"
          class="markdown-editor"
          language="zh-CN"
          preview-theme="default"
          code-theme="github"
          :style="{ height: '520px' }"
          :on-upload-img="handleMarkdownImageUpload"
        />
      </a-form-item>
        </div>
      </div>
    </a-form>
  </a-modal>
</template>

<style scoped lang="less">
:deep(.ant-upload-list-picture-card) {
  .ant-upload-list-item {
    padding: 0;
  }
}

:deep(.ant-form-item) {
  margin-bottom: 16px;
}

.shop-item-form {
  min-width: 0;
}

.shop-item-editor {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr);
  min-height: 620px;
  overflow: hidden;
  border: 1px solid #eef2f6;
  border-radius: 8px;
  background: #fff;
}

.shop-item-section-tabs {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 10px;
  border-right: 1px solid #eef2f6;
  background: #f8fafb;
}

.section-tab {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #52616f;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: background-color .18s ease, color .18s ease, box-shadow .18s ease;

  &:hover {
    background: #edf7f1;
    color: #176c32;
  }

  &.active {
    background: #fff;
    color: #176c32;
    font-weight: 600;
    box-shadow: 0 1px 8px rgba(20, 83, 45, .08);
  }
}

.shop-item-section-panel {
  min-width: 0;
  max-height: calc(100vh - 240px);
  min-height: 620px;
  overflow-y: auto;
  padding: 22px 26px 8px;
}

.section-heading {
  display: flex;
  align-items: center;
  min-height: 32px;
  margin-bottom: 18px;

  h3 {
    margin: 0;
    color: #183323;
    font-size: 18px;
    font-weight: 650;
    line-height: 1.3;
  }
}

.image-field {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
}

.repo-preview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  margin: -4px 0 16px;
  color: #52616f;
  font-size: 13px;

  :deep(.ant-tag) {
    margin-inline-end: 0;
  }
}

.delivery-config {
  margin: 0 0 16px;
  padding: 14px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.default-cover-preview {
  width: 104px;
  height: 104px;
  overflow: hidden;
  border: 1px solid #d9f0df;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fcf9, #f4f8ff);

  img {
    width: 100%;
    height: 100%;
    display: block;
    object-fit: cover;
  }
}

.markdown-editor {
  overflow: hidden;
  border-radius: 8px;
}

:global(.shop-item-modal-wrap .ant-modal-body) {
  padding-top: 12px;
}

:global(.shop-item-modal-wrap .ant-modal) {
  max-width: calc(100vw - 24px);
}

@media (max-width: 760px) {
  :global(.shop-item-modal-wrap .ant-modal) {
    top: 8px;
    max-width: calc(100vw - 16px);
    margin: 0 auto;
  }

  :global(.shop-item-modal-wrap .ant-modal-body) {
    padding: 10px;
  }

  .shop-item-editor {
    grid-template-columns: minmax(0, 1fr);
    min-height: 0;
  }

  .shop-item-section-tabs {
    position: sticky;
    top: 0;
    z-index: 2;
    flex-direction: row;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid #eef2f6;
  }

  .section-tab {
    width: auto;
    min-width: max-content;
    text-align: center;
  }

  .shop-item-section-panel {
    max-height: calc(100vh - 224px);
    min-height: 420px;
    padding: 18px 16px 4px;
  }

  .delivery-config {
    padding: 12px;
  }

  .markdown-editor {
    height: 360px !important;
  }
}
</style>
