package group.flyfish.dev.user.config;

import group.flyfish.dev.common.config.RuntimeHintsSupport;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(AuthRuntimeHintsConfiguration.AuthRuntimeHints.class)
public class AuthRuntimeHintsConfiguration {

    public static class AuthRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.oauth.client.GiteeUserInfoClient");
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.oauth.client.GithubUserInfoClient");
            RuntimeHintsSupport.registerConfigurationProperties(hints, classLoader,
                    "group.flyfish.dev.user.config.JwtProperties",
                    "group.flyfish.dev.oauth.config.OAuthProperties",
                    "group.flyfish.dev.oauth.config.OAuthProperties$Gitea",
                    "group.flyfish.dev.oauth.config.OAuthProperties$Gitee",
                    "group.flyfish.dev.oauth.config.OAuthProperties$Github",
                    "group.flyfish.dev.oauth.config.Pac4jProperties",
                    "group.flyfish.dev.oauth.config.Pac4jProperties$Callback",
                    "group.flyfish.dev.user.email.EmailMagicLinkProperties",
                    "group.flyfish.dev.wechat.config.WechatQuickLoginRedirectProperties");
            RuntimeHintsSupport.registerReflectiveTypes(hints, classLoader,
                    "group.flyfish.dev.user.domain.dto.PortalUserUpdateDto",
                    "group.flyfish.dev.user.email.EmailMagicLinkLoginResult",
                    "group.flyfish.dev.user.email.EmailMagicLinkPayload",
                    "group.flyfish.dev.user.email.EmailMagicLinkRequest",
                    "group.flyfish.dev.user.email.EmailMagicLinkSentVo",
                    "group.flyfish.dev.wechat.bean.WechatLoginDto",
                    "group.flyfish.dev.wechat.bean.WechatLoginResultVo",
                    "group.flyfish.dev.wechat.bean.WechatLoginSession",
                    "group.flyfish.dev.wechat.bean.WechatLoginStatus");
        }
    }
}
