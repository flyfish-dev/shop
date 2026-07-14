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
import { useI18n } from 'vue-i18n';

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
  const { t } = useI18n();
  const bindingLoading = ref(false);
  const providerName = provider => {
    if (provider === 'gitea') {
      return t('shop.gitBinding.sourceName');
    }
    if (provider === 'gitee') {
      return t('shop.gitBinding.giteeName');
    }
    return provider === 'github' ? 'GitHub' : 'Git';
  };

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
    return t(count > 1
      ? 'shop.gitBinding.multipleRepositories'
      : 'shop.gitBinding.singleRepository', { providers: names });
  });
  const gitAuthorization = computed(() => gitProviders.value.length > 0
    && gitProviders.value.every(provider => authorizationOf(provider)));
  const gitAccount = computed(() => displayOf(gitProvider.value));
  const gitBindingReminderVisible = computed(() => (
    isGitRepositoryAccessType(item.value?.type) && !gitAuthorization.value
  ));
  const gitBindingReminderTitle = computed(() => {
    const names = missingProviderNames.value.join(' / ') || gitProviderName.value;
    return t('shop.gitBinding.reminderTitle', { providers: names });
  });
  const gitBindingReminderDescription = computed(() => (
    t('shop.gitBinding.reminderDescription')
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
      store.clearOAuthLoginRedirect();
      const url = await prepareGitBinding(gitProvider.value);
      location.href = url || `/oauth/${gitProvider.value}`;
    } catch (e) {
      message.error(e.message || t('shop.gitBinding.startFailed'));
    } finally {
      bindingLoading.value = false;
    }
  };

  const validateGitCheckout = () => {
    if (isGitRepositoryAccessType(item.value?.type) && !gitAuthorization.value) {
      const names = missingProviders.value.map(providerName).join(' / ') || gitProviderName.value;
      message.warning(t('shop.gitBinding.reminderTitle', { providers: names }));
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
