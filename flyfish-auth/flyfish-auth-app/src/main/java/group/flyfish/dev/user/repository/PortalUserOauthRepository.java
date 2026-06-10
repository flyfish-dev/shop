package group.flyfish.dev.user.repository;

import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.auth.api.user.OAuthType;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 门户用户认证仓库
 *
 * @author wangyu
 */
public interface PortalUserOauthRepository extends ReactiveCrudRepository<PortalUserOauth, Long> {

    @Query("SELECT * FROM user_portal_oauth")
    Flux<PortalUserOauth> findAllAuthorizations();

    /**
     * 通过用户id查询所有授权
     *
     * @param userId 用户id
     * @return 结果
     */
    Flux<PortalUserOauth> findAllByUserId(Long userId);

    Flux<PortalUserOauth> findAllByUserIdAndType(Long userId, OAuthType type);

    Flux<PortalUserOauth> findAllByTypeAndOpenid(OAuthType type, String openid);

    Mono<Long> countByUserId(Long userId);

    @Query("DELETE FROM user_portal_oauth WHERE user_id = :userId AND type = :type")
    Mono<Integer> deleteByUserIdAndType(Long userId, OAuthType type);

    @Query("DELETE FROM user_portal_oauth WHERE type = :type AND openid = :openid AND user_id <> :userId")
    Mono<Integer> deleteByTypeAndOpenidAndUserIdNot(OAuthType type, String openid, Long userId);

    @Query("""
            UPDATE user_portal_oauth
            SET login_name = :loginName,
                display_name = :displayName,
                nickname = :nickname,
                avatar_url = :avatarUrl,
                email = :email,
                profile_url = :profileUrl,
                union_id = :unionId
            WHERE user_id = :userId
              AND type = :type
              AND openid = :openid
            """)
    Mono<Integer> updateProfileColumns(Long userId, OAuthType type, String openid,
                                       String loginName, String displayName, String nickname,
                                       String avatarUrl, String email, String profileUrl, String unionId);

    @Query("""
            UPDATE user_portal_oauth
            SET user_info = :userInfo,
                login_name = :loginName,
                display_name = :displayName,
                nickname = :nickname,
                avatar_url = :avatarUrl,
                email = :email,
                profile_url = :profileUrl,
                union_id = :unionId,
                auth_time = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND type = :type
              AND openid = :openid
            """)
    Mono<Integer> refreshAuthorizationSnapshot(Long userId, OAuthType type, String openid, String userInfo,
                                               String loginName, String displayName, String nickname,
                                               String avatarUrl, String email, String profileUrl, String unionId);
}
