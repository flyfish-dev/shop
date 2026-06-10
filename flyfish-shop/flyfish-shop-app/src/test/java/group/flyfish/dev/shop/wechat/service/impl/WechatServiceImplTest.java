package group.flyfish.dev.shop.wechat.service.impl;

import group.flyfish.dev.auth.api.client.WechatLoginClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatServiceImplTest {

    @Test
    void createsConfirmedSessionForWechatMessageQuickLogin() {
        FakeWechatLoginClient loginClient = new FakeWechatLoginClient();
        WechatServiceImpl service = new WechatServiceImpl(loginClient);

        StepVerifier.create(service.createConfirmedSession("wechat-openid", 900))
                .assertNext(scene -> {
                    assertEquals("000001", scene);
                    assertTrue(loginClient.confirmed(scene));
                    assertEquals("wechat-openid", loginClient.openid(scene));
                })
                .verifyComplete();
    }

    private static class FakeWechatLoginClient implements WechatLoginClient {

        private final AtomicInteger ids = new AtomicInteger();

        private final Map<String, String> confirmedOpenids = new ConcurrentHashMap<>();

        @Override
        public Mono<Boolean> exists(String scene) {
            return Mono.just(confirmedOpenids.containsKey(scene));
        }

        @Override
        public Mono<Void> scan(String scene, String openid) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> confirm(String scene, String openid) {
            confirmedOpenids.put(scene, openid);
            return Mono.empty();
        }

        @Override
        public Mono<String> createConfirmed(String openid, int expireSeconds) {
            String scene = "%06d".formatted(ids.incrementAndGet());
            confirmedOpenids.put(scene, openid);
            return Mono.just(scene);
        }

        private boolean confirmed(String scene) {
            return confirmedOpenids.containsKey(scene);
        }

        private String openid(String scene) {
            return confirmedOpenids.get(scene);
        }
    }
}
