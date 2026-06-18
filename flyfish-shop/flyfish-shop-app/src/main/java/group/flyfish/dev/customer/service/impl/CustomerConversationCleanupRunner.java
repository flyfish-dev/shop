package group.flyfish.dev.customer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 客服会话历史噪声清理器。
 *
 * <p>公众号关注、取消关注和用户在公众号内发送的消息已经由“公众号动态”独立承接。
 * 这些消息天然无法在站内客服会话中直接回复，保留在聊天列表里只会干扰管理员判断。
 * 因此启动时幂等清理旧版本写入的 WECHAT 渠道消息，并隐藏清理后没有 WEB 消息的空会话。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerConversationCleanupRunner implements ApplicationRunner {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final DatabaseClient databaseClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            cleanup().block(TIMEOUT);
        } catch (Exception e) {
            log.warn("客服会话历史噪声清理未完成：{}", e.getMessage());
        }
    }

    private Mono<Void> cleanup() {
        return hideWechatMessages()
                .flatMap(wechatMessages -> hideWechatOnlyConversations()
                        .flatMap(wechatOnlyConversations -> refreshConversationSummary()
                                .doOnNext(refreshed -> log.info(
                                        "客服会话历史噪声清理完成，隐藏公众号消息 {} 条，隐藏纯公众号会话 {} 个，刷新会话摘要 {} 个",
                                        wechatMessages, wechatOnlyConversations, refreshed))))
                .then();
    }

    /**
     * 公众号渠道消息不再作为站内客服聊天展示，统一交由公众号动态列表承接。
     */
    private Mono<Long> hideWechatMessages() {
        return databaseClient.sql("""
                        UPDATE customer_message
                           SET is_delete = true,
                               update_time = CURRENT_TIMESTAMP
                         WHERE is_delete = false
                           AND channel = 'WECHAT'
                        """)
                .fetch()
                .rowsUpdated();
    }

    /**
     * 没有任何 WEB 消息的会话属于旧版公众号噪声会话，逻辑隐藏即可。
     */
    private Mono<Long> hideWechatOnlyConversations() {
        return databaseClient.sql("""
                        UPDATE customer_conversation
                           SET is_delete = true,
                               admin_unread_count = 0,
                               user_unread_count = 0,
                               update_time = CURRENT_TIMESTAMP
                         WHERE is_delete = false
                           AND wechat_openid NOT LIKE 'web:user:%'
                           AND NOT EXISTS (
                               SELECT 1
                                 FROM customer_message message
                                WHERE message.is_delete = false
                                  AND message.conversation_id = customer_conversation.id
                                  AND message.channel = 'WEB'
                           )
                        """)
                .fetch()
                .rowsUpdated();
    }

    /**
     * 对仍然保留的真实站内客服会话，按清理后的消息重新计算摘要和未读数。
     */
    private Mono<Long> refreshConversationSummary() {
        return databaseClient.sql("""
                        UPDATE customer_conversation
                           SET last_message = (
                                   SELECT message.content
                                     FROM customer_message message
                                    WHERE message.is_delete = false
                                      AND message.conversation_id = customer_conversation.id
                                    ORDER BY message.create_time DESC, message.id DESC
                                    LIMIT 1
                               ),
                               last_message_time = (
                                   SELECT message.create_time
                                     FROM customer_message message
                                    WHERE message.is_delete = false
                                      AND message.conversation_id = customer_conversation.id
                                    ORDER BY message.create_time DESC, message.id DESC
                                    LIMIT 1
                               ),
                               last_inbound_time = (
                                   SELECT message.create_time
                                     FROM customer_message message
                                    WHERE message.is_delete = false
                                      AND message.conversation_id = customer_conversation.id
                                      AND message.direction = 'INBOUND'
                                    ORDER BY message.create_time DESC, message.id DESC
                                    LIMIT 1
                               ),
                               admin_unread_count = (
                                   SELECT COUNT(*)
                                     FROM customer_message message
                                    WHERE message.is_delete = false
                                      AND message.conversation_id = customer_conversation.id
                                      AND message.direction = 'INBOUND'
                                      AND message.read_by_admin = false
                               ),
                               user_unread_count = (
                                   SELECT COUNT(*)
                                     FROM customer_message message
                                    WHERE message.is_delete = false
                                      AND message.conversation_id = customer_conversation.id
                                      AND message.direction = 'OUTBOUND'
                                      AND message.read_by_user = false
                               ),
                               update_time = CURRENT_TIMESTAMP
                         WHERE is_delete = false
                           AND EXISTS (
                               SELECT 1
                                 FROM customer_message message
                                WHERE message.is_delete = false
                                  AND message.conversation_id = customer_conversation.id
                           )
                        """)
                .fetch()
                .rowsUpdated();
    }
}
