package group.flyfish.dev.support.notification;

import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import group.flyfish.dev.user.domain.OAuthType;
import group.flyfish.dev.user.domain.po.PortalUser;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.user.repository.PortalUserOauthRepository;
import group.flyfish.dev.user.repository.PortalUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketNotificationServiceImpl implements SupportTicketNotificationService {

    private final PortalUserRepository portalUserRepository;

    private final PortalUserOauthRepository portalUserOauthRepository;

    private final SupportNotificationProperties properties;

    private final SupportTicketNotificationMessageFactory messageFactory;

    private final SupportTicketMailNotifier mailNotifier;

    private final SupportTicketWechatNotifier wechatNotifier;

    @Override
    public Mono<Void> ticketCreated(SupportTicket ticket, SupportTicketMessage message) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        Mono<Optional<PortalUser>> creator = findUser(ticket.getCreatorId()).cache();
        Mono<Void> userNotice = creator.flatMap(optional -> notifyUser(ticket,
                messageFactory.userTicketCreated(ticket, optional.orElse(null))));
        Mono<Void> adminNotice = creator.flatMap(optional -> notifyAdmins(
                messageFactory.adminTicketCreated(ticket, message, optional.orElse(null))));
        return Mono.whenDelayError(userNotice, adminNotice).then().onErrorResume(this::ignoreUnexpectedError);
    }

    @Override
    public Mono<Void> userReplied(SupportTicket ticket, SupportTicketMessage message) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return findUser(ticket.getCreatorId())
                .flatMap(optional -> notifyAdmins(messageFactory.adminUserReplied(ticket, message, optional.orElse(null))))
                .onErrorResume(this::ignoreUnexpectedError);
    }

    @Override
    public Mono<Void> adminReplied(SupportTicket ticket, SupportTicketMessage message) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return findUser(ticket.getCreatorId())
                .flatMap(optional -> notifyUser(ticket,
                        messageFactory.userAdminReplied(ticket, message, optional.orElse(null))))
                .onErrorResume(this::ignoreUnexpectedError);
    }

    @Override
    public Mono<Void> ticketResolved(SupportTicket ticket, SupportTicketMessage message) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return findUser(ticket.getCreatorId())
                .flatMap(optional -> notifyUser(ticket,
                        messageFactory.userTicketResolved(ticket, optional.orElse(null))))
                .onErrorResume(this::ignoreUnexpectedError);
    }

    private Mono<Void> notifyUser(SupportTicket ticket, SupportTicketNotificationMessage message) {
        return userRecipients(ticket.getCreatorId())
                .flatMap(recipients -> send(recipients, message));
    }

    private Mono<Void> notifyAdmins(SupportTicketNotificationMessage message) {
        return adminRecipients().flatMap(recipients -> send(recipients, message));
    }

    private Mono<Void> send(SupportTicketRecipients recipients, SupportTicketNotificationMessage message) {
        return Mono.whenDelayError(
                mailNotifier.send(recipients.emails(), message),
                wechatNotifier.send(recipients.wechatOpenids(), message)
        ).then();
    }

    private Mono<SupportTicketRecipients> userRecipients(Long userId) {
        if (userId == null) {
            return Mono.just(SupportTicketRecipients.of(List.of(), List.of()));
        }
        Mono<List<String>> emails = portalUserRepository.findById(userId)
                .flatMap(user -> Mono.justOrEmpty(user.getEmail()))
                .map(List::of)
                .defaultIfEmpty(List.of())
                .map(mailNotifier::normalizeEmails);
        Mono<List<String>> openids = wechatOpenidsByUserIds(List.of(userId));
        return Mono.zip(emails, openids)
                .map(tuple -> SupportTicketRecipients.of(tuple.getT1(), tuple.getT2()));
    }

    private Mono<SupportTicketRecipients> adminRecipients() {
        Mono<List<Long>> maintainerUserIds = maintainerUserIds().cache();
        Mono<List<String>> emails = maintainerUserIds
                .flatMap(ids -> Flux.fromIterable(ids)
                        .flatMap(portalUserRepository::findById)
                        .flatMap(user -> Mono.justOrEmpty(user.getEmail()))
                        .collectList())
                .map(maintainerEmails -> merge(properties.getMail().getAdminRecipients(), maintainerEmails))
                .map(mailNotifier::normalizeEmails);
        Mono<List<String>> openids = maintainerUserIds
                .flatMap(ids -> wechatOpenidsByUserIds(ids)
                        .map(maintainerOpenids -> merge(properties.getWechat().getAdminOpenids(), maintainerOpenids)))
                .map(wechatNotifier::normalizeOpenids);
        return Mono.zip(emails, openids)
                .map(tuple -> SupportTicketRecipients.of(tuple.getT1(), tuple.getT2()));
    }

    private Mono<List<Long>> maintainerUserIds() {
        String giteaOpenid = properties.getWechat().getMaintainerGiteaOpenid();
        if (StringUtils.isBlank(giteaOpenid)) {
            return Mono.just(List.of());
        }
        return portalUserOauthRepository.findAllByTypeAndOpenid(OAuthType.GITEA, giteaOpenid.trim())
                .map(PortalUserOauth::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collectList();
    }

    private Mono<List<String>> wechatOpenidsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(userIds)
                .filter(id -> id != null && id > 0)
                .distinct()
                .flatMap(userId -> portalUserOauthRepository.findAllByUserIdAndType(userId, OAuthType.WECHAT))
                .map(PortalUserOauth::getOpenid)
                .collectList()
                .map(wechatNotifier::normalizeOpenids);
    }

    private Mono<Optional<PortalUser>> findUser(Long userId) {
        if (userId == null) {
            return Mono.just(Optional.empty());
        }
        return portalUserRepository.findById(userId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private List<String> merge(Collection<String> configured, Collection<String> discovered) {
        List<String> values = new ArrayList<>();
        if (configured != null) {
            values.addAll(configured);
        }
        if (discovered != null) {
            values.addAll(discovered);
        }
        return values;
    }

    private Mono<Void> ignoreUnexpectedError(Throwable e) {
        log.warn("工单通知流程异常，已降级跳过，不影响工单主流程。error={}", e.getMessage());
        return Mono.empty();
    }
}
