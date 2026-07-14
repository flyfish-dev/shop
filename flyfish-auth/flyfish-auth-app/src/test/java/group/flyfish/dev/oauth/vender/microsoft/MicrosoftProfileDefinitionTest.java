package group.flyfish.dev.oauth.vender.microsoft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrosoftProfileDefinitionTest {

    @Test
    void extractsGraphIdentityAndEmail() {
        String response = """
                {
                  "id": "microsoft-user-id",
                  "displayName": "Ada Lovelace",
                  "givenName": "Ada",
                  "surname": "Lovelace",
                  "mail": "ada@example.com",
                  "userPrincipalName": "ada@example.com"
                }
                """;

        MicrosoftProfile profile = new MicrosoftProfileDefinition().extractUserProfile(response);

        assertThat(profile.getId()).isEqualTo("microsoft-user-id");
        assertThat(profile.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(profile.getUsername()).isEqualTo("ada@example.com");
        assertThat(profile.getEmail()).isEqualTo("ada@example.com");
    }
}
