package group.flyfish.dev.oauth.config;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.oauth.client.GiteeUserInfoClient;
import group.flyfish.dev.oauth.client.GithubUserInfoClient;
import group.flyfish.dev.oauth.service.OAuthProfileEnrichmentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OAuthUserInfoConfig {

    @Bean
    public GithubUserInfoClient githubUserInfoClient(OAuthProperties properties) {
        WebClient webClient = WebClient.builder()
                .baseUrl(StringUtils.defaultIfBlank(properties.getGithub().getApiBaseUrl(), "https://api.github.com"))
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "Flyfish-Dev")
                .build();
        return HttpInterfaceClients.create(webClient, GithubUserInfoClient.class);
    }

    @Bean
    public GiteeUserInfoClient giteeUserInfoClient(OAuthProperties properties) {
        WebClient webClient = WebClient.builder()
                .baseUrl(StringUtils.defaultIfBlank(properties.getGitee().getApiBaseUrl(), "https://gitee.com/api/v5"))
                .build();
        return HttpInterfaceClients.create(webClient, GiteeUserInfoClient.class);
    }

    @Bean
    public OAuthProfileEnrichmentService oauthProfileEnrichmentService(GithubUserInfoClient githubUserInfoClient,
                                                                       GiteeUserInfoClient giteeUserInfoClient) {
        return new OAuthProfileEnrichmentService(githubUserInfoClient, giteeUserInfoClient);
    }
}
