<script setup>
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { getShopItemGroups, getShopItemDetail } from '../apis/api';
import { message, Upload } from 'ant-design-vue';
import AttachmentUpload from '@/components/Attachments/AttachmentUpload.vue';
import { getGitRepositoryOptions, uploadImage } from '../apis/manage';
import {
  getShopItemDefaultCover,
  isGitRepositoryAccessType,
  isGitRepositoryDonationAccessType
} from '@/modules/shop/utils/shopCovers.js';
import {
  SHOP_TYPE_OPTIONS,
  defaultDeliveryModeForType,
  deliveryModeOptionsForType,
  isDigitalDownloadType,
  isLicenseType,
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

const GIT_REPOSITORY_ACCESS = 'GIT_REPOSITORY_ACCESS';
const ShopMarkdownEditor = defineAsyncComponent(() => import('../components/ShopMarkdownEditor.vue'));

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
  sort: 0,
  type: GIT_REPOSITORY_ACCESS,
  deliveryMode: defaultDeliveryModeForType(GIT_REPOSITORY_ACCESS),
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
    licenseName: '',
    scope: '',
    validDays: null,
    remark: ''
  }
});
const loading = ref(false);
const repositoryLoading = ref(false);
const repositoryOptions = ref([]);
const repositoryProvider = ref('github');

const groups = ref([]);

const itemTypes = SHOP_TYPE_OPTIONS;
const repositoryProviderOptions = GIT_REPOSITORY_PROVIDER_OPTIONS;

const deliveryModeOptions = computed(() => deliveryModeOptionsForType(formData.value.type));
const deliveryModeLocked = computed(() => deliveryModeOptions.value.length === 1);
const priceLabel = computed(() => isGitRepositoryDonationAccessType(formData.value.type) ? '最低打赏金额' : '商品价格');

const defaults = () => ({
  fileList: [],
  enabled: false,
  pinned: false,
  recommended: false,
  sort: 0,
  type: GIT_REPOSITORY_ACCESS,
  deliveryMode: defaultDeliveryModeForType(GIT_REPOSITORY_ACCESS),
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
    licenseName: '',
    scope: '',
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
  licenseName: '',
  scope: '',
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
  licenseName: value?.licenseName || formData.value.name || '飞鱼小铺授权许可',
  scope: value?.scope || `product:${formData.value.name || 'flyfish'}`,
  validDays: value?.validDays || null,
  remark: value?.remark || ''
});

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
  if (isGitRepositoryAccessType(type)) {
    loadRepositoryOptions();
  }
});

const loadGroups = async () => {
  groups.value = await getShopItemGroups();
};

// 加载商品详情
const loadItemDetail = async (id) => {
  loading.value = true;
  try {
    const detail = await getShopItemDetail(id);
    formData.value = {
      ...detail,
      type: isGitRepositoryAccessType(detail.type) ? GIT_REPOSITORY_ACCESS : detail.type,
      deliveryMode: normalizeDeliveryModeForType(detail.type, detail.deliveryMode),
      enabled: detail.enabled !== false,
      pinned: detail.pinned === true,
      recommended: detail.recommended === true,
      sort: detail.sort || 0,
      repositoryAccess: parseGitRepositoryAccessParams(detail.params),
      digitalDelivery: parseDigitalDeliveryParams(detail.params),
      licenseDelivery: parseLicenseDeliveryParams(detail.params),
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

const handleOk = () => {
  form.value.validate().then(() => {
    const {
      fileList = [],
      repositoryAccess: repositoryAccessParams,
      digitalDelivery,
      licenseDelivery,
      ...rest
    } = formData.value;

    // 处理图片数据
    const images = fileList
      .filter(file => file?.status === 'done')
      .map(file => file.url)
      .filter(Boolean);

    const deliveryMode = normalizeDeliveryModeForType(rest.type, rest.deliveryMode);
    const repositoryAccess = toGitRepositoryAccessParams({
      ...repositoryAccessParams,
      provider: repositoryProvider.value
    });
    const params = (() => {
      if (isGitRepositoryAccessType(rest.type)) {
        return JSON.stringify(repositoryAccess);
      }
      if (isDigitalDownloadType(rest.type)) {
        return JSON.stringify(toDigitalDeliveryParams(digitalDelivery));
      }
      if (isLicenseType(rest.type)) {
        return JSON.stringify(toLicenseDeliveryParams(licenseDelivery));
      }
      return null;
    })();

    emit('save', {
      ...rest,
      deliveryMode,
      params,
      tags: rest.tags || [],
      sort: rest.sort || 0,
      enabled: rest.enabled === true,
      pinned: rest.pinned === true,
      recommended: rest.recommended === true,
      images,
      cover: images[0] || null
    });
  });
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
    width="720px"
  >
    <a-form
      ref="form"
      :model="formData"
      :rules="{
        name: [{ required: true, message: '请输入商品名称' }],
        price: [{ required: true, message: '请输入商品价格' }],
        groupId: [{ required: true, message: '请选择商品分组' }],
        description: [{ required: true, message: '请输入商品描述' }]
      }"
      layout="vertical"
    >
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="商品名称" name="name">
            <a-input v-model:value="formData.name" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
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

      <a-row :gutter="16">
        <a-col :span="12">
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
        <a-col :span="12">
          <a-form-item label="商品标签" name="tags">
            <a-select
              v-model:value="formData.tags"
              mode="tags"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="12">
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
        <a-col :span="12">
          <a-form-item label="交付方式" name="deliveryMode" :rules="[{ required: true, message: '请选择交付方式' }]">
            <a-select
              v-model:value="formData.deliveryMode"
              :options="deliveryModeOptions"
              :disabled="deliveryModeLocked"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
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

      <a-row v-if="isGitRepositoryAccessType(formData.type)" :gutter="16">
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

      <div v-if="isGitRepositoryAccessType(formData.type) && repositoryAccessPreview.length" class="repo-preview">
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

      <section v-if="isDigitalDownloadType(formData.type)" class="delivery-config">
        <a-form-item label="提货标题">
          <a-input v-model:value="formData.digitalDelivery.title" />
        </a-form-item>
        <a-form-item label="提货内容">
          <a-textarea
            v-model:value="formData.digitalDelivery.content"
            :maxlength="4096"
            :auto-size="{ minRows: 4, maxRows: 8 }"
          />
        </a-form-item>
        <a-form-item label="提货附件">
          <attachment-upload
            v-model:value="formData.digitalDelivery.attachments"
            :max-count="8"
          />
        </a-form-item>
      </section>

      <section v-if="isLicenseType(formData.type)" class="delivery-config">
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权名称">
              <a-input v-model:value="formData.licenseDelivery.licenseName" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权范围">
              <a-input v-model:value="formData.licenseDelivery.scope" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12">
            <a-form-item label="有效天数">
              <a-input-number
                v-model:value="formData.licenseDelivery.validDays"
                :min="1"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12">
            <a-form-item label="授权说明">
              <a-input v-model:value="formData.licenseDelivery.remark" />
            </a-form-item>
          </a-col>
        </a-row>
      </section>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="排序" name="sort">
            <a-input-number v-model:value="formData.sort" :min="0" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item label="商品图片" name="fileList">
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

      <a-form-item label="商品描述" name="description">
        <ShopMarkdownEditor
          v-if="visible"
          v-model="formData.description"
          class="markdown-editor"
          language="zh-CN"
          preview-theme="default"
          code-theme="github"
          :style="{ height: '520px' }"
          :on-upload-img="handleMarkdownImageUpload"
        />
      </a-form-item>
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
</style>
