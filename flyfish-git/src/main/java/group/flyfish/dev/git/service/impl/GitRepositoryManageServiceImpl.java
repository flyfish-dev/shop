package group.flyfish.dev.git.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.git.client.GitRepositoryApiRepo;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.git.domain.dto.GitRepositoryCreateDto;
import group.flyfish.dev.git.domain.dto.GitRepositorySyncDto;
import group.flyfish.dev.git.domain.dto.GitRepositoryUpdateDto;
import group.flyfish.dev.git.domain.po.GitAccessToken;
import group.flyfish.dev.git.domain.po.GitManagedRepository;
import group.flyfish.dev.git.domain.vo.GitManagedRepositoryVo;
import group.flyfish.dev.git.domain.vo.GitRepositorySyncResultVo;
import group.flyfish.dev.git.repository.GitAccessTokenRepository;
import group.flyfish.dev.git.repository.GitManagedRepositoryRepository;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.git.service.GitRepositoryManageService;
import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class GitRepositoryManageServiceImpl implements GitRepositoryManageService {

    private static final int REMOTE_PAGE_SIZE = 100;
    private static final int REMOTE_SYNC_MAX_PAGES = 20;

    private final GitManagedRepositoryRepository repositoryRepository;
    private final GitAccessTokenRepository tokenRepository;
    private final GitAccessTokenService tokenService;
    private final GiteaRepositoryClient giteaClient;
    private final GithubRepositoryClient githubClient;
    private final GiteeRepositoryClient giteeClient;

    @Override
    public Flux<GitManagedRepositoryVo> list(String provider, String keyword, boolean includeDisabled) {
        String normalizedProvider = StringUtils.isBlank(provider) ? null : GitProvider.normalizeCode(provider);
        Flux<GitAccessToken> tokenFlux = normalizedProvider == null
                ? tokenRepository.findAll()
                : tokenRepository.findAllByProvider(normalizedProvider);
        return repositoryRepository.findList(normalizedProvider, StringUtils.trimToNull(keyword), includeDisabled)
                .collectList()
                .zipWith(tokenFlux.collectList().defaultIfEmpty(List.of()))
                .flatMapMany(tuple -> {
                    Map<Long, String> tokenNames = tuple.getT2().stream()
                            .filter(token -> token.getId() != null)
                            .collect(java.util.stream.Collectors.toMap(GitAccessToken::getId, GitAccessToken::getName,
                                    (left, right) -> left));
                    return Flux.fromIterable(tuple.getT1()).map(repository -> toVo(repository, tokenNames));
                });
    }

    @Override
    public Flux<GitRepositoryOptionVo> listOptions(String provider, String keyword) {
        String normalizedProvider = StringUtils.isBlank(provider) ? null : GitProvider.normalizeCode(provider);
        return repositoryRepository.findList(normalizedProvider, StringUtils.trimToNull(keyword), false)
                .map(this::toOption);
    }

    @Override
    @Transactional
    public Mono<GitManagedRepositoryVo> create(GitRepositoryCreateDto dto) {
        GitManagedRepository repository = new GitManagedRepository();
        repository.setProvider(GitProvider.normalizeCode(dto.getProvider()));
        repository.setAccessTokenId(dto.getAccessTokenId());
        repository.setOwner(cleanSegment(dto.getOwner()));
        repository.setRepo(cleanRepo(dto.getRepo()));
        repository.setFullName(fullName(repository.getOwner(), repository.getRepo()));
        repository.setName(StringUtils.defaultIfBlank(StringUtils.trimToNull(dto.getName()), repository.getFullName()));
        repository.setDescription(StringUtils.trimToNull(dto.getDescription()));
        repository.setPermission(GitProvider.normalizePermission(repository.getProvider(), dto.getPermission()));
        repository.setUrl(StringUtils.trimToNull(dto.getUrl()));
        repository.setPrivateRepo(Boolean.TRUE.equals(dto.getPrivateRepo()));
        repository.setExpireTime(dto.getExpireTime());
        repository.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        repository.setSort(dto.getSort() == null ? 0 : dto.getSort());
        return validateToken(repository)
                .then(repositoryRepository.save(repository))
                .flatMap(this::withTokenName);
    }

    @Override
    @Transactional
    public Mono<GitManagedRepositoryVo> update(Long id, GitRepositoryUpdateDto dto) {
        return repositoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GIT_REPOSITORY_NOT_FOUND", "代码仓库不存在")))
                .flatMap(repository -> {
                    applyUpdate(repository, dto);
                    return validateToken(repository).thenReturn(repository);
                })
                .flatMap(repositoryRepository::save)
                .flatMap(this::withTokenName);
    }

    @Override
    @Transactional
    public Mono<Void> delete(Long id) {
        return repositoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GIT_REPOSITORY_NOT_FOUND", "代码仓库不存在")))
                .flatMap(repository -> repositoryRepository.deleteById(repository.getId()));
    }

    @Override
    public Flux<GitRepositoryOptionVo> listRemoteRepositories(String provider, Long tokenId, String keyword,
                                                              Integer page, Integer size) {
        String normalizedProvider = GitProvider.normalizeCode(provider);
        int pageNo = Math.max(page == null ? 1 : page, 1);
        int pageSize = Math.min(Math.max(size == null ? 50 : size, 1), 100);
        return tokenService.resolveTokenValue(normalizedProvider, tokenId)
                .flatMapMany(token -> listRemotePage(normalizedProvider, token, keyword, pageNo, pageSize))
                .filter(repo -> matches(repo, keyword))
                .map(repo -> toRemoteOption(normalizedProvider, repo));
    }

    @Override
    @Transactional
    public Mono<GitRepositorySyncResultVo> syncRemoteRepositories(GitRepositorySyncDto dto) {
        String normalizedProvider = GitProvider.normalizeCode(dto.getProvider());
        String keyword = StringUtils.trimToNull(dto.getKeyword());
        return tokenService.resolveManagedTokenValue(normalizedProvider, dto.getAccessTokenId())
                .flatMapMany(token -> listRemotePages(normalizedProvider, token, keyword))
                .filter(repo -> matches(repo, keyword))
                .filter(this::hasRepositoryIdentity)
                .distinct(repo -> fullName(cleanSegment(repo.ownerName()), cleanRepo(repo.repoName())))
                .concatMap(repo -> syncRepository(normalizedProvider, dto.getAccessTokenId(), repo))
                .collectList()
                .flatMap(synced -> toSyncResult(dto.getAccessTokenId(), synced));
    }

    private void applyUpdate(GitManagedRepository repository, GitRepositoryUpdateDto dto) {
        if (StringUtils.isNotBlank(dto.getProvider())) {
            repository.setProvider(GitProvider.normalizeCode(dto.getProvider()));
        }
        if (dto.getAccessTokenId() != null) {
            repository.setAccessTokenId(dto.getAccessTokenId());
        }
        if (dto.getOwner() != null) {
            repository.setOwner(cleanSegment(dto.getOwner()));
        }
        if (dto.getRepo() != null) {
            repository.setRepo(cleanRepo(dto.getRepo()));
        }
        repository.setFullName(fullName(repository.getOwner(), repository.getRepo()));
        if (dto.getName() != null) {
            repository.setName(StringUtils.defaultIfBlank(StringUtils.trimToNull(dto.getName()), repository.getFullName()));
        }
        if (dto.getDescription() != null) {
            repository.setDescription(StringUtils.trimToNull(dto.getDescription()));
        }
        if (dto.getPermission() != null) {
            repository.setPermission(GitProvider.normalizePermission(repository.getProvider(), dto.getPermission()));
        }
        if (dto.getUrl() != null) {
            repository.setUrl(StringUtils.trimToNull(dto.getUrl()));
        }
        if (dto.getPrivateRepo() != null) {
            repository.setPrivateRepo(dto.getPrivateRepo());
        }
        if (dto.getExpireTime() != null) {
            repository.setExpireTime(dto.getExpireTime());
        }
        if (dto.getEnabled() != null) {
            repository.setEnabled(dto.getEnabled());
        }
        if (dto.getSort() != null) {
            repository.setSort(dto.getSort());
        }
    }

    private Mono<Void> validateToken(GitManagedRepository repository) {
        if (repository.getAccessTokenId() == null) {
            return Mono.empty();
        }
        return tokenRepository.findById(repository.getAccessTokenId())
                .switchIfEmpty(Mono.error(new BusinessException("GIT_TOKEN_NOT_FOUND", "API Token 不存在")))
                .filter(token -> GitProvider.normalizeCode(token.getProvider()).equals(repository.getProvider()))
                .switchIfEmpty(Mono.error(new BusinessException("GIT_TOKEN_PROVIDER_MISMATCH", "API Token 平台不匹配")))
                .then();
    }

    private Flux<GitRepositoryApiRepo> listRemotePages(String provider, String token, String keyword) {
        return Flux.range(1, REMOTE_SYNC_MAX_PAGES)
                .concatMap(page -> listRemotePage(provider, token, keyword, page, REMOTE_PAGE_SIZE).collectList())
                .takeUntil(repositories -> repositories.size() < REMOTE_PAGE_SIZE)
                .flatMapIterable(Function.identity());
    }

    private Flux<GitRepositoryApiRepo> listRemotePage(String provider, String token, String keyword, int page, int size) {
        return switch (GitProvider.of(provider)) {
            case GITHUB -> listGithub(token, page, size);
            case GITEA -> listGitea(token, keyword, page, size);
            case GITEE -> listGitee(token, page, size);
        };
    }

    private Flux<GitRepositoryApiRepo> listGitea(String token, String keyword, int page, int size) {
        return giteaClient.searchRepositories("token " + token, StringUtils.trimToNull(keyword),
                        true, "alpha", "asc", page, size)
                .flatMapMany(response -> Flux.fromIterable(response == null || response.data() == null
                        ? List.of()
                        : response.data()))
                .onErrorMap(WebClientResponseException.Forbidden.class,
                        e -> new BusinessException("GITEA_TOKEN_FORBIDDEN", "Gitea API Token 无效或权限不足"));
    }

    private Flux<GitRepositoryApiRepo> listGithub(String token, int page, int size) {
        return githubClient.listRepositories("Bearer " + token,
                        "all", "owner,collaborator,organization_member", "full_name", "asc", size, page)
                .flatMapMany(Flux::fromIterable)
                .onErrorMap(WebClientResponseException.Forbidden.class,
                        e -> new BusinessException("GITHUB_TOKEN_FORBIDDEN", "GitHub API Token 无效或权限不足"));
    }

    private Flux<GitRepositoryApiRepo> listGitee(String token, int page, int size) {
        return giteeClient.listRepositories(token, "all", "full_name", "asc", size, page)
                .flatMapMany(Flux::fromIterable)
                .onErrorMap(WebClientResponseException.Forbidden.class,
                        e -> new BusinessException("GITEE_TOKEN_FORBIDDEN", "码云 API Token 无效或权限不足"));
    }

    private boolean matches(GitRepositoryApiRepo repo, String keyword) {
        String value = StringUtils.trimToNull(keyword);
        if (value == null) {
            return true;
        }
        return StringUtils.containsIgnoreCase(repo.normalizedFullName(), value)
                || StringUtils.containsIgnoreCase(repo.name(), value);
    }

    private boolean hasRepositoryIdentity(GitRepositoryApiRepo repo) {
        return StringUtils.isNoneBlank(repo.ownerName(), repo.repoName());
    }

    private Mono<SyncedRepository> syncRepository(String provider, Long tokenId, GitRepositoryApiRepo remote) {
        String owner = cleanSegment(remote.ownerName());
        String repo = cleanRepo(remote.repoName());
        String fullName = fullName(owner, repo);
        return repositoryRepository.findByProviderAndFullName(provider, fullName)
                .map(repository -> new SyncedRepository(repository, false))
                .switchIfEmpty(Mono.fromSupplier(() -> new SyncedRepository(new GitManagedRepository(), true)))
                .flatMap(synced -> {
                    applyRemoteRepository(synced.repository(), synced.created(), provider, tokenId, remote);
                    return repositoryRepository.save(synced.repository())
                            .map(saved -> new SyncedRepository(saved, synced.created()));
                });
    }

    private void applyRemoteRepository(GitManagedRepository repository, boolean created, String provider, Long tokenId,
                                       GitRepositoryApiRepo remote) {
        String owner = cleanSegment(remote.ownerName());
        String repo = cleanRepo(remote.repoName());
        String fullName = fullName(owner, repo);
        repository.setProvider(provider);
        repository.setAccessTokenId(tokenId);
        repository.setOwner(owner);
        repository.setRepo(repo);
        repository.setFullName(fullName);
        if (created || StringUtils.isBlank(repository.getName())) {
            repository.setName(fullName);
        }
        if (StringUtils.isBlank(repository.getDescription())) {
            repository.setDescription(StringUtils.trimToNull(remote.description()));
        }
        if (created || StringUtils.isBlank(repository.getPermission())) {
            repository.setPermission(GitProvider.normalizePermission(provider, remote.permission()));
        }
        String remoteUrl = StringUtils.trimToNull(remote.url());
        if (remoteUrl != null) {
            repository.setUrl(remoteUrl);
        }
        repository.setPrivateRepo(remote.isPrivate());
        if (repository.getEnabled() == null) {
            repository.setEnabled(true);
        }
        if (repository.getSort() == null) {
            repository.setSort(0);
        }
    }

    private Mono<GitRepositorySyncResultVo> toSyncResult(Long tokenId, List<SyncedRepository> synced) {
        int createdCount = (int) synced.stream().filter(SyncedRepository::created).count();
        List<GitManagedRepository> repositories = synced.stream().map(SyncedRepository::repository).toList();
        return tokenRepository.findById(tokenId)
                .map(token -> Map.of(token.getId(), token.getName()))
                .defaultIfEmpty(Map.of())
                .map(tokenNames -> GitRepositorySyncResultVo.builder()
                        .totalCount(synced.size())
                        .createdCount(createdCount)
                        .updatedCount(synced.size() - createdCount)
                        .repositories(repositories.stream()
                                .map(repository -> toVo(repository, tokenNames))
                                .toList())
                        .build());
    }

    private GitRepositoryOptionVo toOption(GitManagedRepository repository) {
        return GitRepositoryOptionVo.builder()
                .id(repository.getId())
                .provider(repository.getProvider())
                .providerName(GitProvider.titleOf(repository.getProvider()))
                .owner(repository.getOwner())
                .repo(repository.getRepo())
                .fullName(repository.getFullName())
                .name(repository.getName())
                .description(repository.getDescription())
                .url(repository.getUrl())
                .privateRepo(repository.getPrivateRepo())
                .permission(repository.getPermission())
                .tokenPermission(repository.getPermission())
                .build();
    }

    private GitRepositoryOptionVo toRemoteOption(String provider, GitRepositoryApiRepo repo) {
        return GitRepositoryOptionVo.builder()
                .provider(provider)
                .providerName(GitProvider.titleOf(provider))
                .owner(repo.ownerName())
                .repo(repo.repoName())
                .fullName(repo.normalizedFullName())
                .name(repo.normalizedFullName())
                .description(StringUtils.trimToNull(repo.description()))
                .url(repo.url())
                .privateRepo(repo.isPrivate())
                .permission(GitProvider.normalizePermission(provider, repo.permission()))
                .tokenPermission(repo.permission())
                .build();
    }

    private Mono<GitManagedRepositoryVo> withTokenName(GitManagedRepository repository) {
        if (repository.getAccessTokenId() == null) {
            return Mono.just(toVo(repository, Map.of()));
        }
        return tokenRepository.findById(repository.getAccessTokenId())
                .map(token -> toVo(repository, Map.of(token.getId(), token.getName())))
                .defaultIfEmpty(toVo(repository, Map.of()));
    }

    private GitManagedRepositoryVo toVo(GitManagedRepository repository, Map<Long, String> tokenNames) {
        return GitManagedRepositoryVo.builder()
                .id(repository.getId())
                .provider(repository.getProvider())
                .providerName(GitProvider.titleOf(repository.getProvider()))
                .accessTokenId(repository.getAccessTokenId())
                .accessTokenName(repository.getAccessTokenId() == null ? null : tokenNames.get(repository.getAccessTokenId()))
                .owner(repository.getOwner())
                .repo(repository.getRepo())
                .fullName(repository.getFullName())
                .name(repository.getName())
                .description(repository.getDescription())
                .permission(repository.getPermission())
                .permissionName(permissionName(repository.getProvider(), repository.getPermission()))
                .url(repository.getUrl())
                .privateRepo(repository.getPrivateRepo())
                .expireTime(repository.getExpireTime())
                .expired(repository.getExpireTime() != null && !repository.getExpireTime().isAfter(LocalDateTime.now()))
                .enabled(repository.getEnabled())
                .sort(repository.getSort())
                .build();
    }

    private String permissionName(String provider, String permission) {
        String normalized = GitProvider.normalizePermission(provider, permission);
        return switch (normalized) {
            case "admin" -> "管理员";
            case "maintain" -> "维护";
            case "push", "write" -> "写入";
            case "triage" -> "分诊";
            default -> "只读";
        };
    }

    private String cleanSegment(String value) {
        return StringUtils.trimToNull(value);
    }

    private String cleanRepo(String value) {
        String repo = StringUtils.trimToNull(value);
        return repo == null ? null : StringUtils.removeEnd(repo, ".git");
    }

    private String fullName(String owner, String repo) {
        if (StringUtils.isAnyBlank(owner, repo)) {
            throw new BusinessException("GIT_REPOSITORY_INVALID", "仓库信息不完整");
        }
        return owner + "/" + repo;
    }

    private record SyncedRepository(GitManagedRepository repository, boolean created) {
    }
}
