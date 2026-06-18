package group.flyfish.dev.user.service;

import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * token的小黑屋
 *
 * @author wangyu
 */
public interface TokenBlockStore {

    /**
     * 判断token是否被拉黑
     *
     * @param jti token id
     * @return 结果
     */
    Mono<Boolean> isBlock(String jti);

    /**
     * 屏蔽某个token
     *
     * @param jti token id
     * @param expiration 原过期时间
     * @return 结果
     */
    Mono<Void> block(String jti, Date expiration);
}
