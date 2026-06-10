package group.flyfish.dev.user.domain.dto;

import lombok.Data;

/**
 * Portal user profile update request.
 */
@Data
public class PortalUserUpdateDto {

    private String username;

    private String avatar;

    private String phone;

    private String email;

    private String bio;
}
