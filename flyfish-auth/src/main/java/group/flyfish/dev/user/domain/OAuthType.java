package group.flyfish.dev.user.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 开放认证类型
 *
 * @author wangyu
 */
@Getter
@AllArgsConstructor
public enum OAuthType {

    EMAIL("email", "邮箱"),
    WECHAT("wechat", "微信"),
    GITEA("gitea", "飞鱼开源"),
    GITEE("gitee", "码云"),
    GITHUB("github", "GitHub");

    private final String code;

    private final String name;

    public static OAuthType from(String value) {
        for (OAuthType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.code.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported OAuth type: " + value);
    }
}
