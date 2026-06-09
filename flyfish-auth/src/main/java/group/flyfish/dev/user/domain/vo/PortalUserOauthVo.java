package group.flyfish.dev.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.user.domain.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.support.FunNicknameGenerator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;

@Data
public class PortalUserOauthVo {

    private OAuthType type;

    private String openid;

    private Map<String, Object> userInfo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime authTime;

    private String typeCode;

    private String typeName;

    private String login;

    private String displayName;

    private String nickname;

    private String avatar;

    private String email;

    private String profileUrl;

    private String unionId;

    public PortalUserOauthVo(PortalUserOauth oauth) {
        this.type = oauth.getType();
        this.openid = oauth.getOpenid();
        this.userInfo = parseUserInfo(oauth.getUserInfo());
        this.authTime = oauth.getAuthTime();
        this.typeCode = type == null ? null : type.getCode();
        this.typeName = type == null ? null : type.getName();
        this.login = firstNotBlankValue(oauth.getLoginName(), firstNotBlank("login", "username", "login_name", "name", "nickname"));
        this.nickname = firstNotBlankValue(oauth.getNickname(), firstNotBlank("nickname", "name", "full_name", "display_name"));
        this.displayName = firstNotBlankValue(oauth.getDisplayName(), nickname,
                firstNotBlank("display_name", "full_name", "name", "nickname", "login", "username"));
        if (type == OAuthType.WECHAT && FunNicknameGenerator.isGenericWechatName(this.displayName)) {
            this.displayName = FunNicknameGenerator.generate(openid);
        }
        this.avatar = firstNotBlankValue(oauth.getAvatarUrl(), firstNotBlank("avatar_url", "picture_url", "headimgurl"));
        this.email = firstNotBlankValue(oauth.getEmail(), firstNotBlank("primary_email", "email"));
        this.profileUrl = firstNotBlankValue(oauth.getProfileUrl(), firstNotBlank("profile_url", "html_url", "url"));
        this.unionId = firstNotBlankValue(oauth.getUnionId(), firstNotBlank("union_id", "unionid"));
    }

    private Map<String, Object> parseUserInfo(String value) {
        if (StringUtils.isBlank(value)) {
            return new LinkedHashMap<>();
        }
        try {
            return JacksonUtils.readValue(value, new TypeReference<>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", value);
            return fallback;
        }
    }

    private String firstNotBlank(String... keys) {
        for (String key : keys) {
            String value = valueOf(userInfo.get(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstNotBlankValue(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
