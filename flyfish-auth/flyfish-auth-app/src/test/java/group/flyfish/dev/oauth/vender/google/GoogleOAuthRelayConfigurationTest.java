package group.flyfish.dev.oauth.vender.google;

import group.flyfish.dev.oauth.config.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.pac4j.core.logout.GoogleLogoutActionBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthRelayConfigurationTest {

    @Test
    void keepsGoogleClientBehaviorWhenUsingRelayEndpoints() {
        new OAuthProperties();
        GoogleOAuthClient client = new GoogleOAuthClient("client-id", "client-secret");
        client.setCallbackUrl("https://example.com/oauth/callback");

        client.init();

        assertThat(client.getConfiguration().getApi()).isInstanceOf(GoogleOAuthApi.class);
        assertThat(client.getConfiguration().getProfileDefinition())
                .isInstanceOf(GoogleOAuthProfileDefinition.class);
        assertThat(client.getConfiguration().getScope()).isEqualTo("profile email");
        assertThat(client.getConfiguration().isWithState()).isTrue();
        assertThat(client.getLogoutActionBuilder()).isInstanceOf(GoogleLogoutActionBuilder.class);
    }

    @Test
    void usesConfiguredBackChannelEndpoints() {
        OAuthProperties properties = new OAuthProperties();
        properties.getGoogle().setTokenUrl("https://relay.example/token");
        properties.getGoogle().setProfileUrl("https://relay.example/userinfo");

        assertThat(GoogleOAuthApi.instance().getAccessTokenEndpoint())
                .isEqualTo("https://relay.example/token");
        assertThat(new GoogleOAuthProfileDefinition().getProfileUrl(null, null))
                .isEqualTo("https://relay.example/userinfo");
    }

    @Test
    void keepsOfficialEndpointsByDefault() {
        new OAuthProperties();

        assertThat(GoogleOAuthApi.instance().getAccessTokenEndpoint())
                .isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(new GoogleOAuthProfileDefinition().getProfileUrl(null, null))
                .isEqualTo("https://www.googleapis.com/oauth2/v3/userinfo");
    }
}
