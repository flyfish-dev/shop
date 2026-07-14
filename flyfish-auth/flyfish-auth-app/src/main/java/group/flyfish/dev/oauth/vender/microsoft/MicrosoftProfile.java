package group.flyfish.dev.oauth.vender.microsoft;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.oauth.profile.OAuth20Profile;

/** Minimal Microsoft Graph user profile used for authentication. */
public class MicrosoftProfile extends OAuth20Profile {

    @Override
    public String getDisplayName() {
        return StringUtils.defaultIfBlank(
                (String) getAttribute(MicrosoftProfileDefinition.DISPLAY_NAME), getUsername());
    }

    @Override
    public String getUsername() {
        return StringUtils.defaultIfBlank(
                (String) getAttribute(MicrosoftProfileDefinition.USER_PRINCIPAL_NAME), getEmail());
    }

    @Override
    public String getEmail() {
        return StringUtils.defaultIfBlank(
                (String) getAttribute(MicrosoftProfileDefinition.MAIL),
                (String) getAttribute(MicrosoftProfileDefinition.USER_PRINCIPAL_NAME));
    }
}
