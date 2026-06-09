package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.user.domain.vo.PortalUserVo;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.PrincipalExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户信息抽取器
 *
 * @author wangyu
 */
@RequiredArgsConstructor
public class PrincipalExtractorImpl implements PrincipalExtractor {

    private final TokenProvider tokenProvider;

    private final PortalUserService portalUserService;

    /**
     * 抽取用户信息
     *
     * @param exchange 抽取器
     * @return 结果
     */
    @Override
    public Mono<Long> extractUserId(ServerWebExchange exchange) {
        return Flux.fromIterable(tokenProvider.retrieveTokens(exchange))
                .concatMap(tokenProvider::parseAndValidateToken)
                .next()
                .map(token -> token.subject())
                .map(Long::parseLong);
    }

    /**
     * 抽取用户信息
     *
     * @param exchange 抽取器
     * @return 结果
     */
    @Override
    public Mono<PortalUserVo> extractUser(ServerWebExchange exchange) {
        return extractUserId(exchange)
                .flatMap(portalUserService::getById);
    }
}
