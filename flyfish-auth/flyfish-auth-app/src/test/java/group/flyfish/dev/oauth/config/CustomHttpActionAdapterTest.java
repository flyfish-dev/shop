package group.flyfish.dev.oauth.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomHttpActionAdapterTest {

    @Test
    void recognizesGoogleAuthorizationEndpoint() {
        assertTrue(CustomHttpActionAdapter.isAuthorizationRedirect(
                "https://accounts.google.com/o/oauth2/v2/auth?response_type=code"));
        assertTrue(CustomHttpActionAdapter.isAuthorizationRedirect(
                "https://github.com/login/oauth/authorize?client_id=test"));
        assertFalse(CustomHttpActionAdapter.isAuthorizationRedirect(
                "https://dev.flyfish.group/shop/item-list"));
    }
}
