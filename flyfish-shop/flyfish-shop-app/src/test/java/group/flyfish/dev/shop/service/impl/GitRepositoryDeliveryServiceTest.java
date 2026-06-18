package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.git.client.GitCollaboratorRequest;
import group.flyfish.dev.git.client.GitRepositoryApiRepo;
import group.flyfish.dev.git.client.GitRepositoryCollaborator;
import group.flyfish.dev.git.client.GitRepositoryCollaboratorPermission;
import group.flyfish.dev.git.client.GiteaRepositorySearchResponse;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.shop.git.GitRepositoryAccessResolver;
import group.flyfish.dev.shop.git.ResolvedGitRepository;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitRepositoryDeliveryServiceTest {

    @Test
    void deliversGithubCollaboratorSeatWithAdminToken() {
        StubGithubRepositoryClient githubClient = new StubGithubRepositoryClient(
                Mono.just(ResponseEntity.status(HttpStatus.CREATED).body("{}")));

        GitRepositoryDeliveryService service = service(githubClient);

        StepVerifier.create(service.deliver(null, githubItem(), githubBuyer()))
                .assertNext(result -> {
                    assertTrue(result.isSuccess());
                    assertTrue(result.getMessage().contains("octocat"));
                })
                .verifyComplete();

        assertEquals("Bearer github-admin-token", githubClient.authorizationRef.get());
        assertEquals("flyfish", githubClient.ownerRef.get());
        assertEquals("private-repo", githubClient.repoRef.get());
        assertEquals("octocat", githubClient.usernameRef.get());
        assertEquals("pull", githubClient.requestRef.get().permission());
    }

    @Test
    void rejectsGithubDeliveryWhenBuyerIsNotBound() {
        GitRepositoryDeliveryService service = service(
                new StubGithubRepositoryClient(Mono.error(new AssertionError("GitHub should not be called"))));

        StepVerifier.create(service.deliver(null, githubItem(), new PortalUserVo()))
                .assertNext(result -> {
                    assertEquals(false, result.isSuccess());
                    assertTrue(result.getMessage().contains("GitHub"));
                })
                .verifyComplete();
    }

    @Test
    void deliversMultipleGithubRepositories() {
        StubGithubRepositoryClient githubClient = new StubGithubRepositoryClient(
                Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT).body("")));
        GitRepositoryDeliveryService service = service(githubClient);
        ShopItem item = githubItem("""
                {"provider":"github","repositories":[
                  {"owner":"wybaby168","repo":"docx-viewer","permission":"pull"},
                  {"owner":"wybaby168","repo":"pptx-viewer","permission":"push"}
                ]}
                """);

        StepVerifier.create(service.deliver(null, item, githubBuyer()))
                .assertNext(result -> {
                    assertTrue(result.isSuccess());
                    assertTrue(result.getMessage().contains("docx-viewer"));
                    assertTrue(result.getMessage().contains("pptx-viewer"));
                })
                .verifyComplete();

        assertEquals(List.of("docx-viewer", "pptx-viewer"), githubClient.repoRefs);
        assertEquals(List.of("pull", "push"), githubClient.permissionRefs);
    }

    @Test
    void deliversMixedGithubAndGiteaRepositoriesWithRepositoryLevelProvider() {
        StubGiteaRepositoryClient giteaClient = new StubGiteaRepositoryClient(
                Mono.just(ResponseEntity.noContent().build()));
        StubGithubRepositoryClient githubClient = new StubGithubRepositoryClient(
                Mono.just(ResponseEntity.status(HttpStatus.CREATED).body("{}")));
        GitRepositoryDeliveryService service = service(giteaClient, githubClient);
        ShopItem item = githubItem("""
                {"provider":"gitea","repositories":[
                  {"provider":"github","owner":"flyfish-dev","repo":"rtsp-source","permission":"pull"},
                  {"provider":"gitea","owner":"flyfish","repo":"viewer","permission":"read"}
                ]}
                """);

        StepVerifier.create(service.deliver(null, item, mixedBuyer()))
                .assertNext(result -> {
                    assertTrue(result.isSuccess());
                    assertTrue(result.getMessage().contains("rtsp-source"));
                    assertTrue(result.getMessage().contains("viewer"));
                })
                .verifyComplete();

        assertEquals("Bearer github-admin-token", githubClient.authorizationRef.get());
        assertEquals("flyfish-dev", githubClient.ownerRef.get());
        assertEquals("rtsp-source", githubClient.repoRef.get());
        assertEquals("pull", githubClient.requestRef.get().permission());
        assertEquals("token gitea-admin-token", giteaClient.authorizationRef.get());
        assertEquals("flyfish", giteaClient.ownerRef.get());
        assertEquals("viewer", giteaClient.repoRef.get());
        assertEquals("read", giteaClient.requestRef.get().permission());
    }

    @Test
    void refusesAutomaticDeliveryForUnsupportedItemType() {
        GitRepositoryDeliveryService service = service(
                new StubGithubRepositoryClient(Mono.error(new AssertionError("GitHub should not be called"))));
        ShopItem item = new ShopItem();
        item.setType(ShopItem.Type.SERVICE_PACKAGE);

        assertEquals(false, service.supports(item));
    }

    private ShopItem githubItem() {
        ShopItem item = new ShopItem();
        item.setType(ShopItem.Type.GIT_REPOSITORY_ACCESS);
        item.setName("GitHub 私库席位");
        item.setPrice(BigDecimal.ONE);
        item.setParams("""
                {"provider":"github","owner":"flyfish","repo":"private-repo","permission":"pull"}
                """);
        return item;
    }

    private ShopItem githubItem(String params) {
        ShopItem item = githubItem();
        item.setParams(params);
        return item;
    }

    private PortalUserVo githubBuyer() {
        PortalUserOauthVo oauth = oauth(OAuthType.GITHUB, "1", """
                {"id":"1","login":"octocat","name":"Mona","avatar_url":"https://github.com/images/error/octocat_happy.gif","html_url":"https://github.com/octocat"}
                """, "octocat");

        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setAuthorizations(Map.of("github", oauth));
        return user;
    }

    private PortalUserVo mixedBuyer() {
        PortalUserOauthVo githubOauth = oauth(OAuthType.GITHUB, "1", """
                {"id":"1","login":"octocat","name":"Mona"}
                """, "octocat");

        PortalUserOauthVo giteaOauth = oauth(OAuthType.GITEA, "2", """
                {"id":"2","login":"wybaby168","username":"wybaby168"}
                """, "wybaby168");

        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setAuthorizations(Map.of(
                "github", githubOauth,
                "gitea", giteaOauth
        ));
        return user;
    }

    private PortalUserOauthVo oauth(OAuthType type, String openid, String userInfo, String loginName) {
        return PortalUserOauthVo.of(100L, type, openid, userInfo, null, loginName, loginName,
                loginName, null, null, null, null);
    }

    private GitRepositoryDeliveryService service(GithubRepositoryClient githubClient) {
        return service(new NoopGiteaRepositoryClient(), githubClient);
    }

    private GitRepositoryDeliveryService service(GiteaRepositoryClient giteaClient, GithubRepositoryClient githubClient) {
        GitRepositoryAccessResolver resolver = mock(GitRepositoryAccessResolver.class);
        when(resolver.resolve(any(GitRepositoryAccessParamValue.class))).thenAnswer(invocation -> {
            GitRepositoryAccessParamValue param = invocation.getArgument(0);
            return Flux.fromIterable(param.getRepositories().stream()
                    .map(repository -> new ResolvedGitRepository(
                            repository.getRepositoryId(),
                            repository.getProvider(),
                            null,
                            repository.getOwner(),
                            repository.getRepo(),
                            repository.fullName(),
                            repository.getPermission(),
                            null))
                    .toList());
        });
        GitAccessTokenService tokenService = mock(GitAccessTokenService.class);
        when(tokenService.resolveTokenValue("github", null)).thenReturn(Mono.just("github-admin-token"));
        when(tokenService.resolveTokenValue("gitea", null)).thenReturn(Mono.just("gitea-admin-token"));
        return new GitRepositoryDeliveryService(
                giteaClient,
                githubClient,
                mock(GiteeRepositoryClient.class),
                tokenService,
                resolver
        );
    }

    private static class NoopGiteaRepositoryClient implements GiteaRepositoryClient {

        @Override
        public Mono<GiteaRepositorySearchResponse> searchRepositories(String authorization, String q,
                                                                      Boolean includePrivate, String sort,
                                                                      String order, Integer page, Integer limit) {
            return Mono.error(new AssertionError("Gitea should not be called"));
        }

        @Override
        public Mono<List<GitRepositoryCollaborator>> listCollaborators(String authorization, String owner,
                                                                       String repo, Integer page, Integer limit) {
            return Mono.error(new AssertionError("Gitea should not be called"));
        }

        @Override
        public Mono<GitRepositoryCollaboratorPermission> getCollaboratorPermission(String authorization,
                                                                                   String owner, String repo,
                                                                                   String username) {
            return Mono.error(new AssertionError("Gitea should not be called"));
        }

        @Override
        public Mono<ResponseEntity<Void>> addCollaborator(String authorization, String owner, String repo,
                                                          String username, GitCollaboratorRequest request) {
            return Mono.error(new AssertionError("Gitea should not be called"));
        }
    }

    private static class StubGiteaRepositoryClient implements GiteaRepositoryClient {

        private final Mono<ResponseEntity<Void>> response;
        private final AtomicReference<String> authorizationRef = new AtomicReference<>();
        private final AtomicReference<String> ownerRef = new AtomicReference<>();
        private final AtomicReference<String> repoRef = new AtomicReference<>();
        private final AtomicReference<String> usernameRef = new AtomicReference<>();
        private final AtomicReference<GitCollaboratorRequest> requestRef = new AtomicReference<>();

        private StubGiteaRepositoryClient(Mono<ResponseEntity<Void>> response) {
            this.response = response;
        }

        @Override
        public Mono<GiteaRepositorySearchResponse> searchRepositories(String authorization, String q,
                                                                      Boolean includePrivate, String sort,
                                                                      String order, Integer page, Integer limit) {
            return Mono.error(new AssertionError("Gitea search should not be called"));
        }

        @Override
        public Mono<List<GitRepositoryCollaborator>> listCollaborators(String authorization, String owner,
                                                                       String repo, Integer page, Integer limit) {
            return Mono.error(new AssertionError("Gitea collaborator list should not be called"));
        }

        @Override
        public Mono<GitRepositoryCollaboratorPermission> getCollaboratorPermission(String authorization,
                                                                                   String owner, String repo,
                                                                                   String username) {
            return Mono.error(new AssertionError("Gitea permission should not be called"));
        }

        @Override
        public Mono<ResponseEntity<Void>> addCollaborator(String authorization, String owner, String repo,
                                                          String username, GitCollaboratorRequest request) {
            authorizationRef.set(authorization);
            ownerRef.set(owner);
            repoRef.set(repo);
            usernameRef.set(username);
            requestRef.set(request);
            return response;
        }
    }

    private static class StubGithubRepositoryClient implements GithubRepositoryClient {

        private final Mono<ResponseEntity<String>> response;
        private final AtomicReference<String> authorizationRef = new AtomicReference<>();
        private final AtomicReference<String> ownerRef = new AtomicReference<>();
        private final AtomicReference<String> repoRef = new AtomicReference<>();
        private final AtomicReference<String> usernameRef = new AtomicReference<>();
        private final AtomicReference<GitCollaboratorRequest> requestRef = new AtomicReference<>();
        private final List<String> repoRefs = new ArrayList<>();
        private final List<String> permissionRefs = new ArrayList<>();

        private StubGithubRepositoryClient(Mono<ResponseEntity<String>> response) {
            this.response = response;
        }

        @Override
        public Mono<List<GitRepositoryApiRepo>> listRepositories(String authorization, String visibility,
                                                                String affiliation, String sort, String direction,
                                                                Integer perPage, Integer page) {
            return Mono.error(new AssertionError("GitHub list should not be called"));
        }

        @Override
        public Mono<List<GitRepositoryCollaborator>> listCollaborators(String authorization, String owner,
                                                                       String repo, String affiliation,
                                                                       Integer perPage, Integer page) {
            return Mono.error(new AssertionError("GitHub collaborator list should not be called"));
        }

        @Override
        public Mono<GitRepositoryCollaboratorPermission> getCollaboratorPermission(String authorization,
                                                                                   String owner, String repo,
                                                                                   String username) {
            return Mono.error(new AssertionError("GitHub permission should not be called"));
        }

        @Override
        public Mono<ResponseEntity<String>> addCollaborator(String authorization, String owner, String repo,
                                                           String username, GitCollaboratorRequest request) {
            authorizationRef.set(authorization);
            ownerRef.set(owner);
            repoRef.set(repo);
            usernameRef.set(username);
            requestRef.set(request);
            repoRefs.add(repo);
            permissionRefs.add(request.permission());
            return response;
        }
    }
}
