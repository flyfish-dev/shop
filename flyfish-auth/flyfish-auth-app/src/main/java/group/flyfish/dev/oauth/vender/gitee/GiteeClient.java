package group.flyfish.dev.oauth.vender.gitee;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.oauth.client.OAuth20Client;

/**
 * Gitee OAuth2 client.
 */
public class GiteeClient extends OAuth20Client {

    public static final String DEFAULT_SCOPE = "user_info emails";

    public GiteeClient() {
        setScope(DEFAULT_SCOPE);
    }

    public GiteeClient(String key, String secret, String scope) {
        setKey(key);
        setSecret(secret);
        setScope(StringUtils.defaultIfBlank(scope, DEFAULT_SCOPE));
    }

    @Override
    protected void internalInit(boolean forceReinit) {
        this.configuration.setApi(GiteeApi.instance());
        this.configuration.setProfileDefinition(new GiteeProfileDefinition());
        this.configuration.setTokenAsHeader(false);
        super.internalInit(forceReinit);
    }

    public String getScope() {
        return this.getConfiguration().getScope();
    }

    public void setScope(String scope) {
        this.getConfiguration().setScope(scope);
    }
}
