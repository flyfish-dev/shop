package group.flyfish.dev.user.config.filter;

import group.flyfish.dev.user.config.JwtProperties;
import group.flyfish.dev.user.service.PrincipalExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReactiveUserFilterTest {

    @Test
    void invokesDownstreamOnlyOnceForAuthenticatedUser() {
        PrincipalExtractor principalExtractor = mock(PrincipalExtractor.class);
        MockServerWebExchange exchange = exchange("/portal/users/current");
        when(principalExtractor.extractUserId(exchange)).thenReturn(Mono.just(1001L));

        AtomicInteger invocations = new AtomicInteger();
        WebFilterChain chain = currentExchange -> {
            invocations.incrementAndGet();
            return Mono.empty();
        };

        ReactiveUserFilter filter = new ReactiveUserFilter(principalExtractor, new JwtProperties());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(1, invocations.get());
    }

    @Test
    void rejectsGuestOnProtectedPath() {
        PrincipalExtractor principalExtractor = mock(PrincipalExtractor.class);
        MockServerWebExchange exchange = exchange("/shops/managements/items");
        when(principalExtractor.extractUserId(exchange)).thenReturn(Mono.empty());

        JwtProperties properties = new JwtProperties();
        properties.setAuthorizedUris(List.of("/shops/managements/**"));

        ReactiveUserFilter filter = new ReactiveUserFilter(principalExtractor, properties);
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(new AssertionError("不应进入业务处理链"))))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }
}
