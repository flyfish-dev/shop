package group.flyfish.dev.oauth.config;

import group.flyfish.dev.oauth.vender.gitea.GiteaClient;
import group.flyfish.dev.oauth.vender.gitee.GiteeClient;
import group.flyfish.dev.oauth.vender.github.GithubClient;
import group.flyfish.dev.oauth.vender.google.GoogleOAuthClient;
import group.flyfish.dev.oauth.vender.microsoft.MicrosoftClient;
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.matching.matcher.PathMatcher;
import org.pac4j.oauth.client.OAuth20Client;
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

import java.net.InetSocketAddress;
import java.net.Proxy;
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
        OAuthProperties.Google google = properties.getGoogle();
        OAuthProperties.Microsoft microsoft = properties.getMicrosoft();
        List<Client> oauthClients = new ArrayList<>();

        if (StringUtils.isNoneBlank(gitea.getClientId(), gitea.getClientSecret())) {
            oauthClients.add(configureHttpClient(
                    new GiteaClient(gitea.getClientId(), gitea.getClientSecret()), properties, false));
        }
        if (StringUtils.isNoneBlank(gitee.getClientId(), gitee.getClientSecret())) {
            oauthClients.add(configureHttpClient(
                    new GiteeClient(gitee.getClientId(), gitee.getClientSecret(), gitee.getScope()), properties, false));
        }
        if (StringUtils.isNoneBlank(github.getClientId(), github.getClientSecret())) {
            oauthClients.add(configureHttpClient(
                    new GithubClient(github.getClientId(), github.getClientSecret(), github.getScope()), properties, true));
        }
        if (StringUtils.isNoneBlank(google.getClientId(), google.getClientSecret())) {
            GoogleOAuthClient googleClient = new GoogleOAuthClient(google.getClientId(), google.getClientSecret());
            googleClient.setName("Google2Client");
            oauthClients.add(configureHttpClient(googleClient, properties, true));
        }
        if (StringUtils.isNoneBlank(microsoft.getClientId(), microsoft.getClientSecret())) {
            oauthClients.add(configureHttpClient(new MicrosoftClient(microsoft.getClientId(),
                    microsoft.getClientSecret(), microsoft.getScope()), properties, true));
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

    /**
     * 为 pac4j/Scribe 显式配置超时和可选代理。
     *
     * <p>Native Image 不保证运行时 {@code https.proxyHost} 会被 Scribe 的 URLConnection 实现采用，
     * 因此必须在客户端初始化前设置 {@link JDKHttpClientConfig}。授权码是一次性的，不能依赖应用层
     * 盲目重试；将境外平台请求放到稳定出站链路，比重复消费授权码更可靠。</p>
     */
    static <T extends OAuth20Client> T configureHttpClient(T client, OAuthProperties properties,
                                                            boolean useEgressProxy) {
        OAuthProperties.EgressProxy proxyProperties = properties.getEgressProxy();
        JDKHttpClientConfig httpClientConfig = JDKHttpClientConfig.defaultConfig()
                .withConnectTimeout(Math.toIntExact(proxyProperties.getConnectTimeout().toMillis()))
                .withReadTimeout(Math.toIntExact(proxyProperties.getReadTimeout().toMillis()))
                .withFollowRedirects(true);
        if (useEgressProxy && proxyProperties.isEnabled()) {
            InetSocketAddress address = InetSocketAddress.createUnresolved(
                    proxyProperties.getHost(), proxyProperties.getPort());
            httpClientConfig.withProxy(new Proxy(Proxy.Type.HTTP, address));
        }
        client.getConfiguration().setHttpClientConfig(httpClientConfig);
        return client;
    }

    @Bean
    public WebFilter giteaFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Gitea gitea = properties.getGitea();
        if (!StringUtils.isNoneBlank(gitea.getClientId(), gitea.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "GiteaClient",
                new PathMatcher().includePath("/oauth/authorize/gitea"));
    }

    @Bean
    public WebFilter giteeFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Gitee gitee = properties.getGitee();
        if (!StringUtils.isNoneBlank(gitee.getClientId(), gitee.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "GiteeClient",
                new PathMatcher().includePath("/oauth/authorize/gitee"));
    }

    @Bean
    public WebFilter githubFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Github github = properties.getGithub();
        if (!StringUtils.isNoneBlank(github.getClientId(), github.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "GithubClient",
                new PathMatcher().includePath("/oauth/authorize/github"));
    }

    @Bean
    public WebFilter googleFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Google google = properties.getGoogle();
        if (!StringUtils.isNoneBlank(google.getClientId(), google.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "Google2Client",
                new PathMatcher().includePath("/oauth/authorize/google"));
    }

    @Bean
    public WebFilter microsoftFilter(Config config, OAuthProperties properties) {
        OAuthProperties.Microsoft microsoft = properties.getMicrosoft();
        if (!StringUtils.isNoneBlank(microsoft.getClientId(), microsoft.getClientSecret())) {
            return (exchange, chain) -> chain.filter(exchange);
        }
        return SecurityFilter.build(config, "MicrosoftClient",
                new PathMatcher().includePath("/oauth/authorize/microsoft"));
    }
}
