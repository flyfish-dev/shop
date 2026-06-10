package group.flyfish.dev.oauth.vender.github;

import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class GithubApiTest {

    @Test
    void shouldParseGithubDefaultFormEncodedTokenResponse() {
        assertSame(OAuth2AccessTokenExtractor.instance(), GithubApi.instance().getAccessTokenExtractor());
    }
}
