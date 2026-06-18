package group.flyfish.dev.git.service;

import group.flyfish.dev.git.domain.dto.GitRepositoryCreateDto;
import group.flyfish.dev.git.domain.dto.GitRepositorySyncDto;
import group.flyfish.dev.git.domain.dto.GitRepositoryUpdateDto;
import group.flyfish.dev.git.domain.vo.GitManagedRepositoryVo;
import group.flyfish.dev.git.domain.vo.GitRepositorySyncResultVo;
import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GitRepositoryManageService {

    Flux<GitManagedRepositoryVo> list(String provider, String keyword, boolean includeDisabled);

    Flux<GitRepositoryOptionVo> listOptions(String provider, String keyword);

    Mono<GitManagedRepositoryVo> create(GitRepositoryCreateDto dto);

    Mono<GitManagedRepositoryVo> update(Long id, GitRepositoryUpdateDto dto);

    Mono<Void> delete(Long id);

    Flux<GitRepositoryOptionVo> listRemoteRepositories(String provider, Long tokenId, String keyword,
                                                       Integer page, Integer size);

    Mono<GitRepositorySyncResultVo> syncRemoteRepositories(GitRepositorySyncDto dto);
}
