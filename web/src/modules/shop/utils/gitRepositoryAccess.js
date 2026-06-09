const PROVIDER_GITEA = 'gitea';
const PROVIDER_GITHUB = 'github';
const PROVIDER_GITEE = 'gitee';

export const GIT_REPOSITORY_PROVIDER_OPTIONS = [
  { label: 'GitHub', value: PROVIDER_GITHUB },
  { label: 'Gitea', value: PROVIDER_GITEA },
  { label: '码云', value: PROVIDER_GITEE }
];

const trim = value => (typeof value === 'string' ? value.trim() : '');

const cleanRepo = value => trim(value).replace(/\.git$/, '');

const resolveProvider = value => {
  const provider = trim(value).toLowerCase();
  if (provider.includes(PROVIDER_GITHUB)) return PROVIDER_GITHUB;
  if (provider.includes(PROVIDER_GITEE)) return PROVIDER_GITEE;
  if (provider.includes(PROVIDER_GITEA)) return PROVIDER_GITEA;
  return '';
};

export const normalizeGitProvider = (value, fallback = PROVIDER_GITEA) => resolveProvider(value) || fallback;

export const defaultGitPermission = provider => (
  [PROVIDER_GITHUB, PROVIDER_GITEE].includes(normalizeGitProvider(provider)) ? 'pull' : 'read'
);

const normalizePermission = (provider, value) => {
  const permission = trim(value).toLowerCase();
  if (provider === PROVIDER_GITHUB) {
    if (permission === 'read') return 'pull';
    if (permission === 'write') return 'push';
    return ['triage', 'pull', 'push', 'maintain', 'admin'].includes(permission) ? permission : 'pull';
  }
  if (provider === PROVIDER_GITEE) {
    if (permission === 'read') return 'pull';
    if (permission === 'write') return 'push';
    return ['pull', 'push', 'admin'].includes(permission) ? permission : 'pull';
  }
  if (permission === 'pull') return 'read';
  if (permission === 'push') return 'write';
  return ['read', 'write', 'admin'].includes(permission) ? permission : 'read';
};

const parseNestedJson = params => {
  let value = params;
  for (let i = 0; i < 4 && typeof value === 'string' && value.trim(); i += 1) {
    try {
      value = JSON.parse(value);
    } catch (e) {
      break;
    }
  }
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
};

const inferProvider = source => {
  const sourceProvider = resolveProvider(source?.provider);
  if (sourceProvider) {
    return sourceProvider;
  }
  const repositoryProvider = (source?.repositories || [])
    .map(repository => resolveProvider(repository?.provider))
    .find(Boolean);
  return repositoryProvider || PROVIDER_GITEA;
};

const repositoryKey = repository => {
  if (repository?.repositoryId) {
    return `id:${repository.repositoryId}`;
  }
  const owner = trim(repository?.owner);
  const repo = cleanRepo(repository?.repo);
  return owner && repo ? `${normalizeGitProvider(repository?.provider)}:${owner.toLowerCase()}/${repo.toLowerCase()}` : '';
};

export const gitRepositoryValue = repository => repositoryKey({
  repositoryId: repository?.repositoryId || repository?.id,
  provider: repository?.provider,
  owner: repository?.owner,
  repo: repository?.repo
});

export const normalizeGitRepositoryAccess = value => {
  const source = value && typeof value === 'object' ? value : {};
  const provider = inferProvider(source);
  const fallbackPermission = normalizePermission(provider, source.permission);
  const repositories = [];
  const pushRepository = repository => {
    const repositoryId = Number(repository?.repositoryId || repository?.id || 0) || null;
    const repositoryProvider = normalizeGitProvider(repository?.provider, provider);
    const owner = trim(repository?.owner);
    const repo = cleanRepo(repository?.repo);
    if (!repositoryId && (!owner || !repo)) {
      return;
    }
    const normalized = {
      repositoryId,
      provider: repositoryProvider,
      owner,
      repo,
      name: trim(repository?.name),
      permission: normalizePermission(repositoryProvider, repository?.permission || fallbackPermission)
    };
    const key = repositoryKey(normalized);
    if (key && !repositories.some(item => repositoryKey(item) === key)) {
      repositories.push(normalized);
    }
  };
  if (Array.isArray(source.repositories)) {
    source.repositories.forEach(pushRepository);
  }
  if (Array.isArray(source.repositoryIds)) {
    source.repositoryIds.forEach(id => pushRepository({ repositoryId: id, provider, permission: fallbackPermission }));
  }
  pushRepository({
    owner: source.owner,
    repo: source.repo,
    provider,
    permission: fallbackPermission
  });
  const primaryProvider = repositories[0]?.provider || provider;
  return {
    provider: primaryProvider,
    permission: normalizePermission(primaryProvider, source.permission || fallbackPermission),
    repositoryIds: repositories.map(repository => repository.repositoryId).filter(Boolean),
    repositories
  };
};

export const parseGitRepositoryAccessParams = params => normalizeGitRepositoryAccess(parseNestedJson(params));

export const isGitRepositoryConfigured = value => normalizeGitRepositoryAccess(value).repositories.length > 0;

export const toGitRepositoryKeys = value => normalizeGitRepositoryAccess(value)
  .repositories
  .map(gitRepositoryValue);

export const toGitRepositoryAccessParams = value => {
  const normalized = normalizeGitRepositoryAccess(value);
  return {
    provider: normalized.provider,
    repositoryIds: normalized.repositoryIds,
    repositories: normalized.repositories
  };
};
