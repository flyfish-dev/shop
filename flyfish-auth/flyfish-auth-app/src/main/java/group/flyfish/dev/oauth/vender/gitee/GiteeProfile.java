package group.flyfish.dev.oauth.vender.gitee;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.oauth.profile.OAuth20Profile;

import java.net.URI;

/**
 * Gitee user profile.
 */
public class GiteeProfile extends OAuth20Profile {

    @Override
    public String getDisplayName() {
        return StringUtils.defaultIfBlank((String) getAttribute(GiteeProfileDefinition.NAME), getUsername());
    }

    @Override
    public String getUsername() {
        return StringUtils.defaultIfBlank((String) getAttribute(GiteeProfileDefinition.LOGIN),
                (String) getAttribute(GiteeProfileDefinition.NAME));
    }

    @Override
    public URI getPictureUrl() {
        String value = (String) getAttribute(GiteeProfileDefinition.AVATAR_URL);
        return StringUtils.isBlank(value) ? null : URI.create(value);
    }

    @Override
    public URI getProfileUrl() {
        String value = (String) getAttribute(GiteeProfileDefinition.HTML_URL);
        return StringUtils.isBlank(value) ? null : URI.create(value);
    }

    @Override
    public String getEmail() {
        return (String) getAttribute(GiteeProfileDefinition.EMAIL);
    }
}
