package group.flyfish.dev.oauth.vender.microsoft;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/** Microsoft identity platform OAuth 2.0 endpoints. */
public class MicrosoftApi extends DefaultApi20 {

    protected MicrosoftApi() {
    }

    public static MicrosoftApi instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return endpoint("token");
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return endpoint("authorize");
    }

    @Override
    public ClientAuthentication getClientAuthentication() {
        return RequestBodyAuthenticationScheme.instance();
    }

    private String endpoint(String action) {
        return "https://login.microsoftonline.com/" + tenant() + "/oauth2/v2.0/" + action;
    }

    private String tenant() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getMicrosoft)
                .map(OAuthProperties.Microsoft::getTenant)
                .filter(StringUtils::isNotBlank)
                .orElse("common");
    }

    private static class InstanceHolder {
        private static final MicrosoftApi INSTANCE = new MicrosoftApi();
    }
}
