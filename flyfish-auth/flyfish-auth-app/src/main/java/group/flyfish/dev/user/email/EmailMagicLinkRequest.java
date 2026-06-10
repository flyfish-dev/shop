package group.flyfish.dev.user.email;

import lombok.Data;

@Data
public class EmailMagicLinkRequest {

    private String email;

    private String redirect;

    private String mode;
}
