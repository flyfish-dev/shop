package group.flyfish.dev.user.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.oauth.service.OAuthProfileEnrichmentService;
import group.flyfish.dev.oauth.vender.gitea.GiteaProfile;
import group.flyfish.dev.oauth.vender.gitee.GiteeProfile;
import group.flyfish.dev.oauth.vender.github.GithubProfile;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.user.domain.UserToken;
import group.flyfish.dev.user.domain.bo.OAuthBindPlan;
import group.flyfish.dev.user.domain.bo.PendingOAuthBinding;
import group.flyfish.dev.user.domain.dto.PortalUserUpdateDto;
import group.flyfish.dev.user.domain.po.PortalUser;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.domain.vo.OAuthBindConfirmationVo;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.user.repository.PortalUserOauthRepository;
import group.flyfish.dev.user.repository.PortalUserRepository;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.TokenService;
import group.flyfish.dev.auth.api.user.FunNicknameGenerator;
import group.flyfish.dev.wechat.bean.WechatLoginSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.UserProfile;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.core.type.TypeReference;

@RequiredArgsConstructor
@Slf4j
public class PortalUserServiceImpl implements PortalUserService {

    private final PortalUserRepository portalUserRepository;
    private final PortalUserOauthRepository portalUserOauthRepository;
    private final TokenService tokenService;
    private final OAuthProfileEnrichmentService oAuthProfileEnrichmentService;

    /**
     * 使用微信注册
     *
     * @param session 用户会话
     * @return 结果
     */
    @Override
    public Mono<UserToken> registerOrLogin(WechatLoginSession session) {
        String snapshot = buildWechatSnapshot(session);
        AuthorizationMetadata metadata = resolveAuthorizationMetadata(OAuthType.WECHAT, session.getOpenid(), snapshot);
        return findUserByAuthorization(OAuthType.WECHAT, session.getOpenid())
                .flatMap(user -> refreshAuthorization(user.getId(), OAuthType.WECHAT, session.getOpenid(), snapshot)
                        .then(refreshUserDisplayName(user.getId()))
                        .thenReturn(user))
                .switchIfEmpty(Mono.defer(() -> createUserWithAuthorization(OAuthType.WECHAT, session.getOpenid(),
                        metadata.preferredUsername(OAuthType.WECHAT, session.getOpenid()), snapshot)))
                .flatMap(this::getToken);
    }

    @Override
    public Mono<UserToken> registerOrLoginEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return Mono.error(new BusinessException("EMAIL_INVALID", "邮箱格式不正确"));
        }
        String snapshot = buildEmailSnapshot(normalizedEmail);
        return findUserByAuthorization(OAuthType.EMAIL, normalizedEmail)
                .flatMap(user -> refreshAuthorization(user.getId(), OAuthType.EMAIL, normalizedEmail, snapshot)
                        .then(syncVerifiedEmail(user, normalizedEmail))
                        .thenReturn(user))
                .switchIfEmpty(Mono.defer(() -> findUserByVerifiedEmail(normalizedEmail)
                        .flatMap(user -> saveAuthorization(user.getId(), OAuthType.EMAIL, normalizedEmail, snapshot)
                                .then(syncVerifiedEmail(user, normalizedEmail))
                                .thenReturn(user))
                        .switchIfEmpty(Mono.defer(() -> createUserWithAuthorization(OAuthType.EMAIL, normalizedEmail,
                                normalizedEmail, snapshot)))))
                .flatMap(this::getToken);
    }

    /**
     * 通过pac4j的用户注册
     *
     * @param userProfile 用户信息
     * @return 结果
     */
    @Override
    public Mono<UserToken> registerOrLogin(UserProfile userProfile) {
        OAuthType type = determineType(userProfile);
        return buildUserInfoSnapshot(userProfile, type)
                .flatMap(snapshot -> {
                    AuthorizationMetadata metadata = resolveAuthorizationMetadata(type, userProfile.getId(), snapshot);
                    return findUserByAuthorization(type, userProfile.getId())
                            .flatMap(user -> refreshAuthorization(user.getId(), type, userProfile.getId(), snapshot)
                                    .then(refreshUserDisplayName(user.getId()))
                                    .thenReturn(user))
                            .switchIfEmpty(Mono.defer(() -> createUserWithAuthorization(type, userProfile.getId(),
                                    metadata.preferredUsername(type, userProfile.getId()), snapshot)));
                })
                .flatMap(this::getToken);
    }

    @Override
    public Mono<UserToken> bindAuthorization(UserProfile userProfile, Long userId) {
        return prepareBindAuthorization(userProfile, userId, UUID.randomUUID().toString(), "/account/profile")
                .flatMap(plan -> {
                    if (plan.requiresConfirmation()) {
                        return Mono.error(new BusinessException("OAUTH_BIND_CONFIRM_REQUIRED",
                                "该平台账号需要确认后才能换绑"));
                    }
                    return Mono.just(plan.token());
                });
    }

    @Override
    public Mono<UserToken> bindAuthorization(WechatLoginSession session, Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再绑定账号"));
        }
        return prepareBindAuthorization(userId, OAuthType.WECHAT, session.getOpenid(), buildWechatSnapshot(session),
                        UUID.randomUUID().toString(), "/account/profile")
                .flatMap(plan -> {
                    if (plan.requiresConfirmation()) {
                        return Mono.error(new BusinessException("OAUTH_BIND_CONFIRM_REQUIRED",
                                "该微信账号已存在绑定，请在网页端完成换绑确认"));
                    }
                    return Mono.just(plan.token());
                });
    }

    @Override
    public Mono<UserToken> bindEmailAuthorization(String email, Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再绑定邮箱"));
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return Mono.error(new BusinessException("EMAIL_INVALID", "邮箱格式不正确"));
        }
        return prepareBindAuthorization(userId, OAuthType.EMAIL, normalizedEmail, buildEmailSnapshot(normalizedEmail),
                        UUID.randomUUID().toString(), "/account/profile")
                .flatMap(plan -> {
                    if (plan.requiresConfirmation()) {
                        return Mono.error(new BusinessException("EMAIL_BIND_CONFLICT",
                                "该邮箱已存在绑定，请先使用该邮箱登录后再处理"));
                    }
                    return portalUserRepository.findById(userId)
                            .flatMap(user -> syncVerifiedEmail(user, normalizedEmail))
                            .thenReturn(plan.token());
                });
    }

    @Override
    public Mono<OAuthBindPlan> prepareBindAuthorization(UserProfile userProfile, Long userId,
                                                        String ticket, String redirect) {
        if (userId == null || userId <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再绑定账号"));
        }
        OAuthType type = determineType(userProfile);
        return buildUserInfoSnapshot(userProfile, type)
                .flatMap(snapshot -> prepareBindAuthorization(userId, type, userProfile.getId(), snapshot,
                        ticket, redirect));
    }

    @Override
    public Mono<UserToken> confirmBindAuthorization(PendingOAuthBinding binding) {
        if (binding == null || binding.isExpired()) {
            return Mono.error(new BusinessException("OAUTH_BIND_EXPIRED", "换绑确认已过期，请重新发起绑定"));
        }
        return portalUserRepository.findById(binding.userId())
                .switchIfEmpty(Mono.error(new BusinessException("USER_NOT_FOUND", "当前用户不存在")))
                .flatMap(user -> {
                    PortalUserOauth oauth = buildAuthorization(binding.userId(), binding.type(), binding.openid(),
                            binding.userInfo());
                    return moveAuthorizationToUser(binding.userId(), binding.type(), binding.openid(), oauth)
                            .then(refreshUserDisplayName(binding.userId()))
                            .thenReturn(user);
                })
                .flatMap(this::getToken);
    }

    @Override
    public Mono<PortalUserVo> updateProfile(Long userId, PortalUserUpdateDto dto) {
        if (userId == null || userId <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再维护个人信息"));
        }
        String username = StringUtils.trimToEmpty(dto == null ? null : dto.getUsername());
        String avatar = StringUtils.trimToNull(dto == null ? null : dto.getAvatar());
        String phone = StringUtils.trimToNull(dto == null ? null : dto.getPhone());
        String email = StringUtils.trimToNull(dto == null ? null : dto.getEmail());
        String bio = StringUtils.trimToNull(dto == null ? null : dto.getBio());
        if (StringUtils.isBlank(username)) {
            return Mono.error(new BusinessException("USERNAME_REQUIRED", "昵称不能为空"));
        }
        if (username.length() > 64) {
            return Mono.error(new BusinessException("USERNAME_TOO_LONG", "昵称不能超过 64 个字符"));
        }
        if (avatar != null && !isValidAvatarUrl(avatar)) {
            return Mono.error(new BusinessException("AVATAR_INVALID", "头像链接格式不正确"));
        }
        if (StringUtils.isNotBlank(phone) && !PHONE_PATTERN.matcher(phone).matches()) {
            return Mono.error(new BusinessException("PHONE_INVALID", "手机号格式不正确"));
        }
        if (StringUtils.isNotBlank(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            return Mono.error(new BusinessException("EMAIL_INVALID", "邮箱格式不正确"));
        }
        if (StringUtils.length(bio) > 1024) {
            return Mono.error(new BusinessException("BIO_TOO_LONG", "个人简介不能超过 1024 个字符"));
        }
        return portalUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException("USER_NOT_FOUND", "当前用户不存在")))
                .flatMap(user -> ensureUsernameAvailable(username, user.getId())
                        .then(Mono.defer(() -> {
                            user.setUsername(username);
                            user.setAvatar(avatar);
                            user.setPhone(phone);
                            user.setEmail(email);
                            user.setBio(bio);
                            user.setUpdateTime(LocalDateTime.now());
                            return portalUserRepository.save(user);
                        })))
                .flatMap(user -> getById(user.getId()));
    }

    @Override
    public Mono<Void> unbindAuthorization(Long userId, OAuthType type) {
        if (userId == null || userId <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再维护绑定账号"));
        }
        if (type == null) {
            return Mono.error(new BusinessException("OAUTH_TYPE_REQUIRED", "授权类型不能为空"));
        }
        return portalUserOauthRepository.countByUserId(userId)
                .flatMap(count -> {
                    if (count <= 1) {
                        return Mono.error(new BusinessException("LAST_AUTHORIZATION", "至少保留一个登录方式"));
                    }
                    return portalUserOauthRepository.deleteByUserIdAndType(userId, type).then();
                });
    }

    @Override
    public Mono<Long> backfillAuthorizationMetadata() {
        Mono<Long> authorizationCount = portalUserOauthRepository.findAllAuthorizations()
                .flatMap(this::backfillAuthorizationMetadata)
                .reduce(0L, Long::sum)
                .defaultIfEmpty(0L);
        Mono<Long> userCount = portalUserRepository.findAll()
                .flatMap(user -> refreshUserDisplayName(user.getId()))
                .filter(Boolean.TRUE::equals)
                .count();
        return Mono.zip(authorizationCount, userCount)
                .map(tuple -> tuple.getT1() + tuple.getT2());
    }

    /**
     * 通过用户信息获取token
     *
     * @param user 用户
     * @return 结果
     */
    @Override
    public Mono<UserToken> getToken(PortalUser user) {
        return tokenService.createToken(user.getId());
    }

    /**
     * 通过id查询
     *
     * @param id 主键
     * @return 结果
     */
    @Override
    public Mono<PortalUserVo> getById(Long id) {
        return portalUserRepository.findById(id)
                .flatMap(this::toPortalUserVo);
    }

    @Override
    public Flux<PortalUserVo> listUsers(String keyword) {
        String normalizedKeyword = StringUtils.trimToEmpty(keyword).toLowerCase();
        return portalUserRepository.findAll()
                .filter(user -> matchesUserKeyword(user, normalizedKeyword))
                .sort(Comparator.comparing(PortalUser::getCreateTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .flatMap(this::toPortalUserVo, 8);
    }

    @Override
    public Mono<Long> countUsers() {
        return portalUserRepository.countUsers();
    }

    private Mono<PortalUserVo> toPortalUserVo(PortalUser user) {
        return portalUserOauthRepository.findAllByUserId(user.getId())
                .map(this::toPortalUserOauthVo)
                .collectList()
                .defaultIfEmpty(Collections.emptyList())
                .map(list -> {
                    PortalUserVo vo = new PortalUserVo();
                    vo.setId(user.getId());
                    vo.setUsername(user.getUsername());
                    vo.setAvatar(user.getAvatar());
                    vo.setPhone(user.getPhone());
                    vo.setEmail(user.getEmail());
                    vo.setBio(user.getBio());
                    vo.setCreateTime(user.getCreateTime());
                    vo.setUpdateTime(user.getUpdateTime());
                    vo.applyAuthorizations(list);
                    return vo;
                });
    }

    private PortalUserOauthVo toPortalUserOauthVo(PortalUserOauth oauth) {
        return PortalUserOauthVo.of(oauth.getUserId(), oauth.getType(), oauth.getOpenid(), oauth.getUserInfo(),
                oauth.getAuthTime(), oauth.getLoginName(), oauth.getDisplayName(), oauth.getNickname(),
                oauth.getAvatarUrl(), oauth.getEmail(), oauth.getProfileUrl(), oauth.getUnionId());
    }

    private boolean matchesUserKeyword(PortalUser user, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(user.getUsername(), keyword)
                || StringUtils.containsIgnoreCase(user.getPhone(), keyword)
                || StringUtils.containsIgnoreCase(user.getEmail(), keyword);
    }

    private OAuthType determineType(UserProfile profile) {
        if (profile instanceof GiteaProfile) {
            return OAuthType.GITEA;
        }
        if (profile instanceof GiteeProfile) {
            return OAuthType.GITEE;
        }
        if (profile instanceof GithubProfile) {
            return OAuthType.GITHUB;
        }
        return OAuthType.GITEE;
    }

    private Mono<Void> saveAuthorization(Long userId, UserProfile userProfile) {
        OAuthType type = determineType(userProfile);
        return buildUserInfoSnapshot(userProfile, type)
                .flatMap(snapshot -> saveAuthorization(userId, type, userProfile.getId(), snapshot));
    }

    private Mono<Void> saveAuthorization(Long userId, OAuthType type, String openid, String userInfo) {
        PortalUserOauth oauth = buildAuthorization(userId, type, openid, userInfo);
        return moveAuthorizationToUser(userId, type, openid, oauth);
    }

    private Mono<Void> refreshAuthorization(Long userId, OAuthType type, String openid, String userInfo) {
        PortalUserOauth oauth = buildAuthorization(userId, type, openid, userInfo);
        return portalUserOauthRepository.refreshAuthorizationSnapshot(userId, type, openid,
                        oauth.getUserInfo(), oauth.getLoginName(), oauth.getDisplayName(),
                        oauth.getNickname(), oauth.getAvatarUrl(), oauth.getEmail(),
                        oauth.getProfileUrl(), oauth.getUnionId())
                .then();
    }

    private PortalUserOauth buildAuthorization(Long userId, OAuthType type, String openid, String userInfo) {
        AuthorizationMetadata metadata = resolveAuthorizationMetadata(type, openid, userInfo);
        PortalUserOauth oauth = new PortalUserOauth();
        oauth.setType(type);
        oauth.setUserId(userId);
        oauth.setAuthTime(LocalDateTime.now());
        oauth.setUserInfo(StringUtils.defaultIfBlank(userInfo, "{}"));
        oauth.setOpenid(openid);
        oauth.setLoginName(metadata.loginName());
        oauth.setDisplayName(metadata.displayName());
        oauth.setNickname(metadata.nickname());
        oauth.setAvatarUrl(metadata.avatarUrl());
        oauth.setEmail(metadata.email());
        oauth.setProfileUrl(metadata.profileUrl());
        oauth.setUnionId(metadata.unionId());
        return oauth;
    }

    private Mono<OAuthBindPlan> prepareBindAuthorization(Long userId, OAuthType type, String openid,
                                                         String userInfo, String ticket, String redirect) {
        return portalUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException("USER_NOT_FOUND", "当前用户不存在")))
                .flatMap(user -> {
                    PortalUserOauth oauth = buildAuthorization(userId, type, openid, userInfo);
                    Mono<List<PortalUserOauth>> ownerConflicts = portalUserOauthRepository
                            .findAllByTypeAndOpenid(type, openid)
                            .filter(existing -> existing.getUserId() != null && !userId.equals(existing.getUserId()))
                            .collectList();
                    Mono<List<PortalUserOauth>> currentProviderBindings = portalUserOauthRepository
                            .findAllByUserIdAndType(userId, type)
                            .filter(existing -> !StringUtils.equals(existing.getOpenid(), openid))
                            .collectList();
                    return Mono.zip(ownerConflicts, currentProviderBindings)
                            .flatMap(tuple -> {
                                List<PortalUserOauth> ownerConflictList = tuple.getT1();
                                List<PortalUserOauth> currentBindingList = tuple.getT2();
                                if (ownerConflictList.isEmpty() && currentBindingList.isEmpty()) {
                                    return moveAuthorizationToUser(userId, type, openid, oauth)
                                            .then(refreshUserDisplayName(userId))
                                            .then(getToken(user))
                                            .map(OAuthBindPlan::bound);
                                }
                                PendingOAuthBinding pending = new PendingOAuthBinding(userId, type, openid,
                                        StringUtils.defaultIfBlank(userInfo, "{}"), redirect,
                                        Instant.now().plus(Duration.ofMinutes(10)));
                                OAuthBindConfirmationVo confirmation = buildBindConfirmation(
                                        StringUtils.defaultIfBlank(ticket, UUID.randomUUID().toString()),
                                        type, oauth, currentBindingList, !ownerConflictList.isEmpty(), redirect);
                                return Mono.just(OAuthBindPlan.waitConfirm(pending, confirmation));
                            });
                });
    }

    private Mono<Long> backfillAuthorizationMetadata(PortalUserOauth oauth) {
        AuthorizationMetadata metadata = resolveAuthorizationMetadata(oauth.getType(), oauth.getOpenid(), oauth.getUserInfo());
        String loginName = firstNotBlankString(metadata.loginName(), oauth.getLoginName());
        String displayName = firstNotBlankString(metadata.displayName(), oauth.getDisplayName());
        String nickname = firstNotBlankString(metadata.nickname(), oauth.getNickname());
        String avatarUrl = firstNotBlankString(metadata.avatarUrl(), oauth.getAvatarUrl());
        String email = firstNotBlankString(metadata.email(), oauth.getEmail());
        String profileUrl = firstNotBlankString(metadata.profileUrl(), oauth.getProfileUrl());
        String unionId = firstNotBlankString(metadata.unionId(), oauth.getUnionId());
        Mono<Boolean> authorizationChanged;
        if (Objects.equals(loginName, oauth.getLoginName())
                && Objects.equals(displayName, oauth.getDisplayName())
                && Objects.equals(nickname, oauth.getNickname())
                && Objects.equals(avatarUrl, oauth.getAvatarUrl())
                && Objects.equals(email, oauth.getEmail())
                && Objects.equals(profileUrl, oauth.getProfileUrl())
                && Objects.equals(unionId, oauth.getUnionId())) {
            authorizationChanged = Mono.just(false);
        } else {
            authorizationChanged = portalUserOauthRepository.updateProfileColumns(oauth.getUserId(), oauth.getType(), oauth.getOpenid(),
                            loginName, displayName, nickname, avatarUrl, email, profileUrl, unionId)
                    .map(updated -> updated != null && updated > 0)
                    .defaultIfEmpty(false);
        }
        Mono<Boolean> userChanged = refreshUserDisplayName(oauth.getUserId());
        return Mono.zip(authorizationChanged, userChanged)
                .map(tuple -> tuple.getT1() || tuple.getT2() ? 1L : 0L);
    }

    private Mono<String> buildUserInfoSnapshot(UserProfile profile, OAuthType type) {
        return oAuthProfileEnrichmentService.enrich(profile, type)
                .map(attributes -> buildUserInfoSnapshot(profile, type, attributes));
    }

    private String buildUserInfoSnapshot(UserProfile profile, OAuthType type, Map<String, Object> attributes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        attributes = attributes == null ? Map.of() : attributes;
        snapshot.putAll(attributes);
        snapshot.put("provider", type.getCode());
        snapshot.put("provider_name", type.getName());
        snapshot.put("id", profile.getId());
        snapshot.put("openid", profile.getId());
        snapshot.put("username", StringUtils.defaultIfBlank(profile.getUsername(), valueOf(attributes.get("username"))));
        snapshot.put("login", StringUtils.defaultIfBlank(valueOf(attributes.get("login")), profile.getUsername()));
        snapshot.put("display_name", profileUsername(profile, type, attributes));
        snapshot.put("nickname", firstNotBlankValue(attributes, "nickname", "name", "full_name"));
        snapshot.put("avatar_url", firstNotBlankValue(attributes, "avatar_url", "picture_url", "headimgurl"));
        snapshot.put("profile_url", firstNotBlankValue(attributes, "html_url", "profile_url", "url"));
        snapshot.put("email", firstNotBlankValue(attributes, "primary_email", "email"));
        snapshot.put("captured_at", LocalDateTime.now().toString());
        snapshot.put("attributes", attributes);
        return JacksonUtils.toJson(snapshot);
    }

    private String buildWechatSnapshot(WechatLoginSession session) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, Object> wechatUser = session.getUserInfo() == null ? Map.of() : session.getUserInfo();
        String generatedName = FunNicknameGenerator.generate(session.getOpenid());
        snapshot.putAll(wechatUser);
        snapshot.put("provider", OAuthType.WECHAT.getCode());
        snapshot.put("provider_name", OAuthType.WECHAT.getName());
        snapshot.put("id", session.getOpenid());
        snapshot.put("openid", session.getOpenid());
        snapshot.put("scene", session.getScene());
        snapshot.put("display_name", StringUtils.defaultIfBlank(firstNotBlankValue(wechatUser, "nickname", "remark"),
                generatedName));
        snapshot.put("nickname", firstNotBlankValue(wechatUser, "nickname"));
        snapshot.put("avatar_url", firstNotBlankValue(wechatUser, "headimgurl", "avatar_url"));
        snapshot.put("unionid", firstNotBlankValue(wechatUser, "unionid", "union_id"));
        snapshot.put("generated_display_name", generatedName);
        snapshot.put("generated_display_name_version", "petname-cn-v1");
        snapshot.put("captured_at", LocalDateTime.now().toString());
        return JacksonUtils.toJson(snapshot);
    }

    private String buildEmailSnapshot(String email) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("provider", OAuthType.EMAIL.getCode());
        snapshot.put("provider_name", OAuthType.EMAIL.getName());
        snapshot.put("id", email);
        snapshot.put("openid", email);
        snapshot.put("login", email);
        snapshot.put("username", email);
        snapshot.put("display_name", email);
        snapshot.put("email", email);
        snapshot.put("primary_email", email);
        snapshot.put("email_verified", true);
        snapshot.put("captured_at", LocalDateTime.now().toString());
        return JacksonUtils.toJson(snapshot);
    }

    private String firstNotBlankValue(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            String value = valueOf(attributes.get(key));
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstNotBlankString(String... values) {
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

    private Mono<PortalUser> findUserByAuthorization(OAuthType type, String openid) {
        return portalUserOauthRepository.findAllByTypeAndOpenid(type, openid)
                .map(PortalUserOauth::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .distinct()
                .collectList()
                .flatMap(userIds -> {
                    if (userIds.isEmpty()) {
                        return Mono.empty();
                    }
                    Long keepUserId = userIds.stream().min(Long::compareTo).orElse(userIds.get(0));
                    if (userIds.size() > 1) {
                        log.warn("同一第三方账号存在多个绑定记录，已暂停自动清理并继续使用最早用户：type={}, openid={}, userIds={}",
                                type, openid, userIds);
                    }
                    return portalUserRepository.findById(keepUserId);
                });
    }

    private Mono<PortalUser> findUserByVerifiedEmail(String email) {
        return portalUserRepository.findAllByEmailIgnoreCase(email)
                .collectList()
                .flatMap(users -> {
                    if (users.isEmpty()) {
                        return Mono.empty();
                    }
                    if (users.size() > 1) {
                        log.warn("同一邮箱存在多个门户用户，继续使用最早用户：email={}, userIds={}",
                                email, users.stream().map(PortalUser::getId).toList());
                    }
                    return Mono.just(users.get(0));
                });
    }

    private Mono<Void> moveAuthorizationToUser(Long userId, OAuthType type, String openid, PortalUserOauth oauth) {
        return portalUserOauthRepository.deleteByTypeAndOpenidAndUserIdNot(type, openid, userId)
                .then(portalUserOauthRepository.deleteByUserIdAndType(userId, type))
                .then(portalUserOauthRepository.save(oauth))
                .then();
    }

    private OAuthBindConfirmationVo buildBindConfirmation(String ticket, OAuthType type, PortalUserOauth oauth,
                                                          List<PortalUserOauth> currentBindings,
                                                          boolean externalOwnerConflict, String redirect) {
        PortalUserOauthVo account = toPortalUserOauthVo(oauth);
        PortalUserOauthVo current = currentBindings == null || currentBindings.isEmpty()
                ? null
                : toPortalUserOauthVo(currentBindings.get(0));
        return OAuthBindConfirmationVo.builder()
                .ticket(ticket)
                .type(type.getCode())
                .typeName(type.getName())
                .accountName(firstNotBlankString(account.getDisplayName(), account.getNickname(),
                        account.getLogin(), account.getOpenid(), type.getName() + "账号"))
                .accountLogin(account.getLogin())
                .accountAvatar(account.getAvatar())
                .currentAccountName(current == null ? null : firstNotBlankString(current.getDisplayName(),
                        current.getNickname(), current.getLogin(), current.getOpenid()))
                .currentAccountLogin(current == null ? null : current.getLogin())
                .externalOwnerConflict(externalOwnerConflict)
                .currentProviderBinding(current != null)
                .redirect(StringUtils.defaultIfBlank(redirect, "/account/profile"))
                .build();
    }

    private Mono<PortalUser> createUserWithAuthorization(OAuthType type, String openid, String rawUsername,
                                                         String userInfo) {
        String fallback = type.getName() + "-" + StringUtils.defaultIfBlank(openid, "user");
        AuthorizationMetadata metadata = resolveAuthorizationMetadata(type, openid, userInfo);
        String baseUsername = StringUtils.defaultIfBlank(rawUsername, metadata.preferredUsername(type, openid));
        baseUsername = StringUtils.defaultIfBlank(baseUsername, fallback);
        return nextAvailableUsername(baseUsername, 0)
                .flatMap(username -> {
                    PortalUser user = new PortalUser();
                    user.setAvatar(metadata.avatarUrl());
                    user.setEmail(metadata.email());
                    user.setPassword(StringUtils.defaultIfBlank(openid, username));
                    user.setUsername(username);
                    user.setCreateTime(LocalDateTime.now());
                    user.setUpdateTime(LocalDateTime.now());
                    return portalUserRepository.save(user);
                })
                .flatMap(user -> saveAuthorization(user.getId(), type, openid, userInfo).thenReturn(user));
    }

    private Mono<String> nextAvailableUsername(String baseUsername, int attempt) {
        return nextAvailableUsername(baseUsername, null, attempt);
    }

    private Mono<String> nextAvailableUsername(String baseUsername, Long currentUserId, int attempt) {
        String normalized = StringUtils.left(StringUtils.trimToEmpty(baseUsername), 48);
        if (StringUtils.isBlank(normalized)) {
            normalized = "flyfish-user";
        }
        String base = normalized;
        String candidate = attempt == 0 ? normalized : normalized + "-" + attempt;
        return portalUserRepository.findByUsername(candidate)
                .flatMap(existing -> currentUserId != null && currentUserId.equals(existing.getId())
                        ? Mono.just(candidate)
                        : nextAvailableUsername(base, currentUserId, attempt + 1))
                .switchIfEmpty(Mono.just(candidate));
    }

    private Mono<Boolean> refreshUserDisplayName(Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.just(false);
        }
        return Mono.zip(portalUserRepository.findById(userId), portalUserOauthRepository.findAllByUserId(userId).collectList())
                .flatMap(tuple -> {
                    PortalUser user = tuple.getT1();
                    List<PortalUserOauth> authorizations = tuple.getT2();
                    String displayName = preferredAccountDisplayName(authorizations);
                    if (StringUtils.isBlank(displayName) || !shouldReplaceUserDisplayName(user.getUsername(), authorizations)) {
                        return Mono.just(false);
                    }
                    return nextAvailableUsername(displayName, user.getId(), 0)
                            .flatMap(username -> {
                                if (StringUtils.equals(username, user.getUsername())) {
                                    return Mono.just(false);
                                }
                                user.setUsername(username);
                                user.setUpdateTime(LocalDateTime.now());
                                return portalUserRepository.save(user).thenReturn(true);
                            });
                })
                .defaultIfEmpty(false);
    }

    private boolean shouldReplaceWechatUsername(String username, String openid) {
        String text = StringUtils.trimToEmpty(username);
        return FunNicknameGenerator.isGenericWechatName(text)
                || StringUtils.equals(text, openid)
                || StringUtils.startsWith(text, OAuthType.WECHAT.getName() + "-");
    }

    private boolean shouldReplaceUserDisplayName(String username, List<PortalUserOauth> authorizations) {
        String text = StringUtils.trimToEmpty(username);
        if (StringUtils.isBlank(text) || FunNicknameGenerator.isGenericWechatName(text)) {
            return true;
        }
        boolean hasCodeHostAccount = authorizations.stream().anyMatch(this::isCodeHostAccount);
        for (PortalUserOauth oauth : authorizations) {
            if (oauth.getType() != OAuthType.WECHAT) {
                continue;
            }
            if (shouldReplaceWechatUsername(text, oauth.getOpenid())
                    || StringUtils.equals(text, FunNicknameGenerator.generate(oauth.getOpenid()))) {
                return true;
            }
            if (hasCodeHostAccount) {
                AuthorizationMetadata metadata = resolveAuthorizationMetadata(oauth.getType(), oauth.getOpenid(), oauth.getUserInfo());
                if (StringUtils.equalsAny(text, metadata.displayName(), metadata.nickname(), metadata.loginName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String preferredAccountDisplayName(List<PortalUserOauth> authorizations) {
        return firstNotBlankString(
                preferredCodeHostDisplayName(authorizations, OAuthType.GITEA),
                preferredCodeHostDisplayName(authorizations, OAuthType.GITHUB),
                preferredCodeHostDisplayName(authorizations, OAuthType.GITEE),
                preferredWechatDisplayName(authorizations)
        );
    }

    private String preferredCodeHostDisplayName(List<PortalUserOauth> authorizations, OAuthType type) {
        return authorizations.stream()
                .filter(oauth -> oauth.getType() == type)
                .map(oauth -> {
                    AuthorizationMetadata metadata = resolveAuthorizationMetadata(oauth.getType(), oauth.getOpenid(), oauth.getUserInfo());
                    return firstNotBlankString(metadata.loginName(), metadata.displayName(), metadata.nickname(), oauth.getOpenid());
                })
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private String preferredWechatDisplayName(List<PortalUserOauth> authorizations) {
        return authorizations.stream()
                .filter(oauth -> oauth.getType() == OAuthType.WECHAT)
                .map(oauth -> {
                    AuthorizationMetadata metadata = resolveAuthorizationMetadata(oauth.getType(), oauth.getOpenid(), oauth.getUserInfo());
                    return firstNotBlankString(metadata.displayName(), metadata.nickname(), metadata.loginName(),
                            FunNicknameGenerator.generate(oauth.getOpenid()));
                })
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private boolean isCodeHostAccount(PortalUserOauth oauth) {
        return oauth.getType() == OAuthType.GITEA || oauth.getType() == OAuthType.GITHUB
                || oauth.getType() == OAuthType.GITEE;
    }

    private Mono<PortalUser> syncVerifiedEmail(PortalUser user, String email) {
        if (user == null || StringUtils.equalsIgnoreCase(user.getEmail(), email)) {
            return Mono.justOrEmpty(user);
        }
        user.setEmail(email);
        user.setUpdateTime(LocalDateTime.now());
        return portalUserRepository.save(user);
    }

    private Mono<Void> ensureUsernameAvailable(String username, Long currentUserId) {
        return portalUserRepository.findByUsername(username)
                .flatMap(existing -> existing.getId().equals(currentUserId)
                        ? Mono.<Void>empty()
                        : Mono.error(new BusinessException("USERNAME_EXISTS", "昵称已被占用")))
                .then();
    }

    private String profileUsername(UserProfile profile, OAuthType type) {
        Map<String, Object> attributes = profile.getAttributes() == null ? Map.of() : profile.getAttributes();
        return profileUsername(profile, type, attributes);
    }

    private String profileUsername(UserProfile profile, OAuthType type, Map<String, Object> attributes) {
        if (profile instanceof CommonProfile commonProfile && StringUtils.isNotBlank(commonProfile.getDisplayName())) {
            return commonProfile.getDisplayName();
        }
        if (StringUtils.isNotBlank(profile.getUsername())) {
            return profile.getUsername();
        }
        String name = firstNotBlankValue(attributes, "name", "full_name", "nickname", "display_name", "login", "username");
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        Object login = profile.getAttribute("login");
        return login == null ? type.getName() + "用户" : String.valueOf(login);
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9][0-9\\-\\s]{6,19}$");

    private boolean isValidAvatarUrl(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String lower = StringUtils.lowerCase(value);
        return StringUtils.startsWithAny(lower, "http://", "https://");
    }

    private Map<String, Object> parseUserInfoSnapshot(String userInfo) {
        try {
            if (StringUtils.isBlank(userInfo)) {
                return Collections.emptyMap();
            }
            return JacksonUtils.readValue(userInfo, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private AuthorizationMetadata resolveAuthorizationMetadata(OAuthType type, String openid, String userInfo) {
        Map<String, Object> snapshot = parseUserInfoSnapshot(userInfo);
        String loginName = trim(firstNotBlankValue(snapshot, "login", "username", "login_name"));
        String nickname = trim(firstNotBlankValue(snapshot, "nickname", "name", "full_name", "display_name"));
        String displayName = trim(firstNotBlankValue(snapshot, "display_name", "full_name", "name", "nickname",
                "login", "username"));
        String avatarUrl = trim(firstNotBlankValue(snapshot, "avatar_url", "picture_url", "headimgurl"));
        String email = trim(firstNotBlankValue(snapshot, "primary_email", "email"));
        String profileUrl = trim(firstNotBlankValue(snapshot, "profile_url", "html_url", "url", "blog", "website"));
        String unionId = trim(firstNotBlankValue(snapshot, "union_id", "unionid"));
        if (type == OAuthType.WECHAT) {
            String generatedName = FunNicknameGenerator.generate(firstNotBlankString(openid,
                    trim(firstNotBlankValue(snapshot, "openid", "id", "union_id", "unionid"))));
            if (StringUtils.isBlank(displayName) || FunNicknameGenerator.isGenericWechatName(displayName)) {
                displayName = firstNotBlankString(trim(firstNotBlankValue(snapshot, "generated_display_name")),
                        generatedName);
            }
            if (FunNicknameGenerator.isGenericWechatName(nickname)) {
                nickname = null;
            }
        }
        return new AuthorizationMetadata(loginName, displayName, nickname, avatarUrl, email, profileUrl, unionId);
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    private String normalizeEmail(String value) {
        String email = StringUtils.trimToNull(value);
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return null;
        }
        return email.toLowerCase();
    }

    private record AuthorizationMetadata(String loginName, String displayName, String nickname, String avatarUrl,
                                         String email, String profileUrl, String unionId) {

        private String preferredUsername(OAuthType type, String openid) {
            return StringUtils.defaultIfBlank(displayName,
                    StringUtils.defaultIfBlank(nickname,
                            StringUtils.defaultIfBlank(loginName,
                                    type.getName() + "-" + StringUtils.defaultIfBlank(openid, "user"))));
        }
    }
}
