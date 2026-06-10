package group.flyfish.dev.oauth.vender.github;

import com.github.scribejava.core.model.Token;
import group.flyfish.dev.oauth.config.OAuthProperties;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.converter.Converters;
import org.pac4j.oauth.config.OAuthConfiguration;
import org.pac4j.oauth.profile.JsonHelper;
import org.pac4j.oauth.profile.definition.OAuthProfileDefinition;

import java.util.Arrays;
import java.util.Optional;

import static org.pac4j.core.profile.AttributeLocation.PROFILE_ATTRIBUTE;

/**
 * GitHub user profile definition.
 */
public class GithubProfileDefinition extends OAuthProfileDefinition {

    public static final String ID = "id";
    public static final String LOGIN = "login";
    public static final String NAME = "name";
    public static final String AVATAR_URL = "avatar_url";
    public static final String HTML_URL = "html_url";
    public static final String NODE_ID = "node_id";
    public static final String TYPE = "type";
    public static final String SITE_ADMIN = "site_admin";
    public static final String COMPANY = "company";
    public static final String BLOG = "blog";
    public static final String LOCATION = "location";
    public static final String BIO = "bio";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String RAW_PROFILE = "raw_profile";

    public GithubProfileDefinition() {
        super(x -> new GithubProfile());
        Arrays.asList(ID, LOGIN, NAME, EMAIL, AVATAR_URL, HTML_URL, NODE_ID, TYPE,
                        COMPANY, BLOG, LOCATION, BIO, CREATED_AT, UPDATED_AT)
                .forEach(a -> primary(a, Converters.STRING));
        primary(SITE_ADMIN, Converters.BOOLEAN);
    }

    @Override
    public String getProfileUrl(Token accessToken, OAuthConfiguration configuration) {
        return getApiBaseUrl() + "/user";
    }

    @Override
    public GithubProfile extractUserProfile(String body) {
        val profile = (GithubProfile) newProfile();
        val json = JsonHelper.getFirstNode(body);
        if (json == null) {
            raiseProfileExtractionJsonError(body);
            return profile;
        }
        profile.setId(ProfileHelper.sanitizeIdentifier(JsonHelper.getElement(json, ID)));
        for (val attribute : getPrimaryAttributes()) {
            convertAndAdd(profile, PROFILE_ATTRIBUTE, attribute, JsonHelper.getElement(json, attribute));
        }
        profile.addAttribute(RAW_PROFILE, body);
        return profile;
    }

    private String getApiBaseUrl() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(OAuthProperties::getGithub)
                .map(OAuthProperties.Github::getApiBaseUrl)
                .filter(StringUtils::isNotBlank)
                .orElse("https://api.github.com");
    }
}
