package group.flyfish.dev.oauth.vender.google;

import com.github.scribejava.core.model.Token;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.oauth.config.OAuthConfiguration;
import org.pac4j.oauth.profile.google2.Google2ProfileDefinition;

import java.util.Optional;

/** Google profile endpoint with an optional server-side relay. */
public class GoogleOAuthProfileDefinition extends Google2ProfileDefinition {

    private static final String DEFAULT_PROFILE_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    @Override
    public String getProfileUrl(Token accessToken, OAuthConfiguration configuration) {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getGoogle)
                .map(OAuthProperties.Google::getProfileUrl)
                .filter(StringUtils::isNotBlank)
                .orElse(DEFAULT_PROFILE_URL);
    }
}
