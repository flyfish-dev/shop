package group.flyfish.dev.user.service;

import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUser;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.domain.bo.OAuthBindPlan;
import group.flyfish.dev.user.domain.bo.PendingOAuthBinding;
import group.flyfish.dev.user.domain.dto.PortalUserUpdateDto;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.wechat.bean.WechatLoginSession;
import org.pac4j.core.profile.UserProfile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PortalUserService {

    /**
     * 使用微信注册
     *
     * @param session 用户会话
     * @return 结果
     */
    Mono<UserToken> registerOrLogin(WechatLoginSession session);

    /**
     * 使用已验证邮箱注册或登录。
     *
     * @param email 邮箱
     * @return 结果
     */
    Mono<UserToken> registerOrLoginEmail(String email);

    /**
     * 通过pac4j的用户注册
     *
     * @param userProfile 用户信息
     * @return 结果
     */
    Mono<UserToken> registerOrLogin(UserProfile userProfile);

    /**
     * 将第三方授权绑定到指定用户；用户不存在时返回错误。
     *
     * @param userProfile 第三方用户信息
     * @param userId      当前用户id
     * @return 结果
     */
    Mono<UserToken> bindAuthorization(UserProfile userProfile, Long userId);

    Mono<UserToken> bindAuthorization(WechatLoginSession session, Long userId);

    Mono<UserToken> bindEmailAuthorization(String email, Long userId);

    /**
     * 预检第三方账号绑定。
     *
     * <p>当平台账号已经属于其他门户用户，或当前门户用户已有同渠道绑定时，返回待确认结果，由页面引导用户二次确认；
     * 无冲突时直接完成绑定。</p>
     */
    Mono<OAuthBindPlan> prepareBindAuthorization(UserProfile userProfile, Long userId, String ticket, String redirect);

    /**
     * 执行用户已经二次确认的换绑。
     */
    Mono<UserToken> confirmBindAuthorization(PendingOAuthBinding binding);

    Mono<PortalUserVo> updateProfile(Long userId, PortalUserUpdateDto dto);

    Mono<Void> unbindAuthorization(Long userId, OAuthType type);

    Mono<Long> backfillAuthorizationMetadata();

    /**
     * 通过用户信息获取token
     *
     * @param user 用户
     * @return 结果
     */
    Mono<UserToken> getToken(PortalUser user);

    /**
     * 通过id查询
     *
     * @param id 主键
     * @return 结果
     */
    Mono<PortalUserVo> getById(Long id);

    /**
     * 管理后台查询门户用户。
     *
     * @param keyword 用户名、手机号或邮箱关键字；为空时返回全部用户
     * @return 用户资料和已绑定第三方账号
     */
    Flux<PortalUserVo> listUsers(String keyword);
}
