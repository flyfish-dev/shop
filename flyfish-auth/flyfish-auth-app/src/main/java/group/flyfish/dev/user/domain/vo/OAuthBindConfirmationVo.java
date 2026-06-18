package group.flyfish.dev.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方账号换绑确认页展示信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthBindConfirmationVo {

    private String ticket;

    private String type;

    private String typeName;

    private String accountName;

    private String accountLogin;

    private String accountAvatar;

    private String currentAccountName;

    private String currentAccountLogin;

    private boolean externalOwnerConflict;

    private boolean currentProviderBinding;

    private String redirect;
}
