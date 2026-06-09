package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.user.service.TokenBlockStore;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryTokenBlockStore implements TokenBlockStore {

    private static final String TOKEN_BLOCK_PREFIX = "tk_blk_";
    private final Map<String, Date> blockStore = new ConcurrentHashMap<>();

    private String getCacheKey(String jti) {
        return TOKEN_BLOCK_PREFIX + jti;
    }

    /**
     * 判断token是否被拉黑
     *
     * @param jti token id
     * @return 结果
     */
    @Override
    public Mono<Boolean> isBlock(String jti) {
        return Mono.fromCallable(() -> {
            Date result = blockStore.computeIfPresent(jti, (key, exp) -> exp.before(new Date()) ? null : exp);
            return null != result;
        });
    }

    /**
     * 屏蔽某个token
     *
     * @param jti        token id
     * @param expiration 原过期时间
     * @return 结果
     */
    @Override
    public Mono<Void> block(String jti, Date expiration) {
        // long distance = expiration.getTime() - System.currentTimeMillis();
        blockStore.put(jti, expiration);
        return Mono.empty();
    }
}
