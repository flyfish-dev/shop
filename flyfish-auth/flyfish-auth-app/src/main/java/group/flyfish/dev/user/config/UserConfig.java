package group.flyfish.dev.user.config;

import group.flyfish.dev.oauth.service.OAuthProfileEnrichmentService;
import group.flyfish.dev.user.config.context.UserArgumentResolver;
import group.flyfish.dev.user.config.context.UserAuditAware;
import group.flyfish.dev.user.config.filter.ReactiveUserFilter;
import group.flyfish.dev.user.repository.PortalUserOauthRepository;
import group.flyfish.dev.user.repository.PortalUserRepository;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.PrincipalExtractor;
import group.flyfish.dev.user.service.TokenBlockStore;
import group.flyfish.dev.user.service.impl.MemoryTokenBlockStore;
import group.flyfish.dev.user.service.impl.PortalUserServiceImpl;
import group.flyfish.dev.user.service.impl.PrincipalExtractorImpl;
import group.flyfish.dev.user.service.impl.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.data.domain.ReactiveAuditorAware;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 用户配置
 *
 * @author wangyu
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class UserConfig {

    @Bean
    @ConfigurationProperties(prefix = "user.jwt")
    public JwtProperties jwtProperties() {
        return new JwtProperties();
    }

    @Bean
    public PortalUserService portalUserService(PortalUserRepository portalUserRepository,
                                               PortalUserOauthRepository portalUserOauthRepository,
                                               TokenProvider tokenProvider,
                                               OAuthProfileEnrichmentService oAuthProfileEnrichmentService) {
        return new PortalUserServiceImpl(portalUserRepository, portalUserOauthRepository, tokenProvider,
                oAuthProfileEnrichmentService);
    }

    @Bean
    public ApplicationRunner portalUserOauthMetadataBackfill(PortalUserService portalUserService) {
        return args -> {
            Long count = portalUserService.backfillAuthorizationMetadata()
                    .onErrorResume(e -> {
                        log.warn("门户用户第三方资料回填失败：{}", e.getMessage());
                        return Mono.just(0L);
                    })
                    .block(Duration.ofSeconds(30));
            log.info("门户用户第三方资料和展示名回填完成，更新 {} 条记录", count == null ? 0L : count);
        };
    }

    @Bean
    public TokenProvider tokenProvider() {
        return new TokenProvider(jwtProperties(), tokenBlockStore());
    }

    @Bean
    public TokenBlockStore tokenBlockStore() {
        return new MemoryTokenBlockStore();
    }

    @Bean
    public PrincipalExtractor principalExtractor(TokenProvider tokenProvider, PortalUserService portalUserService) {
        return new PrincipalExtractorImpl(tokenProvider, portalUserService);
    }

    @Bean
    public UserArgumentResolver userArgumentResolver(PrincipalExtractor principalExtractor) {
        return new UserArgumentResolver(ReactiveAdapterRegistry.getSharedInstance(), principalExtractor);
    }

    @Bean
    public WebFluxConfigurer userWebFluxConfigurer(UserArgumentResolver userArgumentResolver) {
        return new WebFluxConfigurer() {
            @Override
            public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
                configurer.addCustomResolver(userArgumentResolver);
            }
        };
    }

    @Bean
    public ReactiveAuditorAware<String> userAuditAware() {
        return new UserAuditAware();
    }

    @Bean
    public ReactiveUserFilter reactiveUserFilter(PrincipalExtractor principalExtractor, JwtProperties jwtProperties) {
        return new ReactiveUserFilter(principalExtractor, jwtProperties);
    }
}
