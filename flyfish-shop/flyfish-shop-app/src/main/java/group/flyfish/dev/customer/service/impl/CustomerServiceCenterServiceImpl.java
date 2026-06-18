package group.flyfish.dev.customer.service.impl;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.FunNicknameGenerator;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.customer.domain.dto.CustomerMessageSendDto;
import group.flyfish.dev.customer.domain.po.CustomerConversation;
import group.flyfish.dev.customer.domain.po.CustomerMessage;
import group.flyfish.dev.customer.domain.vo.CustomerConversationDetailVo;
import group.flyfish.dev.customer.domain.vo.CustomerConversationVo;
import group.flyfish.dev.customer.domain.vo.CustomerMessageVo;
import group.flyfish.dev.customer.domain.vo.CustomerServiceSummaryVo;
import group.flyfish.dev.customer.repository.CustomerConversationRepository;
import group.flyfish.dev.customer.repository.CustomerMessageRepository;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.customer.service.CustomerServiceCenterService;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.support.domain.vo.SupportTicketVo;
import group.flyfish.dev.support.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceCenterServiceImpl implements CustomerServiceCenterService {

    private static final int CONTENT_MAX_LENGTH = 4096;
    private static final int LAST_MESSAGE_MAX_LENGTH = 1024;
    private static final int SUMMARY_CONVERSATION_LIMIT = 8;
    private static final int SUMMARY_TICKET_LIMIT = 5;
    private static final int ATTACHMENT_MAX_COUNT = 6;
    private static final String MESSAGE_TYPE_TEXT = "text";
    private static final String MESSAGE_TYPE_MARKDOWN = "markdown";
    private static final String MESSAGE_TYPE_IMAGE = "image";
    private static final String MESSAGE_TYPE_FILE = "file";
    private static final String AUDIT_SYSTEM = "customer-service";

    private final CustomerConversationRepository conversationRepository;
    private final CustomerMessageRepository messageRepository;
    private final AuthUserGateway authUserGateway;
    private final SupportTicketService supportTicketService;
    private final CustomerRealtimeNotifier realtimeNotifier;

    @Override
    public Mono<CustomerServiceSummaryVo> summary(PortalUserVo user) {
        ShopAuthorizationUtils.requireLogin(user);
        boolean manager = ShopAuthorizationUtils.isShopMaintainer(user);
        Mono<Boolean> wechatBound = currentWechatAuthorization(user).hasElement();
        Flux<CustomerConversationVo> conversationFlux = manager
                ? getManagementConversations(user, null).filter(conversation -> conversation.getUnreadCount() > 0)
                : myConversations(user);
        Mono<List<CustomerConversationVo>> conversations = conversationFlux.collectList();
        Mono<Long> ticketUnreadCount = supportTicketService.countUnreadTickets(user).onErrorReturn(0L);
        Mono<List<SupportTicketVo>> ticketReminders =
                supportTicketService.getUnreadTickets(user, SUMMARY_TICKET_LIMIT)
                        .collectList()
                        .onErrorReturn(List.of());
        return Mono.zip(wechatBound, conversations, ticketUnreadCount, ticketReminders)
                .map(tuple -> {
                    List<CustomerConversationVo> conversationList = tuple.getT2();
                    long customerUnreadCount = unreadTotal(conversationList);
                    return new CustomerServiceSummaryVo(manager, tuple.getT1(), customerUnreadCount, tuple.getT3(),
                            limit(conversationList, SUMMARY_CONVERSATION_LIMIT), tuple.getT4());
                });
    }

    @Override
    public Mono<CustomerConversationDetailVo> getMyConversation(PortalUserVo user) {
        ShopAuthorizationUtils.requireLogin(user);
        return findOrCreateConversation(user)
                .flatMap(conversation -> markUserRead(conversation).then(toDetail(conversation, false)));
    }

    @Override
    public Mono<CustomerConversationDetailVo> peekMyConversation(PortalUserVo user) {
        ShopAuthorizationUtils.requireLogin(user);
        return findOrCreateConversation(user)
                .flatMap(conversation -> toDetail(conversation, false));
    }

    @Override
    @Transactional
    public Mono<CustomerConversationDetailVo> sendMyMessage(PortalUserVo user, CustomerMessageSendDto dto) {
        ShopAuthorizationUtils.requireLogin(user);
        List<FileAttachmentVo> attachments = normalizeAttachments(dto == null ? null : dto.getAttachments());
        String content = requiredContent(dto == null ? null : dto.getContent(), attachments);
        return findOrCreateConversation(user)
                .flatMap(conversation -> saveUserWebMessage(conversation, user, content, dto)
                        .then(toDetail(conversation, false)));
    }

    @Override
    public Flux<CustomerConversationVo> getManagementConversations(PortalUserVo user, String keyword) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        String normalizedKeyword = StringUtils.trimToEmpty(keyword);
        return conversationRepository.findAllForManagement()
                .filter(conversation -> matchesKeyword(conversation, normalizedKeyword))
                .flatMap(conversation -> toConversationVo(conversation, true));
    }

    @Override
    public Mono<CustomerConversationDetailVo> getManagementConversation(PortalUserVo user, Long conversationId) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return findConversation(conversationId)
                .flatMap(conversation -> markAdminRead(conversation).then(toDetail(conversation, true)));
    }

    @Override
    public Mono<CustomerConversationDetailVo> peekManagementConversation(PortalUserVo user, Long conversationId) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return findConversation(conversationId)
                .flatMap(conversation -> toDetail(conversation, true));
    }

    @Override
    public Mono<CustomerConversationDetailVo> getManagementConversationByUser(PortalUserVo user, Long targetUserId) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        if (targetUserId == null || targetUserId <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "客户信息异常"));
        }
        return findOrCreateConversation(targetUserId)
                .flatMap(conversation -> markAdminRead(conversation).then(toDetail(conversation, true)));
    }

    @Override
    @Transactional
    public Mono<CustomerConversationDetailVo> sendManagementMessage(PortalUserVo user, Long conversationId,
                                                                    CustomerMessageSendDto dto) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        List<FileAttachmentVo> attachments = normalizeAttachments(dto == null ? null : dto.getAttachments());
        String content = requiredContent(dto == null ? null : dto.getContent(), attachments);
        return findConversation(conversationId)
                .flatMap(conversation -> saveAdminWebMessage(conversation, user.getId(), content, dto)
                        .then(toDetail(conversation, true)));
    }

    @Override
    public Mono<Void> markManagementRead(PortalUserVo user, Long conversationId) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return findConversation(conversationId)
                .flatMap(this::markAdminRead)
                .then();
    }

    @Override
    public Mono<Void> markUserRead(PortalUserVo user) {
        ShopAuthorizationUtils.requireLogin(user);
        return findOrCreateConversation(user)
                .flatMap(this::markUserRead)
                .then();
    }

    private Flux<CustomerConversationVo> myConversations(PortalUserVo user) {
        return findExistingConversation(user).flux()
                .flatMap(conversation -> toConversationVo(conversation, false));
    }

    private Mono<CustomerMessage> saveUserWebMessage(CustomerConversation conversation, Long userId, String content,
                                                    CustomerMessageSendDto dto) {
        LocalDateTime now = LocalDateTime.now();
        List<FileAttachmentVo> attachments = normalizeAttachments(dto == null ? null : dto.getAttachments());
        String messageType = normalizeMessageType(dto, attachments);
        conversation.setLastMessage(messageSummary(content, attachments, messageType));
        conversation.setLastMessageTime(now);
        conversation.setAdminUnreadCount(safe(conversation.getAdminUnreadCount()) + 1);
        return saveConversation(conversation)
                .flatMap(saved -> saveCustomerMessage(webUserMessage(saved, userId, content, dto))
                        .doOnNext(ignored -> realtimeNotifier.conversationChanged(saved)));
    }

    private Mono<CustomerMessage> saveUserWebMessage(CustomerConversation conversation, PortalUserVo user,
                                                     String content, CustomerMessageSendDto dto) {
        applyPortalIdentity(conversation, user);
        return saveUserWebMessage(conversation, user.getId(), content, dto);
    }

    private Mono<CustomerMessage> saveAdminWebMessage(CustomerConversation conversation, Long adminId, String content,
                                                      CustomerMessageSendDto dto) {
        LocalDateTime now = LocalDateTime.now();
        List<FileAttachmentVo> attachments = normalizeAttachments(dto == null ? null : dto.getAttachments());
        String messageType = normalizeMessageType(dto, attachments);
        conversation.setLastMessage(messageSummary(content, attachments, messageType));
        conversation.setLastMessageTime(now);
        conversation.setUserUnreadCount(safe(conversation.getUserUnreadCount()) + 1);
        CustomerMessage message = adminWebMessage(conversation, adminId, content, dto);
        return saveConversation(conversation)
                .flatMap(saved -> saveCustomerMessage(message)
                        .doOnNext(ignored -> realtimeNotifier.conversationChanged(saved)));
    }

    private CustomerMessage webUserMessage(CustomerConversation conversation, Long userId, String content,
                                           CustomerMessageSendDto dto) {
        CustomerMessage message = baseMessage(conversation, content, dto);
        message.setDirection(CustomerMessage.Direction.INBOUND.name());
        message.setChannel(CustomerMessage.Channel.WEB.name());
        message.setSenderRole(CustomerMessage.SenderRole.USER.name());
        message.setSenderId(userId);
        message.setSendStatus(CustomerMessage.SendStatus.RECEIVED.name());
        message.setReadByAdmin(false);
        message.setReadByUser(true);
        return message;
    }

    private CustomerMessage adminWebMessage(CustomerConversation conversation, Long adminId, String content,
                                            CustomerMessageSendDto dto) {
        CustomerMessage message = baseMessage(conversation, content, dto);
        message.setDirection(CustomerMessage.Direction.OUTBOUND.name());
        message.setChannel(CustomerMessage.Channel.WEB.name());
        message.setSenderRole(CustomerMessage.SenderRole.ADMIN.name());
        message.setSenderId(adminId);
        message.setSendStatus(CustomerMessage.SendStatus.SENT.name());
        message.setReadByAdmin(true);
        message.setReadByUser(false);
        return message;
    }

    private CustomerMessage baseMessage(CustomerConversation conversation, String content, CustomerMessageSendDto dto) {
        CustomerMessage message = new CustomerMessage();
        message.setConversationId(conversation.getId());
        message.setUserId(conversation.getUserId());
        message.setWechatOpenid(conversation.getWechatOpenid());
        List<FileAttachmentVo> attachments = normalizeAttachments(dto == null ? null : dto.getAttachments());
        message.setMessageType(normalizeMessageType(dto, attachments));
        message.setContent(clamp(content, CONTENT_MAX_LENGTH));
        message.setAttachments(attachmentsJson(attachments));
        message.setRelatedType(normalize(dto == null ? null : dto.getRelatedType(), 32));
        message.setRelatedNo(normalize(dto == null ? null : dto.getRelatedNo(), 128));
        return message;
    }

    private Mono<CustomerConversation> findOrCreateConversation(String openid, Optional<PortalUserOauthVo> oauth) {
        return findReusableConversation(openid)
                .flatMap(existing -> refreshWechatIdentity(existing, oauth))
                .switchIfEmpty(Mono.defer(() -> createConversation(openid, oauth)
                        .onErrorResume(this::isDuplicateConversationKey,
                                error -> findReusableConversation(openid)
                                        .flatMap(existing -> refreshWechatIdentity(existing, oauth))
                                        .switchIfEmpty(Mono.error(error)))));
    }

    private Mono<CustomerConversation> findExistingConversation(PortalUserVo user) {
        return conversationRepository.findAllByUserId(user.getId())
                .next()
                .switchIfEmpty(currentWechatAuthorization(user)
                        .flatMap(oauth -> conversationRepository.findByWechatOpenid(oauth.getOpenid())));
    }

    private Mono<CustomerConversation> findOrCreateConversation(PortalUserVo user) {
        return findExistingConversation(user)
                .switchIfEmpty(currentWechatAuthorization(user)
                        .flatMap(oauth -> findOrCreateConversation(oauth.getOpenid(), Optional.of(oauth))))
                .switchIfEmpty(Mono.defer(() -> createWebConversation(user)));
    }

    private Mono<CustomerConversation> findOrCreateConversation(Long targetUserId) {
        return conversationRepository.findAllByUserId(targetUserId)
                .next()
                .switchIfEmpty(authUserGateway.authorizationsByUser(targetUserId, OAuthType.WECHAT)
                        .next()
                        .flatMap(oauth -> findOrCreateConversation(oauth.getOpenid(), Optional.of(oauth))))
                .switchIfEmpty(authUserGateway.getById(targetUserId)
                        .switchIfEmpty(Mono.error(new BusinessException("USER_NOT_FOUND", "客户不存在")))
                        .flatMap(this::createWebConversation));
    }

    private Mono<CustomerConversation> createConversation(String openid, Optional<PortalUserOauthVo> oauth) {
        CustomerConversation conversation = new CustomerConversation();
        conversation.setWechatOpenid(openid);
        conversation.setStatus(CustomerConversation.Status.OPEN.name());
        conversation.setAdminUnreadCount(0);
        conversation.setUserUnreadCount(0);
        applyIdentity(conversation, oauth);
        return saveConversation(conversation);
    }

    private Mono<CustomerConversation> findReusableConversation(String openid) {
        return conversationRepository.findByWechatOpenidIncludingDeleted(openid)
                .map(this::restoreReusableConversation);
    }

    private CustomerConversation restoreReusableConversation(CustomerConversation conversation) {
        conversation.setDelete(false);
        conversation.setStatus(StringUtils.defaultIfBlank(
                conversation.getStatus(), CustomerConversation.Status.OPEN.name()));
        conversation.setAdminUnreadCount(safe(conversation.getAdminUnreadCount()));
        conversation.setUserUnreadCount(safe(conversation.getUserUnreadCount()));
        return conversation;
    }

    private Mono<CustomerConversation> refreshWechatIdentity(CustomerConversation conversation,
                                                             Optional<PortalUserOauthVo> oauth) {
        applyIdentity(conversation, oauth);
        return saveConversation(conversation);
    }

    private Mono<CustomerConversation> createWebConversation(PortalUserVo user) {
        CustomerConversation conversation = baseWebConversation(user.getId());
        applyPortalIdentity(conversation, user);
        return findReusableConversation(conversation.getWechatOpenid())
                .flatMap(existing -> refreshWebIdentity(existing, user))
                .switchIfEmpty(Mono.defer(() -> saveConversation(conversation)
                        .onErrorResume(this::isDuplicateConversationKey,
                                error -> findReusableConversation(conversation.getWechatOpenid())
                                        .flatMap(existing -> refreshWebIdentity(existing, user))
                                        .switchIfEmpty(Mono.error(error)))));
    }

    private Mono<CustomerConversation> refreshWebIdentity(CustomerConversation conversation, PortalUserVo user) {
        applyPortalIdentity(conversation, user);
        return saveConversation(conversation);
    }

    private CustomerConversation baseWebConversation(Long userId) {
        CustomerConversation conversation = new CustomerConversation();
        conversation.setUserId(userId);
        conversation.setWechatOpenid("web:user:" + userId);
        conversation.setDisplayName("用户 " + userId);
        conversation.setStatus(CustomerConversation.Status.OPEN.name());
        conversation.setAdminUnreadCount(0);
        conversation.setUserUnreadCount(0);
        return conversation;
    }

    private void applyIdentity(CustomerConversation conversation, Optional<PortalUserOauthVo> optional) {
        if (optional.isEmpty()) {
            if (StringUtils.isBlank(conversation.getDisplayName())
                    || FunNicknameGenerator.isGenericWechatName(conversation.getDisplayName())) {
                conversation.setDisplayName(FunNicknameGenerator.generate(conversation.getWechatOpenid()));
            }
            return;
        }
        PortalUserOauthVo oauthVo = optional.get();
        conversation.setUserId(oauthVo.getUserId());
        conversation.setWechatUnionId(oauthVo.getUnionId());
        conversation.setDisplayName(StringUtils.firstNonBlank(
                usableName(oauthVo.getDisplayName()), usableName(oauthVo.getNickname()), oauthVo.getLogin(),
                FunNicknameGenerator.generate(oauthVo.getOpenid())));
        conversation.setAvatar(oauthVo.getAvatar());
    }

    private String usableName(String value) {
        String text = StringUtils.trimToNull(value);
        return FunNicknameGenerator.isGenericWechatName(text) ? null : text;
    }

    private void applyPortalIdentity(CustomerConversation conversation, PortalUserVo user) {
        conversation.setUserId(user.getId());
        conversation.setDisplayName(StringUtils.firstNonBlank(user.getUsername(), conversation.getDisplayName(),
                "用户 " + user.getId()));
        conversation.setAvatar(StringUtils.defaultIfBlank(user.getAvatar(), conversation.getAvatar()));
    }

    private Mono<PortalUserOauthVo> currentWechatAuthorization(PortalUserVo user) {
        return authUserGateway.authorizationsByUser(user.getId(), OAuthType.WECHAT).next();
    }

    private Mono<CustomerConversation> findConversation(Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            return Mono.error(new BusinessException("CONVERSATION_NOT_FOUND", "客服会话不存在"));
        }
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new BusinessException("CONVERSATION_NOT_FOUND", "客服会话不存在")));
    }

    private Mono<Void> markAdminRead(CustomerConversation conversation) {
        if (safe(conversation.getAdminUnreadCount()) <= 0) {
            return messageRepository.markAdminRead(conversation.getId()).then();
        }
        conversation.setAdminUnreadCount(0);
        return saveConversation(conversation)
                .then(messageRepository.markAdminRead(conversation.getId()))
                .doOnSuccess(ignored -> realtimeNotifier.conversationChanged(conversation))
                .then();
    }

    private Mono<Void> markUserRead(CustomerConversation conversation) {
        if (safe(conversation.getUserUnreadCount()) <= 0) {
            return messageRepository.markUserRead(conversation.getId()).then();
        }
        conversation.setUserUnreadCount(0);
        return saveConversation(conversation)
                .then(messageRepository.markUserRead(conversation.getId()))
                .doOnSuccess(ignored -> realtimeNotifier.conversationChanged(conversation))
                .then();
    }

    private Mono<CustomerConversationDetailVo> toDetail(CustomerConversation conversation, boolean managerView) {
        return toViewConversation(conversation)
                .flatMap(view -> messageRepository.findAllByConversationId(conversation.getId())
                        .collectList()
                        .flatMap(list -> toMessageVos(view, managerView, list))
                        .map(messages -> new CustomerConversationDetailVo(
                                new CustomerConversationVo(view, managerView), messages)));
    }

    private Mono<CustomerConversationVo> toConversationVo(CustomerConversation conversation, boolean managerView) {
        return toViewConversation(conversation)
                .map(view -> new CustomerConversationVo(view, managerView));
    }

    private Mono<CustomerConversation> toViewConversation(CustomerConversation conversation) {
        if (conversation.getUserId() == null || conversation.getUserId() <= 0) {
            return Mono.just(conversation);
        }
        return authUserGateway.getById(conversation.getUserId())
                .map(user -> {
                    CustomerConversation view = cloneConversation(conversation);
                    view.setAvatar(StringUtils.defaultIfBlank(user.getAvatar(), view.getAvatar()));
                    if (StringUtils.isBlank(view.getDisplayName())
                            || FunNicknameGenerator.isGenericWechatName(view.getDisplayName())) {
                        view.setDisplayName(user.getUsername());
                    }
                    return view;
                })
                .defaultIfEmpty(conversation);
    }

    private CustomerConversation cloneConversation(CustomerConversation source) {
        CustomerConversation target = new CustomerConversation();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setWechatOpenid(source.getWechatOpenid());
        target.setWechatUnionId(source.getWechatUnionId());
        target.setDisplayName(source.getDisplayName());
        target.setAvatar(source.getAvatar());
        target.setStatus(source.getStatus());
        target.setLastMessage(source.getLastMessage());
        target.setLastMessageTime(source.getLastMessageTime());
        target.setLastInboundTime(source.getLastInboundTime());
        target.setAdminUnreadCount(source.getAdminUnreadCount());
        target.setUserUnreadCount(source.getUserUnreadCount());
        return target;
    }

    private Mono<List<CustomerMessageVo>> toMessageVos(CustomerConversation conversation, boolean managerView,
                                                       List<CustomerMessage> messages) {
        Set<Long> adminIds = messages.stream()
                .filter(message -> CustomerMessage.SenderRole.ADMIN.name().equals(message.getSenderRole()))
                .map(CustomerMessage::getSenderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Mono<Map<Long, PortalUserVo>> adminUsers = adminIds.isEmpty()
                ? Mono.just(Map.of())
                : authUserGateway.findAllByIds(adminIds).collectMap(PortalUserVo::getId);
        return adminUsers.map(users -> messages.stream()
                .map(message -> toMessageVo(conversation, managerView, message, users))
                .toList());
    }

    private CustomerMessageVo toMessageVo(CustomerConversation conversation, boolean managerView,
                                          CustomerMessage message, Map<Long, PortalUserVo> adminUsers) {
        CustomerMessageVo vo = new CustomerMessageVo(message);
        if (CustomerMessage.SenderRole.USER.name().equals(message.getSenderRole())) {
            vo.applySender(StringUtils.defaultIfBlank(conversation.getDisplayName(), "客户"), conversation.getAvatar());
            return vo;
        }
        if (CustomerMessage.SenderRole.ADMIN.name().equals(message.getSenderRole())) {
            PortalUserVo admin = adminUsers.get(message.getSenderId());
            String name = managerView
                    ? StringUtils.firstNonBlank(admin == null ? null : admin.getUsername(), "飞鱼小铺客服")
                    : "飞鱼小铺客服";
            vo.applySender(name, admin == null ? null : admin.getAvatar());
            return vo;
        }
        vo.applySender("系统消息", null);
        return vo;
    }

    private String requiredContent(String value, List<FileAttachmentVo> attachments) {
        String content = StringUtils.trimToEmpty(value);
        if (StringUtils.isBlank(content) && (attachments == null || attachments.isEmpty())) {
            throw new BusinessException("CUSTOMER_MESSAGE_EMPTY", "消息内容不能为空");
        }
        if (StringUtils.isBlank(content)) {
            if (allImages(attachments)) {
                return "发送了图片";
            }
            return "上传了附件：" + attachments.stream()
                    .map(FileAttachmentVo::getName)
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElse("附件");
        }
        return clamp(content, CONTENT_MAX_LENGTH);
    }

    private List<FileAttachmentVo> normalizeAttachments(List<FileAttachmentVo> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .filter(attachment -> attachment != null && StringUtils.isNotBlank(attachment.getUrl()))
                .limit(ATTACHMENT_MAX_COUNT)
                .map(this::normalizeAttachment)
                .toList();
    }

    private FileAttachmentVo normalizeAttachment(FileAttachmentVo source) {
        FileAttachmentVo target = new FileAttachmentVo();
        target.setId(source.getId());
        target.setName(clamp(StringUtils.defaultIfBlank(source.getName(), "附件"), 256));
        target.setUrl(clamp(source.getUrl(), 512));
        target.setSize(source.getSize());
        target.setContentType(clamp(StringUtils.defaultIfBlank(source.getContentType(), "application/octet-stream"), 128));
        target.setImage(Boolean.TRUE.equals(source.getImage())
                || StringUtils.startsWithIgnoreCase(source.getContentType(), "image/"));
        return target;
    }

    private String attachmentsJson(List<FileAttachmentVo> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        return JacksonUtils.toJson(attachments);
    }

    private String messageSummary(String content, List<FileAttachmentVo> attachments, String messageType) {
        if (MESSAGE_TYPE_MARKDOWN.equals(messageType)) {
            return clamp("[Markdown] " + content, LAST_MESSAGE_MAX_LENGTH);
        }
        if (MESSAGE_TYPE_IMAGE.equals(messageType)) {
            return clamp(StringUtils.defaultIfBlank(content, "发送了图片"), LAST_MESSAGE_MAX_LENGTH);
        }
        if (attachments == null || attachments.isEmpty()) {
            return clamp(content, LAST_MESSAGE_MAX_LENGTH);
        }
        String name = attachments.stream()
                .map(FileAttachmentVo::getName)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("附件");
        if (StringUtils.isBlank(content)) {
            return clamp("上传了附件：" + name, LAST_MESSAGE_MAX_LENGTH);
        }
        return clamp(content + " · 附件 " + attachments.size(), LAST_MESSAGE_MAX_LENGTH);
    }

    private String normalizeMessageType(CustomerMessageSendDto dto, List<FileAttachmentVo> attachments) {
        String requested = StringUtils.lowerCase(StringUtils.trimToEmpty(dto == null ? null : dto.getMessageType()));
        if (MESSAGE_TYPE_MARKDOWN.equals(requested)) {
            return MESSAGE_TYPE_MARKDOWN;
        }
        if (attachments != null && !attachments.isEmpty()) {
            if (MESSAGE_TYPE_IMAGE.equals(requested) || allImages(attachments)) {
                return MESSAGE_TYPE_IMAGE;
            }
            return MESSAGE_TYPE_FILE;
        }
        return MESSAGE_TYPE_TEXT;
    }

    private boolean allImages(List<FileAttachmentVo> attachments) {
        return attachments != null && !attachments.isEmpty()
                && attachments.stream().allMatch(attachment -> Boolean.TRUE.equals(attachment.getImage()));
    }

    private boolean matchesKeyword(CustomerConversation conversation, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(conversation.getDisplayName(), keyword)
                || StringUtils.containsIgnoreCase(conversation.getWechatOpenid(), keyword)
                || StringUtils.containsIgnoreCase(conversation.getLastMessage(), keyword);
    }

    private long unreadTotal(List<CustomerConversationVo> conversations) {
        return conversations.stream().map(CustomerConversationVo::getUnreadCount)
                .filter(count -> count != null && count > 0)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private List<CustomerConversationVo> limit(List<CustomerConversationVo> conversations, int limit) {
        return conversations.stream()
                .sorted(Comparator.comparing(CustomerConversationVo::getLastMessageTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CustomerConversationVo::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Mono<CustomerConversation> saveConversation(CustomerConversation conversation) {
        applyAudit(conversation, conversationAuditActor(conversation));
        return conversationRepository.save(conversation);
    }

    private boolean isDuplicateConversationKey(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }
            String message = current.getMessage();
            if (StringUtils.containsIgnoreCase(message, "Duplicate entry")
                    && StringUtils.containsIgnoreCase(message, "wechat_openid")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Mono<CustomerMessage> saveCustomerMessage(CustomerMessage message) {
        applyAudit(message, messageAuditActor(message));
        return messageRepository.save(message);
    }

    private void applyAudit(AuditDomain domain, String actor) {
        String normalizedActor = clamp(StringUtils.defaultIfBlank(actor, AUDIT_SYSTEM), 32);
        if (StringUtils.isBlank(domain.getCreateBy())) {
            domain.setCreateBy(normalizedActor);
        }
        if (StringUtils.isBlank(domain.getUpdateBy())) {
            domain.setUpdateBy(normalizedActor);
        }
        if (domain.getDelete() == null) {
            domain.setDelete(false);
        }
    }

    private String conversationAuditActor(CustomerConversation conversation) {
        if (conversation.getUserId() != null && conversation.getUserId() > 0) {
            return "user:" + conversation.getUserId();
        }
        return AUDIT_SYSTEM;
    }

    private String messageAuditActor(CustomerMessage message) {
        if (CustomerMessage.SenderRole.ADMIN.name().equals(message.getSenderRole())
                && message.getSenderId() != null && message.getSenderId() > 0) {
            return "admin:" + message.getSenderId();
        }
        if (message.getUserId() != null && message.getUserId() > 0) {
            return "user:" + message.getUserId();
        }
        return AUDIT_SYSTEM;
    }

    private String normalize(String value, int maxLength) {
        return StringUtils.trimToNull(clamp(value, maxLength));
    }

    private String clamp(String value, int maxLength) {
        String text = StringUtils.defaultString(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

}
