package group.flyfish.dev.auth.api.client;

/**
 * 登录 token 在 HTTP 请求中的标准名称。
 */
public final class AuthTokenNames {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String AUTHORIZATION_COOKIE = "FF_ACCESS_TOKEN";

    private AuthTokenNames() {
    }
}
