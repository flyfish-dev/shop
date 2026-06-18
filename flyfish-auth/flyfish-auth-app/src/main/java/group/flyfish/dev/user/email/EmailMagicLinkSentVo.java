package group.flyfish.dev.user.email;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailMagicLinkSentVo {

    private String email;

    private String maskedEmail;

    private long expiresInSeconds;

    private long resendCooldownSeconds;
}
