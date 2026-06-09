package group.flyfish.dev.oauth.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GitHub authenticated user companion APIs.
 */
@HttpExchange
public interface GithubUserInfoClient {

    @GetExchange("/user/emails")
    Mono<List<OAuthEmail>> listEmails(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
