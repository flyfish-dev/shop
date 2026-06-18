//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package group.flyfish.dev.oauth.vender.gitea;

import group.flyfish.dev.oauth.config.OAuthProperties;
import org.pac4j.core.util.HttpActionHelper;
import org.pac4j.oauth.client.OAuth20Client;
import org.pac4j.oauth.profile.github.GitHubProfileDefinition;

import java.util.Optional;

public class GiteaClient extends OAuth20Client {
    public static final String DEFAULT_SCOPE = "user";

    public GiteaClient() {
        this.setScope(DEFAULT_SCOPE);
    }

    public GiteaClient(String key, String secret) {
        this.setScope(DEFAULT_SCOPE);
        this.setKey(key);
        this.setSecret(secret);
    }

    protected void internalInit(boolean forceReinit) {
        this.configuration.setApi(GiteaApi.instance());
        this.configuration.setProfileDefinition(new GiteaProfileDefinition());
        this.configuration.setTokenAsHeader(true);
        String giteaServer = OAuthProperties.instance().getGitea().getServer();
        String logout = giteaServer + "/user/logout";
        this.setLogoutActionBuilderIfUndefined((ctx, profile, targetUrl) ->
                Optional.of(HttpActionHelper.buildRedirectUrlAction(ctx.webContext(), logout)));
        super.internalInit(forceReinit);
    }

    public String getScope() {
        return this.getConfiguration().getScope();
    }

    public void setScope(String scope) {
        this.getConfiguration().setScope(scope);
    }

}
