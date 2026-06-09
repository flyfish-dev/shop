package group.flyfish.dev.shop.git.impl;

import group.flyfish.dev.git.config.GiteaProperties;
import group.flyfish.dev.git.config.GithubProperties;
import group.flyfish.dev.git.domain.po.GitManagedRepository;
import group.flyfish.dev.git.repository.GitAccessTokenRepository;
import group.flyfish.dev.git.repository.GitManagedRepositoryRepository;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitRepositoryDataMigrationTest {

    @Test
    void completesRepositoryIdOnlyShopItemParamsFromManagedRepository() {
        GitAccessTokenRepository tokenRepository = mock(GitAccessTokenRepository.class);
        GitManagedRepositoryRepository repositoryRepository = mock(GitManagedRepositoryRepository.class);
        ShopItemRepository shopItemRepository = mock(ShopItemRepository.class);
        DatabaseClient databaseClient = mock(DatabaseClient.class);
        DatabaseClient.GenericExecuteSpec executeSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        @SuppressWarnings("unchecked")
        FetchSpec<Map<String, Object>> fetchSpec = mock(FetchSpec.class);

        when(databaseClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));

        ShopItem item = new ShopItem();
        item.setId(3L);
        item.setType(ShopItem.Type.GIT_REPOSITORY_ACCESS);
        item.setParams("""
                {"provider":"gitea","repositoryIds":[18]}
                """);
        when(shopItemRepository.findAll()).thenReturn(Flux.just(item));
        when(shopItemRepository.save(any(ShopItem.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        GitManagedRepository repository = new GitManagedRepository();
        repository.setId(18L);
        repository.setProvider("github");
        repository.setOwner("flyfish-dev");
        repository.setRepo("rtsp-source");
        repository.setFullName("flyfish-dev/rtsp-source");
        repository.setName("RTSP 源码仓库");
        repository.setPermission("pull");
        when(repositoryRepository.findById(18L)).thenReturn(Mono.just(repository));

        GitRepositoryDataMigration migration = new GitRepositoryDataMigration(
                tokenRepository,
                repositoryRepository,
                shopItemRepository,
                new GiteaProperties(),
                new GithubProperties(),
                databaseClient
        );

        migration.run(mock(ApplicationArguments.class));

        ArgumentCaptor<ShopItem> itemCaptor = ArgumentCaptor.forClass(ShopItem.class);
        verify(shopItemRepository).save(itemCaptor.capture());
        GitRepositoryAccessParamValue migrated = ShopItemParamValue.gitRepositoryAccess(itemCaptor.getValue().getParams());
        assertEquals("github", migrated.getProvider());
        assertEquals(18L, migrated.getRepositories().getFirst().getRepositoryId());
        assertEquals("github", migrated.getRepositories().getFirst().getProvider());
        assertEquals("flyfish-dev", migrated.getRepositories().getFirst().getOwner());
        assertEquals("rtsp-source", migrated.getRepositories().getFirst().getRepo());
        assertEquals("pull", migrated.getRepositories().getFirst().getPermission());
    }
}
