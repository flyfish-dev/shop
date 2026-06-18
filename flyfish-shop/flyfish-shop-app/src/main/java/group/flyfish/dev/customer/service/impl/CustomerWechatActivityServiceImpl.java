package group.flyfish.dev.customer.service.impl;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.FunNicknameGenerator;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.customer.domain.po.CustomerWechatActivity;
import group.flyfish.dev.customer.domain.vo.CustomerWechatActivityVo;
import group.flyfish.dev.customer.repository.CustomerWechatActivityRepository;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.customer.service.CustomerWechatActivityService;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerWechatActivityServiceImpl implements CustomerWechatActivityService {

    private static final int CONTENT_MAX_LENGTH = 4096;
    private static final int RAW_PAYLOAD_MAX_LENGTH = 8192;
    private static final int TITLE_MAX_LENGTH = 128;
    private static final int DEFAULT_LIMIT = 80;
    private static final String AUDIT_SYSTEM = "wechat-activity";

    private final CustomerWechatActivityRepository activityRepository;
    private final AuthUserGateway authUserGateway;
    private final CustomerRealtimeNotifier realtimeNotifier;

    @Override
    public Mono<CustomerWechatActivity> recordInbound(WechatInboundMessage message, String rawXml) {
        if (message == null || StringUtils.isBlank(message.getFromUserName())) {
            return Mono.empty();
        }
        if (StringUtils.isNotBlank(message.getMsgId())) {
            return activityRepository.findByWechatMsgId(message.getMsgId())
                    .switchIfEmpty(Mono.defer(() -> saveNewActivity(message, rawXml)));
        }
        return saveNewActivity(message, rawXml);
    }

    @Override
    public Flux<CustomerWechatActivityVo> getManagementActivities(PortalUserVo user, String keyword,
                                                                  String activityType, int limit) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        Criteria criteria = Criteria.where("id").greaterThan(0);
        String normalizedType = normalizeActivityType(activityType);
        if (StringUtils.isNotBlank(normalizedType)) {
            criteria = criteria.and("activity_type").is(normalizedType);
        }
        String normalizedKeyword = StringUtils.trimToEmpty(keyword);
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 200);
        return activityRepository.findLatest(criteria, size)
                .filter(activity -> matchesKeyword(activity, normalizedKeyword))
                .map(CustomerWechatActivityVo::new);
    }

    private Mono<CustomerWechatActivity> saveNewActivity(WechatInboundMessage message, String rawXml) {
        String openid = message.getFromUserName().trim();
        return findWechatAuthorization(openid)
                .map(optional -> buildActivity(message, rawXml, optional))
                .flatMap(activity -> {
                    applyAudit(activity);
                    return activityRepository.save(activity);
                })
                .doOnNext(ignored -> realtimeNotifier.wechatActivitiesChanged());
    }

    private CustomerWechatActivity buildActivity(WechatInboundMessage message, String rawXml,
                                                 Optional<PortalUserOauthVo> oauth) {
        CustomerWechatActivity activity = new CustomerWechatActivity();
        activity.setWechatOpenid(message.getFromUserName());
        activity.setActivityType(resolveActivityType(message).name());
        activity.setMessageType(normalize(message.getMsgType(), 24));
        activity.setEventType(normalize(message.getEvent(), 32));
        activity.setEventKey(normalize(message.getEventKey(), 128));
        activity.setTitle(clamp(resolveTitle(message), TITLE_MAX_LENGTH));
        activity.setContent(clamp(resolveContent(message), CONTENT_MAX_LENGTH));
        activity.setWechatMsgId(normalize(message.getMsgId(), 128));
        activity.setRawPayload(clamp(rawXml, RAW_PAYLOAD_MAX_LENGTH));
        applyIdentity(activity, oauth);
        return activity;
    }

    private CustomerWechatActivity.ActivityType resolveActivityType(WechatInboundMessage message) {
        if (message.eventIs(WechatInboundMessage.EVENT_SUBSCRIBE)) {
            return CustomerWechatActivity.ActivityType.SUBSCRIBE;
        }
        if (message.eventIs(WechatInboundMessage.EVENT_UNSUBSCRIBE)) {
            return CustomerWechatActivity.ActivityType.UNSUBSCRIBE;
        }
        if (message.eventIs(WechatInboundMessage.EVENT_SCAN)) {
            return CustomerWechatActivity.ActivityType.SCAN;
        }
        if (message.isText()) {
            return CustomerWechatActivity.ActivityType.TEXT;
        }
        if (message.isLocationMessage()) {
            return CustomerWechatActivity.ActivityType.LOCATION;
        }
        if (StringUtils.isNotBlank(message.getPicUrl()) || StringUtils.equalsIgnoreCase(message.getMsgType(), "image")) {
            return CustomerWechatActivity.ActivityType.IMAGE;
        }
        if (message.isEvent()) {
            return CustomerWechatActivity.ActivityType.EVENT;
        }
        return CustomerWechatActivity.ActivityType.MESSAGE;
    }

    private String resolveTitle(WechatInboundMessage message) {
        CustomerWechatActivity.ActivityType type = resolveActivityType(message);
        return switch (type) {
            case SUBSCRIBE -> "关注公众号";
            case UNSUBSCRIBE -> "取消关注公众号";
            case SCAN -> "扫码进入场景";
            case TEXT -> "发送文字消息";
            case IMAGE -> "发送图片消息";
            case LOCATION -> "发送地理位置";
            case EVENT -> "触发公众号事件";
            case MESSAGE -> "发送公众号消息";
        };
    }

    private String resolveContent(WechatInboundMessage message) {
        if (message.isText()) {
            return StringUtils.defaultIfBlank(message.getContent(), "[空消息]");
        }
        if (message.isLocationMessage()) {
            return "纬度 " + StringUtils.defaultString(message.getLatitude())
                    + "，经度 " + StringUtils.defaultString(message.getLongitude());
        }
        if (StringUtils.isNotBlank(message.getPicUrl())) {
            return message.getPicUrl();
        }
        if (StringUtils.isNotBlank(message.getMediaId())) {
            return "媒体素材：" + message.getMediaId();
        }
        if (message.isEvent()) {
            String event = StringUtils.defaultIfBlank(message.getEvent(), "event");
            String key = StringUtils.defaultIfBlank(message.getEventKey(), "");
            return StringUtils.isBlank(key) ? event : event + " · " + key;
        }
        return "[" + StringUtils.defaultIfBlank(message.getMsgType(), "unknown") + "] 用户发送了一条公众号消息";
    }

    private void applyIdentity(CustomerWechatActivity activity, Optional<PortalUserOauthVo> optional) {
        if (optional.isEmpty()) {
            activity.setDisplayName(FunNicknameGenerator.generate(activity.getWechatOpenid()));
            return;
        }
        PortalUserOauthVo oauthVo = optional.get();
        activity.setUserId(oauthVo.getUserId());
        activity.setWechatUnionId(oauthVo.getUnionId());
        activity.setDisplayName(StringUtils.firstNonBlank(
                usableName(oauthVo.getDisplayName()), usableName(oauthVo.getNickname()), oauthVo.getLogin(),
                FunNicknameGenerator.generate(oauthVo.getOpenid())));
        activity.setAvatar(oauthVo.getAvatar());
    }

    private Mono<Optional<PortalUserOauthVo>> findWechatAuthorization(String openid) {
        return authUserGateway.authorizationByOpenid(OAuthType.WECHAT, openid)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private boolean matchesKeyword(CustomerWechatActivity activity, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(activity.getDisplayName(), keyword)
                || StringUtils.containsIgnoreCase(activity.getWechatOpenid(), keyword)
                || StringUtils.containsIgnoreCase(activity.getTitle(), keyword)
                || StringUtils.containsIgnoreCase(activity.getContent(), keyword)
                || StringUtils.containsIgnoreCase(activity.getEventKey(), keyword);
    }

    private String normalizeActivityType(String value) {
        String text = StringUtils.upperCase(StringUtils.trimToEmpty(value));
        if (StringUtils.isBlank(text) || "ALL".equals(text)) {
            return null;
        }
        for (CustomerWechatActivity.ActivityType type : CustomerWechatActivity.ActivityType.values()) {
            if (type.name().equals(text)) {
                return text;
            }
        }
        return null;
    }

    private String usableName(String value) {
        String text = StringUtils.trimToNull(value);
        return FunNicknameGenerator.isGenericWechatName(text) ? null : text;
    }

    private void applyAudit(AuditDomain domain) {
        if (StringUtils.isBlank(domain.getCreateBy())) {
            domain.setCreateBy(AUDIT_SYSTEM);
        }
        if (StringUtils.isBlank(domain.getUpdateBy())) {
            domain.setUpdateBy(AUDIT_SYSTEM);
        }
        if (domain.getDelete() == null) {
            domain.setDelete(false);
        }
    }

    private String normalize(String value, int maxLength) {
        return StringUtils.trimToNull(clamp(value, maxLength));
    }

    private String clamp(String value, int maxLength) {
        String text = StringUtils.defaultString(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
