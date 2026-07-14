<script setup>
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';
import {
  CopyOutlined,
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  RightOutlined,
  UpOutlined
} from '@ant-design/icons-vue';
import { useI18n } from 'vue-i18n';
import { getShopItemGroups, getShopItemDetail, getShopPricing } from '../apis/api';
import { message, Upload } from 'ant-design-vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import { getContracts, getGitRepositoryOptions, uploadImage } from '../apis/manage';
import {
  getShopItemDefaultCover,
  isGitRepositoryAccessType,
  isDonationType
} from '@/modules/shop/utils/shopCovers.js';
import {
  DEFAULT_OFFICE_LICENSE_FEATURES,
  DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES,
  DELIVERY_ACTION,
  DELIVERY_ACTION_OPTIONS,
  LICENSE_PRODUCT_OPTIONS,
  OFFICE_LICENSE_KIND,
  OFFICE_LICENSE_KIND_OPTIONS,
  OFFICE_LICENSE_EDITION_OPTIONS,
  OFFICE_LICENSE_FEATURE_OPTIONS,
  SHOP_TYPE_OPTIONS,
  VIEWER_BRAND_REMOVAL_REMARK,
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
import {
  compactShopItemI18n,
  defaultShopItemI18n,
  normalizeShopItemI18n
} from '@/modules/shop/utils/shopI18n.js';

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

const defaultOrderFormDraft = () => ({
  enabled: false,
  title: '',
  description: '',
  fields: []
});

const VIEWER_BRAND_REMOVAL_LICENSE_NAME = 'Flyfish Viewer 去品牌标识授权声明';
const VIEWER_BRAND_REMOVAL_SCOPE = 'product:file-viewer:remove-branding';
const VIEWER_BRAND_REMOVAL_DESCRIPTION = `### 授权说明

本商品用于签发 Flyfish Viewer 去品牌标识授权声明，便于采购、审计和商业合规留痕。

Flyfish Viewer 使用 Apache License 2.0 开源协议。该协议本身允许使用方修改、分发和移除界面中的可见品牌标识；本商品不改变 Apache 2.0 的开源授权边界，也不提供代码级品牌授权接入流程。

### 使用边界

- 可以在自有产品或客户项目中修改、替换或移除界面可见的 Flyfish/Flyfish Viewer 品牌标识。
- 必须保留项目版权声明、Apache 2.0 许可证文本、NOTICE 文件和必要的来源说明。
- 不得暗示 Flyfish 官方对你的产品、服务或客户项目提供背书、认证或联合发布。
- 不包含其他闭源产品、私有技术支持或定制开发授权。

购买后系统会自动签发授权声明文件，可在我的订单中下载。`;

const props = defineProps({
  visible: Boolean,
  item: Object,
});

const emit = defineEmits(['update:visible', 'save']);
const { t } = useI18n();

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
  skuMode: 'SINGLE',
  skus: [],
  usdPriceMode: 'AUTO',
  usdPrice: null,
  sort: 0,
  type: GIT_REPOSITORY_ACCESS,
  deliveryMode: defaultDeliveryModeForType(GIT_REPOSITORY_ACCESS),
  deliveryActions: defaultDeliveryActionsForType(GIT_REPOSITORY_ACCESS),
  orderForm: defaultOrderFormDraft(),
  extraParamsText: '',
  tags: [],
  i18n: defaultShopItemI18n(),
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
const activeSkuKey = ref('');
const pricing = ref({ cnyPerUsd: 6.78 });

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
const licenseProductOptions = LICENSE_PRODUCT_OPTIONS;
const licenseEditionOptions = OFFICE_LICENSE_EDITION_OPTIONS;
const brandRemovalFeatureSet = new Set(DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES);
const isBrandRemovalLicense = license => license?.licenseKind === OFFICE_LICENSE_KIND.BRAND_REMOVAL;
const brandRemovalSelected = computed(() => isBrandRemovalLicense(formData.value.licenseDelivery));
const licenseFeatureOptionsFor = license => (
  isBrandRemovalLicense(license)
    ? OFFICE_LICENSE_FEATURE_OPTIONS.filter(option => brandRemovalFeatureSet.has(option.value))
    : OFFICE_LICENSE_FEATURE_OPTIONS.filter(option => !brandRemovalFeatureSet.has(option.value))
);
const licenseFeatureOptions = computed(() => licenseFeatureOptionsFor(formData.value.licenseDelivery));
const priceLabel = computed(() => isDonationType(formData.value.type) ? '打赏起始金额' : '商品价格');
const usdPriceModeOptions = [
  { label: '自动换算', value: 'AUTO' },
  { label: '手动定价', value: 'MANUAL' }
];
const toPositiveNumber = value => {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount : 0;
};
const convertedUsdPrice = (cnyPrice, donation = false) => {
  const rate = toPositiveNumber(pricing.value.cnyPerUsd) || 6.78;
  const converted = toPositiveNumber(cnyPrice) / rate;
  return Math.max(donation ? 1 : 0.5, Math.round(converted * 100) / 100);
};
const formatUsd = value => `$${toPositiveNumber(value).toFixed(2)}`;
const automaticUsdPrice = computed(() => convertedUsdPrice(
  formData.value.price,
  isDonationType(formData.value.type)
));
const displayedUsdPrice = computed(() => formData.value.usdPriceMode === 'MANUAL'
  ? Math.max(isDonationType(formData.value.type) ? 1 : 0, toPositiveNumber(formData.value.usdPrice))
  : automaticUsdPrice.value);
const skuModeOptions = [
  { label: '单 SKU', value: 'SINGLE' },
  { label: '多 SKU', value: 'MULTI' }
];
const skuCouponModeOptions = [
  { label: '继承商品默认优惠', value: 'INHERIT' },
  { label: 'SKU 独立启用', value: 'ENABLED' },
  { label: 'SKU 关闭优惠', value: 'DISABLED' }
];
const orderFormFieldTypeOptions = [
  { label: '单行文本', value: 'text' },
  { label: '多行文本', value: 'textarea' },
  { label: '网址', value: 'url' },
  { label: '勾选确认', value: 'checkbox' },
  { label: '下拉选择', value: 'select' }
];
const orderFormTargetOptions = [
  { label: '仅记录到订单', value: '' },
  { label: '写入授权域名', value: 'license.allowedOrigins' }
];
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
  skuMode: 'SINGLE',
  skus: [],
  usdPriceMode: 'AUTO',
  usdPrice: null,
  sort: 0,
  type: GIT_REPOSITORY_ACCESS,
  deliveryMode: defaultDeliveryModeForType(GIT_REPOSITORY_ACCESS),
  deliveryActions: defaultDeliveryActionsForType(GIT_REPOSITORY_ACCESS),
  orderForm: defaultOrderFormDraft(),
  extraParamsText: '',
  tags: [],
  i18n: defaultShopItemI18n(),
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

const normalizeOrderFormField = field => ({
  key: String(field?.key || '').trim(),
  label: String(field?.label || field?.key || '').trim(),
  type: ['text', 'textarea', 'url', 'checkbox', 'select'].includes(field?.type) ? field.type : 'text',
  required: field?.required === true,
  requiredValue: field?.requiredValue,
  target: field?.target || '',
  normalize: field?.normalize || '',
  placeholder: field?.placeholder || '',
  help: field?.help || '',
  maxLength: Number(field?.maxLength || 256),
  maxItems: Number(field?.maxItems || 0) || null,
  options: Array.isArray(field?.options) ? field.options : []
});

const normalizeOrderFormDraft = value => {
  const parsed = typeof value === 'string' ? parseParams(value) : (value || {});
  const fields = Array.isArray(parsed.fields)
    ? parsed.fields.map(normalizeOrderFormField)
    : [];
  return {
    enabled: parsed.enabled === true && fields.length > 0,
    title: parsed.title || '',
    description: parsed.description || '',
    fields
  };
};

const deploymentOriginOrderForm = () => ({
  enabled: true,
  title: '部署授权信息',
  description: '用于签发生产部署运行授权，请填写实际访问 origin。路径会自动忽略，只保留协议、域名和端口。',
  fields: [{
    key: 'origin',
    label: '授权部署域名',
    type: 'url',
    required: true,
    target: 'license.allowedOrigins',
    normalize: 'origin',
    placeholder: 'https://example.com',
    help: '填写正式部署访问地址，例如 https://docs.example.com；本地测试地址不作为生产授权域名。',
    maxLength: 256,
    options: []
  }]
});

const multiDeploymentOriginOrderForm = (maxOrigins = 5) => ({
  enabled: true,
  title: '企业授权信息',
  description: `用于签发多个生产部署运行授权，请填写实际访问 origin，最多 ${maxOrigins} 个。路径会自动忽略，只保留协议、域名和端口。`,
  fields: [{
    key: 'origins',
    label: '授权部署域名',
    type: 'textarea',
    required: true,
    target: 'license.allowedOrigins',
    normalize: 'origin',
    placeholder: 'https://docs.example.com\nhttps://editor.example.com',
    help: `每行一个正式生产访问地址，最多 ${maxOrigins} 个；本地测试地址不作为生产授权域名。`,
    maxLength: 2048,
    maxItems: maxOrigins,
    options: []
  }]
});

const viewerBrandRemovalOrderForm = () => ({
  enabled: true,
  title: '授权声明信息',
  description: '用于签发 Flyfish Viewer 去品牌标识授权声明，请填写授权主体并确认保留版权与许可证声明。',
  fields: [
    {
      key: 'licenseeName',
      label: '授权主体/公司名称',
      type: 'text',
      required: true,
      target: '',
      normalize: '',
      placeholder: '例如：某某科技有限公司',
      help: '将记录在订单下单信息中，作为授权声明的采购主体留痕。',
      maxLength: 128,
      options: []
    },
    {
      key: 'usageScenario',
      label: '使用场景',
      type: 'textarea',
      required: false,
      target: '',
      normalize: '',
      placeholder: '例如：集成到内部知识库、SaaS 文件预览模块',
      help: '',
      maxLength: 1024,
      options: []
    },
    {
      key: 'attributionAcknowledgement',
      label: '确认保留版权、Apache 2.0 许可证文本和项目来源说明',
      type: 'checkbox',
      required: true,
      requiredValue: true,
      target: '',
      normalize: '',
      placeholder: '',
      help: '',
      maxLength: 256,
      options: []
    }
  ]
});

const orderFormFieldKeyPattern = /^[A-Za-z][A-Za-z0-9_-]{0,63}$/;

const compactOrderForm = value => {
  const draft = normalizeOrderFormDraft(value);
  if (!draft.enabled) {
    return null;
  }
  const fields = draft.fields.map((field, index) => {
    const key = String(field.key || '').trim();
    if (!orderFormFieldKeyPattern.test(key)) {
      throw new Error(`下单信息第 ${index + 1} 个字段编码不正确`);
    }
    const label = String(field.label || key).trim();
    if (!label) {
      throw new Error(`下单信息第 ${index + 1} 个字段缺少名称`);
    }
    const normalized = {
      key,
      label,
      type: field.type || 'text',
      required: field.required === true,
      target: field.target || '',
      normalize: field.target === 'license.allowedOrigins' ? 'origin' : (field.normalize || ''),
      placeholder: String(field.placeholder || '').trim(),
      help: String(field.help || '').trim(),
      maxLength: Number(field.maxLength || 256)
    };
    const maxItems = Number(field.maxItems || 0);
    if (maxItems > 0) {
      normalized.maxItems = maxItems;
    }
    if (field.type === 'checkbox' && field.requiredValue !== undefined) {
      normalized.requiredValue = field.requiredValue;
    }
    if (field.type === 'select') {
      normalized.options = (field.options || [])
        .filter(option => option?.value !== undefined && String(option.value).trim() !== '')
        .map(option => ({
          value: String(option.value).trim(),
          label: String(option.label || option.value).trim()
        }));
      if (!normalized.options.length) {
        throw new Error(`${label} 至少需要一个选项`);
      }
    }
    return normalized;
  });
  if (!fields.length) {
    throw new Error('启用下单信息后至少需要一个字段');
  }
  return {
    enabled: true,
    title: String(draft.title || '').trim(),
    description: String(draft.description || '').trim(),
    fields
  };
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

const knownParamKeys = new Set([
  'provider',
  'permission',
  'repositoryIds',
  'repositories',
  'owner',
  'repo',
  'deliveryActions',
  'orderForm',
  'licenseDelivery',
  'title',
  'content',
  'attachments',
  'licenseKind',
  'licenseName',
  'scope',
  'product',
  'edition',
  'holder',
  'allowedOrigins',
  'features',
  'maxDeployments',
  'commercialUse',
  'validDays',
  'remark'
]);

const extractExtraParams = params => {
  const extra = {};
  Object.entries(params || {}).forEach(([key, value]) => {
    if (!knownParamKeys.has(key)) {
      extra[key] = value;
    }
  });
  return extra;
};

const stringifyExtraParams = params => {
  const extra = extractExtraParams(params);
  return Object.keys(extra).length ? JSON.stringify(extra, null, 2) : '';
};

const parseExtraParamsText = (value, label = '扩展参数') => {
  const text = String(value || '').trim();
  if (!text) {
    return {};
  }
  try {
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error();
    }
    return parsed;
  } catch {
    throw new Error(`${label}必须是 JSON 对象`);
  }
};

let skuLocalSeq = 0;
const nextSkuLocalKey = () => `sku-${Date.now()}-${skuLocalSeq += 1}`;

const skuPanelKey = sku => String(sku?.id || sku?.localKey || '');

const skuPanelTitle = (sku, index) => String(sku?.name || '').trim() || `套餐${index + 1}`;

const skuPanelCode = sku => String(sku?.code || '').trim().toUpperCase();

const skuPanelPrice = sku => {
  const price = Number(sku?.price);
  if (!Number.isFinite(price)) {
    return '未定价';
  }
  const usd = sku?.usdPriceMode === 'MANUAL'
    ? toPositiveNumber(sku?.usdPrice)
    : convertedUsdPrice(price, isDonationType(sku?.type));
  return `¥${price.toFixed(2)} / ${formatUsd(usd)}`;
};

const skuDisplayedUsdPrice = sku => sku?.usdPriceMode === 'MANUAL'
  ? Math.max(isDonationType(sku?.type) ? 1 : 0, toPositiveNumber(sku?.usdPrice))
  : convertedUsdPrice(sku?.price, isDonationType(sku?.type));

const skuTypeLabel = sku => itemTypes.find(option => option.value === sku?.type)?.label || sku?.type || '商品';

const skuDeliveryModeLabel = sku => (
  skuDeliveryModeOptions(sku).find(option => option.value === sku?.deliveryMode)?.label || sku?.deliveryMode || '交付方式'
);

const expandSkuPanel = sku => {
  const key = skuPanelKey(sku);
  if (key) {
    activeSkuKey.value = key;
  }
};

const isSkuPanelActive = sku => skuPanelKey(sku) === activeSkuKey.value;

const toggleSkuPanel = sku => {
  const key = skuPanelKey(sku);
  activeSkuKey.value = activeSkuKey.value === key ? '' : key;
};

const expandSkuPanelAt = index => {
  expandSkuPanel(formData.value.skus?.[index]);
};

const collapseAllSkus = () => {
  activeSkuKey.value = '';
};

const expandDefaultSku = () => {
  const skus = formData.value.skus || [];
  expandSkuPanel(skus.find(sku => sku.defaultSelected) || skus[0]);
};

const normalizeSkuI18n = value => ({
  ...(value || {}),
  'en-US': {
    name: '',
    description: '',
    tags: [],
    ...(value?.['en-US'] || {})
  }
});

const compactSkuI18n = value => {
  const en = value?.['en-US'] || {};
  const payload = {};
  if (String(en.name || '').trim()) {
    payload.name = String(en.name).trim();
  }
  if (String(en.description || '').trim()) {
    payload.description = String(en.description).trim();
  }
  const tags = Array.isArray(en.tags) ? en.tags.map(tag => String(tag || '').trim()).filter(Boolean) : [];
  if (tags.length) {
    payload.tags = tags;
  }
  return Object.keys(payload).length ? { 'en-US': payload } : null;
};

const normalizeParamDraft = params => {
  const parsedParams = parseParams(params);
  const nestedLicenseParams = parsedParams.licenseDelivery || parsedParams;
  return {
    repositoryAccess: parseGitRepositoryAccessParams(parsedParams),
    digitalDelivery: parseDigitalDeliveryParams(parsedParams),
    licenseDelivery: parseLicenseDeliveryParams(nestedLicenseParams),
    orderForm: normalizeOrderFormDraft(parsedParams.orderForm),
    extraParamsText: stringifyExtraParams(parsedParams)
  };
};

const normalizeSkuDraft = sku => {
  const paramDraft = normalizeParamDraft(sku?.paramsText || sku?.params);
  const type = sku?.type || formData.value.type;
  return {
    localKey: sku?.localKey || nextSkuLocalKey(),
    id: sku?.id || null,
    code: sku?.code || '',
    name: sku?.name || '',
    description: sku?.description || '',
    price: sku?.price ?? formData.value.price ?? 0,
    usdPriceMode: sku?.usdPrice != null ? 'MANUAL' : 'AUTO',
    usdPrice: sku?.usdPrice ?? null,
    type,
    deliveryMode: normalizeDeliveryModeForType(type, sku?.deliveryMode),
    deliveryActions: normalizeDeliveryActionsForType(type, sku?.deliveryActions || paramDraft.licenseDelivery.deliveryActions),
    repositoryAccess: normalizeGitRepositoryAccess(sku?.repositoryAccess || paramDraft.repositoryAccess),
    digitalDelivery: parseDigitalDeliveryParams(sku?.digitalDelivery || paramDraft.digitalDelivery),
    licenseDelivery: parseLicenseDeliveryParams(sku?.licenseDelivery || paramDraft.licenseDelivery),
    orderForm: normalizeOrderFormDraft(sku?.orderForm || paramDraft.orderForm),
    extraParamsText: typeof sku?.extraParamsText === 'string' ? sku.extraParamsText : paramDraft.extraParamsText,
    tags: Array.isArray(sku?.tags) ? sku.tags : [],
    sort: sku?.sort ?? 0,
    enabled: sku?.enabled !== false,
    defaultSelected: sku?.defaultSelected === true,
    couponMode: sku?.defaultCouponEnabled === true
      ? 'ENABLED'
      : (sku?.defaultCouponEnabled === false ? 'DISABLED' : 'INHERIT'),
    defaultCouponCode: sku?.defaultCouponCode || '',
    contractIds: sku?.contractIds || [],
    i18n: normalizeSkuI18n(sku?.i18n)
  };
};

const skuDeliveryModeOptions = sku => deliveryModeOptionsForType(sku?.type);

const skuDeliveryActionOptions = sku => {
  if (isGitRepositoryAccessType(sku?.type)) {
    return DELIVERY_ACTION_OPTIONS.filter(option => [
      DELIVERY_ACTION.GIT_REPOSITORY_ACCESS,
      DELIVERY_ACTION.LICENSE
    ].includes(option.value));
  }
  if (isLicenseType(sku?.type)) {
    return DELIVERY_ACTION_OPTIONS.filter(option => option.value === DELIVERY_ACTION.LICENSE);
  }
  if (isDigitalDownloadType(sku?.type)) {
    return DELIVERY_ACTION_OPTIONS.filter(option => option.value === DELIVERY_ACTION.DIGITAL_DOWNLOAD);
  }
  return [];
};

const toDigitalDeliveryParams = value => ({
  title: value?.title || '数字商品提货内容',
  content: value?.content || '',
  attachments: value?.attachments || []
});

const normalizeLicenseKindForPayload = value => {
  if (value === OFFICE_LICENSE_KIND.BRAND_REMOVAL) {
    return value;
  }
  return OFFICE_LICENSE_KIND.RUNTIME;
};

const toLicenseDeliveryParams = value => {
  const licenseKind = normalizeLicenseKindForPayload(value?.licenseKind);
  const brandRemoval = licenseKind === OFFICE_LICENSE_KIND.BRAND_REMOVAL;
  const rawFeatures = Array.isArray(value?.features) && value.features.length
    ? value.features
    : (brandRemoval ? [...DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES] : [...DEFAULT_OFFICE_LICENSE_FEATURES]);
  const brandRemovalFeatures = rawFeatures.filter(feature => brandRemovalFeatureSet.has(feature));
  return {
    licenseKind,
    licenseName: value?.licenseName || (brandRemoval ? VIEWER_BRAND_REMOVAL_LICENSE_NAME : formData.value.name) || '飞鱼小铺授权许可',
    scope: value?.scope || (brandRemoval ? VIEWER_BRAND_REMOVAL_SCOPE : `product:${formData.value.name || 'flyfish'}`),
    product: value?.product || (brandRemoval ? 'file-viewer' : 'license-product'),
    edition: value?.edition || 'commercial',
    holder: value?.holder || value?.licenseName || (brandRemoval ? 'Flyfish Viewer' : formData.value.name) || '授权用户',
    allowedOrigins: brandRemoval
      ? []
      : (Array.isArray(value?.allowedOrigins)
        ? value.allowedOrigins.map(origin => String(origin || '').trim()).filter(Boolean)
        : []),
    features: brandRemoval
      ? (brandRemovalFeatures.length ? brandRemovalFeatures : [...DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES])
      : rawFeatures,
    maxDeployments: Number(value?.maxDeployments || 1),
    commercialUse: value?.edition === 'personal' ? false : value?.commercialUse !== false,
    validDays: value?.validDays || null,
    remark: value?.remark || (brandRemoval ? VIEWER_BRAND_REMOVAL_REMARK : '')
  };
};

const buildParamsPayload = ({ type, deliveryActions, repositoryAccess, digitalDelivery, licenseDelivery, orderForm, extraParamsText }, label) => {
  const extraParams = parseExtraParamsText(extraParamsText, `${label || '商品'}的扩展参数`);
  const normalizedActions = normalizeDeliveryActionsForType(type, deliveryActions);
  const orderFormConfig = compactOrderForm(orderForm);
  if (isGitRepositoryAccessType(type)) {
    return {
      ...extraParams,
      ...toGitRepositoryAccessParams(repositoryAccess),
      deliveryActions: normalizedActions,
      ...(orderFormConfig ? { orderForm: orderFormConfig } : {}),
      ...(normalizedActions.includes(DELIVERY_ACTION.LICENSE)
        ? { licenseDelivery: toLicenseDeliveryParams(licenseDelivery) }
        : {})
    };
  }
  if (isDigitalDownloadType(type)) {
    return {
      ...extraParams,
      ...toDigitalDeliveryParams(digitalDelivery),
      ...(orderFormConfig ? { orderForm: orderFormConfig } : {})
    };
  }
  if (isLicenseType(type)) {
    return {
      ...extraParams,
      ...toLicenseDeliveryParams(licenseDelivery),
      deliveryActions: normalizedActions,
      ...(orderFormConfig ? { orderForm: orderFormConfig } : {})
    };
  }
  return Object.keys(extraParams).length ? extraParams : null;
};

const skuParamsPayload = (sku, skuName) => {
  const type = sku.type || formData.value.type;
  const deliveryActions = normalizeDeliveryActionsForType(type, sku.deliveryActions);
  if (deliveryActions.includes(DELIVERY_ACTION.GIT_REPOSITORY_ACCESS)
    && !isGitRepositoryConfigured(sku.repositoryAccess)) {
    throw new Error(`${skuName || 'SKU'} 请选择代码仓库`);
  }
  return buildParamsPayload({
    type,
    deliveryActions,
    repositoryAccess: sku.repositoryAccess,
    digitalDelivery: sku.digitalDelivery,
    licenseDelivery: sku.licenseDelivery,
    orderForm: sku.orderForm,
    extraParamsText: sku.extraParamsText
  }, skuName);
};

const seedSkuFromCurrent = overrides => normalizeSkuDraft({
  name: formData.value.name || `套餐${(formData.value.skus || []).length + 1}`,
  code: '',
  description: formData.value.description || '',
  price: formData.value.price ?? 0,
  usdPriceMode: formData.value.usdPriceMode,
  usdPrice: formData.value.usdPrice,
  type: formData.value.type,
  deliveryMode: formData.value.deliveryMode,
  deliveryActions: formData.value.deliveryActions,
  repositoryAccess: formData.value.repositoryAccess,
  digitalDelivery: formData.value.digitalDelivery,
  licenseDelivery: formData.value.licenseDelivery,
  orderForm: formData.value.orderForm,
  extraParamsText: formData.value.extraParamsText,
  tags: formData.value.tags || [],
  enabled: true,
  defaultSelected: !(formData.value.skus || []).some(sku => sku.defaultSelected),
  defaultCouponCode: formData.value.defaultCouponCode || '',
  contractIds: formData.value.contractIds || [],
  ...overrides
});

const addSku = () => {
  const sku = seedSkuFromCurrent({
    name: `套餐${(formData.value.skus || []).length + 1}`,
    defaultSelected: !(formData.value.skus || []).some(item => item.defaultSelected)
  });
  formData.value.skus = [
    ...(formData.value.skus || []),
    sku
  ];
  expandSkuPanel(sku);
};

const duplicateSku = index => {
  const source = formData.value.skus?.[index];
  if (!source) {
    return;
  }
  const copy = normalizeSkuDraft({
    ...source,
    id: null,
    localKey: nextSkuLocalKey(),
    code: '',
    name: `${source.name || '套餐'} 副本`,
    defaultSelected: false
  });
  formData.value.skus.splice(index + 1, 0, copy);
  expandSkuPanel(copy);
};

const removeSku = index => {
  const removedKey = skuPanelKey(formData.value.skus?.[index]);
  formData.value.skus.splice(index, 1);
  if (formData.value.skus.length && !formData.value.skus.some(sku => sku.defaultSelected)) {
    markSkuDefault(0);
  }
  if (!formData.value.skus.length) {
    activeSkuKey.value = '';
  } else if (activeSkuKey.value === removedKey) {
    expandSkuPanel(formData.value.skus[Math.min(index, formData.value.skus.length - 1)]);
  }
};

const moveSku = (index, direction) => {
  const nextIndex = index + direction;
  if (nextIndex < 0 || nextIndex >= formData.value.skus.length) {
    return;
  }
  const [sku] = formData.value.skus.splice(index, 1);
  formData.value.skus.splice(nextIndex, 0, sku);
};

const markSkuDefault = index => {
  formData.value.skus = (formData.value.skus || []).map((sku, skuIndex) => ({
    ...sku,
    defaultSelected: skuIndex === index,
    enabled: skuIndex === index ? true : sku.enabled
  }));
};

const syncSkuParamsFromCurrent = index => {
  const sku = formData.value.skus?.[index];
  if (!sku) {
    return;
  }
  sku.type = formData.value.type;
  sku.deliveryMode = normalizeDeliveryModeForType(sku.type, formData.value.deliveryMode);
  sku.deliveryActions = normalizeDeliveryActionsForType(sku.type, formData.value.deliveryActions);
  sku.repositoryAccess = normalizeGitRepositoryAccess({
    ...formData.value.repositoryAccess,
    provider: repositoryProvider.value
  });
  sku.digitalDelivery = parseDigitalDeliveryParams(formData.value.digitalDelivery);
  sku.licenseDelivery = parseLicenseDeliveryParams(formData.value.licenseDelivery);
  sku.orderForm = normalizeOrderFormDraft(formData.value.orderForm);
  sku.extraParamsText = formData.value.extraParamsText || '';
};

const handleSkuTypeChange = sku => {
  sku.deliveryMode = normalizeDeliveryModeForType(sku.type, sku.deliveryMode);
  sku.deliveryActions = normalizeDeliveryActionsForType(sku.type, sku.deliveryActions);
  if (isGitRepositoryAccessType(sku.type)) {
    loadRepositoryOptions('', { includeAllProviders: true });
  }
};

const normalizeSkusForSubmit = skus => {
  const normalized = (skus || []).map((sku, index) => {
    const name = String(sku.name || '').trim();
    if (!name) {
      expandSkuPanelAt(index);
      throw new Error(`第 ${index + 1} 个 SKU 缺少名称`);
    }
    const price = Number(sku.price);
    if (!Number.isFinite(price) || price <= 0) {
      expandSkuPanelAt(index);
      throw new Error(`${name} 的价格不正确`);
    }
    const type = sku.type || formData.value.type;
    const deliveryActions = normalizeDeliveryActionsForType(type, sku.deliveryActions);
    let paramsPayload = null;
    try {
      paramsPayload = skuParamsPayload(sku, name);
    } catch (error) {
      expandSkuPanelAt(index);
      throw error;
    }
    const params = paramsPayload ? JSON.stringify(paramsPayload) : null;
    const usdPrice = sku.usdPriceMode === 'MANUAL' ? Number(sku.usdPrice) : null;
    if (sku.usdPriceMode === 'MANUAL' && (!Number.isFinite(usdPrice) || usdPrice <= 0)) {
      expandSkuPanelAt(index);
      throw new Error(`${name} 的美元价格不正确`);
    }
    return {
      id: sku.id || null,
      code: String(sku.code || '').trim().toUpperCase() || null,
      name,
      description: String(sku.description || '').trim() || null,
      price,
      usdPrice,
      type,
      deliveryMode: normalizeDeliveryModeForType(type, sku.deliveryMode),
      deliveryActions,
      params,
      tags: Array.isArray(sku.tags) ? sku.tags.map(tag => String(tag || '').trim()).filter(Boolean) : [],
      sort: index,
      enabled: sku.enabled !== false,
      defaultSelected: sku.defaultSelected === true,
      defaultCouponEnabled: sku.couponMode === 'INHERIT' ? null : sku.couponMode === 'ENABLED',
      defaultCouponCode: sku.couponMode === 'ENABLED'
        ? String(sku.defaultCouponCode || '').trim().toUpperCase() || null
        : null,
      contractIds: Array.isArray(sku.contractIds) ? sku.contractIds : [],
      i18n: compactSkuI18n(sku.i18n)
    };
  });
  if (!normalized.length) {
    throw new Error('多 SKU 商品至少需要一个套餐');
  }
  if (!normalized.some(sku => sku.enabled)) {
    throw new Error('多 SKU 商品至少需要一个上架套餐');
  }
  const enabledDefaultIndex = normalized.findIndex(sku => sku.enabled && sku.defaultSelected);
  const fallbackDefaultIndex = normalized.findIndex(sku => sku.enabled);
  normalized.forEach((sku, index) => {
    sku.defaultSelected = index === (enabledDefaultIndex >= 0 ? enabledDefaultIndex : fallbackDefaultIndex);
  });
  return normalized;
};

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
const hasGitRepositorySku = computed(() => (
  formData.value.skuMode === 'MULTI'
  && (formData.value.skus || []).some(sku => isGitRepositoryAccessType(sku.type))
));
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
  if (!isGitRepositoryAccessType(formData.value.type) && !hasGitRepositorySku.value) {
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

const addOrderFormField = orderForm => {
  const target = orderForm || defaultOrderFormDraft();
  target.enabled = true;
  target.fields = [
    ...(target.fields || []),
    normalizeOrderFormField({
      key: `field${(target.fields || []).length + 1}`,
      label: '补充信息',
      type: 'text',
      required: true,
      maxLength: 256
    })
  ];
};

const removeOrderFormField = (orderForm, index) => {
  orderForm.fields.splice(index, 1);
  if (!orderForm.fields.length) {
    orderForm.enabled = false;
  }
};

const applyDeploymentOriginOrderForm = target => {
  target.orderForm = deploymentOriginOrderForm();
};

const applyViewerBrandRemovalLicenseDefaults = license => {
  if (!license) {
    return;
  }
  license.licenseKind = OFFICE_LICENSE_KIND.BRAND_REMOVAL;
  license.licenseName = license.licenseName || VIEWER_BRAND_REMOVAL_LICENSE_NAME;
  license.scope = license.scope || VIEWER_BRAND_REMOVAL_SCOPE;
  license.product = 'file-viewer';
  license.edition = 'commercial';
  license.holder = license.holder || 'Flyfish Viewer';
  license.allowedOrigins = [];
  license.features = Array.isArray(license.features) && license.features.some(feature => brandRemovalFeatureSet.has(feature))
    ? license.features.filter(feature => brandRemovalFeatureSet.has(feature))
    : [...DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES];
  license.maxDeployments = 1;
  license.commercialUse = true;
  license.remark = license.remark || VIEWER_BRAND_REMOVAL_REMARK;
};

const applyViewerBrandRemovalPreset = target => {
  target.name = target.name || 'Flyfish Viewer 去品牌标识授权';
  target.type = 'LICENSE';
  target.deliveryMode = defaultDeliveryModeForType('LICENSE');
  target.deliveryActions = [DELIVERY_ACTION.LICENSE];
  target.licenseDelivery = parseLicenseDeliveryParams({
    ...target.licenseDelivery,
    licenseKind: OFFICE_LICENSE_KIND.BRAND_REMOVAL,
    licenseName: VIEWER_BRAND_REMOVAL_LICENSE_NAME,
    scope: VIEWER_BRAND_REMOVAL_SCOPE,
    product: 'file-viewer',
    edition: 'commercial',
    holder: 'Flyfish Viewer',
    allowedOrigins: [],
    features: [...DEFAULT_VIEWER_BRAND_STATEMENT_FEATURES],
    maxDeployments: 1,
    commercialUse: true,
    remark: VIEWER_BRAND_REMOVAL_REMARK
  });
  target.orderForm = viewerBrandRemovalOrderForm();
  target.description = VIEWER_BRAND_REMOVAL_DESCRIPTION;
  target.tags = [...new Set([...(target.tags || []), 'Flyfish Viewer', '去品牌标识', 'Apache 2.0'])];
  message.success('已套用 Flyfish Viewer 去品牌标识授权声明配置');
};

const handleLicenseKindChange = license => {
  if (isBrandRemovalLicense(license)) {
    applyViewerBrandRemovalLicenseDefaults(license);
  }
};

const skuLicenseDeliveryEnabled = sku => (sku.deliveryActions || []).includes(DELIVERY_ACTION.LICENSE);

const skuRepositoryOptions = sku => repositoryOptions.value
  .filter(option => normalizeGitProvider(option.repo?.provider) === normalizeGitProvider(sku.repositoryAccess?.provider));

const skuRepositoryKeys = sku => toGitRepositoryKeys(sku.repositoryAccess);

const updateSkuRepositoryKeys = (sku, values) => {
  const provider = normalizeGitProvider(sku.repositoryAccess?.provider);
  const preserved = normalizeGitRepositoryAccess(sku.repositoryAccess).repositories
    .filter(repository => normalizeGitProvider(repository.provider) !== provider);
  sku.repositoryAccess = normalizeGitRepositoryAccess({
    provider,
    repositories: [
      ...preserved,
      ...(values || []).map(value => {
        const selected = repositoryOptions.value.find(option => option.value === value)?.repo;
        return selected ? toAccessRepository(selected) : toRepositoryFromValue(value);
      })
    ]
  });
};

const handleSkuRepositoryProviderChange = sku => {
  sku.repositoryAccess = normalizeGitRepositoryAccess({
    ...sku.repositoryAccess,
    provider: normalizeGitProvider(sku.repositoryAccess?.provider)
  });
  loadRepositoryOptions('', { includeAllProviders: true });
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

watch(() => formData.value.licenseDelivery.licenseKind, licenseKind => {
  if (licenseKind === OFFICE_LICENSE_KIND.BRAND_REMOVAL) {
    applyViewerBrandRemovalLicenseDefaults(formData.value.licenseDelivery);
  }
});

watch(() => formData.value.skuMode, skuMode => {
  if (skuMode === 'MULTI' && !(formData.value.skus || []).length) {
    const sku = seedSkuFromCurrent({ defaultSelected: true });
    formData.value.skus = [sku];
    expandSkuPanel(sku);
  } else if (skuMode !== 'MULTI') {
    activeSkuKey.value = '';
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

const loadPricing = async () => {
  try {
    const value = await getShopPricing();
    if (toPositiveNumber(value?.cnyPerUsd)) {
      pricing.value = value;
    }
  } catch {
    pricing.value = { cnyPerUsd: 6.78 };
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
      orderForm: normalizeOrderFormDraft(parsedParams.orderForm),
      extraParamsText: stringifyExtraParams(parsedParams),
      enabled: detail.enabled !== false,
      pinned: detail.pinned === true,
      recommended: detail.recommended === true,
      defaultCouponEnabled: detail.defaultCouponEnabled === true,
      defaultCouponCode: detail.defaultCouponCode || '',
      highlightStyle: detail.highlightStyle || '',
      highlightIcon: detail.highlightIcon || '',
      contractIds: detail.contractIds || [],
      skuMode: detail.skuMode === 'MULTI' ? 'MULTI' : 'SINGLE',
      skus: (detail.skus || []).map(normalizeSkuDraft),
      usdPriceMode: detail.usdPrice != null ? 'MANUAL' : 'AUTO',
      usdPrice: detail.usdPrice ?? null,
      i18n: normalizeShopItemI18n(detail.i18n),
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
    if (toPositiveNumber(detail.cnyPerUsd)) {
      pricing.value = { cnyPerUsd: detail.cnyPerUsd };
    }
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
    activeSkuKey.value = '';
    if (formData.value.skuMode === 'MULTI') {
      expandDefaultSku();
    }
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
  loadPricing();
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
      activeSkuKey.value = '';
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
      orderForm,
      extraParamsText,
      skuMode,
      skus,
      usdPriceMode,
      ...rest
    } = formData.value;

    if (usdPriceMode === 'MANUAL' && !toPositiveNumber(rest.usdPrice)) {
      activeSection.value = 'sales';
      message.warning('请填写有效的美元价格');
      return;
    }

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
    let paramsPayload = null;
    try {
      paramsPayload = buildParamsPayload({
        type: rest.type,
        deliveryActions: normalizedActions,
        repositoryAccess,
        digitalDelivery,
        licenseDelivery,
        orderForm,
        extraParamsText
      }, '商品');
    } catch (error) {
      activeSection.value = String(error.message || '').includes('扩展参数') ? 'delivery' : 'orderForm';
      message.error(error.message || '交付配置异常');
      return;
    }
    const params = paramsPayload ? JSON.stringify(paramsPayload) : null;

    let normalizedSkus = [];
    if (skuMode === 'MULTI') {
      try {
        normalizedSkus = normalizeSkusForSubmit(skus);
      } catch (error) {
        activeSection.value = 'skus';
        message.error(error.message || 'SKU 配置异常');
        return;
      }
    }

    emit('save', {
      ...rest,
      usdPrice: usdPriceMode === 'MANUAL' ? Number(rest.usdPrice) : null,
      usdPriceAutomatic: usdPriceMode !== 'MANUAL',
      skuMode: skuMode === 'MULTI' ? 'MULTI' : 'SINGLE',
      skus: skuMode === 'MULTI' ? normalizedSkus : [],
      i18n: compactShopItemI18n(rest.i18n),
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
        <a-col :xs="24">
          <div class="usd-pricing-panel">
            <div class="usd-pricing-heading">
              <div>
                <strong>美元定价</strong>
                <span>1 USD = {{ pricing.cnyPerUsd }} CNY</span>
              </div>
              <a-segmented
                v-model:value="formData.usdPriceMode"
                :options="usdPriceModeOptions"
              />
            </div>
            <div class="usd-pricing-value">
              <a-input-number
                v-if="formData.usdPriceMode === 'MANUAL'"
                v-model:value="formData.usdPrice"
                :min="isDonationType(formData.type) ? 1 : 0.01"
                :precision="2"
                :step="0.01"
                addon-before="$"
              />
              <div v-else class="usd-price-preview">
                <span>自动美元价</span>
                <strong>{{ formatUsd(displayedUsdPrice) }}</strong>
              </div>
            </div>
          </div>
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

      <a-collapse v-if="formData.extraParamsText" v-show="activeSection === 'delivery'" ghost class="advanced-json">
        <a-collapse-panel key="extra" header="高级扩展参数">
          <a-textarea
            v-model:value="formData.extraParamsText"
            :auto-size="{ minRows: 4, maxRows: 10 }"
            spellcheck="false"
          />
        </a-collapse-panel>
      </a-collapse>

      <section v-show="activeSection === 'orderForm'" class="delivery-config">
        <div class="config-toolbar">
          <a-switch
            v-model:checked="formData.orderForm.enabled"
            :checkedValue="true"
            :unCheckedValue="false"
            checked-children="启用"
            un-checked-children="关闭"
          />
          <div class="config-toolbar-actions">
            <a-button size="small" @click="applyDeploymentOriginOrderForm(formData)">部署域名表单</a-button>
            <a-button size="small" type="primary" ghost @click="addOrderFormField(formData.orderForm)">
              <template #icon><plus-outlined /></template>
              添加字段
            </a-button>
          </div>
        </div>
        <template v-if="formData.orderForm.enabled">
          <a-row :gutter="12">
            <a-col :xs="24" :md="10">
              <a-form-item label="表单标题">
                <a-input v-model:value="formData.orderForm.title" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="14">
              <a-form-item label="表单说明">
                <a-input v-model:value="formData.orderForm.description" />
              </a-form-item>
            </a-col>
          </a-row>
          <div v-if="formData.orderForm.fields.length" class="order-form-fields">
            <article
              v-for="(field, fieldIndex) in formData.orderForm.fields"
              :key="`${field.key}-${fieldIndex}`"
              class="order-form-field"
            >
              <div class="order-form-field-head">
                <strong>{{ field.label || field.key || `字段${fieldIndex + 1}` }}</strong>
                <a-button size="small" danger title="删除字段" @click="removeOrderFormField(formData.orderForm, fieldIndex)">
                  <template #icon><delete-outlined /></template>
                </a-button>
              </div>
              <a-row :gutter="12">
                <a-col :xs="24" :md="8">
                  <a-form-item label="字段编码" required>
                    <a-input v-model:value="field.key" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="显示名称" required>
                    <a-input v-model:value="field.label" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="字段类型">
                    <a-select v-model:value="field.type" :options="orderFormFieldTypeOptions" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="12">
                <a-col :xs="24" :md="8">
                  <a-form-item label="保存目标">
                    <a-select v-model:value="field.target" :options="orderFormTargetOptions" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="必填">
                    <a-switch
                      v-model:checked="field.required"
                      :checkedValue="true"
                      :unCheckedValue="false"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="最大长度">
                    <a-input-number v-model:value="field.maxLength" :min="1" :max="2048" style="width: 100%" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="12">
                <a-col :xs="24" :md="12">
                  <a-form-item label="占位提示">
                    <a-input v-model:value="field.placeholder" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="12">
                  <a-form-item label="帮助说明">
                    <a-input v-model:value="field.help" />
                  </a-form-item>
                </a-col>
              </a-row>
            </article>
          </div>
          <a-empty v-else class="sku-empty" description="还没有下单字段" />
        </template>
      </section>

      <section v-show="activeSection === 'skus'" class="sku-editor">
        <a-form-item label="SKU模式" name="skuMode">
          <a-radio-group
            v-model:value="formData.skuMode"
            :options="skuModeOptions"
            option-type="button"
            button-style="solid"
          />
        </a-form-item>

        <template v-if="formData.skuMode === 'MULTI'">
          <div class="sku-toolbar">
            <span class="sku-count">{{ formData.skus.length }} 个套餐</span>
            <div class="sku-toolbar-actions">
              <a-button size="small" :disabled="!activeSkuKey" @click="collapseAllSkus">折叠全部</a-button>
              <a-button size="small" :disabled="!formData.skus.length" @click="expandDefaultSku">展开默认</a-button>
              <a-button type="primary" size="small" @click="addSku">
                <template #icon><plus-outlined /></template>
                新增SKU
              </a-button>
            </div>
          </div>

          <a-empty v-if="!formData.skus.length" class="sku-empty" />

          <div
            v-else
            class="sku-editor-list sku-accordion"
            role="list"
          >
            <article
              v-for="(sku, index) in formData.skus"
              :key="skuPanelKey(sku)"
              class="sku-editor-card"
              :class="{ 'is-active': isSkuPanelActive(sku) }"
              role="listitem"
            >
              <div class="sku-panel-head">
                <button
                  type="button"
                  class="sku-panel-toggle"
                  :aria-expanded="isSkuPanelActive(sku)"
                  @click="toggleSkuPanel(sku)"
                >
                  <right-outlined
                    class="sku-panel-arrow"
                    :class="{ open: isSkuPanelActive(sku) }"
                  />
                  <div class="sku-panel-title">
                    <div class="sku-panel-main">
                      <strong>{{ skuPanelTitle(sku, index) }}</strong>
                      <a-tag v-if="sku.defaultSelected" color="green">默认</a-tag>
                      <a-tag :color="sku.enabled ? 'blue' : 'default'">{{ sku.enabled ? '上架' : '下架' }}</a-tag>
                    </div>
                    <div class="sku-panel-meta">
                      <span v-if="skuPanelCode(sku)">编码 {{ skuPanelCode(sku) }}</span>
                      <span>{{ skuPanelPrice(sku) }}</span>
                      <span>{{ skuTypeLabel(sku) }}</span>
                      <span>{{ skuDeliveryModeLabel(sku) }}</span>
                    </div>
                  </div>
                </button>

                <div class="sku-card-extra" @click.stop @mousedown.stop>
                  <a-radio
                    :checked="sku.defaultSelected"
                    @change="() => markSkuDefault(index)"
                  >
                    默认
                  </a-radio>
                  <a-switch
                    v-model:checked="sku.enabled"
                    :checkedValue="true"
                    :unCheckedValue="false"
                    checked-children="上架"
                    un-checked-children="下架"
                  />
                  <div class="sku-card-actions">
                    <a-button
                      size="small"
                      :disabled="index === 0"
                      title="上移"
                      @click="moveSku(index, -1)"
                    >
                      <template #icon><up-outlined /></template>
                    </a-button>
                    <a-button
                      size="small"
                      :disabled="index === formData.skus.length - 1"
                      title="下移"
                      @click="moveSku(index, 1)"
                    >
                      <template #icon><down-outlined /></template>
                    </a-button>
                    <a-button size="small" title="复制" @click="duplicateSku(index)">
                      <template #icon><copy-outlined /></template>
                    </a-button>
                    <a-button
                      size="small"
                      danger
                      :disabled="formData.skus.length <= 1"
                      title="删除"
                      @click="removeSku(index)"
                    >
                      <template #icon><delete-outlined /></template>
                    </a-button>
                  </div>
                </div>
              </div>

              <div v-show="isSkuPanelActive(sku)" class="sku-panel-body">
              <a-row :gutter="12">
                <a-col :xs="24" :md="10">
                  <a-form-item label="SKU名称" required>
                    <a-input v-model:value="sku.name" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="6">
                  <a-form-item label="编码">
                    <a-input v-model:value="sku.code" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="价格" required>
                    <a-input-number
                      v-model:value="sku.price"
                      :min="0"
                      :precision="2"
                      :step="0.01"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
              </a-row>

              <div class="sku-usd-pricing">
                <div class="sku-usd-heading">
                  <span>美元定价</span>
                  <a-segmented
                    v-model:value="sku.usdPriceMode"
                    :options="usdPriceModeOptions"
                    size="small"
                  />
                </div>
                <a-input-number
                  v-if="sku.usdPriceMode === 'MANUAL'"
                  v-model:value="sku.usdPrice"
                  :min="isDonationType(sku.type) ? 1 : 0.01"
                  :precision="2"
                  :step="0.01"
                  addon-before="$"
                />
                <strong v-else>{{ formatUsd(skuDisplayedUsdPrice(sku)) }}</strong>
              </div>

              <a-form-item label="简介">
                <a-textarea
                  v-model:value="sku.description"
                  :auto-size="{ minRows: 2, maxRows: 4 }"
                />
              </a-form-item>

              <a-row :gutter="12">
                <a-col :xs="24" :md="8">
                  <a-form-item label="销售类型">
                    <a-select
                      v-model:value="sku.type"
                      :options="itemTypes"
                      @change="() => handleSkuTypeChange(sku)"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="交付方式">
                    <a-select
                      v-model:value="sku.deliveryMode"
                      :options="skuDeliveryModeOptions(sku)"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="8">
                  <a-form-item label="标签">
                    <a-select
                      v-model:value="sku.tags"
                      mode="tags"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item v-if="skuDeliveryActionOptions(sku).length" label="交付动作">
                <a-checkbox-group
                  v-model:value="sku.deliveryActions"
                  :options="skuDeliveryActionOptions(sku)"
                />
              </a-form-item>

              <a-row :gutter="12">
                <a-col :xs="24" :md="10">
                  <a-form-item label="默认优惠">
                    <a-select
                      v-model:value="sku.couponMode"
                      :options="skuCouponModeOptions"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="14">
                  <a-form-item label="SKU优惠券编码">
                    <a-input
                      v-model:value="sku.defaultCouponCode"
                      :disabled="sku.couponMode !== 'ENABLED'"
                    />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="购买前合同">
                <a-select
                  v-model:value="sku.contractIds"
                  mode="multiple"
                  allow-clear
                  :max-tag-count="'responsive'"
                  :loading="contractLoading"
                  :options="contractOptions"
                />
              </a-form-item>

              <a-row :gutter="12">
                <a-col :xs="24" :md="10">
                  <a-form-item label="英文名称">
                    <a-input v-model:value="sku.i18n['en-US'].name" />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :md="14">
                  <a-form-item label="英文标签">
                    <a-select
                      v-model:value="sku.i18n['en-US'].tags"
                      mode="tags"
                      style="width: 100%"
                    />
                  </a-form-item>
                </a-col>
              </a-row>

              <section class="sku-param-panel">
                <div class="sku-param-head">
                  <strong>交付参数</strong>
                  <div class="sku-param-actions">
                    <a-button size="small" @click="syncSkuParamsFromCurrent(index)">同步商品交付配置</a-button>
                    <a-button
                      v-if="skuLicenseDeliveryEnabled(sku)"
                      size="small"
                      @click="applyViewerBrandRemovalPreset(sku)"
                    >
                      Flyfish Viewer 声明
                    </a-button>
                  </div>
                </div>

                <div v-if="isGitRepositoryAccessType(sku.type)" class="nested-config">
                  <a-row :gutter="12">
                    <a-col :xs="24" :md="8">
                      <a-form-item label="仓库平台">
                        <a-select
                          v-model:value="sku.repositoryAccess.provider"
                          :options="repositoryProviderOptions"
                          @change="() => handleSkuRepositoryProviderChange(sku)"
                        />
                      </a-form-item>
                    </a-col>
                    <a-col :xs="24" :md="16">
                      <a-form-item label="代码仓库">
                        <a-select
                          :value="skuRepositoryKeys(sku)"
                          mode="multiple"
                          show-search
                          allow-clear
                          :max-tag-count="'responsive'"
                          :filter-option="false"
                          :loading="repositoryLoading"
                          :options="skuRepositoryOptions(sku)"
                          @update:value="values => updateSkuRepositoryKeys(sku, values)"
                          @search="keyword => loadRepositoryOptions(keyword, { includeAllProviders: true })"
                          @dropdown-visible-change="open => open && loadRepositoryOptions('', { includeAllProviders: true })"
                        />
                      </a-form-item>
                    </a-col>
                  </a-row>
                </div>

                <div v-if="isDigitalDownloadType(sku.type)" class="nested-config">
                  <a-form-item label="提货标题">
                    <a-input v-model:value="sku.digitalDelivery.title" />
                  </a-form-item>
                  <a-form-item label="提货内容">
                    <a-textarea
                      v-model:value="sku.digitalDelivery.content"
                      :maxlength="4096"
                      :auto-size="{ minRows: 3, maxRows: 6 }"
                    />
                  </a-form-item>
                  <a-form-item label="提货附件">
                    <attachment-upload
                      v-model:value="sku.digitalDelivery.attachments"
                      :max-count="8"
                    />
                  </a-form-item>
                </div>

                <div v-if="skuLicenseDeliveryEnabled(sku)" class="nested-config">
                  <a-row :gutter="12">
                    <a-col :xs="24" :md="12">
                      <a-form-item label="授权类型">
                        <a-select
                          v-model:value="sku.licenseDelivery.licenseKind"
                          :options="licenseKindOptions"
                          @change="handleLicenseKindChange(sku.licenseDelivery)"
                        />
                      </a-form-item>
                    </a-col>
                    <a-col :xs="24" :md="12">
                      <a-form-item label="授权名称">
                        <a-input v-model:value="sku.licenseDelivery.licenseName" />
                      </a-form-item>
                    </a-col>
                  </a-row>
                  <a-alert
                    v-if="isBrandRemovalLicense(sku.licenseDelivery)"
                    class="license-boundary-alert compact"
                    type="info"
                    show-icon
                    message="Apache 2.0 允许修改和移除界面可见品牌；本声明只用于采购与合规留痕，仍需保留版权、许可证和来源说明。"
                  />
                  <a-row :gutter="12">
                    <a-col :xs="24" :md="8">
                      <a-form-item label="授权范围">
                        <a-input v-model:value="sku.licenseDelivery.scope" />
                      </a-form-item>
                    </a-col>
                    <a-col :xs="24" :md="8">
                      <a-form-item label="产品标识">
                        <a-select
                          v-model:value="sku.licenseDelivery.product"
                          :options="licenseProductOptions"
                        />
                      </a-form-item>
                    </a-col>
                    <a-col :xs="24" :md="8">
                      <a-form-item label="授权套餐">
                        <a-select v-model:value="sku.licenseDelivery.edition" :options="licenseEditionOptions" />
                      </a-form-item>
                    </a-col>
                  </a-row>
                  <a-row :gutter="12">
                    <a-col :xs="24" :md="8">
                      <a-form-item label="持有人">
                        <a-input v-model:value="sku.licenseDelivery.holder" />
                      </a-form-item>
                    </a-col>
                    <a-col v-if="!isBrandRemovalLicense(sku.licenseDelivery)" :xs="24" :md="8">
                      <a-form-item label="最大部署数">
                        <a-input-number
                          v-model:value="sku.licenseDelivery.maxDeployments"
                          :min="1"
                          style="width: 100%"
                        />
                      </a-form-item>
                    </a-col>
                    <a-col v-if="!isBrandRemovalLicense(sku.licenseDelivery)" :xs="24" :md="8">
                      <a-form-item label="商业使用">
                        <a-switch
                          v-model:checked="sku.licenseDelivery.commercialUse"
                          :checkedValue="true"
                          :unCheckedValue="false"
                          checked-children="允许"
                          un-checked-children="禁止"
                        />
                      </a-form-item>
                    </a-col>
                    <a-col v-else :xs="24" :md="8">
                      <a-form-item label="声明有效天数">
                        <a-input-number
                          v-model:value="sku.licenseDelivery.validDays"
                          :min="1"
                          placeholder="长期"
                          style="width: 100%"
                        />
                      </a-form-item>
                    </a-col>
                  </a-row>
                  <a-form-item v-if="!isBrandRemovalLicense(sku.licenseDelivery)" label="授权域名">
                    <a-select
                      v-model:value="sku.licenseDelivery.allowedOrigins"
                      mode="tags"
                      placeholder="默认值，可由下单信息覆盖"
                      :token-separators="[',', '，', '\\n', ' ']"
                    />
                  </a-form-item>
                  <a-form-item label="授权功能">
                    <a-checkbox-group
                      v-model:value="sku.licenseDelivery.features"
                      :options="licenseFeatureOptionsFor(sku.licenseDelivery)"
                    />
                  </a-form-item>
                  <a-form-item label="授权说明">
                    <a-input v-model:value="sku.licenseDelivery.remark" />
                  </a-form-item>
                </div>

                <div class="nested-config">
                  <div class="config-toolbar compact">
                    <a-switch
                      v-model:checked="sku.orderForm.enabled"
                      :checkedValue="true"
                      :unCheckedValue="false"
                      checked-children="下单信息"
                      un-checked-children="无表单"
                    />
                    <div class="config-toolbar-actions">
                      <a-button size="small" @click="applyDeploymentOriginOrderForm(sku)">部署域名表单</a-button>
                      <a-button size="small" @click="addOrderFormField(sku.orderForm)">
                        <template #icon><plus-outlined /></template>
                        字段
                      </a-button>
                    </div>
                  </div>
                  <template v-if="sku.orderForm.enabled">
                    <a-row :gutter="12">
                      <a-col :xs="24" :md="10">
                        <a-form-item label="表单标题">
                          <a-input v-model:value="sku.orderForm.title" />
                        </a-form-item>
                      </a-col>
                      <a-col :xs="24" :md="14">
                        <a-form-item label="表单说明">
                          <a-input v-model:value="sku.orderForm.description" />
                        </a-form-item>
                      </a-col>
                    </a-row>
                    <article
                      v-for="(field, fieldIndex) in sku.orderForm.fields"
                      :key="`${sku.localKey}-field-${fieldIndex}`"
                      class="order-form-field compact"
                    >
                      <div class="order-form-field-head">
                        <strong>{{ field.label || field.key || `字段${fieldIndex + 1}` }}</strong>
                        <a-button size="small" danger title="删除字段" @click="removeOrderFormField(sku.orderForm, fieldIndex)">
                          <template #icon><delete-outlined /></template>
                        </a-button>
                      </div>
                      <a-row :gutter="12">
                        <a-col :xs="24" :md="6">
                          <a-form-item label="编码" required>
                            <a-input v-model:value="field.key" />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :md="6">
                          <a-form-item label="名称" required>
                            <a-input v-model:value="field.label" />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :md="6">
                          <a-form-item label="类型">
                            <a-select v-model:value="field.type" :options="orderFormFieldTypeOptions" />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :md="6">
                          <a-form-item label="保存目标">
                            <a-select v-model:value="field.target" :options="orderFormTargetOptions" />
                          </a-form-item>
                        </a-col>
                      </a-row>
                      <a-row :gutter="12">
                        <a-col :xs="24" :md="6">
                          <a-form-item label="必填">
                            <a-switch
                              v-model:checked="field.required"
                              :checkedValue="true"
                              :unCheckedValue="false"
                            />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :md="6">
                          <a-form-item label="最大长度">
                            <a-input-number v-model:value="field.maxLength" :min="1" :max="2048" style="width: 100%" />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :md="6">
                          <a-form-item label="占位提示">
                            <a-input v-model:value="field.placeholder" />
                          </a-form-item>
                        </a-col>
                        <a-col :xs="24" :md="6">
                          <a-form-item label="帮助说明">
                            <a-input v-model:value="field.help" />
                          </a-form-item>
                        </a-col>
                      </a-row>
                    </article>
                  </template>
                </div>

                <a-collapse v-if="sku.extraParamsText" ghost class="advanced-json">
                  <a-collapse-panel key="extra" header="高级扩展参数">
                    <a-textarea
                      v-model:value="sku.extraParamsText"
                      :auto-size="{ minRows: 4, maxRows: 10 }"
                      spellcheck="false"
                    />
                  </a-collapse-panel>
                </a-collapse>
              </section>
              </div>
            </article>
          </div>
        </template>
      </section>

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
        <div class="config-toolbar">
          <span class="config-toolbar-title">授权签发配置</span>
          <div class="config-toolbar-actions">
            <a-button size="small" @click="applyViewerBrandRemovalPreset(formData)">Flyfish Viewer 去品牌声明</a-button>
            <a-button v-if="!brandRemovalSelected" size="small" @click="applyDeploymentOriginOrderForm(formData)">部署域名表单</a-button>
          </div>
        </div>
        <a-alert
          v-if="brandRemovalSelected"
          class="license-boundary-alert"
          type="info"
          show-icon
          message="Apache 2.0 允许修改和移除界面可见品牌标识；本商品签发正式授权声明用于采购与合规留痕，不提供代码级品牌授权接入。使用方仍需保留版权、许可证文本和项目来源说明。"
        />
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权类型" :name="['licenseDelivery', 'licenseKind']">
              <a-select
                v-model:value="formData.licenseDelivery.licenseKind"
                :options="licenseKindOptions"
                @change="handleLicenseKindChange(formData.licenseDelivery)"
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
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权产品" :name="['licenseDelivery', 'product']">
              <a-select
                v-model:value="formData.licenseDelivery.product"
                :options="licenseProductOptions"
              />
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
          v-if="!brandRemovalSelected"
          label="授权域名"
          :name="['licenseDelivery', 'allowedOrigins']"
          required
        >
          <a-select
            v-model:value="formData.licenseDelivery.allowedOrigins"
            mode="tags"
            placeholder="例如 https://demo.example.com 或 https://*.example.com"
            :token-separators="[',', '，', '\\n', ' ']"
          />
        </a-form-item>
        <a-form-item label="授权功能" :name="['licenseDelivery', 'features']">
          <a-checkbox-group
            v-model:value="formData.licenseDelivery.features"
            :options="licenseFeatureOptions"
          />
        </a-form-item>
        <a-row v-if="!brandRemovalSelected" :gutter="16">
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
        <a-row v-else :gutter="16">
          <a-col :xs="24" :sm="8">
            <a-form-item label="声明有效天数" :name="['licenseDelivery', 'validDays']">
              <a-input-number
                v-model:value="formData.licenseDelivery.validDays"
                :min="1"
                placeholder="长期"
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

      <section v-show="activeSection === 'i18n'" class="delivery-config i18n-config">
        <a-alert
          class="i18n-hint"
          type="info"
          show-icon
          :message="t('manage.chineseSourceHint')"
        />
        <a-form-item :label="t('manage.englishName')" :name="['i18n', 'en-US', 'name']">
          <a-input v-model:value="formData.i18n['en-US'].name" />
        </a-form-item>
        <a-form-item :label="t('manage.englishTags')" :name="['i18n', 'en-US', 'tags']">
          <a-select
            v-model:value="formData.i18n['en-US'].tags"
            mode="tags"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item :label="t('manage.englishDescription')" :name="['i18n', 'en-US', 'description']">
          <ShopMarkdownEditor
            v-if="visible && activeSection === 'i18n'"
            v-model="formData.i18n['en-US'].description"
            class="markdown-editor"
            language="en-US"
            preview-theme="default"
            code-theme="github"
            :style="{ height: '460px' }"
            :on-upload-img="handleMarkdownImageUpload"
          />
        </a-form-item>
      </section>
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

.usd-pricing-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid #dce9e1;
  border-radius: 8px;
  background: #f8fcfa;
}

.usd-pricing-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 14px;

  > div {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong {
    color: #203626;
    font-size: 14px;
  }

  span {
    color: #718078;
    font-size: 12px;
  }
}

.usd-pricing-value {
  min-width: 150px;

  :deep(.ant-input-number-group-wrapper),
  :deep(.ant-input-number) {
    width: 100%;
  }
}

.usd-price-preview {
  display: grid;
  justify-items: end;
  gap: 2px;

  span {
    color: #718078;
    font-size: 12px;
  }

  strong {
    color: #1565c0;
    font-size: 20px;
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

.config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  margin-bottom: 14px;

  &.compact {
    margin-bottom: 10px;
  }
}

.config-toolbar-title {
  color: #263746;
  font-weight: 600;
}

.config-toolbar-actions,
.sku-toolbar-actions,
.sku-param-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.order-form-fields {
  display: grid;
  gap: 12px;
}

.order-form-field {
  min-width: 0;
  padding: 12px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fff;

  &.compact {
    margin-top: 10px;
  }
}

.order-form-field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #263746;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.sku-editor {
  min-width: 0;
}

.sku-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.sku-count {
  color: #52616f;
  font-size: 13px;
}

.sku-empty {
  padding: 28px 0;
  border: 1px dashed #dfe9e3;
  border-radius: 8px;
  background: #fbfdfc;
}

.sku-editor-list {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.sku-editor-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  background: #fbfdff;

  &.is-active {
    border-color: #b8d9c2;
    background: #fff;
    box-shadow: 0 8px 22px rgba(20, 83, 45, 0.07);
  }
}

.sku-accordion {
  background: transparent;
}

.sku-panel-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 12px 14px;
  background: #fbfdff;
}

.sku-panel-toggle {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.sku-panel-arrow {
  color: #607080;
  font-size: 12px;
  transition: transform 0.18s ease;

  &.open {
    transform: rotate(90deg);
  }
}

.sku-panel-body {
  min-width: 0;
  padding: 14px;
  border-top: 1px solid #e7eef5;
  background: #fff;
}

.sku-usd-pricing {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(130px, 180px);
  gap: 12px;
  align-items: center;
  margin: -2px 0 16px;
  padding: 10px 12px;
  border: 1px solid #e0e8ef;
  border-radius: 8px;
  background: #f8fafc;

  > strong {
    justify-self: end;
    color: #1565c0;
    font-size: 16px;
  }

  :deep(.ant-input-number-group-wrapper),
  :deep(.ant-input-number) {
    width: 100%;
  }
}

.sku-usd-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  > span {
    color: #405261;
    font-size: 13px;
    font-weight: 600;
  }
}

.sku-panel-title {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.sku-panel-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #263746;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.sku-panel-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  min-width: 0;
  color: #6b7785;
  font-size: 12px;
  line-height: 1.4;

  span {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.sku-card-extra {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.sku-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;

  :deep(.ant-btn) {
    width: 30px;
    padding-inline: 0;
  }
}

.sku-param-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.sku-param-panel {
  min-width: 0;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid #e5edf6;
  border-radius: 8px;
  background: #fff;
}

.nested-config {
  min-width: 0;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.license-boundary-alert {
  margin-bottom: 16px;
  text-align: left;

  &.compact {
    margin: 0 0 12px;
  }
}

.advanced-json {
  margin-top: 10px;
}

.i18n-config {
  .i18n-hint {
    margin-bottom: 16px;
    text-align: left;
  }
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

  .usd-pricing-panel,
  .sku-usd-pricing {
    grid-template-columns: minmax(0, 1fr);
  }

  .usd-pricing-heading,
  .sku-usd-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .usd-price-preview {
    justify-items: start;
  }

  .sku-usd-pricing > strong {
    justify-self: start;
  }

  .delivery-config {
    padding: 12px;
  }

  .config-toolbar,
  .sku-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .config-toolbar-actions,
  .sku-toolbar-actions,
  .sku-param-actions {
    justify-content: flex-start;
  }

  .sku-panel-head {
    grid-template-columns: minmax(0, 1fr);
    align-items: flex-start;
  }

  .sku-panel-meta span {
    max-width: 100%;
  }

  .sku-card-extra {
    align-items: flex-start;
    flex-direction: column;
  }

  .sku-param-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .sku-card-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
    overflow-x: auto;
  }

  .markdown-editor {
    height: 360px !important;
  }
}
</style>
