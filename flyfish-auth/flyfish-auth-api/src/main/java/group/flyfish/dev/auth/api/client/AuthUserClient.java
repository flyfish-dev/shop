package group.flyfish.dev.auth.api.client;

import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 认证服务内部 HTTP 契约。
 */
@HttpExchange("/internal/auth")
public interface AuthUserClient {

    @GetExchange("/users/current")
    Mono<PortalUserVo> current(@RequestHeader(name = "Authorization", required = false) String authorization);

    @GetExchange("/users/{id}")
    Mono<PortalUserVo> getById(@PathVariable("id") Long id);

    @GetExchange("/users")
    Mono<List<PortalUserVo>> listUsers(@RequestParam(name = "keyword", required = false) String keyword,
                                       @RequestParam(name = "ids", required = false) String ids);

    @GetExchange("/authorizations/by-openid")
    Mono<PortalUserOauthVo> authorizationByOpenid(@RequestParam("type") String type,
                                                  @RequestParam("openid") String openid);

    @GetExchange("/authorizations/by-user")
    Mono<List<PortalUserOauthVo>> authorizationsByUser(@RequestParam("userId") Long userId,
                                                       @RequestParam(name = "type", required = false) String type);
}
