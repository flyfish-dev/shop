package group.flyfish.dev.common.bridge;

import group.flyfish.dev.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(XianyuBridgeProperties.class)
public class XianyuBridgeGuard {
    public static final String CHANNEL_CONTEXT = "flyfish.xianyu.verifiedChannel";
    private final XianyuBridgeProperties properties;
    private final DatabaseClient database;

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
    public Mono<Void> purgeExpiredNonces() {
        return Mono.defer(() -> database.sql("DELETE FROM xianyu_bridge_nonce WHERE expire_time<:now")
                .bind("now", Instant.now().getEpochSecond()).fetch().rowsUpdated().then());
    }

    public Mono<String> accept(BridgeRequest request, String path) {
        return Mono.defer(() -> {
            if (!properties.isEnabled() || !properties.isDataFlowApproved()) {
                return Mono.error(new BusinessException("XIANYU_BRIDGE_DISABLED", "渠道暂未启用"));
            }
            try {
                BridgeSignature.verify(request, path, properties.getSecret(), Instant.now());
            } catch (IllegalArgumentException error) {
                return Mono.error(new BusinessException("XIANYU_BRIDGE_INVALID", "渠道请求校验失败"));
            }
            return database.sql("INSERT INTO xianyu_bridge_nonce (nonce, expire_time) VALUES (:nonce, :expire)")
                    .bind("nonce", request.nonce()).bind("expire", Instant.now().getEpochSecond() + 120)
                    .fetch().rowsUpdated().thenReturn(request.body())
                    .onErrorMap(DuplicateKeyException.class, error ->
                            new BusinessException("XIANYU_BRIDGE_REPLAY", "渠道请求已处理"));
        });
    }
}
