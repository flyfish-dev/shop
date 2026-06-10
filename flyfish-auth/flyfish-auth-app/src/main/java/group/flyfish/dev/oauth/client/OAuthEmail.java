package group.flyfish.dev.oauth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Email record returned by code hosting OAuth providers.
 *
 * <p>GitHub and Gitee expose private email addresses through dedicated user email APIs.
 * Provider payloads are not perfectly identical, so this record keeps the common fields
 * and ignores provider-specific extras.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OAuthEmail(
        String email,
        Boolean primary,
        Boolean verified,
        Boolean confirmed,
        String visibility
) {

    public boolean usable() {
        return email != null && !email.isBlank();
    }

    public boolean verifiedOrConfirmed() {
        return Boolean.TRUE.equals(verified) || Boolean.TRUE.equals(confirmed);
    }
}
