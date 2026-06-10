package group.flyfish.dev.user.service;

import group.flyfish.dev.user.domain.UserToken;
import reactor.core.publisher.Mono;

/**
 * 令牌服务
 * @author wangyu
 */
public interface TokenService {

    Mono<UserToken> createToken(Long userId);

    Mono<Boolean> validateToken(String authToken);
}
