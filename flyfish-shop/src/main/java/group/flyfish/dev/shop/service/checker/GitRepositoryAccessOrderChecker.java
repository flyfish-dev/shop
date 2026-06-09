package group.flyfish.dev.shop.service.checker;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.git.client.GitRepositoryCollaborator;
import group.flyfish.dev.git.client.GitRepositoryCollaboratorPermission;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.shop.git.GitRepositoryAccessResolver;
import group.flyfish.dev.shop.git.ResolvedGitRepository;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopPurchaseAvailabilityVo;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.user.support.UserAuthorizationUtils;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Git 仓库自动开通类商品的购买前检查器。
 *
 * <p>判断依据分两层：第一层使用订单创建时固化的仓库快照，避免用户为完全相同的
 * 仓库组合重复付款；第二层调用各代码平台的协作者接口，核验当前绑定账号是否已经
 * 具备所需权限。仓库和 Token 都从统一的仓库管理模型解析，商品本身只保存仓库引用。</p>
 */
@Component
@RequiredArgsConstructor
public class GitRepositoryAccessOrderChecker {

    private static final String ORDER_PROPERTY_GIT_PROVIDER = "gitProvider";
    private static final String ORDER_PROPERTY_GIT_REPOSITORIES = "gitRepositories";
    private static final String ORDER_PROPERTY_GIT_REPOSITORY_KEYS = "gitRepositoryKeys";
    private static final int COLLABORATOR_PAGE_SIZE = 100;

    private final ShopOrderRepository shopOrderRepository;
    private final GitRepositoryAccessResolver repositoryAccessResolver;
    private final GitAccessTokenService tokenService;
    private final GiteaRepositoryClient giteaClient;
    private final GithubRepositoryClient githubClient;
    private final GiteeRepositoryClient giteeClient;

    private record GitPurchaseConflict(String orderNo, List<String> repositories) {
    }

    private record BoundGitIdentity(String username, Set<String> names, Set<String> ids) {
    }

    private record RepositoryAccessState(String provider, String repository, boolean matched, boolean opened,
                                         String requestedPermission, String actualPermission) {
    }

    private record RemoteAccessSummary(List<RepositoryAccessState> states, Map<String, String> usernames) {

        private List<String> openedRepositories() {
            return states.stream().filter(RepositoryAccessState::opened)
                    .map(RepositoryAccessState::repository).toList();
        }

        private List<String> pendingRepositories() {
            return states.stream().filter(state -> !state.matched())
                    .map(RepositoryAccessState::repository).toList();
        }

        private List<String> insufficientRepositories() {
            return states.stream().filter(state -> state.matched() && !state.opened())
                    .map(RepositoryAccessState::repository).toList();
        }

        private String firstUsername() {
            return usernames.values().stream().filter(StringUtils::isNotBlank).findFirst().orElse(null);
        }
    }

    public boolean supports(ShopItem item) {
        return item != null && item.getType() != null && item.getType().usesGitRepositoryAccessParams();
    }

    public Mono<ShopPurchaseAvailabilityVo> check(ShopItem item, PortalUserVo buyer) {
        ensureItemEnabled(item);
        if (!supports(item)) {
            return Mono.just(ShopPurchaseAvailabilityVo.available(null, List.of()));
        }
        GitRepositoryAccessParamValue param = (GitRepositoryAccessParamValue) ShopItemParamValue.from(item);
        if (!param.hasRepository()) {
            return Mono.just(unavailable(null, List.of(), "GIT_REPOSITORY_REQUIRED", "商品仓库参数未配置完整"));
        }
        return repositoryAccessResolver.resolve(param)
                .collectList()
                .flatMap(repositories -> checkResolvedRepositories(repositories, buyer))
                .onErrorResume(BusinessException.class, e -> Mono.just(
                        unavailable(null, List.of(), e.getCode(), e.getMessage())));
    }

    public Map<String, Object> orderProperties(ShopOrderDto dto, ShopItem item) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (dto.getProperties() != null) {
            properties.putAll(dto.getProperties());
        }
        if (!supports(item)) {
            return properties;
        }
        GitRepositoryAccessParamValue param = (GitRepositoryAccessParamValue) ShopItemParamValue.from(item);
        if (!param.hasRepository()) {
            return properties;
        }
        Set<String> providers = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (GitRepositoryAccessParamValue.Repository repository : param.getRepositories()) {
            if (repository == null || !repository.hasRepository()) {
                continue;
            }
            String provider = GitProvider.normalizeCode(StringUtils.defaultIfBlank(repository.getProvider(), param.getProvider()));
            providers.add(provider);
            String name = repository.fullName();
            names.add(name);
            keys.add(provider + ":" + name.toLowerCase(Locale.ROOT));
        }
        if (names.isEmpty()) {
            return properties;
        }
        properties.put(ORDER_PROPERTY_GIT_PROVIDER, providers.size() == 1 ? providers.iterator().next() : "mixed");
        properties.put(ORDER_PROPERTY_GIT_REPOSITORIES, names);
        properties.put(ORDER_PROPERTY_GIT_REPOSITORY_KEYS, keys);
        return properties;
    }

    private Mono<ShopPurchaseAvailabilityVo> checkResolvedRepositories(List<ResolvedGitRepository> repositories,
                                                                       PortalUserVo buyer) {
        if (repositories.isEmpty()) {
            return Mono.just(unavailable(null, List.of(), "GIT_REPOSITORY_REQUIRED", "商品仓库参数未配置完整"));
        }
        List<String> visibleRepositories = repositoryFullNames(repositories);
        return findGitPurchaseConflict(buyer.getId(), repositories)
                .map(conflict -> unavailable(primaryProvider(repositories), visibleRepositories,
                        "GIT_REPOSITORY_ALREADY_PURCHASED",
                        duplicatePurchaseMessage(primaryProvider(repositories), conflict.repositories()), conflict))
                .switchIfEmpty(checkRemoteAccess(repositories, buyer)
                        .map(summary -> toAvailability(repositories, visibleRepositories, summary)));
    }

    private ShopPurchaseAvailabilityVo toAvailability(List<ResolvedGitRepository> repositories,
                                                      List<String> visibleRepositories,
                                                      RemoteAccessSummary summary) {
        List<String> opened = summary.openedRepositories();
        List<String> pending = summary.pendingRepositories();
        List<String> insufficient = summary.insufficientRepositories();
        if (!opened.isEmpty() && opened.size() == repositories.size()) {
            ShopPurchaseAvailabilityVo vo = unavailable(primaryProvider(repositories), visibleRepositories,
                    "GIT_REPOSITORY_ALREADY_OPENED", remoteOpenedMessage(summary.firstUsername(), opened));
            fillRemoteStatus(vo, summary, opened, pending, insufficient);
            return vo;
        }
        ShopPurchaseAvailabilityVo vo = ShopPurchaseAvailabilityVo.available(primaryProvider(repositories),
                visibleRepositories);
        fillRemoteStatus(vo, summary, opened, pending, insufficient);
        vo.setMessage(remoteStatusMessage(summary));
        return vo;
    }

    private void fillRemoteStatus(ShopPurchaseAvailabilityVo vo, RemoteAccessSummary summary, List<String> opened,
                                  List<String> pending, List<String> insufficient) {
        vo.setRemoteChecked(true);
        vo.setBoundUsername(summary.firstUsername());
        vo.setOpenedRepositories(opened);
        vo.setPendingRepositories(pending);
        vo.setInsufficientRepositories(insufficient);
    }

    private Mono<RemoteAccessSummary> checkRemoteAccess(List<ResolvedGitRepository> repositories, PortalUserVo buyer) {
        Map<String, BoundGitIdentity> identities = new LinkedHashMap<>();
        for (ResolvedGitRepository repository : repositories) {
            identities.computeIfAbsent(repository.provider(), provider -> resolveBoundIdentity(provider, buyer));
            BoundGitIdentity identity = identities.get(repository.provider());
            if (identity == null || StringUtils.isBlank(identity.username())) {
                return Mono.error(new BusinessException(repository.provider().toUpperCase(Locale.ROOT) + "_REQUIRED",
                        "请先绑定 " + GitProvider.titleOf(repository.provider()) + " 账号"));
            }
        }
        Map<String, String> usernames = new LinkedHashMap<>();
        identities.forEach((provider, identity) -> usernames.put(provider, identity.username()));
        return Flux.fromIterable(repositories)
                .concatMap(repository -> checkRemoteRepository(repository, identities.get(repository.provider())))
                .collectList()
                .map(states -> new RemoteAccessSummary(states, usernames));
    }

    private Mono<RepositoryAccessState> checkRemoteRepository(ResolvedGitRepository repository,
                                                             BoundGitIdentity identity) {
        return tokenService.resolveTokenValue(repository.provider(), repository.accessTokenId())
                .flatMap(token -> switch (GitProvider.of(repository.provider())) {
                    case GITHUB -> checkGithubRepository(repository, identity, token);
                    case GITEA -> checkGiteaRepository(repository, identity, token);
                    case GITEE -> checkGiteeRepository(repository, identity, token);
                })
                .onErrorResume(e -> Mono.just(missing(repository)));
    }

    private Mono<RepositoryAccessState> checkGithubRepository(ResolvedGitRepository repository,
                                                             BoundGitIdentity identity,
                                                             String token) {
        String authorization = "Bearer " + token;
        return listGithubCollaborators(repository, authorization, 1)
                .filter(collaborator -> matches(collaborator, identity))
                .next()
                .flatMap(collaborator -> githubClient.getCollaboratorPermission(authorization,
                                repository.owner(), repository.repo(), collaboratorUsername(collaborator, identity))
                        .map(permission -> state(repository, collaborator, actualPermission(repository.provider(),
                                collaborator, permission)))
                        .onErrorResume(e -> Mono.just(state(repository, collaborator,
                                actualPermission(repository.provider(), collaborator, null)))))
                .defaultIfEmpty(missing(repository))
                .onErrorResume(e -> Mono.just(missing(repository)));
    }

    private Mono<RepositoryAccessState> checkGiteaRepository(ResolvedGitRepository repository,
                                                            BoundGitIdentity identity,
                                                            String token) {
        String authorization = "token " + token;
        return listGiteaCollaborators(repository, authorization, 1)
                .filter(collaborator -> matches(collaborator, identity))
                .next()
                .flatMap(collaborator -> giteaClient.getCollaboratorPermission(authorization,
                                repository.owner(), repository.repo(), collaboratorUsername(collaborator, identity))
                        .map(permission -> state(repository, collaborator, actualPermission(repository.provider(),
                                collaborator, permission)))
                        .onErrorResume(e -> Mono.just(state(repository, collaborator,
                                actualPermission(repository.provider(), collaborator, null)))))
                .defaultIfEmpty(missing(repository))
                .onErrorResume(e -> Mono.just(missing(repository)));
    }

    private Mono<RepositoryAccessState> checkGiteeRepository(ResolvedGitRepository repository,
                                                            BoundGitIdentity identity,
                                                            String token) {
        return listGiteeCollaborators(repository, token, 1)
                .filter(collaborator -> matches(collaborator, identity))
                .next()
                .flatMap(collaborator -> giteeClient.getCollaboratorPermission(repository.owner(), repository.repo(),
                                collaboratorUsername(collaborator, identity), token)
                        .map(permission -> state(repository, collaborator, actualPermission(repository.provider(),
                                collaborator, permission)))
                        .onErrorResume(e -> Mono.just(state(repository, collaborator,
                                actualPermission(repository.provider(), collaborator, null)))))
                .defaultIfEmpty(missing(repository))
                .onErrorResume(e -> Mono.just(missing(repository)));
    }

    private Flux<GitRepositoryCollaborator> listGithubCollaborators(ResolvedGitRepository repository,
                                                                    String authorization, int page) {
        return githubClient.listCollaborators(authorization, repository.owner(), repository.repo(),
                        "all", COLLABORATOR_PAGE_SIZE, page)
                .flatMapMany(collaborators -> pagedCollaborators(collaborators,
                        () -> listGithubCollaborators(repository, authorization, page + 1)));
    }

    private Flux<GitRepositoryCollaborator> listGiteaCollaborators(ResolvedGitRepository repository,
                                                                   String authorization, int page) {
        return giteaClient.listCollaborators(authorization, repository.owner(), repository.repo(),
                        page, COLLABORATOR_PAGE_SIZE)
                .flatMapMany(collaborators -> pagedCollaborators(collaborators,
                        () -> listGiteaCollaborators(repository, authorization, page + 1)));
    }

    private Flux<GitRepositoryCollaborator> listGiteeCollaborators(ResolvedGitRepository repository,
                                                                   String token, int page) {
        return giteeClient.listCollaborators(repository.owner(), repository.repo(), token,
                        page, COLLABORATOR_PAGE_SIZE)
                .flatMapMany(collaborators -> pagedCollaborators(collaborators,
                        () -> listGiteeCollaborators(repository, token, page + 1)));
    }

    private Flux<GitRepositoryCollaborator> pagedCollaborators(List<GitRepositoryCollaborator> collaborators,
                                                              Supplier<Flux<GitRepositoryCollaborator>> nextPage) {
        Flux<GitRepositoryCollaborator> current = Flux.fromIterable(collaborators == null ? List.of() : collaborators);
        if (collaborators == null || collaborators.size() < COLLABORATOR_PAGE_SIZE) {
            return current;
        }
        return current.concatWith(Flux.defer(nextPage));
    }

    private RepositoryAccessState state(ResolvedGitRepository repository,
                                        GitRepositoryCollaborator collaborator,
                                        String actualPermission) {
        String requestedPermission = GitProvider.normalizePermission(repository.provider(), repository.permission());
        boolean opened = hasRequiredPermission(repository.provider(), requestedPermission, actualPermission);
        return new RepositoryAccessState(repository.provider(), repository.fullName(), true, opened,
                requestedPermission, actualPermission);
    }

    private RepositoryAccessState missing(ResolvedGitRepository repository) {
        return new RepositoryAccessState(repository.provider(), repository.fullName(), false, false,
                repository.permission(), null);
    }

    private String actualPermission(String provider, GitRepositoryCollaborator collaborator,
                                    GitRepositoryCollaboratorPermission permission) {
        if (permission != null) {
            String value = normalizeActualPermission(provider,
                    StringUtils.firstNonBlank(permission.permission(), permission.roleName()));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return normalizeActualPermission(provider, StringUtils.firstNonBlank(collaborator.roleName(),
                highestPermission(provider, collaborator.permissions())));
    }

    private String highestPermission(String provider, Map<String, Boolean> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        String normalizedProvider = GitProvider.normalizeCode(provider);
        if (GitProvider.GITHUB.code().equals(normalizedProvider)) {
            for (String permission : List.of("admin", "maintain", "push", "triage", "pull")) {
                if (Boolean.TRUE.equals(permissions.get(permission))) {
                    return permission;
                }
            }
        }
        if (GitProvider.GITEE.code().equals(normalizedProvider)) {
            for (String permission : List.of("admin", "push", "pull")) {
                if (Boolean.TRUE.equals(permissions.get(permission))) {
                    return permission;
                }
            }
        }
        for (String permission : List.of("admin", "write", "read")) {
            if (Boolean.TRUE.equals(permissions.get(permission))) {
                return permission;
            }
        }
        return null;
    }

    private boolean hasRequiredPermission(String provider, String requestedPermission, String actualPermission) {
        if (StringUtils.isBlank(actualPermission)) {
            return false;
        }
        return permissionRank(provider, actualPermission) >= permissionRank(provider, requestedPermission);
    }

    private int permissionRank(String provider, String permission) {
        String normalized = GitProvider.normalizePermission(provider, permission);
        String normalizedProvider = GitProvider.normalizeCode(provider);
        if (GitProvider.GITHUB.code().equals(normalizedProvider)) {
            return switch (normalized) {
                case "admin" -> 5;
                case "maintain" -> 4;
                case "push" -> 3;
                case "triage" -> 2;
                default -> 1;
            };
        }
        if (GitProvider.GITEE.code().equals(normalizedProvider)) {
            return switch (normalized) {
                case "admin" -> 3;
                case "push" -> 2;
                default -> 1;
            };
        }
        return switch (normalized) {
            case "admin" -> 3;
            case "write" -> 2;
            default -> 1;
        };
    }

    private Mono<GitPurchaseConflict> findGitPurchaseConflict(Long buyerId,
                                                              List<ResolvedGitRepository> repositories) {
        Set<String> currentKeys = repositoryKeys(repositories);
        if (currentKeys.isEmpty()) {
            return Mono.empty();
        }
        List<String> currentVisible = repositoryFullNames(repositories);
        return shopOrderRepository.findPaidOrDeliveredByBuyerId(buyerId)
                .flatMap(order -> Mono.justOrEmpty(conflictFromOrderSnapshot(order, currentVisible, currentKeys)))
                .filter(java.util.Objects::nonNull)
                .collectList()
                .flatMap(conflicts -> mergeFullRepositoryConflict(conflicts, currentVisible, currentKeys));
    }

    private Mono<GitPurchaseConflict> mergeFullRepositoryConflict(List<GitPurchaseConflict> conflicts,
                                                                  List<String> currentRepositories,
                                                                  Set<String> currentKeys) {
        if (conflicts.isEmpty()) {
            return Mono.empty();
        }
        Set<String> coveredKeys = new LinkedHashSet<>();
        String firstOrderNo = null;
        for (GitPurchaseConflict conflict : conflicts) {
            if (firstOrderNo == null) {
                firstOrderNo = conflict.orderNo();
            }
            for (String repository : conflict.repositories()) {
                String key = StringUtils.substringBefore(repository, "|");
                if (currentKeys.contains(key)) {
                    coveredKeys.add(key);
                }
            }
        }
        if (!coveredKeys.containsAll(currentKeys)) {
            return Mono.empty();
        }
        List<String> coveredRepositories = currentRepositories.stream()
                .filter(repository -> coveredKeys.stream().anyMatch(key -> key.endsWith(":" + repository.toLowerCase(Locale.ROOT))))
                .toList();
        return Mono.just(new GitPurchaseConflict(firstOrderNo, coveredRepositories));
    }

    private GitPurchaseConflict conflictFromOrderSnapshot(ShopOrder order,
                                                          List<String> currentRepositories,
                                                          Set<String> currentKeys) {
        Map<String, Object> properties = readOrderProperties(order);
        String snapshotProvider = StringUtils.trimToEmpty(text(properties.get(ORDER_PROPERTY_GIT_PROVIDER)))
                .toLowerCase(Locale.ROOT);
        Set<String> snapshotKeys = repositoryKeysFromProperty(properties.get(ORDER_PROPERTY_GIT_REPOSITORY_KEYS),
                snapshotProvider);
        if (snapshotKeys.isEmpty()) {
            snapshotKeys = repositoryKeysFromProperty(properties.get(ORDER_PROPERTY_GIT_REPOSITORIES), snapshotProvider);
        }
        if (snapshotKeys.isEmpty()) {
            return null;
        }
        List<String> overlap = new ArrayList<>();
        for (String currentKey : currentKeys) {
            if (snapshotKeys.contains(currentKey)) {
                String repository = currentRepositories.stream()
                        .filter(name -> currentKey.endsWith(":" + name.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .orElse(currentKey);
                overlap.add(currentKey + "|" + repository);
            }
        }
        return overlap.isEmpty() ? null : new GitPurchaseConflict(order.getOrderNo(), overlap);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readOrderProperties(ShopOrder order) {
        if (order == null || StringUtils.isBlank(order.getProperties())) {
            return Map.of();
        }
        try {
            return JacksonUtils.readValue(order.getProperties(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private BoundGitIdentity resolveBoundIdentity(String provider, PortalUserVo buyer) {
        Map<String, Object> profile = switch (GitProvider.of(provider)) {
            case GITHUB -> UserAuthorizationUtils.getGithubProfile(buyer);
            case GITEE -> UserAuthorizationUtils.getGiteeProfile(buyer);
            case GITEA -> UserAuthorizationUtils.getGiteaProfile(buyer);
        };
        return profile == null ? null : resolveIdentity(profile);
    }

    private BoundGitIdentity resolveIdentity(Map<String, Object> profile) {
        Set<String> names = new LinkedHashSet<>();
        addIdentity(names, profile.get("login"));
        addIdentity(names, profile.get("username"));
        addIdentity(names, profile.get("login_name"));
        addIdentity(names, profile.get("name"));
        Set<String> ids = new LinkedHashSet<>();
        addIdentity(ids, profile.get("id"));
        addIdentity(ids, profile.get("openid"));
        String username = names.stream().findFirst().orElse(null);
        return new BoundGitIdentity(username, names, ids);
    }

    private boolean matches(GitRepositoryCollaborator collaborator, BoundGitIdentity identity) {
        if (collaborator == null) {
            return false;
        }
        if (containsIgnoreCase(identity.ids(), collaborator.idText())) {
            return true;
        }
        return containsIgnoreCase(identity.names(), collaborator.login())
                || containsIgnoreCase(identity.names(), collaborator.username())
                || containsIgnoreCase(identity.names(), collaborator.loginName())
                || containsIgnoreCase(identity.names(), collaborator.accountName());
    }

    private String collaboratorUsername(GitRepositoryCollaborator collaborator, BoundGitIdentity identity) {
        return StringUtils.defaultIfBlank(collaborator.accountName(),
                StringUtils.defaultIfBlank(collaborator.login(), identity.username()));
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        String normalized = StringUtils.trimToNull(candidate);
        return normalized != null && values.stream().anyMatch(value -> value.equalsIgnoreCase(normalized));
    }

    private void addIdentity(Set<String> target, Object value) {
        String text = text(value);
        if (text != null) {
            target.add(text);
        }
    }

    private Set<String> repositoryKeys(List<ResolvedGitRepository> repositories) {
        Set<String> keys = new LinkedHashSet<>();
        for (ResolvedGitRepository repository : repositories) {
            keys.add(repositoryKey(repository));
        }
        return keys;
    }

    private String repositoryKey(ResolvedGitRepository repository) {
        return repository.provider() + ":" + repository.fullName().toLowerCase(Locale.ROOT);
    }

    private Set<String> repositoryKeysFromProperty(Object value, String provider) {
        Set<String> keys = new LinkedHashSet<>();
        for (String name : repositoryNamesFromProperty(value)) {
            String normalized = StringUtils.trimToNull(name);
            if (normalized == null) {
                continue;
            }
            if (normalized.contains(":")) {
                keys.add(normalized.toLowerCase(Locale.ROOT));
                continue;
            }
            if (!"mixed".equals(provider) && StringUtils.isNotBlank(provider)) {
                keys.add(GitProvider.normalizeCode(provider) + ":" + normalized.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    private List<String> repositoryNamesFromProperty(Object value) {
        List<String> names = new ArrayList<>();
        collectRepositoryNames(value, names);
        return names;
    }

    private void collectRepositoryNames(Object value, List<String> names) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                collectRepositoryNames(item, names);
            }
            return;
        }
        if (value instanceof Map<?, ?> repository) {
            String fullName = text(repository.get("fullName"));
            if (fullName != null) {
                names.add(fullName);
                return;
            }
            String owner = text(repository.get("owner"));
            String repo = text(repository.get("repo"));
            if (StringUtils.isNoneBlank(owner, repo)) {
                names.add(owner + "/" + repo);
            }
            return;
        }
        String text = text(value);
        if (text == null) {
            return;
        }
        if (text.startsWith("[") || text.startsWith("{")) {
            try {
                collectRepositoryNames(JacksonUtils.readValue(text, Object.class), names);
                return;
            } catch (Exception ignored) {
                // 老订单属性可能只是普通文本，解析失败时按逗号分隔继续兜底。
            }
        }
        for (String repository : text.split(",")) {
            String normalized = StringUtils.trimToNull(repository);
            if (normalized != null) {
                names.add(normalized);
            }
        }
    }

    private List<String> repositoryFullNames(List<ResolvedGitRepository> repositories) {
        return repositories.stream().map(ResolvedGitRepository::fullName).toList();
    }

    private String primaryProvider(List<ResolvedGitRepository> repositories) {
        return repositories.stream().map(ResolvedGitRepository::provider).findFirst().orElse(null);
    }

    private String normalizeActualPermission(String provider, String permission) {
        String normalized = StringUtils.trimToEmpty(permission).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(normalized) || "none".equals(normalized) || "no_access".equals(normalized)
                || "no permission".equals(normalized) || "false".equals(normalized)) {
            return null;
        }
        String normalizedProvider = GitProvider.normalizeCode(provider);
        if (GitProvider.GITHUB.code().equals(normalizedProvider)) {
            return switch (normalized) {
                case "read", "pull" -> "pull";
                case "write", "push" -> "push";
                case "triage", "maintain", "admin" -> normalized;
                default -> null;
            };
        }
        if (GitProvider.GITEE.code().equals(normalizedProvider)) {
            return switch (normalized) {
                case "read", "pull" -> "pull";
                case "write", "push" -> "push";
                case "admin" -> "admin";
                default -> null;
            };
        }
        return switch (normalized) {
            case "read", "pull" -> "read";
            case "write", "push" -> "write";
            case "admin" -> "admin";
            default -> null;
        };
    }

    private String duplicatePurchaseMessage(String provider, List<String> repositories) {
        String target = provider == null ? "代码仓库" : GitProvider.titleOf(provider) + " 仓库";
        return "您已购买过 " + target + " " + repositoryText(repositories)
                + "，无需重复购买；可在我的订单中查看开通记录。";
    }

    private String remoteOpenedMessage(String username, List<String> repositories) {
        return "当前绑定账号 " + username + " 已拥有仓库 " + repositoryText(repositories) + " 的所需权限，无需重复购买。";
    }

    private String remoteStatusMessage(RemoteAccessSummary summary) {
        List<String> opened = summary.openedRepositories();
        List<String> pending = summary.pendingRepositories();
        List<String> insufficient = summary.insufficientRepositories();
        if (opened.isEmpty() && insufficient.isEmpty()) {
            return "当前绑定账号尚未开通该商品对应仓库权限。";
        }
        List<String> parts = new ArrayList<>();
        if (!opened.isEmpty()) {
            parts.add("已拥有权限：" + repositoryText(opened));
        }
        if (!insufficient.isEmpty()) {
            parts.add("权限不足：" + repositoryText(insufficient));
        }
        if (!pending.isEmpty()) {
            parts.add("待开通：" + repositoryText(pending));
        }
        return "当前绑定账号仓库权限状态：" + String.join("；", parts);
    }

    private String repositoryText(List<String> repositories) {
        List<String> visibleRepositories = repositories == null ? List.of() : repositories;
        String text = visibleRepositories.stream().limit(3).reduce((left, right) -> left + "、" + right)
                .orElse("该仓库");
        if (visibleRepositories.size() > 3) {
            text = text + " 等 " + visibleRepositories.size() + " 个仓库";
        }
        return text;
    }

    private String text(Object value) {
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private ShopPurchaseAvailabilityVo unavailable(String provider, List<String> repositories,
                                                   String reasonCode, String message) {
        ShopPurchaseAvailabilityVo vo = ShopPurchaseAvailabilityVo.unavailable(reasonCode, message);
        vo.setProvider(provider);
        vo.setRepositories(repositories == null ? List.of() : repositories);
        return vo;
    }

    private ShopPurchaseAvailabilityVo unavailable(String provider, List<String> repositories,
                                                   String reasonCode, String message, GitPurchaseConflict conflict) {
        ShopPurchaseAvailabilityVo vo = unavailable(provider, repositories, reasonCode, message);
        vo.setConflictOrderNo(conflict.orderNo());
        vo.setConflictRepositories(conflict.repositories().stream()
                .map(repository -> repository.contains("|") ? StringUtils.substringAfter(repository, "|") : repository)
                .toList());
        return vo;
    }

    private void ensureItemEnabled(ShopItem item) {
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            throw new BusinessException("ITEM_DISABLED", "商品已下架");
        }
    }
}
