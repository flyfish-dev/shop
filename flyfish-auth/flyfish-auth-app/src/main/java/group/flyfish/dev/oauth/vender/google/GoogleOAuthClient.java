package group.flyfish.dev.oauth.vender.google;

import org.pac4j.core.logout.GoogleLogoutActionBuilder;
import org.pac4j.oauth.client.OAuth20Client;

/** Google OAuth client whose back-channel endpoints can use a trusted relay. */
public class GoogleOAuthClient extends OAuth20Client {

    private static final String DEFAULT_SCOPE = "profile email";

    public GoogleOAuthClient(String key, String secret) {
        setKey(key);
        setSecret(secret);
        configuration.setScope(DEFAULT_SCOPE);
    }

    @Override
    protected void internalInit(boolean forceReinit) {
        configuration.setApi(GoogleOAuthApi.instance());
        configuration.setProfileDefinition(new GoogleOAuthProfileDefinition());
        configuration.setWithState(true);
        configuration.setHasBeenCancelledFactory(context -> "access_denied".equals(
                context.getRequestParameter("error").orElse(null)));
        setLogoutActionBuilderIfUndefined(new GoogleLogoutActionBuilder());
        super.internalInit(forceReinit);
    }
}
