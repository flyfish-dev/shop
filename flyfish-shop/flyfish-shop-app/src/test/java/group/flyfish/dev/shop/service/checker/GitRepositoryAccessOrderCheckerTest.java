package group.flyfish.dev.shop.service.checker;

import group.flyfish.dev.git.client.GitRepositoryCollaborator;
import group.flyfish.dev.git.client.GitRepositoryCollaboratorPermission;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.config.GiteaProperties;
import group.flyfish.dev.git.config.GithubProperties;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.shop.git.GitRepositoryAccessResolver;
import group.flyfish.dev.shop.git.ResolvedGitRepository;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

class GitRepositoryAccessOrderCheckerTest {

    @Test
    void blocksGithubPurchaseWhenBoundUserAlreadyHasPermission() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setAdminToken("github-admin");
        GitRepositoryCollaborator collaborator = collaborator(123456L, "imdiana168",
                Map.of("pull", true));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "office-render-demo", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of(collaborator)));
        when(githubClient.getCollaboratorPermission("Bearer github-admin", "wybaby168",
                "office-render-demo", "imdiana168"))
                .thenReturn(Mono.just(new GitRepositoryCollaboratorPermission("pull", "read", collaborator)));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                null, new GiteaProperties(), githubClient, githubProperties);

        StepVerifier.create(checker.check(gitItem(1L, "github", """
                        {"owner":"wybaby168","repo":"office-render-demo","permission":"pull"}
                        """), sanitizedOauthBuyer(OAuthType.GITHUB, "github", "imdiana168", 123456L)))
                .assertNext(availability -> {
                    assertFalse(availability.isPurchasable());
                    assertEquals("GIT_REPOSITORY_ALREADY_OPENED", availability.getReasonCode());
                    assertEquals("imdiana168", availability.getBoundUsername());
                    assertEquals("wybaby168/office-render-demo", availability.getOpenedRepositories().get(0));
                    assertTrue(availability.getMessage().contains("已拥有仓库"));
                })
                .verifyComplete();
    }

    @Test
    void keepsGithubPurchaseAvailableWhenOnlyPartOfRepositoriesOpened() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setAdminToken("github-admin");
        GitRepositoryCollaborator collaborator = collaborator(123456L, "imdiana168",
                Map.of("pull", true));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "office-render-demo", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of(collaborator)));
        when(githubClient.getCollaboratorPermission("Bearer github-admin", "wybaby168",
                "office-render-demo", "imdiana168"))
                .thenReturn(Mono.just(new GitRepositoryCollaboratorPermission("pull", "read", collaborator)));
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "excel-viewer", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of()));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                null, new GiteaProperties(), githubClient, githubProperties);

        StepVerifier.create(checker.check(gitItem(2L, "github", """
                        {"repositories":[
                          {"owner":"wybaby168","repo":"office-render-demo","permission":"pull"},
                          {"owner":"wybaby168","repo":"excel-viewer","permission":"pull"}
                        ]}
                        """), oauthBuyer(OAuthType.GITHUB, "github", "imdiana168", 123456L)))
                .assertNext(availability -> {
                    assertTrue(availability.isPurchasable());
                    assertEquals("wybaby168/office-render-demo", availability.getOpenedRepositories().get(0));
                    assertEquals("wybaby168/excel-viewer", availability.getPendingRepositories().get(0));
                    assertTrue(availability.getMessage().contains("待开通"));
                })
                .verifyComplete();
    }

    @Test
    void keepsGithubPurchaseAvailableWhenPermissionEndpointReturnsNone() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setAdminToken("github-admin");
        GitRepositoryCollaborator collaborator = collaborator(123456L, "imdiana168", Map.of());
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "office-render-demo", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of(collaborator)));
        when(githubClient.getCollaboratorPermission("Bearer github-admin", "wybaby168",
                "office-render-demo", "imdiana168"))
                .thenReturn(Mono.just(new GitRepositoryCollaboratorPermission("none", null, collaborator)));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                null, new GiteaProperties(), githubClient, githubProperties);

        StepVerifier.create(checker.check(gitItem(5L, "github", """
                        {"owner":"wybaby168","repo":"office-render-demo","permission":"pull"}
                        """), oauthBuyer(OAuthType.GITHUB, "github", "imdiana168", 123456L)))
                .assertNext(availability -> {
                    assertTrue(availability.isPurchasable());
                    assertEquals("wybaby168/office-render-demo", availability.getInsufficientRepositories().get(0));
                    assertTrue(availability.getMessage().contains("权限不足"));
                })
                .verifyComplete();
    }

    @Test
    void keepsGiteaPurchaseAvailableWhenPermissionIsMissing() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GiteaRepositoryClient giteaClient = mock(GiteaRepositoryClient.class);
        GiteaProperties giteaProperties = new GiteaProperties();
        giteaProperties.setAdminToken("gitea-admin");
        GitRepositoryCollaborator collaborator = collaborator(1L, "wybaby168", Map.of());
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());
        when(giteaClient.listCollaborators("token gitea-admin", "flyfish",
                "viewer", 1, 100)).thenReturn(Mono.just(java.util.List.of(collaborator)));
        when(giteaClient.getCollaboratorPermission("token gitea-admin", "flyfish",
                "viewer", "wybaby168"))
                .thenReturn(Mono.just(new GitRepositoryCollaboratorPermission(null, null, collaborator)));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                giteaClient, giteaProperties, null, new GithubProperties());

        StepVerifier.create(checker.check(gitItem(6L, "gitea", """
                        {"owner":"flyfish","repo":"viewer","permission":"read"}
                        """), oauthBuyer(OAuthType.GITEA, "gitea", "wybaby168", 1L)))
                .assertNext(availability -> {
                    assertTrue(availability.isPurchasable());
                    assertEquals("flyfish/viewer", availability.getInsufficientRepositories().get(0));
                    assertTrue(availability.getMessage().contains("权限不足"));
                })
                .verifyComplete();
    }

    @Test
    void keepsMultiRepositoryPurchaseAvailableWhenLocalHistoryOnlyCoversPartOfRepositories() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setAdminToken("github-admin");
        GitRepositoryCollaborator collaborator = collaborator(123456L, "imdiana168",
                Map.of("pull", true));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.just(paidOrder("FF-HISTORY", 99L)));
        when(itemRepository.findById(99L)).thenReturn(Mono.just(gitItem(99L, "github", """
                        {"owner":"wybaby168","repo":"office-render-demo","permission":"pull"}
                        """)));
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "office-render-demo", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of(collaborator)));
        when(githubClient.getCollaboratorPermission("Bearer github-admin", "wybaby168",
                "office-render-demo", "imdiana168"))
                .thenReturn(Mono.just(new GitRepositoryCollaboratorPermission("pull", "read", collaborator)));
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "excel-viewer", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of()));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                null, new GiteaProperties(), githubClient, githubProperties);

        StepVerifier.create(checker.check(gitItem(4L, "github", """
                        {"repositories":[
                          {"owner":"wybaby168","repo":"office-render-demo","permission":"pull"},
                          {"owner":"wybaby168","repo":"excel-viewer","permission":"pull"}
                        ]}
                        """), oauthBuyer(OAuthType.GITHUB, "github", "imdiana168", 123456L)))
                .assertNext(availability -> {
                    assertTrue(availability.isPurchasable());
                    assertEquals("wybaby168/office-render-demo", availability.getOpenedRepositories().get(0));
                    assertEquals("wybaby168/excel-viewer", availability.getPendingRepositories().get(0));
                    assertTrue(availability.getMessage().contains("待开通"));
                })
                .verifyComplete();
    }

    @Test
    void ignoresLegacyPaidOrderWithoutRepositorySnapshot() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);
        GithubProperties githubProperties = new GithubProperties();
        githubProperties.setAdminToken("github-admin");
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.just(paidOrder("FF-LEGACY", 7L)));
        when(githubClient.listCollaborators("Bearer github-admin", "wybaby168",
                "office-render-demo", "all", 100, 1)).thenReturn(Mono.just(java.util.List.of()));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                null, new GiteaProperties(), githubClient, githubProperties);

        StepVerifier.create(checker.check(gitItem(7L, "github", """
                        {"owner":"wybaby168","repo":"office-render-demo","permission":"pull"}
                        """), oauthBuyer(OAuthType.GITHUB, "github", "imdiana168", 123456L)))
                .assertNext(availability -> {
                    assertTrue(availability.isPurchasable());
                    assertEquals("wybaby168/office-render-demo", availability.getPendingRepositories().get(0));
                })
                .verifyComplete();
    }

    @Test
    void blocksGiteaPurchaseWhenPermissionEndpointConfirmsAccess() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        GiteaRepositoryClient giteaClient = mock(GiteaRepositoryClient.class);
        GiteaProperties giteaProperties = new GiteaProperties();
        giteaProperties.setAdminToken("gitea-admin");
        GitRepositoryCollaborator collaborator = collaborator(1L, "wybaby168", Map.of());
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());
        when(giteaClient.listCollaborators("token gitea-admin", "flyfish",
                "viewer", 1, 100)).thenReturn(Mono.just(java.util.List.of(collaborator)));
        when(giteaClient.getCollaboratorPermission("token gitea-admin", "flyfish",
                "viewer", "wybaby168"))
                .thenReturn(Mono.just(new GitRepositoryCollaboratorPermission("write", "write", collaborator)));

        GitRepositoryAccessOrderChecker checker = checker(orderRepository, itemRepository,
                giteaClient, giteaProperties, null, new GithubProperties());

        StepVerifier.create(checker.check(gitItem(3L, "gitea", """
                        {"owner":"flyfish","repo":"viewer","permission":"write"}
                        """), oauthBuyer(OAuthType.GITEA, "gitea", "wybaby168", 1L)))
                .assertNext(availability -> {
                    assertFalse(availability.isPurchasable());
                    assertEquals("GIT_REPOSITORY_ALREADY_OPENED", availability.getReasonCode());
                    assertEquals("flyfish/viewer", availability.getOpenedRepositories().get(0));
                })
                .verifyComplete();
    }

    private GitRepositoryAccessOrderChecker checker(ShopOrderRepository orderRepository,
                                                    ShopItemRepository itemRepository,
                                                    GiteaRepositoryClient giteaClient,
                                                    GiteaProperties giteaProperties,
                                                    GithubRepositoryClient githubClient,
                                                    GithubProperties githubProperties) {
        GitRepositoryAccessResolver resolver = mock(GitRepositoryAccessResolver.class);
        when(resolver.resolve(any(GitRepositoryAccessParamValue.class))).thenAnswer(invocation -> {
            GitRepositoryAccessParamValue param = invocation.getArgument(0);
            return Flux.fromIterable(toResolved(param));
        });
        GitAccessTokenService tokenService = mock(GitAccessTokenService.class);
        when(tokenService.resolveTokenValue(anyString(), nullable(Long.class))).thenAnswer(invocation -> {
            String provider = invocation.getArgument(0);
            if ("github".equals(provider)) {
                return Mono.justOrEmpty(githubProperties.getAdminToken());
            }
            if ("gitea".equals(provider)) {
                return Mono.justOrEmpty(giteaProperties.getAdminToken());
            }
            return Mono.empty();
        });
        return new GitRepositoryAccessOrderChecker(
                orderRepository,
                resolver,
                tokenService,
                giteaClient == null ? mock(GiteaRepositoryClient.class) : giteaClient,
                githubClient == null ? mock(GithubRepositoryClient.class) : githubClient,
                mock(GiteeRepositoryClient.class)
        );
    }

    private List<ResolvedGitRepository> toResolved(GitRepositoryAccessParamValue param) {
        return param.getRepositories().stream()
                .map(repository -> new ResolvedGitRepository(
                        repository.getRepositoryId(),
                        repository.getProvider(),
                        null,
                        repository.getOwner(),
                        repository.getRepo(),
                        repository.fullName(),
                        repository.getPermission(),
                        null))
                .toList();
    }

    private ShopItem gitItem(Long id, String provider, String params) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setType(ShopItem.Type.GIT_REPOSITORY_ACCESS);
        item.setEnabled(true);
        item.setPrice(BigDecimal.ONE);
        String normalized = params.strip();
        item.setParams("""
                {"provider":"%s",%s}
                """.formatted(provider, normalized.substring(1, normalized.length() - 1)));
        return item;
    }

    private GitRepositoryCollaborator collaborator(Long id, String login, Map<String, Boolean> permissions) {
        return new GitRepositoryCollaborator(id, login, login, null, login, null,
                "https://example.com/" + login, permissions, null);
    }

    private ShopOrder paidOrder(String orderNo, Long itemId) {
        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNo);
        order.setItemId(itemId);
        order.setBuyerId(100L);
        order.setStatus(ShopOrder.Status.DELIVERED);
        return order;
    }

    private PortalUserVo oauthBuyer(OAuthType type, String code, String login, Long id) {
        PortalUserOauthVo oauth = PortalUserOauthVo.of(100L, type, String.valueOf(id), """
                {"id":%d,"login":"%s","username":"%s","name":"%s"}
                """.formatted(id, login, login, login), null, login, login, login,
                null, null, null, null);

        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setAuthorizations(Map.of(code, oauth));
        return user;
    }

    private PortalUserVo sanitizedOauthBuyer(OAuthType type, String code, String login, Long id) {
        PortalUserVo user = oauthBuyer(type, code, login, id);
        user.setAuthorizations(Map.of(code, user.getAuthorizations().get(code).withoutUserInfo()));
        return user;
    }
}
