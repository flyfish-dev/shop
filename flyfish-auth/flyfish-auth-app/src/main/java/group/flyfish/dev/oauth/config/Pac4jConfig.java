package group.flyfish.dev.oauth.config;

import group.flyfish.dev.oauth.vender.gitea.GiteaClient;
import group.flyfish.dev.oauth.vender.gitee.GiteeClient;
import group.flyfish.dev.oauth.vender.github.GithubClient;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.matching.matcher.PathMatcher;
import org.pac4j.springframework.context.SpringWebfluxSessionStoreFactory;
import org.pac4j.springframework.context.SpringWebfluxWebContextFactory;
import org.pac4j.springframework.web.CallbackController;
import org.pac4j.springframework.web.SecurityFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.server.WebFilter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ComponentScan(basePackages = "org.pac4j.springframework.web",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = CallbackController.class))
public class Pac4jConfig {

    public static final String USER_PROFILE = "user_profile";

    @Bean
    @ConfigurationProperties(prefix = "oauth")
    public OAuthProperties oAuthProperties() {
        return new OAuthProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "pac4j")
    public Pac4jProperties pac4jProperties() {
        return new Pac4jProperties();
    }

    @Bean
    public Config config(OAuthProperties properties) {
        OAuthProperties.Gitea gitea = properties.getGitea();
        OAuthProperties.Gitee gitee = properties.getGitee();
        OAuthProperties.Github github = properties.getGithub();
        List<Client> oauthClients = new ArrayList<>();

        if (StringUtils.isNoneBlank(gitea.getClientId(), gitea.getClientSecret())) {
            oauthClients.add(new GiteaClient(gitea.getClientId(), gitea.getClientSecret()));
        }
        if (StringUtils.isNoneBlank(gitee.getClientId(), gitee.getClientSecret())) {
            oauthClients.add(new GiteeClient(gitee.getClientId(), gitee.getClientSecret(), gitee.getScope()));
        }
        if (StringUtils.isNoneBlank(github.getClientId(), github.getClientSecret())) {
            oauthClients.add(new GithubClient(github.getClientId(), github.getClientSecret(), github.getScope()));
        }

        final Clients clients = new Clients(properties.getCallbackUrl(), oauthClients);

        final Config config = new Config(clients);

        // Native images do not always hit pac4j's framework defaults in the same
        // order as the JVM runtime, so wire the WebFlux context pieces explicitly.
        config.setWebContextFactory(SpringWebfluxWebContextFactory.INSTANCE);
        config.setSessionStoreFactory(SpringWebfluxSessionStoreFactory.INSTANCE);
        config.setHttpActionAdapter(new CustomHttpActionAdapter());

        return config;
    }

    @Bean
    public WebFilter giteaFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Gitea gitea = properties.getGitea();
        if (!StringUtils.isNoneBlank(gitea.getClientId(), gitea.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "GiteaClient", new PathMatcher().includePath("/oauth/gitea"));
    }

    @Bean
    public WebFilter giteeFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Gitee gitee = properties.getGitee();
        if (!StringUtils.isNoneBlank(gitee.getClientId(), gitee.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "GiteeClient", new PathMatcher().includePath("/oauth/gitee"));
    }

    @Bean
    public WebFilter githubFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Github github = properties.getGithub();
        if (!StringUtils.isNoneBlank(github.getClientId(), github.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "GithubClient", new PathMatcher().includePath("/oauth/github"));
    }
}
