package group.flyfish.dev.auth.api.client;

import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 业务服务访问认证服务的轻量网关。
 */
public interface AuthUserGateway {

    Mono<PortalUserVo> current(ServerWebExchange exchange);

    Mono<PortalUserVo> authenticate(Collection<String> tokens);

    Mono<PortalUserVo> getById(Long id);

    Flux<PortalUserVo> listUsers(String keyword);

    Flux<PortalUserVo> findAllByIds(Collection<Long> ids);

    Mono<PortalUserOauthVo> authorizationByOpenid(OAuthType type, String openid);

    Flux<PortalUserOauthVo> authorizationsByUser(Long userId, OAuthType type);
}
