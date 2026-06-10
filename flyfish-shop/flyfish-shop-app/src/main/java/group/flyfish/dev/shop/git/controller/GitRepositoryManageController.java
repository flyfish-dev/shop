package group.flyfish.dev.shop.git.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.git.domain.dto.GitAccessTokenCreateDto;
import group.flyfish.dev.git.domain.dto.GitAccessTokenUpdateDto;
import group.flyfish.dev.git.domain.dto.GitRepositoryCreateDto;
import group.flyfish.dev.git.domain.dto.GitRepositorySyncDto;
import group.flyfish.dev.git.domain.dto.GitRepositoryUpdateDto;
import group.flyfish.dev.git.domain.vo.GitAccessTokenVo;
import group.flyfish.dev.git.domain.vo.GitManagedRepositoryVo;
import group.flyfish.dev.git.domain.vo.GitRepositorySyncResultVo;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.git.service.GitRepositoryManageService;
import group.flyfish.dev.git.domain.vo.GitRepositoryOptionVo;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Validated
@RestController
@RequestMapping("/shops/managements/git")
@RequiredArgsConstructor
public class GitRepositoryManageController {

    private final GitAccessTokenService tokenService;
    private final GitRepositoryManageService repositoryManageService;

    @GetMapping("api-tokens")
    public Mono<Result<List<GitAccessTokenVo>>> tokens(@RequestParam(defaultValue = "github") String provider,
                                                       @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return tokenService.list(provider).collectList().map(Result::ok);
    }

    @PostMapping("api-tokens")
    public Mono<Result<GitAccessTokenVo>> createToken(@Valid @RequestBody GitAccessTokenCreateDto dto,
                                                     @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return tokenService.create(dto).map(Result::ok);
    }

    @PutMapping("api-tokens/{id}")
    public Mono<Result<GitAccessTokenVo>> updateToken(@NotNull @PathVariable Long id,
                                                     @Valid @RequestBody GitAccessTokenUpdateDto dto,
                                                     @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return tokenService.update(id, dto).map(Result::ok);
    }

    @DeleteMapping("api-tokens/{id}")
    public Mono<Result<Void>> deleteToken(@NotNull @PathVariable Long id,
                                          @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return tokenService.delete(id).thenReturn(Result.ok());
    }

    @GetMapping("repositories")
    public Mono<Result<List<GitManagedRepositoryVo>>> repositories(@RequestParam(required = false) String provider,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(defaultValue = "true")
                                                                   Boolean includeDisabled,
                                                                   @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.list(provider, keyword, Boolean.TRUE.equals(includeDisabled))
                .collectList()
                .map(Result::ok);
    }

    @GetMapping("repository-options")
    public Mono<Result<List<GitRepositoryOptionVo>>> repositoryOptions(@RequestParam(required = false) String provider,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.listOptions(provider, keyword).collectList().map(Result::ok);
    }

    @GetMapping("remote-repositories")
    public Mono<Result<List<GitRepositoryOptionVo>>> remoteRepositories(@RequestParam(defaultValue = "github") String provider,
                                                                        @RequestParam(required = false) Long tokenId,
                                                                        @RequestParam(required = false) String q,
                                                                        @RequestParam(defaultValue = "1") Integer page,
                                                                        @RequestParam(defaultValue = "50") Integer size,
                                                                        @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.listRemoteRepositories(provider, tokenId, q, page, size)
                .collectList()
                .map(Result::ok);
    }

    @PostMapping("repositories/sync")
    public Mono<Result<GitRepositorySyncResultVo>> syncRepositories(@Valid @RequestBody GitRepositorySyncDto dto,
                                                                    @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.syncRemoteRepositories(dto).map(Result::ok);
    }

    @PostMapping("repositories")
    public Mono<Result<GitManagedRepositoryVo>> createRepository(@Valid @RequestBody GitRepositoryCreateDto dto,
                                                                @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.create(dto).map(Result::ok);
    }

    @PutMapping("repositories/{id}")
    public Mono<Result<GitManagedRepositoryVo>> updateRepository(@NotNull @PathVariable Long id,
                                                                @Valid @RequestBody GitRepositoryUpdateDto dto,
                                                                @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.update(id, dto).map(Result::ok);
    }

    @DeleteMapping("repositories/{id}")
    public Mono<Result<Void>> deleteRepository(@NotNull @PathVariable Long id,
                                               @CurrentUser PortalUserVo user) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return repositoryManageService.delete(id).thenReturn(Result.ok());
    }
}
