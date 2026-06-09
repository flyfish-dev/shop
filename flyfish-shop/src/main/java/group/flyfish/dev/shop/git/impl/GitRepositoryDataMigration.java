package group.flyfish.dev.shop.git.impl;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import group.flyfish.dev.git.config.GiteaProperties;
import group.flyfish.dev.git.config.GithubProperties;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.git.domain.po.GitAccessToken;
import group.flyfish.dev.git.domain.po.GitManagedRepository;
import group.flyfish.dev.git.repository.GitAccessTokenRepository;
import group.flyfish.dev.git.repository.GitManagedRepositoryRepository;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Git 仓库模型升级迁移。
 *
 * <p>老版本商品把 owner/repo/provider 直接写在商品参数里。本迁移在启动时幂等执行：
 * 先把旧环境变量里的管理 Token 迁入数据库，再把商品参数中的仓库沉淀成独立仓库记录，
 * 最后回写商品参数为仓库引用快照。这样生产升级后不需要手工补录已有商品。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitRepositoryDataMigration implements ApplicationRunner {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String SYSTEM_AUDITOR = "system";

    private final GitAccessTokenRepository tokenRepository;
    private final GitManagedRepositoryRepository repositoryRepository;
    private final ShopItemRepository shopItemRepository;
    private final GiteaProperties giteaProperties;
    private final GithubProperties githubProperties;
    private final DatabaseClient databaseClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrate().block(TIMEOUT);
        } catch (Exception e) {
            log.warn("Git 仓库模型启动迁移未完成：{}", e.getMessage());
        }
    }

    private Mono<Void> migrate() {
        Map<String, Long> tokenIds = new LinkedHashMap<>();
        return normalizeLegacyShopItemTypes()
                .then(ensureDefaultToken(GitProvider.GITEA.code(), giteaProperties.getAdminToken(),
                        "默认 Gitea Token", "由旧版 Gitea 管理令牌自动迁入")
                        .doOnNext(id -> tokenIds.put(GitProvider.GITEA.code(), id)))
                .then(ensureDefaultToken(GitProvider.GITHUB.code(), githubProperties.getAdminToken(),
                        "默认 GitHub Token", "由旧版 GitHub 管理令牌自动迁入")
                        .doOnNext(id -> tokenIds.put(GitProvider.GITHUB.code(), id)))
                .thenMany(shopItemRepository.findAll())
                .filter(item -> item.getType() != null && item.getType().usesGitRepositoryAccessParams())
                .concatMap(item -> migrateItem(item, tokenIds))
                .then();
    }

    private Mono<Void> normalizeLegacyShopItemTypes() {
        return databaseClient.sql("""
                        UPDATE shop_item
                           SET type = 'GIT_REPOSITORY_ACCESS',
                               delivery_mode = 'AUTOMATIC'
                         WHERE type IN ('GITEA_OPENING', 'GITHUB_OPENING', 'GIT_OPENING')
                        """)
                .fetch()
                .rowsUpdated()
                .doOnNext(rows -> {
                    if (rows > 0) {
                        log.info("旧版仓库开通商品类型已归一化，数量 {}", rows);
                    }
                })
                .then();
    }

    private Mono<Long> ensureDefaultToken(String provider, String tokenValue, String name, String description) {
        if (StringUtils.isBlank(tokenValue)) {
            return Mono.empty();
        }
        return tokenRepository.findFirstEnabledByProvider(provider)
                .map(GitAccessToken::getId)
                .switchIfEmpty(Mono.defer(() -> {
                    GitAccessToken token = new GitAccessToken();
                    token.setProvider(provider);
                    token.setName(name);
                    token.setDescription(description);
                    token.setTokenValue(StringUtils.trim(tokenValue));
                    token.setEnabled(true);
                    token.setSort(0);
                    fillAudit(token);
                    return tokenRepository.save(token).map(GitAccessToken::getId);
                }));
    }

    private Mono<Void> migrateItem(ShopItem item, Map<String, Long> tokenIds) {
        GitRepositoryAccessParamValue param;
        try {
            param = ShopItemParamValue.gitRepositoryAccess(item.getParams());
        } catch (Exception e) {
            log.warn("商品 {} Git 参数解析失败，跳过迁移：{}", item.getId(), e.getMessage());
            return Mono.empty();
        }
        if (!param.hasRepository()) {
            return Mono.empty();
        }
        return Flux.fromIterable(param.getRepositories())
                .filter(GitRepositoryAccessParamValue.Repository::hasRepository)
                .concatMap(repository -> completeRepository(repository, tokenIds)
                        .doOnNext(saved -> applyRepositorySnapshot(repository, saved))
                        .switchIfEmpty(Mono.defer(() -> {
                            log.warn("商品 {} 的仓库引用无法补全，保留原始参数：repositoryId={}, owner={}, repo={}",
                                    item.getId(), repository.getRepositoryId(), repository.getOwner(), repository.getRepo());
                            return Mono.empty();
                        })))
                .then(Mono.defer(() -> saveItemIfChanged(item, param)));
    }

    private Mono<GitManagedRepository> completeRepository(GitRepositoryAccessParamValue.Repository source,
                                                          Map<String, Long> tokenIds) {
        if (source.getRepositoryId() != null) {
            return repositoryRepository.findById(source.getRepositoryId())
                    .switchIfEmpty(Mono.defer(() -> ensureLegacyRepositoryIfPossible(source, tokenIds)));
        }
        return ensureLegacyRepositoryIfPossible(source, tokenIds);
    }

    private Mono<GitManagedRepository> ensureLegacyRepositoryIfPossible(GitRepositoryAccessParamValue.Repository source,
                                                                        Map<String, Long> tokenIds) {
        if (StringUtils.isAnyBlank(source.getOwner(), source.getRepo())) {
            return Mono.empty();
        }
        return ensureRepository(source, tokenIds);
    }

    private void applyRepositorySnapshot(GitRepositoryAccessParamValue.Repository target, GitManagedRepository saved) {
        target.setRepositoryId(saved.getId());
        target.setProvider(saved.getProvider());
        target.setOwner(saved.getOwner());
        target.setRepo(saved.getRepo());
        target.setName(saved.getName());
        target.setPermission(saved.getPermission());
    }

    private Mono<GitManagedRepository> ensureRepository(GitRepositoryAccessParamValue.Repository source,
                                                       Map<String, Long> tokenIds) {
        String provider = GitProvider.normalizeCode(source.getProvider());
        String fullName = source.getOwner() + "/" + source.getRepo();
        return repositoryRepository.findByProviderAndFullName(provider, fullName)
                .switchIfEmpty(Mono.defer(() -> {
                    GitManagedRepository repository = new GitManagedRepository();
                    repository.setProvider(provider);
                    repository.setAccessTokenId(tokenIds.get(provider));
                    repository.setOwner(source.getOwner());
                    repository.setRepo(source.getRepo());
                    repository.setFullName(fullName);
                    repository.setName(fullName);
                    repository.setDescription("由旧商品参数自动迁移");
                    repository.setPermission(GitProvider.normalizePermission(provider, source.getPermission()));
                    repository.setPrivateRepo(true);
                    repository.setEnabled(true);
                    repository.setSort(0);
                    fillAudit(repository);
                    return repositoryRepository.save(repository);
                }));
    }

    private void fillAudit(AuditDomain entity) {
        entity.setCreateBy(SYSTEM_AUDITOR);
        entity.setUpdateBy(SYSTEM_AUDITOR);
    }

    private Mono<Void> saveItemIfChanged(ShopItem item, GitRepositoryAccessParamValue param) {
        param.normalize();
        String next = JacksonUtils.toJson(param);
        if (StringUtils.equals(item.getParams(), next)) {
            return Mono.empty();
        }
        item.setParams(next);
        return shopItemRepository.save(item).then();
    }
}
