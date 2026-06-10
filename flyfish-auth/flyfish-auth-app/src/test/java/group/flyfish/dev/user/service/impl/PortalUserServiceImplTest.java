package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.oauth.service.OAuthProfileEnrichmentService;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUser;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.repository.PortalUserOauthRepository;
import group.flyfish.dev.user.repository.PortalUserRepository;
import group.flyfish.dev.user.service.TokenService;
import group.flyfish.dev.auth.api.user.FunNicknameGenerator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalUserServiceImplTest {

    @Test
    void backfillPrefersCodeHostLoginOverGeneratedWechatName() {
        Fixture fixture = new Fixture();
        PortalUser user = fixture.user(100L, FunNicknameGenerator.generate("wechat-openid"));
        PortalUserOauth wechat = fixture.wechatOauth(100L, "wechat-openid");
        PortalUserOauth github = fixture.githubOauth(100L, "octocat");
        fixture.seed(user, List.of(wechat, github));

        StepVerifier.create(fixture.service.backfillAuthorizationMetadata())
                .expectNext(1L)
                .verifyComplete();

        assertThat(fixture.savedUser.get().getUsername()).isEqualTo("octocat");
    }

    @Test
    void backfillKeepsUserCustomizedNickname() {
        Fixture fixture = new Fixture();
        PortalUser user = fixture.user(100L, "我自己改的昵称");
        PortalUserOauth wechat = fixture.wechatOauth(100L, "wechat-openid");
        PortalUserOauth github = fixture.githubOauth(100L, "octocat");
        fixture.seed(user, List.of(wechat, github));

        StepVerifier.create(fixture.service.backfillAuthorizationMetadata())
                .expectNext(0L)
                .verifyComplete();

        verify(fixture.userRepository, never()).save(any(PortalUser.class));
    }

    @Test
    void emailLoginCreatesEmailAuthorization() {
        Fixture fixture = new Fixture();
        fixture.prepareNewEmailLogin("user@example.com", 200L);

        StepVerifier.create(fixture.service.registerOrLoginEmail(" User@Example.COM "))
                .assertNext(token -> assertThat(token.getToken()).isEqualTo("email-token"))
                .verifyComplete();

        assertThat(fixture.savedUser.get().getEmail()).isEqualTo("user@example.com");
        verify(fixture.oauthRepository).save(argThat(oauth -> oauth.getUserId().equals(200L)
                && oauth.getType() == OAuthType.EMAIL
                && "user@example.com".equals(oauth.getOpenid())
                && "user@example.com".equals(oauth.getEmail())));
    }

    private static class Fixture {

        private final PortalUserRepository userRepository = mock(PortalUserRepository.class);
        private final PortalUserOauthRepository oauthRepository = mock(PortalUserOauthRepository.class);
        private final TokenService tokenService = mock(TokenService.class);
        private final OAuthProfileEnrichmentService enrichmentService = mock(OAuthProfileEnrichmentService.class);
        private final PortalUserServiceImpl service = new PortalUserServiceImpl(userRepository, oauthRepository,
                tokenService, enrichmentService);
        private final AtomicReference<PortalUser> savedUser = new AtomicReference<>();

        private void seed(PortalUser user, List<PortalUserOauth> authorizations) {
            when(oauthRepository.findAllAuthorizations()).thenReturn(Flux.fromIterable(authorizations));
            when(oauthRepository.findAllByUserId(eq(user.getId()))).thenReturn(Flux.fromIterable(authorizations));
            when(oauthRepository.updateProfileColumns(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Mono.just(0));
            when(userRepository.findAll()).thenReturn(Flux.just(user));
            when(userRepository.findById(eq(user.getId()))).thenReturn(Mono.just(user));
            when(userRepository.findByUsername(any())).thenReturn(Mono.empty());
            when(userRepository.save(any(PortalUser.class))).thenAnswer(invocation -> {
                PortalUser saved = invocation.getArgument(0);
                savedUser.set(saved);
                return Mono.just(saved);
            });
        }

        private PortalUser user(Long id, String username) {
            PortalUser user = new PortalUser();
            user.setId(id);
            user.setUsername(username);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            return user;
        }

        private PortalUserOauth wechatOauth(Long userId, String openid) {
            String generatedName = FunNicknameGenerator.generate(openid);
            PortalUserOauth oauth = new PortalUserOauth();
            oauth.setUserId(userId);
            oauth.setType(OAuthType.WECHAT);
            oauth.setOpenid(openid);
            oauth.setDisplayName(generatedName);
            oauth.setUserInfo("{\"openid\":\"" + openid + "\",\"generated_display_name\":\"" + generatedName + "\"}");
            return oauth;
        }

        private PortalUserOauth githubOauth(Long userId, String login) {
            PortalUserOauth oauth = new PortalUserOauth();
            oauth.setUserId(userId);
            oauth.setType(OAuthType.GITHUB);
            oauth.setOpenid("github-" + login);
            oauth.setLoginName(login);
            oauth.setDisplayName("Mona " + login);
            oauth.setUserInfo("{\"login\":\"" + login + "\",\"display_name\":\"Mona " + login + "\"}");
            return oauth;
        }

        private void prepareNewEmailLogin(String email, Long userId) {
            when(oauthRepository.findAllByTypeAndOpenid(eq(OAuthType.EMAIL), eq(email))).thenReturn(Flux.empty());
            when(userRepository.findAllByEmailIgnoreCase(eq(email))).thenReturn(Flux.empty());
            when(userRepository.findByUsername(any())).thenReturn(Mono.empty());
            when(userRepository.save(any(PortalUser.class))).thenAnswer(invocation -> {
                PortalUser user = invocation.getArgument(0);
                if (user.getId() == null) {
                    user.setId(userId);
                }
                savedUser.set(user);
                return Mono.just(user);
            });
            when(oauthRepository.deleteByTypeAndOpenidAndUserIdNot(any(), any(), any())).thenReturn(Mono.just(0));
            when(oauthRepository.deleteByUserIdAndType(any(), any())).thenReturn(Mono.just(0));
            when(oauthRepository.save(any(PortalUserOauth.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(tokenService.createToken(eq(userId)))
                    .thenReturn(Mono.just(new group.flyfish.dev.user.domain.UserToken("email-token",
                            Date.from(Instant.now().plusSeconds(600)))));
        }
    }
}
