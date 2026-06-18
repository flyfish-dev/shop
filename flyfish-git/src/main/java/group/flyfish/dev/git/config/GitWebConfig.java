package group.flyfish.dev.git.config;

import group.flyfish.dev.common.http.HttpInterfaceClients;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.service.GitRepositoryLookupService;
import group.flyfish.dev.git.service.GitRepositoryManageService;
import group.flyfish.dev.git.service.impl.GitRepositoryLookupServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GitWebConfig {

    /**
     * 代码托管平台的仓库列表响应会包含描述、权限、Owner 等完整元数据，GitHub 组织仓库较多时
     * 单页响应很容易超过 WebFlux 默认 256KB。这里仅放宽 Git 外部 API 客户端的解码上限，
     * 不影响站内接口和文件上传链路，避免大响应直接触发 DataBufferLimitException。
     */
    private static final int GIT_API_MAX_IN_MEMORY_SIZE = 4 * 1024 * 1024;

    @Bean
    public GiteaProperties giteaProperties(Environment environment) {
        Binder binder = Binder.get(environment);
        GiteaProperties properties = binder.bind("git.gitea", Bindable.of(GiteaProperties.class))
                .orElseGet(GiteaProperties::new);
        if (StringUtils.isBlank(properties.getServer())) {
            properties.setServer(binder.bind("oauth.gitea.server", String.class)
                    .orElse("https://gitea.example.com"));
        }
        if (StringUtils.isBlank(properties.getAdminToken())) {
            String oauthAdminToken = binder.bind("oauth.gitea.admin-token", String.class).orElse(null);
            if (StringUtils.isNotBlank(oauthAdminToken)) {
                properties.setAdminToken(oauthAdminToken);
            }
        }
        return properties;
    }

    @Bean
    public GithubProperties githubProperties(Environment environment) {
        Binder binder = Binder.get(environment);
        return binder.bind("git.github", Bindable.of(GithubProperties.class))
                .orElseGet(GithubProperties::new);
    }

    @Bean
    public GiteeProperties giteeProperties(Environment environment) {
        Binder binder = Binder.get(environment);
        GiteeProperties properties = binder.bind("git.gitee", Bindable.of(GiteeProperties.class))
                .orElseGet(GiteeProperties::new);
        if (StringUtils.isBlank(properties.getApiBaseUrl())) {
            properties.setApiBaseUrl(binder.bind("oauth.gitee.api-base-url", String.class)
                    .orElse("https://gitee.com/api/v5"));
        }
        return properties;
    }

    @Bean
    public WebClient giteaWebClient(GiteaProperties properties) {
        return gitWebClientBuilder()
                .baseUrl(properties.getServer())
                .build();
    }

    @Bean
    public WebClient githubWebClient(GithubProperties properties) {
        return gitWebClientBuilder()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "Flyfish-Dev")
                .build();
    }

    @Bean
    public WebClient giteeRepositoryWebClient(GiteeProperties properties) {
        return gitWebClientBuilder()
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, "Flyfish-Dev")
                .build();
    }

    @Bean
    public GiteaRepositoryClient giteaRepositoryClient(@Qualifier("giteaWebClient") WebClient giteaWebClient) {
        return HttpInterfaceClients.create(giteaWebClient, GiteaRepositoryClient.class);
    }

    @Bean
    public GithubRepositoryClient githubRepositoryClient(@Qualifier("githubWebClient") WebClient githubWebClient) {
        return HttpInterfaceClients.create(githubWebClient, GithubRepositoryClient.class);
    }

    @Bean
    public GiteeRepositoryClient giteeRepositoryClient(
            @Qualifier("giteeRepositoryWebClient") WebClient giteeRepositoryWebClient) {
        return HttpInterfaceClients.create(giteeRepositoryWebClient, GiteeRepositoryClient.class);
    }

    @Bean
    public GitRepositoryLookupService gitRepositoryLookupService(
            GitRepositoryManageService repositoryManageService) {
        return new GitRepositoryLookupServiceImpl(repositoryManageService);
    }

    private WebClient.Builder gitWebClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(GIT_API_MAX_IN_MEMORY_SIZE));
    }
}
