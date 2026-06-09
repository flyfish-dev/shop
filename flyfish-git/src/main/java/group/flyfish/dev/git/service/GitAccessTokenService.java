package group.flyfish.dev.git.service;

import group.flyfish.dev.git.domain.dto.GitAccessTokenCreateDto;
import group.flyfish.dev.git.domain.dto.GitAccessTokenUpdateDto;
import group.flyfish.dev.git.domain.vo.GitAccessTokenVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GitAccessTokenService {

    Flux<GitAccessTokenVo> list(String provider);

    Mono<GitAccessTokenVo> create(GitAccessTokenCreateDto dto);

    Mono<GitAccessTokenVo> update(Long id, GitAccessTokenUpdateDto dto);

    Mono<Void> delete(Long id);

    Mono<String> resolveTokenValue(String provider, Long tokenId);

    Mono<String> resolveManagedTokenValue(String provider, Long tokenId);
}
