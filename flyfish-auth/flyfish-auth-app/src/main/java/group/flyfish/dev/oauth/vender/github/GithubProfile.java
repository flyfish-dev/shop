package group.flyfish.dev.oauth.vender.github;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.oauth.profile.OAuth20Profile;

import java.net.URI;

/**
 * GitHub user profile.
 */
public class GithubProfile extends OAuth20Profile {

    @Override
    public String getDisplayName() {
        return StringUtils.defaultIfBlank((String) getAttribute(GithubProfileDefinition.NAME), getUsername());
    }

    @Override
    public String getUsername() {
        return (String) getAttribute(GithubProfileDefinition.LOGIN);
    }

    @Override
    public URI getPictureUrl() {
        String value = (String) getAttribute(GithubProfileDefinition.AVATAR_URL);
        return StringUtils.isBlank(value) ? null : URI.create(value);
    }

    @Override
    public URI getProfileUrl() {
        String value = (String) getAttribute(GithubProfileDefinition.HTML_URL);
        return StringUtils.isBlank(value) ? null : URI.create(value);
    }

    @Override
    public String getEmail() {
        return (String) getAttribute(GithubProfileDefinition.EMAIL);
    }
}
