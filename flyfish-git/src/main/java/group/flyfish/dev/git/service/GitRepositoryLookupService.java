package group.flyfish.dev.git.service;

import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import reactor.core.publisher.Flux;

public interface GitRepositoryLookupService {

    Flux<GitRepositoryOptionVo> list(String provider, String keyword, Integer page, Integer size);
}
