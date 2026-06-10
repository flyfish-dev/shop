package group.flyfish.dev.oauth.vender.gitea;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * 内部控制的gitea api 声明
 *
 * @author wangyu
 */
public class GiteaApi extends DefaultApi20 {

    protected GiteaApi() {
    }

    public static GiteaApi instance() {
        return GiteaApi.InstanceHolder.INSTANCE;
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
    protected String getAuthorizationBaseUrl() {
        return getServer() + "/login/oauth/authorize";
    }

    @Override
    public ClientAuthentication getClientAuthentication() {
        return RequestBodyAuthenticationScheme.instance();
    }

    private String getServer() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(properties -> properties.getGitea().getServer())
                .filter(StringUtils::isNotBlank)
                .orElse("https://git.example.com");
    }

    private static class InstanceHolder {
        private static final GiteaApi INSTANCE = new GiteaApi();
    }
}
