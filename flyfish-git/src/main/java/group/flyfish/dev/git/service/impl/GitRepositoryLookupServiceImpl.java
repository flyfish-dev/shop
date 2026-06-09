package group.flyfish.dev.git.service.impl;

import group.flyfish.dev.git.service.GitRepositoryLookupService;
import group.flyfish.dev.git.service.GitRepositoryManageService;
import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * 兼容旧商品表单的远程仓库查询入口。
 */
@RequiredArgsConstructor
public class GitRepositoryLookupServiceImpl implements GitRepositoryLookupService {

    private final GitRepositoryManageService repositoryManageService;

    @Override
    public Flux<GitRepositoryOptionVo> list(String provider, String keyword, Integer page, Integer size) {
        return repositoryManageService.listRemoteRepositories(provider, null, keyword, page, size);
    }
}
