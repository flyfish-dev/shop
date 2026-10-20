package group.flyfish.dev.oauth.config;

import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig;
import group.flyfish.dev.oauth.vender.gitea.GiteaClient;
import group.flyfish.dev.oauth.vender.github.GithubClient;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Pac4jConfigTest {

    @Test
    void foreignOAuthClientUsesConfiguredEgressProxy() {
        OAuthProperties properties = propertiesWithProxy();

        GithubClient client = Pac4jConfig.configureHttpClient(
                new GithubClient("client", "secret", "read:user"), properties, true);
        JDKHttpClientConfig http = (JDKHttpClientConfig) client.getConfiguration().getHttpClientConfig();

        assertEquals(Proxy.Type.HTTP, http.getProxy().type());
        assertEquals(InetSocketAddress.createUnresolved("127.0.0.1", 13128), http.getProxy().address());
        assertEquals(4_000, http.getConnectTimeout());
        assertEquals(12_000, http.getReadTimeout());
    }

    @Test
    void domesticOAuthClientKeepsDirectConnection() {
        OAuthProperties properties = propertiesWithProxy();

        GiteaClient client = Pac4jConfig.configureHttpClient(
                new GiteaClient("client", "secret"), properties, false);
        JDKHttpClientConfig http = (JDKHttpClientConfig) client.getConfiguration().getHttpClientConfig();

        assertNull(http.getProxy());
        assertEquals(4_000, http.getConnectTimeout());
        assertEquals(12_000, http.getReadTimeout());
    }

    private OAuthProperties propertiesWithProxy() {
        OAuthProperties properties = new OAuthProperties();
        OAuthProperties.EgressProxy proxy = properties.getEgressProxy();
        proxy.setEnabled(true);
        proxy.setHost("127.0.0.1");
        proxy.setPort(13128);
        proxy.setConnectTimeout(Duration.ofSeconds(4));
        proxy.setReadTimeout(Duration.ofSeconds(12));
        return properties;
    }
}
