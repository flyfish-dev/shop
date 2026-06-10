package group.flyfish.dev.user.domain.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.auth.api.user.OAuthType;
import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 门户用户授权
 *
 * @author wangyu
 */
@Data
@Table("user_portal_oauth")
public class PortalUserOauth {

    private Long userId;

    private OAuthType type;

    private String openid;

    private String userInfo;

    private String loginName;

    private String displayName;

    private String nickname;

    private String avatarUrl;

    private String email;

    private String profileUrl;

    private String unionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime authTime;
}
