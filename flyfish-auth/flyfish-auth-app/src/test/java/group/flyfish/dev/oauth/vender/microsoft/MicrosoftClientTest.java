package group.flyfish.dev.oauth.vender.microsoft;

import org.junit.jupiter.api.Test;
import org.pac4j.core.http.callback.PathParameterCallbackUrlResolver;

import static org.assertj.core.api.Assertions.assertThat;

class MicrosoftClientTest {

    @Test
    void usesPathParameterCallbackForPersonalMicrosoftAccounts() {
        MicrosoftClient client = new MicrosoftClient("client-id", "client-secret", null);

        assertThat(client.getCallbackUrlResolver()).isInstanceOf(PathParameterCallbackUrlResolver.class);
        assertThat(client.getCallbackUrlResolver().compute(
                (url, context) -> url,
                "https://dev.flyfish.group/oauth/callback",
                "MicrosoftClient",
                null))
                .isEqualTo("https://dev.flyfish.group/oauth/callback/MicrosoftClient");
    }
}
