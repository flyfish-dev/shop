package group.flyfish.dev.user.email;

import group.flyfish.dev.user.domain.UserToken;

public record EmailMagicLinkLoginResult(UserToken token, String redirect) {
}
