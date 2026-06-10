package group.flyfish.dev.oauth.vender.github;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.oauth.client.OAuth20Client;

/**
 * GitHub OAuth2 client.
 */
public class GithubClient extends OAuth20Client {

    public static final String DEFAULT_SCOPE = "read:user user:email";

    public GithubClient() {
        setScope(DEFAULT_SCOPE);
    }

    public GithubClient(String key, String secret, String scope) {
        setKey(key);
        setSecret(secret);
        setScope(StringUtils.defaultIfBlank(scope, DEFAULT_SCOPE));
    }

    @Override
    protected void internalInit(boolean forceReinit) {
        this.configuration.setApi(GithubApi.instance());
        this.configuration.setProfileDefinition(new GithubProfileDefinition());
        this.configuration.setTokenAsHeader(true);
        super.internalInit(forceReinit);
    }

    public String getScope() {
        return this.getConfiguration().getScope();
    }

    public void setScope(String scope) {
        this.getConfiguration().setScope(scope);
    }
}
