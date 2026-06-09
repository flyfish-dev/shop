package group.flyfish.dev.user.service;

import group.flyfish.dev.user.domain.vo.PortalUserVo;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 用户信息抽取器
 *
 * @author wangyu
 */
public interface PrincipalExtractor {

    /**
     * 抽取用户id
     *
     * @param exchange 抽取器
     * @return 结果
     */
    Mono<Long> extractUserId(ServerWebExchange exchange);

    /**
     * 抽取用户信息
     *
     * @param exchange 抽取器
     * @return 结果
     */
    Mono<PortalUserVo> extractUser(ServerWebExchange exchange);
}
