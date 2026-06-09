package group.flyfish.dev.customer.websocket;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.customer.domain.dto.CustomerMessageSendDto;
import group.flyfish.dev.customer.domain.dto.CustomerSocketCommand;
import group.flyfish.dev.customer.domain.vo.CustomerConversationDetailVo;
import group.flyfish.dev.customer.domain.vo.CustomerConversationVo;
import group.flyfish.dev.customer.domain.vo.CustomerServiceSummaryVo;
import group.flyfish.dev.customer.domain.vo.CustomerSocketEnvelopeVo;
import group.flyfish.dev.customer.service.CustomerRealtimeEvent;
import group.flyfish.dev.customer.service.CustomerRealtimeNotifier;
import group.flyfish.dev.customer.service.CustomerServiceCenterService;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import group.flyfish.dev.user.service.PortalUserService;
import group.flyfish.dev.user.service.impl.TokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceWebSocketHandler implements WebSocketHandler {

    private static final int COMMAND_PAYLOAD_MAX_LENGTH = 8192;
    private static final int PUSH_BATCH_MAX_SIZE = 32;
    private static final Duration PUSH_BATCH_WINDOW = Duration.ofMillis(160);

    private final TokenProvider tokenProvider;
    private final PortalUserService portalUserService;
    private final CustomerServiceCenterService customerServiceCenterService;
    private final CustomerRealtimeNotifier realtimeNotifier;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return authenticate(session.getHandshakeInfo())
                .flatMap(user -> handleAuthenticated(session, user))
                .onErrorResume(e -> session.close());
    }

    private Mono<Void> handleAuthenticated(WebSocketSession session, PortalUserVo user) {
        AtomicLong activeConversationId = new AtomicLong(0);
        Sinks.Empty<Void> sessionClosed = Sinks.empty();
        Mono<Void> closeSignal = sessionClosed.asMono();
        Flux<CustomerSocketEnvelopeVo> initial = Flux.concat(
                Mono.just(CustomerSocketEnvelopeVo.connected()),
                buildState(user, null, false).flux());
        Flux<CustomerSocketEnvelopeVo> commandReplies = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .takeUntilOther(closeSignal)
                .concatMap(text -> handleCommand(user, activeConversationId, text)
                        .onErrorResume(e -> Mono.just(errorEnvelope(e))))
                .doFinally(signal -> sessionClosed.tryEmitEmpty());
        Flux<CustomerSocketEnvelopeVo> pushes = realtimeNotifier.events()
                .filter(event -> visibleTo(user, event))
                .windowTimeout(PUSH_BATCH_MAX_SIZE, PUSH_BATCH_WINDOW)
                .flatMap(window -> window.reduce((left, right) -> right))
                .takeUntilOther(closeSignal)
                .concatMap(event -> buildState(user, activeConversationId.get(), activeConversationId.get() > 0)
                        .onErrorResume(e -> Mono.just(errorEnvelope(e))));
        Flux<CustomerSocketEnvelopeVo> outbound = Flux.concat(initial, Flux.merge(commandReplies, pushes))
                .doFinally(signal -> {
                    activeConversationId.set(0);
                    sessionClosed.tryEmitEmpty();
                    log.debug("客服 WebSocket 会话释放。sessionId={}, userId={}, signal={}",
                            session.getId(), user.getId(), signal);
                });

        return session.send(outbound
                .map(JacksonUtils::toJson)
                .map(session::textMessage));
    }

    private Flux<CustomerSocketEnvelopeVo> handleCommand(PortalUserVo user, AtomicLong activeConversationId,
                                                        String payload) {
        if (StringUtils.length(payload) > COMMAND_PAYLOAD_MAX_LENGTH) {
            return Flux.just(CustomerSocketEnvelopeVo.error("PAYLOAD_TOO_LARGE", "消息内容过长"));
        }
        CustomerSocketCommand command = readCommand(payload);
        String type = StringUtils.upperCase(StringUtils.defaultIfBlank(command.getType(), "SYNC"));
        return switch (type) {
            case "OPEN" -> openConversation(user, command)
                    .doOnNext(detail -> activeConversationId.set(detail.getConversation().getId()))
                    .flatMapMany(detail -> buildState(user, detail));
            case "SEND" -> sendMessage(user, activeConversationId, command)
                    .doOnNext(detail -> activeConversationId.set(detail.getConversation().getId()))
                    .flatMapMany(detail -> buildState(user, detail));
            case "READ" -> markRead(user, activeConversationId.get())
                    .thenMany(buildState(user, activeConversationId.get(), activeConversationId.get() > 0).flux());
            case "CLOSE" -> {
                activeConversationId.set(0);
                yield buildState(user, null, false).flux();
            }
            default -> buildState(user, activeConversationId.get(), activeConversationId.get() > 0).flux();
        };
    }

    private Mono<CustomerConversationDetailVo> openConversation(PortalUserVo user, CustomerSocketCommand command) {
        if (ShopAuthorizationUtils.isShopMaintainer(user)) {
            if (command.getUserId() != null && command.getUserId() > 0) {
                return customerServiceCenterService.getManagementConversationByUser(user, command.getUserId());
            }
            return customerServiceCenterService.getManagementConversation(user, command.getConversationId());
        }
        return customerServiceCenterService.getMyConversation(user);
    }

    private Mono<CustomerConversationDetailVo> sendMessage(PortalUserVo user, AtomicLong activeConversationId,
                                                          CustomerSocketCommand command) {
        CustomerMessageSendDto dto = new CustomerMessageSendDto();
        dto.setContent(command.getContent());
        dto.setMessageType(command.getMessageType());
        dto.setAttachments(command.getAttachments());
        dto.setRelatedType(command.getRelatedType());
        dto.setRelatedNo(command.getRelatedNo());
        if (ShopAuthorizationUtils.isShopMaintainer(user)) {
            Long conversationId = command.getConversationId() == null ? activeConversationId.get()
                    : command.getConversationId();
            return customerServiceCenterService.sendManagementMessage(user, conversationId, dto);
        }
        return customerServiceCenterService.sendMyMessage(user, dto);
    }

    private Mono<Void> markRead(PortalUserVo user, Long conversationId) {
        if (ShopAuthorizationUtils.isShopMaintainer(user) && conversationId != null && conversationId > 0) {
            return customerServiceCenterService.markManagementRead(user, conversationId);
        }
        return customerServiceCenterService.markUserRead(user);
    }

    private Mono<CustomerSocketEnvelopeVo> buildState(PortalUserVo user, Long conversationId, boolean includeDetail) {
        Mono<CustomerServiceSummaryVo> summary = customerServiceCenterService.summary(user);
        Mono<List<CustomerConversationVo>> conversations = ShopAuthorizationUtils.isShopMaintainer(user)
                ? customerServiceCenterService.getManagementConversations(user, null).collectList()
                : Mono.just(List.of());
        Mono<Optional<CustomerConversationDetailVo>> detail = includeDetail
                ? detail(user, conversationId).onErrorResume(e -> Mono.empty())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                : Mono.just(Optional.empty());
        return Mono.zip(summary, conversations, detail)
                .map(tuple -> CustomerSocketEnvelopeVo.state(tuple.getT1(), tuple.getT2(), tuple.getT3().orElse(null)));
    }

    private Flux<CustomerSocketEnvelopeVo> buildState(PortalUserVo user, CustomerConversationDetailVo detail) {
        Mono<CustomerServiceSummaryVo> summary = customerServiceCenterService.summary(user);
        Mono<List<CustomerConversationVo>> conversations = ShopAuthorizationUtils.isShopMaintainer(user)
                ? customerServiceCenterService.getManagementConversations(user, null).collectList()
                : Mono.just(List.of());
        return Mono.zip(summary, conversations)
                .map(tuple -> CustomerSocketEnvelopeVo.state(tuple.getT1(), tuple.getT2(), detail))
                .flux();
    }

    private Mono<CustomerConversationDetailVo> detail(PortalUserVo user, Long conversationId) {
        if (ShopAuthorizationUtils.isShopMaintainer(user)) {
            return customerServiceCenterService.peekManagementConversation(user, conversationId);
        }
        return customerServiceCenterService.peekMyConversation(user);
    }

    private boolean visibleTo(PortalUserVo user, CustomerRealtimeEvent event) {
        if (event.broadcast()) {
            return true;
        }
        if (ShopAuthorizationUtils.isShopMaintainer(user)) {
            return true;
        }
        return user != null && user.getId() != null && user.getId().equals(event.userId());
    }

    private Mono<PortalUserVo> authenticate(HandshakeInfo handshakeInfo) {
        return Flux.fromIterable(handshakeTokens(handshakeInfo))
                .concatMap(tokenProvider::parseAndValidateToken)
                .next()
                .map(token -> Long.parseLong(token.subject()))
                .flatMap(portalUserService::getById)
                .filter(user -> user.getId() != null && user.getId() > 0)
                .switchIfEmpty(Mono.error(new BusinessException("UNAUTHORIZED", "请先登录")));
    }

    private List<String> handshakeTokens(HandshakeInfo handshakeInfo) {
        List<String> tokens = new ArrayList<>();
        addToken(tokens, queryParam(handshakeInfo, "access_token"));
        addToken(tokens, queryParam(handshakeInfo, "token"));
        String authorization = handshakeInfo.getHeaders().getFirst(TokenProvider.AUTHORIZATION_HEADER);
        if (StringUtils.startsWith(authorization, "Bearer ")) {
            addToken(tokens, authorization.substring(7));
        }
        for (String cookieHeader : handshakeInfo.getHeaders().getOrEmpty("Cookie")) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && TokenProvider.AUTHORIZATION_COOKIE.equals(parts[0])) {
                    addToken(tokens, parts[1]);
                }
            }
        }
        return tokens;
    }

    private String queryParam(HandshakeInfo handshakeInfo, String name) {
        String query = handshakeInfo.getUri().getRawQuery();
        if (StringUtils.isBlank(query)) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            if (name.equals(key)) {
                return parts.length > 1 ? decode(parts[1]) : "";
            }
        }
        return null;
    }

    private String decode(String value) {
        return URLDecoder.decode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }

    private void addToken(List<String> tokens, String token) {
        String value = StringUtils.trimToNull(token);
        if (value != null && !tokens.contains(value)) {
            tokens.add(value);
        }
    }

    private CustomerSocketCommand readCommand(String payload) {
        try {
            return JacksonUtils.readValue(payload, CustomerSocketCommand.class);
        } catch (RuntimeException e) {
            CustomerSocketCommand command = new CustomerSocketCommand();
            command.setType("SYNC");
            return command;
        }
    }

    private CustomerSocketEnvelopeVo errorEnvelope(Throwable e) {
        String message = StringUtils.defaultIfBlank(e.getMessage(), "消息处理失败");
        log.warn("客服 WebSocket 消息处理失败：{}", message);
        return CustomerSocketEnvelopeVo.error("CUSTOMER_SOCKET_ERROR", message);
    }
}
