package group.flyfish.dev.auth.internal;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.auth.api.user.UserAuthorizationUtils;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.repository.PortalUserOauthRepository;
import group.flyfish.dev.user.service.PortalUserService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 认证服务内部用户资料接口。
 *
 * <p>低代码和小铺只通过这里读取登录用户、用户展示资料和第三方绑定资料，避免直接依赖认证服务的
 * Repository、OAuth、JWT、模板等具体实现。</p>
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class AuthInternalUserController {

    private final PortalUserService portalUserService;

    private final PortalUserOauthRepository oauthRepository;

    @GetMapping("/users/current")
    public Mono<PortalUserVo> current(@CurrentUser PortalUserVo user) {
        UserAuthorizationUtils.requireLogin(user);
        return Mono.just(user.withoutAuthorizationUserInfo());
    }

    @GetMapping("/users/{id:\\d+}")
    public Mono<PortalUserVo> getById(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Mono.error(new BusinessException("USER_NOT_FOUND", "用户不存在"));
        }
        return portalUserService.getById(id)
                .map(PortalUserVo::withoutAuthorizationUserInfo)
                .switchIfEmpty(Mono.error(new BusinessException("USER_NOT_FOUND", "用户不存在")));
    }

    @GetMapping("/users")
    public Mono<List<PortalUserVo>> listUsers(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String ids) {
        List<Long> userIds = parseIds(ids);
        Flux<PortalUserVo> users = userIds.isEmpty()
                ? portalUserService.listUsers(keyword)
                : Flux.fromIterable(userIds).flatMap(portalUserService::getById, 8);
        return users.map(PortalUserVo::withoutAuthorizationUserInfo).collectList();
    }

    @GetMapping("/users/count")
    public Mono<Long> countUsers() {
        return portalUserService.countUsers();
    }

    @GetMapping("/authorizations/by-openid")
    public Mono<PortalUserOauthVo> authorizationByOpenid(@RequestParam String type, @RequestParam String openid) {
        if (StringUtils.isBlank(openid)) {
            return Mono.error(new BusinessException("AUTHORIZATION_NOT_FOUND", "授权信息不存在"));
        }
        return oauthRepository.findAllByTypeAndOpenid(OAuthType.from(type), openid.trim())
                .next()
                .map(this::toVo)
                .map(PortalUserOauthVo::withoutUserInfo)
                .switchIfEmpty(Mono.error(new BusinessException("AUTHORIZATION_NOT_FOUND", "授权信息不存在")));
    }

    @GetMapping("/authorizations/by-user")
    public Mono<List<PortalUserOauthVo>> authorizationsByUser(@RequestParam Long userId,
                                                              @RequestParam(required = false) String type) {
        if (userId == null || userId <= 0) {
            return Mono.just(List.of());
        }
        Flux<PortalUserOauth> authorizations = StringUtils.isBlank(type)
                ? oauthRepository.findAllByUserId(userId)
                : oauthRepository.findAllByUserIdAndType(userId, OAuthType.from(type));
        return authorizations.map(this::toVo)
                .map(PortalUserOauthVo::withoutUserInfo)
                .collectList();
    }

    private List<Long> parseIds(String ids) {
        if (StringUtils.isBlank(ids)) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .map(Long::parseLong)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private PortalUserOauthVo toVo(PortalUserOauth oauth) {
        return PortalUserOauthVo.of(oauth.getUserId(), oauth.getType(), oauth.getOpenid(), oauth.getUserInfo(),
                oauth.getAuthTime(), oauth.getLoginName(), oauth.getDisplayName(), oauth.getNickname(),
                oauth.getAvatarUrl(), oauth.getEmail(), oauth.getProfileUrl(), oauth.getUnionId());
    }
}
