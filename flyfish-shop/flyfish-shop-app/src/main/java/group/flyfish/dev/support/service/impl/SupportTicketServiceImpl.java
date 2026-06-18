package group.flyfish.dev.support.service.impl;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.support.domain.dto.SupportTicketCreateDto;
import group.flyfish.dev.support.domain.dto.SupportTicketMessageDto;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.support.domain.vo.SupportTicketDetailVo;
import group.flyfish.dev.support.domain.vo.SupportTicketMessageVo;
import group.flyfish.dev.support.domain.vo.SupportTicketVo;
import group.flyfish.dev.support.notification.SupportTicketNotificationService;
import group.flyfish.dev.support.repository.SupportTicketMessageRepository;
import group.flyfish.dev.support.repository.SupportTicketRepository;
import group.flyfish.dev.support.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketServiceImpl implements SupportTicketService {

    private static final String DEFAULT_CATEGORY = "GENERAL";
    private static final String RESOLVED_MESSAGE = "问题已标记解决";
    private static final int ATTACHMENT_MAX_COUNT = 6;
    private static final Comparator<SupportTicket> TICKET_TIME_DESC = Comparator
            .comparing(SupportTicketServiceImpl::ticketSortTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SupportTicket::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final AuthUserGateway authUserGateway;
    private final SupportTicketNotificationService notificationService;
    private final CustomerRealtimeNotifier realtimeNotifier;

    @Override
    public Flux<SupportTicketVo> getMyTickets(PortalUserVo user, String status) {
        ShopAuthorizationUtils.requireLogin(user);
        return ticketRepository.findAllByCreatorId(user.getId(), parseStatusName(status))
                .sort(TICKET_TIME_DESC)
                .concatMap(ticket -> toVo(ticket, false));
    }

    @Override
    public Mono<Long> countUnreadTickets(PortalUserVo user) {
        ShopAuthorizationUtils.requireLogin(user);
        boolean managerView = ShopAuthorizationUtils.isShopMaintainer(user);
        Flux<SupportTicket> unreadTickets = managerView
                ? ticketRepository.findAllUnreadForManagement()
                : ticketRepository.findAllUnreadByCreatorId(user.getId());
        return unreadTickets
                .map(ticket -> managerView ? safe(ticket.getAdminUnreadCount()) : safe(ticket.getUserUnreadCount()))
                .reduce(0L, (sum, count) -> sum + count);
    }

    @Override
    public Flux<SupportTicketVo> getUnreadTickets(PortalUserVo user, int limit) {
        ShopAuthorizationUtils.requireLogin(user);
        boolean managerView = ShopAuthorizationUtils.isShopMaintainer(user);
        Flux<SupportTicket> unreadTickets = managerView
                ? ticketRepository.findAllUnreadForManagement()
                : ticketRepository.findAllUnreadByCreatorId(user.getId());
        return unreadTickets.sort(TICKET_TIME_DESC)
                .take(Math.max(1, limit))
                .concatMap(ticket -> toVo(ticket, managerView));
    }

    @Override
    @Transactional
    public Mono<SupportTicketDetailVo> createTicket(PortalUserVo user, SupportTicketCreateDto dto) {
        ShopAuthorizationUtils.requireLogin(user);
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNo("TK" + IdGenerators.idString());
        ticket.setCreatorId(user.getId());
        ticket.setTitle(trimRequired(dto.getTitle(), "工单标题不能为空"));
        ticket.setCategory(StringUtils.defaultIfBlank(trim(dto.getCategory()), DEFAULT_CATEGORY));
        ticket.setPriority(parsePriorityName(dto.getPriority()));
        setStatus(ticket, SupportTicket.Status.OPEN);
        ticket.setContact(resolveContact(dto.getContact(), user));
        var attachments = normalizeAttachments(dto.getAttachments());
        String content = requiredContent(dto.getContent(), attachments, "问题内容不能为空");
        ticket.setLastMessage(messageSummary(content, attachments));
        ticket.setAdminUnreadCount(1);
        ticket.setUserUnreadCount(0);
        return ticketRepository.save(ticket)
                .flatMap(saved -> saveMessage(saved, user.getId(), SupportTicketMessage.SenderRole.USER,
                        content, attachments).map(message -> new TicketChange(saved, message)))
                .flatMap(change -> toDetail(change.ticket(), false)
                        .doOnSuccess(detail -> {
                            realtimeNotifier.ticketsChanged();
                            notifyAsync("ticketCreated",
                                    notificationService.ticketCreated(change.ticket(), change.message()));
                        }));
    }

    @Override
    public Mono<SupportTicketDetailVo> getMyTicket(PortalUserVo user, String ticketNo) {
        ShopAuthorizationUtils.requireLogin(user);
        return findOwnedTicket(user, ticketNo)
                .flatMap(this::markUserRead)
                .doOnNext(ticket -> realtimeNotifier.ticketsChanged())
                .flatMap(ticket -> toDetail(ticket, false));
    }

    @Override
    @Transactional
    public Mono<SupportTicketDetailVo> addUserMessage(PortalUserVo user, String ticketNo, SupportTicketMessageDto dto) {
        ShopAuthorizationUtils.requireLogin(user);
        var attachments = normalizeAttachments(dto.getAttachments());
        String content = requiredContent(dto.getContent(), attachments, "回复内容不能为空");
        return findOwnedTicket(user, ticketNo)
                .flatMap(ticket -> {
                    ticket.setLastMessage(messageSummary(content, attachments));
                    ticket.setAdminUnreadCount(safe(ticket.getAdminUnreadCount()) + 1);
                    if (hasStatus(ticket, SupportTicket.Status.RESOLVED) || hasStatus(ticket, SupportTicket.Status.CLOSED)) {
                        setStatus(ticket, SupportTicket.Status.OPEN);
                        ticket.setResolvedBy(null);
                        ticket.setResolvedTime(null);
                    } else if (hasStatus(ticket, SupportTicket.Status.WAITING_USER)) {
                        setStatus(ticket, SupportTicket.Status.PROCESSING);
                    }
                    return ticketRepository.save(ticket)
                            .flatMap(saved -> saveMessage(saved, user.getId(), SupportTicketMessage.SenderRole.USER,
                                            content, attachments)
                                    .map(message -> new TicketChange(saved, message)));
                })
                .flatMap(change -> toDetail(change.ticket(), false)
                        .doOnSuccess(detail -> {
                            realtimeNotifier.ticketsChanged();
                            notifyAsync("userReplied",
                                    notificationService.userReplied(change.ticket(), change.message()));
                        }));
    }

    @Override
    public Flux<SupportTicketVo> getManagementTickets(PortalUserVo user, String status) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return ticketRepository.findAllForManagement(parseStatusName(status))
                .sort(TICKET_TIME_DESC)
                .concatMap(ticket -> toVo(ticket, true));
    }

    @Override
    public Mono<SupportTicketDetailVo> getManagementTicket(PortalUserVo user, String ticketNo) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return findTicket(ticketNo)
                .flatMap(this::markAdminRead)
                .doOnNext(ticket -> realtimeNotifier.ticketsChanged())
                .flatMap(ticket -> toDetail(ticket, true));
    }

    @Override
    @Transactional
    public Mono<SupportTicketDetailVo> addManagementMessage(PortalUserVo user, String ticketNo, SupportTicketMessageDto dto) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        var attachments = normalizeAttachments(dto.getAttachments());
        String content = requiredContent(dto.getContent(), attachments, "回复内容不能为空");
        return findTicket(ticketNo)
                .flatMap(ticket -> {
                    if (hasStatus(ticket, SupportTicket.Status.RESOLVED) || hasStatus(ticket, SupportTicket.Status.CLOSED)) {
                        return Mono.error(new BusinessException("TICKET_CLOSED", "工单已结束"));
                    }
                    ticket.setAssigneeId(user.getId());
                    setStatus(ticket, SupportTicket.Status.WAITING_USER);
                    ticket.setLastMessage(messageSummary(content, attachments));
                    ticket.setUserUnreadCount(safe(ticket.getUserUnreadCount()) + 1);
                    return ticketRepository.save(ticket)
                            .flatMap(saved -> saveMessage(saved, user.getId(), SupportTicketMessage.SenderRole.ADMIN,
                                            content, attachments)
                                    .map(message -> new TicketChange(saved, message)));
                })
                .flatMap(change -> toDetail(change.ticket(), true)
                        .doOnSuccess(detail -> {
                            realtimeNotifier.ticketsChanged();
                            notifyAsync("adminReplied",
                                    notificationService.adminReplied(change.ticket(), change.message()));
                        }));
    }

    @Override
    @Transactional
    public Mono<SupportTicketDetailVo> resolveTicket(PortalUserVo user, String ticketNo) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return findTicket(ticketNo)
                .flatMap(ticket -> {
                    if (hasStatus(ticket, SupportTicket.Status.RESOLVED)) {
                        return toDetail(ticket, true);
                    }
                    setStatus(ticket, SupportTicket.Status.RESOLVED);
                    ticket.setResolvedBy(user.getId());
                    ticket.setResolvedTime(LocalDateTime.now());
                    ticket.setLastMessage(RESOLVED_MESSAGE);
                    ticket.setUserUnreadCount(safe(ticket.getUserUnreadCount()) + 1);
                    return ticketRepository.save(ticket)
                            .flatMap(saved -> saveMessage(saved, user.getId(), SupportTicketMessage.SenderRole.ADMIN,
                                    RESOLVED_MESSAGE, List.of()).map(message -> new TicketChange(saved, message)))
                            .flatMap(change -> toDetail(change.ticket(), true)
                                    .doOnSuccess(detail -> {
                                        realtimeNotifier.ticketsChanged();
                                        notifyAsync("ticketResolved",
                                                notificationService.ticketResolved(change.ticket(), change.message()));
                                    }));
                });
    }

    private Mono<SupportTicket> findOwnedTicket(PortalUserVo user, String ticketNo) {
        return findTicket(ticketNo)
                .filter(ticket -> user.getId().equals(ticket.getCreatorId()))
                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "工单不存在")));
    }

    private Mono<SupportTicket> findTicket(String ticketNo) {
        if (StringUtils.isBlank(ticketNo)) {
            return Mono.error(new BusinessException("TICKET_NOT_FOUND", "工单不存在"));
        }
        return ticketRepository.findByTicketNo(ticketNo.trim())
                .switchIfEmpty(Mono.error(new BusinessException("TICKET_NOT_FOUND", "工单不存在")));
    }

    private Mono<SupportTicketMessage> saveMessage(SupportTicket ticket, Long senderId,
                                                   SupportTicketMessage.SenderRole role, String content,
                                                   List<FileAttachmentVo> attachments) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicketId(ticket.getId());
        message.setSenderId(senderId);
        message.setSenderRole(role.name());
        message.setContent(content);
        message.setAttachments(attachmentsJson(attachments));
        return messageRepository.save(message);
    }

    private Mono<SupportTicketVo> toVo(SupportTicket ticket, boolean managerView) {
        return findUser(ticket.getCreatorId())
                .map(optional -> new SupportTicketVo(ticket, optional.orElse(null), managerView));
    }

    private Mono<SupportTicketDetailVo> toDetail(SupportTicket ticket, boolean managerView) {
        Mono<Optional<PortalUserVo>> creator = findUser(ticket.getCreatorId());
        return creator.flatMap(optional -> messageRepository.findAllByTicketIdOrderByCreateTimeAsc(ticket.getId())
                .map(message -> toMessageVo(message, optional.orElse(null)))
                .collectList()
                .map(messages -> new SupportTicketDetailVo(ticket, optional.orElse(null), messages, managerView)));
    }

    private Mono<SupportTicket> markAdminRead(SupportTicket ticket) {
        if (safe(ticket.getAdminUnreadCount()) == 0) {
            return Mono.just(ticket);
        }
        return ticketRepository.clearAdminUnread(ticket.getId())
                .thenReturn(ticket)
                .doOnNext(readTicket -> readTicket.setAdminUnreadCount(0));
    }

    private Mono<SupportTicket> markUserRead(SupportTicket ticket) {
        if (safe(ticket.getUserUnreadCount()) == 0) {
            return Mono.just(ticket);
        }
        return ticketRepository.clearUserUnread(ticket.getId())
                .thenReturn(ticket)
                .doOnNext(readTicket -> readTicket.setUserUnreadCount(0));
    }

    private SupportTicketMessageVo toMessageVo(SupportTicketMessage message, PortalUserVo creator) {
        boolean fromUser = SupportTicketMessage.SenderRole.USER.name().equals(message.getSenderRole());
        String senderName = fromUser ? creatorName(creator, message.getSenderId()) : "客服";
        String senderAvatar = fromUser && creator != null ? creator.getAvatar() : null;
        return new SupportTicketMessageVo(message, senderName, senderAvatar);
    }

    private Mono<Optional<PortalUserVo>> findUser(Long userId) {
        if (userId == null) {
            return Mono.just(Optional.empty());
        }
        return authUserGateway.getById(userId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private String parseStatusName(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        try {
            return SupportTicket.Status.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_TICKET_STATUS", "工单状态异常");
        }
    }

    private String parsePriorityName(String priority) {
        if (StringUtils.isBlank(priority)) {
            return SupportTicket.Priority.NORMAL.name();
        }
        try {
            return SupportTicket.Priority.valueOf(priority.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_TICKET_PRIORITY", "工单优先级异常");
        }
    }

    private String resolveContact(String contact, PortalUserVo user) {
        String explicit = trim(contact);
        if (StringUtils.isNotBlank(explicit)) {
            return explicit;
        }
        return StringUtils.firstNonBlank(trim(user.getEmail()), trim(user.getPhone()));
    }

    private String trimRequired(String value, String message) {
        String text = trim(value);
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("INVALID_TICKET_CONTENT", message);
        }
        return text;
    }

    private String requiredContent(String value, List<FileAttachmentVo> attachments, String message) {
        String content = trim(value);
        if (StringUtils.isBlank(content) && (attachments == null || attachments.isEmpty())) {
            throw new BusinessException("INVALID_TICKET_CONTENT", message);
        }
        if (StringUtils.isBlank(content)) {
            return "上传了附件：" + attachments.stream()
                    .map(FileAttachmentVo::getName)
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElse("附件");
        }
        return content;
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
        target.setName(StringUtils.left(StringUtils.defaultIfBlank(source.getName(), "附件"), 256));
        target.setUrl(StringUtils.left(source.getUrl(), 512));
        target.setSize(source.getSize());
        target.setContentType(StringUtils.left(
                StringUtils.defaultIfBlank(source.getContentType(), "application/octet-stream"), 128));
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

    private String messageSummary(String content, List<FileAttachmentVo> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return StringUtils.left(content, 1024);
        }
        String name = attachments.stream()
                .map(FileAttachmentVo::getName)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse("附件");
        if (StringUtils.isBlank(content)) {
            return StringUtils.left("上传了附件：" + name, 1024);
        }
        return StringUtils.left(content + " · 附件 " + attachments.size(), 1024);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String creatorName(PortalUserVo creator, Long fallbackId) {
        if (creator == null) {
            return fallbackId == null ? "用户" : "用户 " + fallbackId;
        }
        return StringUtils.defaultIfBlank(creator.getUsername(), "用户 " + creator.getId());
    }

    private static LocalDateTime ticketSortTime(SupportTicket ticket) {
        if (ticket.getUpdateTime() != null) {
            return ticket.getUpdateTime();
        }
        return ticket.getCreateTime();
    }

    private boolean hasStatus(SupportTicket ticket, SupportTicket.Status status) {
        return status.name().equals(ticket.getStatus());
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private void setStatus(SupportTicket ticket, SupportTicket.Status status) {
        ticket.setStatus(status.name());
    }

    private void notifyAsync(String scene, Mono<Void> notification) {
        if (notification == null) {
            return;
        }
        notification.subscribe(null, e -> log.warn("工单通知触发失败。scene={}, error={}", scene, e.getMessage()));
    }

    private record TicketChange(SupportTicket ticket, SupportTicketMessage message) {
    }
}
