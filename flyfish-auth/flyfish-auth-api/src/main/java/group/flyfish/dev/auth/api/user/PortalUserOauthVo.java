package group.flyfish.dev.auth.api.user;

import group.flyfish.dev.common.json.JacksonUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门户用户第三方授权资料。
 */
@Data
@NoArgsConstructor
public class PortalUserOauthVo {

    private Long userId;

    private OAuthType type;

    private String openid;

    private Map<String, Object> userInfo;

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

    /**
     * 返回仅包含展示与识别字段的授权资料。
     *
     * <p>{@code userInfo} 保存的是第三方平台原始快照，可能包含 access_token、refresh_token、
     * raw_profile 等大字段和敏感字段。列表、客服、工单、订单等跨服务场景只需要 openid、昵称、
     * 头像、邮箱和主页地址，不能把原始快照继续向外传递。</p>
     */
    public PortalUserOauthVo withoutUserInfo() {
        PortalUserOauthVo vo = new PortalUserOauthVo();
        vo.userId = userId;
        vo.type = type;
        vo.openid = openid;
        vo.authTime = authTime;
        vo.typeCode = typeCode;
        vo.typeName = typeName;
        vo.login = login;
        vo.displayName = displayName;
        vo.nickname = nickname;
        vo.avatar = avatar;
        vo.email = email;
        vo.profileUrl = profileUrl;
        vo.unionId = unionId;
        return vo;
    }

    public static PortalUserOauthVo of(Long userId, OAuthType type, String openid, String userInfo,
                                       LocalDateTime authTime, String loginName, String displayName,
                                       String nickname, String avatarUrl, String email,
                                       String profileUrl, String unionId) {
        PortalUserOauthVo vo = new PortalUserOauthVo();
        vo.userId = userId;
        vo.type = type;
        vo.openid = openid;
        vo.userInfo = parseUserInfo(userInfo);
        vo.authTime = authTime;
        vo.typeCode = type == null ? null : type.getCode();
        vo.typeName = type == null ? null : type.getName();
        vo.login = firstNotBlankValue(loginName, firstNotBlank(vo.userInfo,
                "login", "username", "login_name", "name", "nickname"));
        vo.nickname = firstNotBlankValue(nickname, firstNotBlank(vo.userInfo,
                "nickname", "name", "full_name", "display_name"));
        vo.displayName = firstNotBlankValue(displayName, vo.nickname, firstNotBlank(vo.userInfo,
                "display_name", "full_name", "name", "nickname", "login", "username"));
        if (type == OAuthType.WECHAT && FunNicknameGenerator.isGenericWechatName(vo.displayName)) {
            vo.displayName = FunNicknameGenerator.generate(openid);
        }
        vo.avatar = firstNotBlankValue(avatarUrl, firstNotBlank(vo.userInfo,
                "avatar_url", "picture_url", "headimgurl"));
        vo.email = firstNotBlankValue(email, firstNotBlank(vo.userInfo, "primary_email", "email"));
        vo.profileUrl = firstNotBlankValue(profileUrl, firstNotBlank(vo.userInfo, "profile_url", "html_url", "url"));
        vo.unionId = firstNotBlankValue(unionId, firstNotBlank(vo.userInfo, "union_id", "unionid"));
        return vo;
    }

    private static Map<String, Object> parseUserInfo(String value) {
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

    private static String firstNotBlank(Map<String, Object> userInfo, String... keys) {
        for (String key : keys) {
            String value = valueOf(userInfo.get(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String firstNotBlankValue(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
