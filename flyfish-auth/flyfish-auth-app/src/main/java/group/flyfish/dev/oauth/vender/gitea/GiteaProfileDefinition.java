package group.flyfish.dev.oauth.vender.gitea;

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
 * This class is the GitHub profile definition.
 * {
 * "id": 1,
 * "login": "wybaby168",
 * "login_name": "",
 * "full_name": "",
 * "email": "wybaby168@gmail.com",
 * "avatar_url": "https://gitea.example.com/avatars/71afec53541a0171eaaef5cac218210b",
 * "language": "zh-CN",
 * "is_admin": true,
 * "last_login": "2024-02-22T03:20:34Z",
 * "created": "2022-06-06T06:42:58Z",
 * "restricted": false,
 * "active": true,
 * "prohibit_login": false,
 * "location": "",
 * "website": "",
 * "description": "",
 * "visibility": "public",
 * "followers_count": 0,
 * "following_count": 0,
 * "starred_repos_count": 2,
 * "username": "wybaby168"
 * }
 *
 * @author Jerome Leleu
 * @since 1.1.0
 */
public class GiteaProfileDefinition extends OAuthProfileDefinition {

    public static final String ID = "id";
    public static final String FULL_NAME = "full_name";
    public static final String LOGIN = "login";
    public static final String LOGIN_NAME = "login_name";
    public static final String AVATAR_URL = "avatar_url";
    public static final String HTML_URL = "html_url";
    public static final String IS_ADMIN = "is_admin";
    public static final String CREATED_AT = "created";
    public static final String LAST_LOGIN = "last_login";
    public static final String ACTIVE = "active";
    public static final String RESTRICTED = "restricted";
    public static final String PROHIBIT_LOGIN = "prohibit_login";
    public static final String DESCRIPTION = "description";
    public static final String USERNAME = "username";
    public static final String LANGUAGE = "language";
    public static final String LOCATION = "location";
    public static final String WEBSITE = "website";
    public static final String VISIBILITY = "visibility";
    public static final String FOLLOWERS_COUNT = "followers_count";
    public static final String FOLLOWING_COUNT = "following_count";
    public static final String STARRED_REPOS_COUNT = "starred_repos_count";
    public static final String RAW_PROFILE = "raw_profile";

    /**
     * <p>Constructor for GitHubProfileDefinition.</p>
     */
    public GiteaProfileDefinition() {
        super(x -> new GiteaProfile());
        Arrays.asList(new String[]{
                ID, FULL_NAME, LOGIN, LOGIN_NAME, EMAIL, DESCRIPTION, USERNAME, HTML_URL,
                LANGUAGE, LOCATION, WEBSITE, VISIBILITY, LAST_LOGIN
        }).forEach(a -> primary(a, Converters.STRING));
        Arrays.asList(new String[]{
                IS_ADMIN, ACTIVE, RESTRICTED, PROHIBIT_LOGIN
        }).forEach(a -> primary(a, Converters.BOOLEAN));
        Arrays.asList(new String[]{
                FOLLOWERS_COUNT, FOLLOWING_COUNT, STARRED_REPOS_COUNT
        }).forEach(a -> primary(a, Converters.INTEGER));
        primary(CREATED_AT, Converters.DATE_TZ_RFC822);
        primary(AVATAR_URL, Converters.URL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProfileUrl(final Token accessToken, final OAuthConfiguration configuration) {
        return getServer() + "/api/v1/user";
    }

    private String getServer() {
        return Optional.ofNullable(OAuthProperties.instance())
                .map(properties -> properties.getGitea().getServer())
                .filter(StringUtils::isNotBlank)
                .orElse("https://gitea.example.com");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GiteaProfile extractUserProfile(final String body) {
        val profile = (GiteaProfile) newProfile();
        val json = JsonHelper.getFirstNode(body);
        if (json != null) {
            profile.setId(ProfileHelper.sanitizeIdentifier(JsonHelper.getElement(json, "id")));
            for (val attribute : getPrimaryAttributes()) {
                convertAndAdd(profile, PROFILE_ATTRIBUTE, attribute, JsonHelper.getElement(json, attribute));
            }
            profile.addAttribute(RAW_PROFILE, body);
        } else {
            raiseProfileExtractionJsonError(body);
        }
        return profile;
    }
}
