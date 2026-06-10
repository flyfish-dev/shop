package group.flyfish.dev.shop.git;

import group.flyfish.dev.git.domain.po.GitManagedRepository;
import group.flyfish.dev.git.repository.GitManagedRepositoryRepository;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitRepositoryAccessResolverTest {

    @Test
    void repositoryIdUsesManagedRepositoryProviderInsteadOfLegacyFallback() {
        GitManagedRepositoryRepository repositoryRepository = mock(GitManagedRepositoryRepository.class);
        GitManagedRepository saved = new GitManagedRepository();
        saved.setId(18L);
        saved.setProvider("github");
        saved.setAccessTokenId(7L);
        saved.setOwner("flyfish-dev");
        saved.setRepo("rtsp-source");
        saved.setFullName("flyfish-dev/rtsp-source");
        saved.setName("RTSP 源码仓库");
        saved.setPermission("pull");
        saved.setEnabled(true);
        when(repositoryRepository.findById(18L)).thenReturn(Mono.just(saved));

        GitRepositoryAccessParamValue param = ShopItemParamValue.gitRepositoryAccess("""
                {"provider":"gitea","repositoryIds":[18]}
                """);
        GitRepositoryAccessResolver resolver = new GitRepositoryAccessResolver(repositoryRepository);

        StepVerifier.create(resolver.resolve(param))
                .assertNext(repository -> {
                    assertEquals("github", repository.provider());
                    assertEquals("flyfish-dev", repository.owner());
                    assertEquals("rtsp-source", repository.repo());
                    assertEquals("pull", repository.permission());
                    assertEquals(7L, repository.accessTokenId());
                })
                .verifyComplete();
    }
}
