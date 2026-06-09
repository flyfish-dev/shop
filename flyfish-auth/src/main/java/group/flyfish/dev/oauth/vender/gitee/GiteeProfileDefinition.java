package group.flyfish.dev.oauth.vender.gitee;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.Token;
import group.flyfish.dev.oauth.config.OAuthProperties;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.converter.Converters;
import org.pac4j.oauth.config.OAuthConfiguration;
import org.pac4j.oauth.profile.JsonHelper;
import org.pac4j.oauth.profile.definition.OAuthProfileDefinition;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.pac4j.core.profile.AttributeLocation.PROFILE_ATTRIBUTE;

/**
 * Gitee user profile definition.
 */
public class GiteeProfileDefinition extends OAuthProfileDefinition {

    public static final String ID = "id";
    public static final String LOGIN = "login";
    public static final String NAME = "name";
    public static final String AVATAR_URL = "avatar_url";
    public static final String HTML_URL = "html_url";
    public static final String BLOG = "blog";
    public static final String WEIBO = "weibo";
    public static final String BIO = "bio";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    public static final String RAW_PROFILE = "raw_profile";

    public GiteeProfileDefinition() {
        super(x -> new GiteeProfile());
        Arrays.asList(ID, LOGIN, NAME, EMAIL, AVATAR_URL, HTML_URL, BLOG, WEIBO, BIO, CREATED_AT, UPDATED_AT)
                .forEach(a -> primary(a, Converters.STRING));
    }

    @Override
    public String getProfileUrl(Token accessToken, OAuthConfiguration configuration) {
        String url = getApiBaseUrl() + "/user";
        if (accessToken instanceof OAuth2AccessToken token && StringUtils.isNotBlank(token.getAccessToken())) {
            return url + "?access_token=" + URLEncoder.encode(token.getAccessToken(), StandardCharsets.UTF_8);
        }
        return url;
    }

    @Override
    public GiteeProfile extractUserProfile(String body) {
        val profile = (GiteeProfile) newProfile();
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
                .map(OAuthProperties::getGitee)
                .map(OAuthProperties.Gitee::getApiBaseUrl)
                .filter(StringUtils::isNotBlank)
                .orElse("https://gitee.com/api/v5");
    }
}
