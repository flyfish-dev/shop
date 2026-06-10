package group.flyfish.dev.git.service.impl;

import group.flyfish.dev.git.client.GitRepositoryApiRepo;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.domain.dto.GitRepositorySyncDto;
import group.flyfish.dev.git.domain.po.GitAccessToken;
import group.flyfish.dev.git.domain.po.GitManagedRepository;
import group.flyfish.dev.git.domain.vo.GitManagedRepositoryVo;
import group.flyfish.dev.git.repository.GitAccessTokenRepository;
import group.flyfish.dev.git.repository.GitManagedRepositoryRepository;
import group.flyfish.dev.git.service.GitAccessTokenService;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitRepositoryManageServiceImplTest {

    @Test
    void syncRemoteRepositoriesCreatesNewAndUpdatesExistingRepository() {
        GitManagedRepositoryRepository repositoryRepository = mock(GitManagedRepositoryRepository.class);
        GitAccessTokenRepository tokenRepository = mock(GitAccessTokenRepository.class);
        GitAccessTokenService tokenService = mock(GitAccessTokenService.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);

        GitAccessToken token = new GitAccessToken();
        token.setId(10L);
        token.setProvider("github");
        token.setName("Group GitHub Token");
        when(tokenService.resolveManagedTokenValue("github", 10L)).thenReturn(Mono.just("github-token"));
        when(tokenRepository.findById(10L)).thenReturn(Mono.just(token));

        GitRepositoryApiRepo newRepo = githubRepo("wybaby168/office-render-demo", "office-render-demo",
                "Office Render Demo", Map.of("pull", true));
        GitRepositoryApiRepo existingRemote = githubRepo("wybaby168/excel-viewer", "excel-viewer",
                "Excel Viewer", Map.of("push", true));
        when(githubClient.listRepositories("Bearer github-token", "all", "owner,collaborator,organization_member",
                "full_name", "asc", 50, 1)).thenReturn(Mono.just(List.of(newRepo, existingRemote)));

        GitManagedRepository existing = new GitManagedRepository();
        existing.setId(55L);
        existing.setProvider("github");
        existing.setAccessTokenId(3L);
        existing.setOwner("wybaby168");
        existing.setRepo("excel-viewer");
        existing.setFullName("wybaby168/excel-viewer");
        existing.setName("保留的 Excel 管理名");
        existing.setDescription("保留的中文描述");
        existing.setPermission("pull");
        existing.setUrl("https://github.com/old/excel-viewer");
        existing.setPrivateRepo(true);
        existing.setEnabled(true);
        existing.setSort(7);

        doReturn(Mono.empty()).when(repositoryRepository)
                .findByProviderAndFullNameIncludingDeleted("github", "wybaby168/office-render-demo");
        doReturn(Mono.just(existing)).when(repositoryRepository)
                .findByProviderAndFullNameIncludingDeleted("github", "wybaby168/excel-viewer");
        when(repositoryRepository.save(any(GitManagedRepository.class))).thenAnswer(invocation -> {
            GitManagedRepository repository = invocation.getArgument(0);
            if (repository.getId() == null) {
                repository.setId(100L);
            }
            return Mono.just(repository);
        });

        GitRepositorySyncDto dto = new GitRepositorySyncDto();
        dto.setProvider("github");
        dto.setAccessTokenId(10L);

        GitRepositoryManageServiceImpl service = new GitRepositoryManageServiceImpl(
                repositoryRepository,
                tokenRepository,
                tokenService,
                mock(GiteaRepositoryClient.class),
                githubClient,
                mock(GiteeRepositoryClient.class)
        );

        StepVerifier.create(service.syncRemoteRepositories(dto))
                .assertNext(result -> {
                    assertEquals(2, result.getTotalCount());
                    assertEquals(1, result.getCreatedCount());
                    assertEquals(1, result.getUpdatedCount());

                    GitManagedRepositoryVo created = find(result.getRepositories(), "wybaby168/office-render-demo");
                    assertEquals(10L, created.getAccessTokenId());
                    assertEquals("Group GitHub Token", created.getAccessTokenName());
                    assertEquals("pull", created.getPermission());
                    assertEquals("Office Render Demo", created.getDescription());

                    GitManagedRepositoryVo updated = find(result.getRepositories(), "wybaby168/excel-viewer");
                    assertEquals(55L, updated.getId());
                    assertEquals(10L, updated.getAccessTokenId());
                    assertEquals("保留的 Excel 管理名", updated.getName());
                    assertEquals("保留的中文描述", updated.getDescription());
                    assertEquals("pull", updated.getPermission());
                    assertEquals("https://github.com/wybaby168/excel-viewer", updated.getUrl());
                    assertEquals(7, updated.getSort());
                })
                .verifyComplete();

        verify(githubClient).listRepositories("Bearer github-token", "all",
                "owner,collaborator,organization_member", "full_name", "asc", 50, 1);
    }

    @Test
    void syncRemoteRepositoriesRestoresSoftDeletedRepository() {
        GitManagedRepositoryRepository repositoryRepository = mock(GitManagedRepositoryRepository.class);
        GitAccessTokenRepository tokenRepository = mock(GitAccessTokenRepository.class);
        GitAccessTokenService tokenService = mock(GitAccessTokenService.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);

        GitAccessToken token = new GitAccessToken();
        token.setId(10L);
        token.setProvider("github");
        token.setName("Group GitHub Token");
        when(tokenService.resolveManagedTokenValue("github", 10L)).thenReturn(Mono.just("github-token"));
        when(tokenRepository.findById(10L)).thenReturn(Mono.just(token));

        GitRepositoryApiRepo remote = githubRepo("wybaby168/file-viewer-demo", "file-viewer-demo",
                "File Viewer Demo", Map.of("push", true));
        when(githubClient.listRepositories("Bearer github-token", "all", "owner,collaborator,organization_member",
                "full_name", "asc", 50, 1)).thenReturn(Mono.just(List.of(remote)));

        GitManagedRepository deleted = new GitManagedRepository();
        deleted.setId(66L);
        deleted.setProvider("github");
        deleted.setOwner("wybaby168");
        deleted.setRepo("file-viewer-demo");
        deleted.setFullName("wybaby168/file-viewer-demo");
        deleted.setName("历史仓库");
        deleted.setPermission("pull");
        deleted.setDelete(true);

        doReturn(Mono.just(deleted)).when(repositoryRepository)
                .findByProviderAndFullNameIncludingDeleted("github", "wybaby168/file-viewer-demo");
        when(repositoryRepository.save(any(GitManagedRepository.class))).thenAnswer(invocation -> {
            GitManagedRepository repository = invocation.getArgument(0);
            assertFalse(repository.getDelete());
            return Mono.just(repository);
        });

        GitRepositorySyncDto dto = new GitRepositorySyncDto();
        dto.setProvider("github");
        dto.setAccessTokenId(10L);

        GitRepositoryManageServiceImpl service = service(repositoryRepository, tokenRepository, tokenService, githubClient);

        StepVerifier.create(service.syncRemoteRepositories(dto))
                .assertNext(result -> {
                    assertEquals(1, result.getTotalCount());
                    assertEquals(0, result.getCreatedCount());
                    GitManagedRepositoryVo repository = find(result.getRepositories(), "wybaby168/file-viewer-demo");
                    assertEquals(66L, repository.getId());
                    assertEquals(10L, repository.getAccessTokenId());
                    assertEquals("push", repository.getPermission());
                })
                .verifyComplete();
    }

    @Test
    void syncRemoteRepositoriesRecoversDuplicateKeyRace() {
        GitManagedRepositoryRepository repositoryRepository = mock(GitManagedRepositoryRepository.class);
        GitAccessTokenRepository tokenRepository = mock(GitAccessTokenRepository.class);
        GitAccessTokenService tokenService = mock(GitAccessTokenService.class);
        GithubRepositoryClient githubClient = mock(GithubRepositoryClient.class);

        GitAccessToken token = new GitAccessToken();
        token.setId(10L);
        token.setProvider("github");
        token.setName("Group GitHub Token");
        when(tokenService.resolveManagedTokenValue("github", 10L)).thenReturn(Mono.just("github-token"));
        when(tokenRepository.findById(10L)).thenReturn(Mono.just(token));

        GitRepositoryApiRepo remote = githubRepo("wybaby168/file-viewer-demo", "file-viewer-demo",
                "File Viewer Demo", Map.of("pull", true));
        when(githubClient.listRepositories("Bearer github-token", "all", "owner,collaborator,organization_member",
                "full_name", "asc", 50, 1)).thenReturn(Mono.just(List.of(remote)));

        GitManagedRepository existing = new GitManagedRepository();
        existing.setId(88L);
        existing.setProvider("github");
        existing.setOwner("wybaby168");
        existing.setRepo("file-viewer-demo");
        existing.setFullName("wybaby168/file-viewer-demo");
        existing.setName("已存在仓库");
        existing.setPermission("pull");
        existing.setEnabled(true);

        doReturn(Mono.empty(), Mono.just(existing)).when(repositoryRepository)
                .findByProviderAndFullNameIncludingDeleted("github", "wybaby168/file-viewer-demo");

        AtomicInteger saveCount = new AtomicInteger();
        when(repositoryRepository.save(any(GitManagedRepository.class))).thenAnswer(invocation -> {
            GitManagedRepository repository = invocation.getArgument(0);
            if (saveCount.incrementAndGet() == 1) {
                return Mono.error(new DuplicateKeyException(
                        "executeMany; SQL [INSERT INTO git_repository ...]; Duplicate entry"));
            }
            return Mono.just(repository);
        });

        GitRepositorySyncDto dto = new GitRepositorySyncDto();
        dto.setProvider("github");
        dto.setAccessTokenId(10L);

        GitRepositoryManageServiceImpl service = service(repositoryRepository, tokenRepository, tokenService, githubClient);

        StepVerifier.create(service.syncRemoteRepositories(dto))
                .assertNext(result -> {
                    assertEquals(1, result.getTotalCount());
                    assertEquals(0, result.getCreatedCount());
                    GitManagedRepositoryVo repository = find(result.getRepositories(), "wybaby168/file-viewer-demo");
                    assertEquals(88L, repository.getId());
                    assertEquals(10L, repository.getAccessTokenId());
                    assertEquals("pull", repository.getPermission());
                })
                .verifyComplete();
    }

    private GitRepositoryManageServiceImpl service(GitManagedRepositoryRepository repositoryRepository,
                                                   GitAccessTokenRepository tokenRepository,
                                                   GitAccessTokenService tokenService,
                                                   GithubRepositoryClient githubClient) {
        return new GitRepositoryManageServiceImpl(
                repositoryRepository,
                tokenRepository,
                tokenService,
                mock(GiteaRepositoryClient.class),
                githubClient,
                mock(GiteeRepositoryClient.class)
        );
    }

    private GitManagedRepositoryVo find(List<GitManagedRepositoryVo> repositories, String fullName) {
        return repositories.stream()
                .filter(repository -> fullName.equals(repository.getFullName()))
                .findFirst()
                .orElseThrow();
    }

    private GitRepositoryApiRepo githubRepo(String fullName, String name, String description,
                                            Map<String, Boolean> permissions) {
        return new GitRepositoryApiRepo(
                name,
                description,
                fullName,
                "https://github.com/" + fullName,
                null,
                null,
                name,
                true,
                null,
                null,
                new GitRepositoryApiRepo.GitRepositoryOwner("wybaby168", null, null),
                null,
                permissions
        );
    }
}
