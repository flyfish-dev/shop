package group.flyfish.dev.user.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.oauth.service.OAuthSessionService;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.user.domain.dto.PortalUserUpdateDto;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.impl.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/portal/users")
@RequiredArgsConstructor
public class PortalUserController {

    private final PortalUserService portalUserService;

    private final TokenProvider tokenProvider;

    private final UploadService uploadService;

    private final OAuthSessionService oauthSessionService;

    /**
     * 获取当前登录用户
     *
     * @return 结果
     */
    @GetMapping("current")
    public Result<PortalUserVo> getCurrentUser(@CurrentUser PortalUserVo user) {
        return Result.ok(user);
    }

    @PutMapping("current")
    public Mono<Result<PortalUserVo>> updateCurrentUser(@CurrentUser PortalUserVo user,
                                                        @RequestBody PortalUserUpdateDto dto) {
        return portalUserService.updateProfile(user == null ? null : user.getId(), dto).map(Result::ok);
    }

    @PostMapping(value = "current/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<String>> uploadCurrentUserAvatar(@CurrentUser PortalUserVo user,
                                                        @RequestPart("file") FilePart file) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再上传头像"));
        }
        MediaType contentType = file.headers().getContentType();
        if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
            return Mono.error(new BusinessException("AVATAR_TYPE_INVALID", "头像必须是图片文件"));
        }
        return uploadService.upload(file)
                .map(metadata -> Result.accept(metadata.getUrl()));
    }

    @DeleteMapping("authorizations/{type}")
    public Mono<Result<Void>> unbindAuthorization(@CurrentUser PortalUserVo user, @PathVariable String type) {
        return portalUserService.unbindAuthorization(user == null ? null : user.getId(), OAuthType.from(type))
                .thenReturn(Result.ok());
    }

    @PostMapping("logout")
    public Mono<Result<Void>> logout(ServerWebExchange exchange) {
        return tokenProvider.removeToken(exchange)
                .then(oauthSessionService.invalidate(exchange))
                .thenReturn(Result.ok());
    }
}
