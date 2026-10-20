package group.flyfish.dev.customer.notification;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

/**
 * 客服离线提醒 Webhook HTTP Interface。
 */
public interface CustomerOfflineWebhookClient {

    /**
     * 向动态配置的 Webhook 地址发送事件。
     *
     * @param uri     完整 Webhook 地址
     * @param headers 事件、投递编号、时间戳和可选签名
     * @param body    已序列化的 JSON 原始字节，确保签名正文与实际请求完全一致
     * @return 完成信号
     */
    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    Mono<Void> send(URI uri, @RequestHeader Map<String, String> headers, @RequestBody byte[] body);
}
