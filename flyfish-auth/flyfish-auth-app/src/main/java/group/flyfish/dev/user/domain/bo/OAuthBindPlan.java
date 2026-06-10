package group.flyfish.dev.user.domain.bo;

import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.domain.vo.OAuthBindConfirmationVo;

/**
 * 第三方账号绑定预检结果。
 *
 * <p>无冲突时直接携带新 token；存在换绑风险时携带确认页展示信息和待确认绑定，调用方负责保存待确认绑定
 * 并等待用户二次确认。</p>
 */
public record OAuthBindPlan(UserToken token, PendingOAuthBinding pending,
                            OAuthBindConfirmationVo confirmation) {

    public static OAuthBindPlan bound(UserToken token) {
        return new OAuthBindPlan(token, null, null);
    }

    public static OAuthBindPlan waitConfirm(PendingOAuthBinding pending, OAuthBindConfirmationVo confirmation) {
        return new OAuthBindPlan(null, pending, confirmation);
    }

    public boolean requiresConfirmation() {
        return pending != null && confirmation != null;
    }
}
