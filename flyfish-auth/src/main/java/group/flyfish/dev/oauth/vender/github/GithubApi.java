package group.flyfish.dev.oauth.vender.github;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * GitHub OAuth2 API definition.
 */
public class GithubApi extends DefaultApi20 {

    protected GithubApi() {
    }

    public static GithubApi instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public Verb getAccessTokenVerb() {
        return Verb.POST;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return getServer() + "/login/oauth/access_token";
    }

    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
        return OAuth2AccessTokenExtractor.instance();
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return getServer() + "/login/oauth/authorize";
    }

    @Override
    public ClientAuthentication getClientAuthentication() {
        return RequestBodyAuthenticationScheme.instance();
    }

    private String getServer() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getGithub)
                .map(OAuthProperties.Github::getServer)
                .filter(StringUtils::isNotBlank)
                .orElse("https://github.com");
    }

    private static class InstanceHolder {
        private static final GithubApi INSTANCE = new GithubApi();
    }
}
