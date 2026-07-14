package group.flyfish.dev.oauth.vender.github;

import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import group.flyfish.dev.oauth.config.OAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

class GithubApiTest {

    @Test
    void shouldParseGithubDefaultFormEncodedTokenResponse() {
        assertSame(OAuth2AccessTokenExtractor.instance(), GithubApi.instance().getAccessTokenExtractor());
    }

    @Test
    void usesConfiguredBackChannelEndpoints() {
        OAuthProperties properties = new OAuthProperties();
        properties.getGithub().setTokenUrl("https://relay.example/github/token");
        properties.getGithub().setApiBaseUrl("https://relay.example/github");

        assertThat(GithubApi.instance().getAccessTokenEndpoint())
                .isEqualTo("https://relay.example/github/token");
        assertThat(new GithubProfileDefinition().getProfileUrl(null, null))
                .isEqualTo("https://relay.example/github/user");
    }

    @Test
    void keepsOfficialBackChannelEndpointsByDefault() {
        new OAuthProperties();

        assertThat(GithubApi.instance().getAccessTokenEndpoint())
                .isEqualTo("https://github.com/login/oauth/access_token");
        assertThat(new GithubProfileDefinition().getProfileUrl(null, null))
                .isEqualTo("https://api.github.com/user");
    }
}
