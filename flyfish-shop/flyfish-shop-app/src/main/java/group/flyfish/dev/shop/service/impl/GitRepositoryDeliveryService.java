package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.git.client.GitCollaboratorRequest;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.shop.git.GitRepositoryAccessResolver;
import group.flyfish.dev.shop.git.ResolvedGitRepository;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import group.flyfish.dev.auth.api.user.UserAuthorizationUtils;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class GitRepositoryDeliveryService implements ShopDeliveryHandler {

    private static final int DELIVERY_MESSAGE_LIMIT = 500;
    private static final int PROVIDER_ERROR_LIMIT = 160;

    private final GiteaRepositoryClient giteaClient;
    private final GithubRepositoryClient githubClient;
    private final GiteeRepositoryClient giteeClient;
    private final GitAccessTokenService tokenService;
    private final GitRepositoryAccessResolver repositoryAccessResolver;

    @Override
    public boolean supports(ShopItem item) {
        return isGitRepositoryAccess(item);
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        GitRepositoryAccessParamValue param = (GitRepositoryAccessParamValue) ShopItemParamValue.from(item);
        if (!param.hasRepository()) {
            return Mono.just(DeliveryResult.failed("商品未配置代码仓库"));
        }
        return repositoryAccessResolver.resolve(param)
                .collectList()
                .flatMap(repositories -> deliverRepositories(repositories, buyer))
                .onErrorResume(e -> Mono.just(DeliveryResult.failed(describeProviderError(e))));
    }

    private boolean isGitRepositoryAccess(ShopItem item) {
        return item != null && item.getType() != null && item.getType().usesGitRepositoryAccessParams();
    }

    private Mono<DeliveryResult> deliverRepositories(List<ResolvedGitRepository> repositories, PortalUserVo buyer) {
        if (repositories.isEmpty()) {
            return Mono.just(DeliveryResult.failed("商品未配置代码仓库"));
        }
        return Flux.fromIterable(repositories)
                .concatMap(repository -> deliverRepository(repository, buyer))
                .collectList()
                .map(this::mergeDeliveryResults);
    }

    private Mono<RepositoryDeliveryResult> deliverRepository(ResolvedGitRepository repository, PortalUserVo buyer) {
        String username = resolveBoundUsername(repository.provider(), buyer);
        if (StringUtils.isBlank(username)) {
            return Mono.just(RepositoryDeliveryResult.failed(repository.fullName(),
                    "购买用户未绑定 " + GitProvider.titleOf(repository.provider()) + " 账号"));
        }
        return tokenService.resolveTokenValue(repository.provider(), repository.accessTokenId())
                .flatMap(token -> switch (GitProvider.of(repository.provider())) {
                    case GITHUB -> deliverGithubRepository(repository, username, token);
                    case GITEA -> deliverGiteaRepository(repository, username, token);
                    case GITEE -> deliverGiteeRepository(repository, username, token);
                })
                .onErrorResume(e -> Mono.just(RepositoryDeliveryResult.failed(repository.fullName(),
                        describeProviderError(e))));
    }

    private Mono<RepositoryDeliveryResult> deliverGiteaRepository(ResolvedGitRepository repository,
                                                                  String username,
                                                                  String token) {
        return giteaClient.addCollaborator("token " + token,
                        repository.owner(), repository.repo(), username,
                        new GitCollaboratorRequest(repository.permission()))
                .map(response -> RepositoryDeliveryResult.ok(repository.fullName(), username, repository.provider()))
                .onErrorResume(e -> Mono.just(RepositoryDeliveryResult.failed(repository.fullName(),
                        describeProviderError(e))));
    }

    private Mono<RepositoryDeliveryResult> deliverGithubRepository(ResolvedGitRepository repository,
                                                                   String username,
                                                                   String token) {
        return githubClient.addCollaborator("Bearer " + token,
                        repository.owner(), repository.repo(), username,
                        new GitCollaboratorRequest(repository.permission()))
                .map(response -> {
                    int status = response.getStatusCode().value();
                    if (status == 201 || status == 204) {
                        return RepositoryDeliveryResult.ok(repository.fullName(), username, repository.provider());
                    }
                    String body = StringUtils.defaultIfBlank(response.getBody(), response.getStatusCode().toString());
                    return RepositoryDeliveryResult.failed(repository.fullName(), body);
                })
                .onErrorResume(e -> Mono.just(RepositoryDeliveryResult.failed(repository.fullName(),
                        describeProviderError(e))));
    }

    private Mono<RepositoryDeliveryResult> deliverGiteeRepository(ResolvedGitRepository repository,
                                                                  String username,
                                                                  String token) {
        return giteeClient.addCollaborator(repository.owner(), repository.repo(), username, token,
                        new GitCollaboratorRequest(repository.permission()))
                .map(response -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return RepositoryDeliveryResult.ok(repository.fullName(), username, repository.provider());
                    }
                    return RepositoryDeliveryResult.failed(repository.fullName(),
                            StringUtils.defaultIfBlank(response.getBody(), status.toString()));
                })
                .onErrorResume(e -> Mono.just(RepositoryDeliveryResult.failed(repository.fullName(),
                        describeProviderError(e))));
    }

    private DeliveryResult mergeDeliveryResults(List<RepositoryDeliveryResult> results) {
        List<String> failed = results.stream()
                .filter(result -> !result.success())
                .map(result -> result.repository() + "：" + result.message())
                .toList();
        if (!failed.isEmpty()) {
            return DeliveryResult.failed(abbreviateDeliveryMessage("部分仓库开通失败：" + String.join("；", failed)));
        }
        String repositories = results.stream()
                .map(RepositoryDeliveryResult::repository)
                .reduce((left, right) -> left + "、" + right)
                .orElse("未配置仓库");
        String username = results.stream().map(RepositoryDeliveryResult::username).filter(StringUtils::isNotBlank)
                .findFirst().orElse("购买用户");
        return DeliveryResult.ok(abbreviateDeliveryMessage(
                "已为 " + username + " 开通代码仓库权限：" + repositories));
    }

    private String resolveBoundUsername(String provider, PortalUserVo buyer) {
        Map<String, Object> profile = switch (GitProvider.of(provider)) {
            case GITHUB -> UserAuthorizationUtils.getGithubProfile(buyer);
            case GITEE -> UserAuthorizationUtils.getGiteeProfile(buyer);
            case GITEA -> UserAuthorizationUtils.getGiteaProfile(buyer);
        };
        if (profile == null) {
            return null;
        }
        return StringUtils.firstNonBlank(valueOf(profile.get("login")),
                valueOf(profile.get("username")),
                valueOf(profile.get("login_name")),
                valueOf(profile.get("name")));
    }

    private String describeProviderError(Throwable e) {
        if (e instanceof WebClientResponseException webError) {
            String body = StringUtils.defaultIfBlank(webError.getResponseBodyAsString(), webError.getStatusText());
            return webError.getStatusCode().value() + " " + StringUtils.abbreviate(body, PROVIDER_ERROR_LIMIT);
        }
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()),
                PROVIDER_ERROR_LIMIT);
    }

    private String abbreviateDeliveryMessage(String message) {
        return StringUtils.abbreviate(message, DELIVERY_MESSAGE_LIMIT);
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record RepositoryDeliveryResult(boolean success, String repository, String username,
                                            String provider, String message) {

        private static RepositoryDeliveryResult ok(String repository, String username, String provider) {
            return new RepositoryDeliveryResult(true, repository, username, provider, null);
        }

        private static RepositoryDeliveryResult failed(String repository, String message) {
            return new RepositoryDeliveryResult(false, repository, null, null, message);
        }
    }
}
