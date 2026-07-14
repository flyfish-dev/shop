package group.flyfish.dev.oauth.vender.microsoft;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.http.callback.PathParameterCallbackUrlResolver;
import org.pac4j.oauth.client.OAuth20Client;

/** Microsoft identity platform client backed by Microsoft Graph. */
public class MicrosoftClient extends OAuth20Client {

    public static final String DEFAULT_SCOPE = "openid profile email User.Read";

    public MicrosoftClient() {
        configureCallbackUrlResolver();
        setScope(DEFAULT_SCOPE);
    }

    public MicrosoftClient(String key, String secret, String scope) {
        configureCallbackUrlResolver();
        setKey(key);
        setSecret(secret);
        setScope(StringUtils.defaultIfBlank(scope, DEFAULT_SCOPE));
    }

    @Override
    protected void internalInit(boolean forceReinit) {
        configuration.setApi(MicrosoftApi.instance());
        configuration.setProfileDefinition(new MicrosoftProfileDefinition());
        configuration.setTokenAsHeader(true);
        configuration.setWithState(true);
        super.internalInit(forceReinit);
    }

    public void setScope(String scope) {
        configuration.setScope(scope);
    }

    private void configureCallbackUrlResolver() {
        // Microsoft apps supporting personal accounts reject redirect URI query parameters.
        setCallbackUrlResolver(new PathParameterCallbackUrlResolver());
    }
}
