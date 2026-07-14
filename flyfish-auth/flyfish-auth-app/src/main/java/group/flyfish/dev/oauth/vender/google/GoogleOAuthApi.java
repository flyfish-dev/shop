package group.flyfish.dev.oauth.vender.google;

import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/** Google OAuth endpoints with an optional server-side relay for blocked networks. */
public class GoogleOAuthApi extends DefaultApi20 {

    private static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";

    protected GoogleOAuthApi() {
    }

    public static GoogleOAuthApi instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getGoogle)
                .map(OAuthProperties.Google::getTokenUrl)
                .filter(StringUtils::isNotBlank)
                .orElse(DEFAULT_TOKEN_URL);
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return "https://accounts.google.com/o/oauth2/v2/auth";
    }

    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
        return OpenIdJsonTokenExtractor.instance();
    }

    private static class InstanceHolder {
        private static final GoogleOAuthApi INSTANCE = new GoogleOAuthApi();
    }
}
