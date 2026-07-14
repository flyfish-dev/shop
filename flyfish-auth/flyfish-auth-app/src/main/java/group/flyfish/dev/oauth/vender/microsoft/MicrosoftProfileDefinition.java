package group.flyfish.dev.oauth.vender.microsoft;

import com.github.scribejava.core.model.Token;
import group.flyfish.dev.oauth.config.OAuthProperties;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.converter.Converters;
import org.pac4j.oauth.config.OAuthConfiguration;
import org.pac4j.oauth.profile.JsonHelper;
import org.pac4j.oauth.profile.definition.OAuthProfileDefinition;

import java.util.List;
import java.util.Optional;

import static org.pac4j.core.profile.AttributeLocation.PROFILE_ATTRIBUTE;

/** Maps the Microsoft Graph /me response into a pac4j profile. */
public class MicrosoftProfileDefinition extends OAuthProfileDefinition {

    public static final String ID = "id";
    public static final String DISPLAY_NAME = "displayName";
    public static final String GIVEN_NAME = "givenName";
    public static final String SURNAME = "surname";
    public static final String MAIL = "mail";
    public static final String USER_PRINCIPAL_NAME = "userPrincipalName";

    public MicrosoftProfileDefinition() {
        super(x -> new MicrosoftProfile());
        List.of(ID, DISPLAY_NAME, GIVEN_NAME, SURNAME, MAIL, USER_PRINCIPAL_NAME)
                .forEach(attribute -> primary(attribute, Converters.STRING));
    }

    @Override
    public String getProfileUrl(Token accessToken, OAuthConfiguration configuration) {
        return graphBaseUrl()
                + "/me?$select=id,displayName,givenName,surname,mail,userPrincipalName";
    }

    @Override
    public MicrosoftProfile extractUserProfile(String body) {
        val profile = (MicrosoftProfile) newProfile();
        val json = JsonHelper.getFirstNode(body);
        if (json == null) {
            raiseProfileExtractionJsonError(body);
            return profile;
        }
        profile.setId(ProfileHelper.sanitizeIdentifier(JsonHelper.getElement(json, ID)));
        for (val attribute : getPrimaryAttributes()) {
            convertAndAdd(profile, PROFILE_ATTRIBUTE, attribute, JsonHelper.getElement(json, attribute));
        }
        return profile;
    }

    private String graphBaseUrl() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getMicrosoft)
                .map(OAuthProperties.Microsoft::getGraphBaseUrl)
                .filter(StringUtils::isNotBlank)
                .orElse("https://graph.microsoft.com/v1.0");
    }
}
