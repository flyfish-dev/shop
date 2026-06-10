package group.flyfish.dev.shop.support;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.auth.api.user.UserAuthorizationUtils;

public final class ShopAuthorizationUtils {

    private ShopAuthorizationUtils() {
    }

    public static void requireLogin(PortalUserVo user) {
        UserAuthorizationUtils.requireLogin(user);
    }

    public static void requireShopMaintainer(PortalUserVo user) {
        UserAuthorizationUtils.requireMaintainer(user);
    }

    public static boolean isShopMaintainer(PortalUserVo user) {
        return UserAuthorizationUtils.isMaintainer(user);
    }
}
