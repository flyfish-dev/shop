package group.flyfish.dev.oauth.client;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gitee authenticated user companion APIs.
 */
@HttpExchange
public interface GiteeUserInfoClient {

    @GetExchange("/emails")
    Mono<List<OAuthEmail>> listEmails(@RequestParam("access_token") String accessToken);
}
