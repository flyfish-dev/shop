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

    /**
     * 查询门户用户总数。
     *
     * <p>管理工作台、统计卡片等场景只关心数量时必须使用这个轻量接口，
     * 避免跨服务拉取完整用户列表和第三方授权信息。</p>
     */
    Mono<Long> countUsers();

    Flux<PortalUserVo> findAllByIds(Collection<Long> ids);

    Mono<PortalUserOauthVo> authorizationByOpenid(OAuthType type, String openid);

    Flux<PortalUserOauthVo> authorizationsByUser(Long userId, OAuthType type);
}
