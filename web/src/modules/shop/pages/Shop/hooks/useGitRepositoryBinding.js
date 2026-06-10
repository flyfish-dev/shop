import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  getGiteaAuthorization,
  getGiteaDisplay,
  getGiteeAuthorization,
  getGiteeDisplay,
  getGithubAuthorization,
  getGithubDisplay
} from '@/modules/auth/authority.js';
import { parseGitRepositoryAccessParams, normalizeGitProvider } from '@/modules/shop/utils/gitRepositoryAccess.js';
import { isGitRepositoryAccessType } from '@/modules/shop/utils/shopCovers.js';
import { prepareGitBinding } from '../apis/api.js';

const PROVIDER_NAMES = {
  github: 'GitHub',
  gitea: '飞鱼开源',
  gitee: '码云'
};

const providerName = provider => PROVIDER_NAMES[provider] || 'Git';

const uniqueProviders = params => {
  const providers = (params.repositories || [])
    .map(repository => normalizeGitProvider(repository.provider, params.provider))
    .filter(Boolean);
  if (!providers.length && params.provider) {
    providers.push(normalizeGitProvider(params.provider));
  }
  return Array.from(new Set(providers));
};

const parseItemParams = item => {
  const params = item?.params;
  if (!params) {
    return {};
  }
  if (typeof params === 'object') {
    return params;
  }
  try {
    let parsed = params;
    let depth = 0;
    while (typeof parsed === 'string' && depth < 3) {
      parsed = JSON.parse(parsed);
      depth += 1;
    }
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

export function useGitRepositoryBinding({ item, user, store, router }) {
  const bindingLoading = ref(false);

  const itemParams = computed(() => parseGitRepositoryAccessParams(parseItemParams(item.value)));
  const gitProviders = computed(() => uniqueProviders(itemParams.value));
  const authorizationOf = provider => {
    if (provider === 'github') {
      return getGithubAuthorization(user.value);
    }
    if (provider === 'gitee') {
      return getGiteeAuthorization(user.value);
    }
    return getGiteaAuthorization(user.value);
  };
  const displayOf = provider => {
    if (provider === 'github') {
      return getGithubDisplay(user.value);
    }
    if (provider === 'gitee') {
      return getGiteeDisplay(user.value);
    }
    return getGiteaDisplay(user.value);
  };
  const missingProviders = computed(() => gitProviders.value.filter(provider => !authorizationOf(provider)));
  const gitProvider = computed(() => missingProviders.value[0] || gitProviders.value[0] || 'gitea');
  const gitProviderName = computed(() => providerName(gitProvider.value));
  const missingProviderNames = computed(() => missingProviders.value.map(providerName));
  const gitBindTitle = computed(() => {
    const count = itemParams.value.repositories?.length || 0;
    const names = gitProviders.value.map(providerName).join(' / ') || gitProviderName.value;
    return `${names}${count > 1 ? '多仓库' : '仓库'}绑定`;
  });
  const gitAuthorization = computed(() => gitProviders.value.length > 0
    && gitProviders.value.every(provider => authorizationOf(provider)));
  const gitAccount = computed(() => displayOf(gitProvider.value));
  const gitBindingReminderVisible = computed(() => (
    isGitRepositoryAccessType(item.value?.type) && !gitAuthorization.value
  ));
  const gitBindingReminderTitle = computed(() => {
    const names = missingProviderNames.value.join('、') || gitProviderName.value;
    return `请先绑定 ${names} 账号`;
  });
  const gitBindingReminderDescription = computed(() => (
    '源码开通类商品，必须先绑定对应 Git 平台账号才能购买，请根据下方卡片绑定后完成购买。'
  ));

  const authorize = async () => {
    store.rememberRedirect(location.pathname + location.search);
    if (!user.value?.id) {
      router.push('/login');
      return;
    }
    if (bindingLoading.value) {
      return;
    }
    bindingLoading.value = true;
    try {
      const url = await prepareGitBinding(gitProvider.value);
      location.href = url || `/oauth/${gitProvider.value}`;
    } catch (e) {
      message.error(e.message || '发起账号绑定失败');
    } finally {
      bindingLoading.value = false;
    }
  };

  const validateGitCheckout = () => {
    if (isGitRepositoryAccessType(item.value?.type) && !gitAuthorization.value) {
      const names = missingProviders.value.map(providerName).join('、') || gitProviderName.value;
      message.warning(`请先绑定 ${names} 账号`);
      return false;
    }
    return true;
  };

  return {
    bindingLoading,
    gitProvider,
    gitProviderName,
    gitBindTitle,
    gitAuthorization,
    gitAccount,
    gitBindingReminderVisible,
    gitBindingReminderTitle,
    gitBindingReminderDescription,
    authorize,
    validateGitCheckout
  };
}
