package group.flyfish.dev.user.email;

import java.time.Instant;

public record EmailMagicLinkPayload(
        String email,
        String nonce,
        Long bindUserId,
        String redirect,
        Instant expiresAt
) {
}
