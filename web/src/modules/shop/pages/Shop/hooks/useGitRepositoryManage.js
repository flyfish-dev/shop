import { computed, reactive, ref } from 'vue';
import dayjs from 'dayjs';
import { message } from 'ant-design-vue';
import {
  createGitAccessToken,
  createManagedGitRepository,
  deleteGitAccessToken,
  deleteManagedGitRepository,
  getGitAccessTokens,
  getManagedGitRepositories,
  getRemoteGitRepositories,
  syncManagedGitRepositories,
  updateGitAccessToken,
  updateManagedGitRepository
} from '../apis/manage.js';

export const gitProviders = [
  { label: 'GitHub', value: 'github' },
  { label: 'Gitea', value: 'gitea' },
  { label: '码云', value: 'gitee' }
];

export const permissionOptions = provider => {
  if (provider === 'github') {
    return [
      { label: '只读 Pull', value: 'pull' },
      { label: '分诊 Triage', value: 'triage' },
      { label: '写入 Push', value: 'push' },
      { label: '维护 Maintain', value: 'maintain' },
      { label: '管理员 Admin', value: 'admin' }
    ];
  }
  if (provider === 'gitee') {
    return [
      { label: '只读 Pull', value: 'pull' },
      { label: '写入 Push', value: 'push' },
      { label: '管理员 Admin', value: 'admin' }
    ];
  }
  return [
    { label: '只读', value: 'read' },
    { label: '写入', value: 'write' },
    { label: '管理员', value: 'admin' }
  ];
};

export function useGitRepositoryManage() {
  const loading = ref(false);
  const saving = ref(false);
  const tokenLoading = ref(false);
  const remoteLoading = ref(false);
  const syncing = ref(false);
  const repositories = ref([]);
  const tokens = ref([]);
  const remoteRepositories = ref([]);
  const provider = ref('github');
  const keyword = ref('');
  const syncTokenId = ref();

  const repositoryForm = reactive({
    provider: 'github',
    accessTokenId: undefined,
    remoteKey: undefined,
    owner: '',
    repo: '',
    name: '',
    description: '',
    permission: 'pull',
    url: '',
    privateRepo: true,
    expireTime: null,
    enabled: true,
    sort: 0
  });

  const tokenForm = reactive({
    provider: 'github',
    name: '',
    description: '',
    tokenValue: '',
    username: '',
    expireTime: null,
    enabled: true,
    sort: 0
  });

  const tokenOptions = computed(() => tokens.value.map(token => ({
    label: `${token.name}${token.username ? ` · ${token.username}` : ''}`,
    value: token.id
  })));

  const syncTokenOptions = computed(() => tokens.value
    .filter(token => token.provider === provider.value && token.enabled && !token.expired)
    .map(token => ({
      label: `${token.name}${token.username ? ` · ${token.username}` : ''}`,
      value: token.id
    })));

  const remoteOptions = computed(() => remoteRepositories.value.map(repo => ({
    label: `${repo.fullName}${repo.privateRepo ? ' · 私有' : ''}`,
    value: repo.fullName,
    repo
  })));

  const loadRepositories = async () => {
    loading.value = true;
    try {
      repositories.value = await getManagedGitRepositories({
        provider: provider.value || undefined,
        keyword: keyword.value || undefined,
        includeDisabled: true
      });
    } finally {
      loading.value = false;
    }
  };

  const loadTokens = async (nextProvider = provider.value || 'github') => {
    tokenLoading.value = true;
    try {
      tokens.value = await getGitAccessTokens({ provider: nextProvider });
      if (nextProvider === provider.value) {
        const currentUsable = tokens.value.some(token => token.id === syncTokenId.value && token.enabled && !token.expired);
        if (!currentUsable) {
          syncTokenId.value = tokens.value.find(token => token.enabled && !token.expired)?.id;
        }
      }
    } finally {
      tokenLoading.value = false;
    }
  };

  const loadRemoteRepositories = async (q = '') => {
    if (!repositoryForm.provider || !repositoryForm.accessTokenId) {
      remoteRepositories.value = [];
      return;
    }
    remoteLoading.value = true;
    try {
      remoteRepositories.value = await getRemoteGitRepositories({
        provider: repositoryForm.provider,
        tokenId: repositoryForm.accessTokenId,
        q: q || undefined,
        size: 100
      });
    } catch (e) {
      remoteRepositories.value = [];
      message.error(e.message || '远程仓库加载失败');
    } finally {
      remoteLoading.value = false;
    }
  };

  const resetRepositoryForm = (record = null) => {
    Object.assign(repositoryForm, {
      provider: record?.provider || provider.value || 'github',
      accessTokenId: record?.accessTokenId,
      remoteKey: record?.fullName,
      owner: record?.owner || '',
      repo: record?.repo || '',
      name: record?.name || '',
      description: record?.description || '',
      permission: record?.permission || permissionOptions(record?.provider || provider.value || 'github')[0].value,
      url: record?.url || '',
      privateRepo: record?.privateRepo !== false,
      expireTime: record?.expireTime || null,
      enabled: record?.enabled !== false,
      sort: record?.sort || 0
    });
    remoteRepositories.value = record?.fullName ? [record] : [];
  };

  const resetTokenForm = (record = null, nextProvider = provider.value || 'github') => {
    Object.assign(tokenForm, {
      provider: record?.provider || nextProvider,
      name: record?.name || '',
      description: record?.description || '',
      tokenValue: '',
      username: record?.username || '',
      expireTime: record?.expireTime || null,
      enabled: record?.enabled !== false,
      sort: record?.sort || 0
    });
  };

  const applyRemoteRepository = fullName => {
    const selected = remoteOptions.value.find(option => option.value === fullName)?.repo;
    if (!selected) {
      return;
    }
    Object.assign(repositoryForm, {
      owner: selected.owner,
      repo: selected.repo,
      name: repositoryForm.name || selected.fullName,
      permission: selected.permission || permissionOptions(repositoryForm.provider)[0].value,
      url: selected.url || '',
      privateRepo: selected.privateRepo !== false
    });
  };

  const repositoryPayload = () => ({
    provider: repositoryForm.provider,
    accessTokenId: repositoryForm.accessTokenId,
    owner: repositoryForm.owner?.trim(),
    repo: repositoryForm.repo?.trim(),
    name: repositoryForm.name?.trim(),
    description: repositoryForm.description?.trim() || null,
    permission: repositoryForm.permission,
    url: repositoryForm.url?.trim() || null,
    privateRepo: repositoryForm.privateRepo,
    expireTime: repositoryForm.expireTime ? dayjs(repositoryForm.expireTime).format('YYYY-MM-DD HH:mm:ss') : null,
    enabled: repositoryForm.enabled,
    sort: repositoryForm.sort || 0
  });

  const tokenPayload = () => ({
    provider: tokenForm.provider,
    name: tokenForm.name?.trim(),
    description: tokenForm.description?.trim() || null,
    tokenValue: tokenForm.tokenValue?.trim() || undefined,
    username: tokenForm.username?.trim() || null,
    expireTime: tokenForm.expireTime ? dayjs(tokenForm.expireTime).format('YYYY-MM-DD HH:mm:ss') : null,
    enabled: tokenForm.enabled,
    sort: tokenForm.sort || 0
  });

  const saveRepository = async id => {
    saving.value = true;
    try {
      if (id) {
        await updateManagedGitRepository(id, repositoryPayload());
      } else {
        await createManagedGitRepository(repositoryPayload());
      }
      await loadRepositories();
    } finally {
      saving.value = false;
    }
  };

  const syncRepositories = async () => {
    if (!syncTokenId.value) {
      message.warning('请先选择 API Token');
      return null;
    }
    syncing.value = true;
    try {
      const result = await syncManagedGitRepositories({
        provider: provider.value,
        accessTokenId: syncTokenId.value
      });
      await loadRepositories();
      return result;
    } finally {
      syncing.value = false;
    }
  };

  const saveToken = async id => {
    saving.value = true;
    try {
      if (id) {
        await updateGitAccessToken(id, tokenPayload());
      } else {
        await createGitAccessToken(tokenPayload());
      }
      await loadTokens(tokenForm.provider);
    } finally {
      saving.value = false;
    }
  };

  const removeRepository = async record => {
    await deleteManagedGitRepository(record.id);
    await loadRepositories();
  };

  const removeToken = async record => {
    await deleteGitAccessToken(record.id);
    await loadTokens(record.provider);
  };

  return {
    loading,
    saving,
    tokenLoading,
    remoteLoading,
    syncing,
    repositories,
    tokens,
    provider,
    keyword,
    syncTokenId,
    repositoryForm,
    tokenForm,
    tokenOptions,
    syncTokenOptions,
    remoteOptions,
    loadRepositories,
    loadTokens,
    loadRemoteRepositories,
    resetRepositoryForm,
    resetTokenForm,
    applyRemoteRepository,
    saveRepository,
    syncRepositories,
    saveToken,
    removeRepository,
    removeToken
  };
}
