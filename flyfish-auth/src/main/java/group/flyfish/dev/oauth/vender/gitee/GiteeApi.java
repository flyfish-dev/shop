package group.flyfish.dev.oauth.vender.gitee;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Gitee OAuth2 API definition.
 */
public class GiteeApi extends DefaultApi20 {

    protected GiteeApi() {
    }

    public static GiteeApi instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public Verb getAccessTokenVerb() {
        return Verb.POST;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return getServer() + "/oauth/token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return getServer() + "/oauth/authorize";
    }

    @Override
    public ClientAuthentication getClientAuthentication() {
        return RequestBodyAuthenticationScheme.instance();
    }

    private String getServer() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getGitee)
                .map(OAuthProperties.Gitee::getServer)
                .filter(StringUtils::isNotBlank)
                .orElse("https://gitee.com");
    }

    private static class InstanceHolder {
        private static final GiteeApi INSTANCE = new GiteeApi();
    }
}
