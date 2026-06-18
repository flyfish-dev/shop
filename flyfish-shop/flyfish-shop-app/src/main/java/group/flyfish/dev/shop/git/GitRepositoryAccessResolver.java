package group.flyfish.dev.shop.git;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.git.domain.GitProvider;
import group.flyfish.dev.git.domain.po.GitManagedRepository;
import group.flyfish.dev.git.repository.GitManagedRepositoryRepository;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 将商品保存的仓库引用解析为可交付仓库。
 */
@Service
@RequiredArgsConstructor
public class GitRepositoryAccessResolver {

    private final GitManagedRepositoryRepository repositoryRepository;

    public Flux<ResolvedGitRepository> resolve(GitRepositoryAccessParamValue param) {
        if (param == null || param.getRepositories() == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(param.getRepositories())
                .filter(GitRepositoryAccessParamValue.Repository::hasRepository)
                .concatMap(this::resolveRepository)
                .distinct(repository -> repository.provider() + ":" + repository.fullName());
    }

    private Mono<ResolvedGitRepository> resolveRepository(GitRepositoryAccessParamValue.Repository repository) {
        if (repository.getRepositoryId() != null) {
            return repositoryRepository.findById(repository.getRepositoryId())
                    .switchIfEmpty(Mono.error(new BusinessException("GIT_REPOSITORY_NOT_FOUND", "代码仓库不存在")))
                    .filter(saved -> Boolean.TRUE.equals(saved.getEnabled()))
                    .switchIfEmpty(Mono.error(new BusinessException("GIT_REPOSITORY_DISABLED", "代码仓库已停用")))
                    .map(saved -> fromSaved(saved, repository.getPermission()));
        }
        return Mono.just(fromLegacy(repository));
    }

    private ResolvedGitRepository fromSaved(GitManagedRepository repository, String permissionOverride) {
        String provider = GitProvider.normalizeCode(repository.getProvider());
        String permission = GitProvider.normalizePermission(provider,
                StringUtils.defaultIfBlank(permissionOverride, repository.getPermission()));
        return new ResolvedGitRepository(
                repository.getId(),
                provider,
                repository.getAccessTokenId(),
                repository.getOwner(),
                repository.getRepo(),
                repository.getName(),
                permission,
                repository.getUrl()
        );
    }

    private ResolvedGitRepository fromLegacy(GitRepositoryAccessParamValue.Repository repository) {
        String provider = GitProvider.normalizeCode(repository.getProvider());
        return new ResolvedGitRepository(
                null,
                provider,
                null,
                repository.getOwner(),
                repository.getRepo(),
                repository.fullName(),
                GitProvider.normalizePermission(provider, repository.getPermission()),
                null
        );
    }
}
