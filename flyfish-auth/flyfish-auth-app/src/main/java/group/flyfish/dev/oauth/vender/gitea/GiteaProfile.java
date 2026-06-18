package group.flyfish.dev.oauth.vender.gitea;

import org.pac4j.oauth.profile.OAuth20Profile;

import java.net.URI;
import java.util.Date;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * gitea用户信息
 *
 * @author wangyu
 */
public class GiteaProfile extends OAuth20Profile {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return defaultIfBlank((String) getAttribute(GiteaProfileDefinition.FULL_NAME), getUsername());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUsername() {
        return (String) getAttribute(GiteaProfileDefinition.LOGIN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI getPictureUrl() {
        return (URI) getAttribute(GiteaProfileDefinition.AVATAR_URL);
    }

    /**
     * <p>getCreatedAt.</p>
     *
     * @return a {@link Date} object
     */
    public Date getCreatedAt() {
        return (Date) getAttribute(GiteaProfileDefinition.CREATED_AT);
    }

    @Override
    public String getEmail() {
        return (String) getAttribute(GiteaProfileDefinition.EMAIL);
    }

    public boolean isAdmin() {
        return (boolean) getAttribute(GiteaProfileDefinition.IS_ADMIN);
    }

    public boolean isActive() {
        Object active = getAttribute(GiteaProfileDefinition.ACTIVE);
        return active instanceof Boolean value && value;
    }

    public String getDescription() {
        return (String) getAttribute(GiteaProfileDefinition.DESCRIPTION);
    }
}
