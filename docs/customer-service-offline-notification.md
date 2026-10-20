# 客服离线邮件与 Webhook 提醒

飞鱼小铺在没有管理员客服 WebSocket 在线时，会向客户写入一条离线自动回复，并按配置向管理员
发送邮件和 Webhook 提醒。离线自动回复与外部提醒共享数据库冷却记录，因此应用重启后也不会因
客户连续发送消息而重复轰炸。

## 触发规则

- 仅处理站内客服会话中的客户入站消息。
- 有任意管理员客服 WebSocket 在线时，不发送自动回复、邮件或 Webhook。
- 同一会话在冷却周期内只触发一次；默认冷却周期为 8 小时。
- 邮件和 Webhook 独立启停，单个通道失败不会影响客户消息落库、自动回复或另一通道。
- 通知仅包含展示名、用户 ID、会话 ID、消息类型和最多 500 字的正文摘要，不包含原始报文、附件
  地址、登录凭据或支付信息。

邮件与 Webhook 中的处理链接会打开管理员工作台并直接展开对应客户会话。查询参数在消费后会
被前端清除，刷新页面不会重复弹出。

## 环境变量

以下配置写入部署环境文件，例如 `/opt/flyfish-shop/config/flyfish-shop.env`。不要把真实邮箱授权码、Webhook
密钥或包含访问令牌的 URL 写入 Git。

```bash
# 总开关和同一会话冷却周期
CUSTOMER_OFFLINE_NOTIFICATION_ENABLED=true
CUSTOMER_OFFLINE_NOTIFICATION_COOLDOWN=8h
CUSTOMER_OFFLINE_NOTIFICATION_PORTAL_BASE_URL=https://shop.example.com

# 邮件通道；默认收件人会回退到既有工单管理员收件人
CUSTOMER_OFFLINE_NOTIFICATION_MAIL_ENABLED=true
CUSTOMER_OFFLINE_NOTIFICATION_MAIL_FROM=service@example.com
CUSTOMER_OFFLINE_NOTIFICATION_MAIL_RECIPIENTS=admin@example.com,backup@example.com

# Webhook 通道；多个 HTTPS 地址使用英文逗号分隔
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_ENABLED=true
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_URLS=https://hooks.example.com/flyfish
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_SECRET=replace-with-a-random-secret
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_TIMEOUT=5s
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_MAX_RETRIES=1
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_RETRY_BACKOFF=500ms

# 仅本地 HTTP 接收器联调时临时开启，生产保持 false
CUSTOMER_OFFLINE_NOTIFICATION_WEBHOOK_ALLOW_HTTP=false
```

邮件仍使用 Spring Mail 的 `spring.mail.*` 或项目既有 SMTP 环境变量。Webhook 未配置地址时即使
总开关开启也会安全跳过。

## Webhook 协议

请求方法为 `POST`，`Content-Type` 为 `application/json`。事件正文示例：

```json
{
  "event": "customer.service.offline_message",
  "eventId": "customer-message-1288",
  "occurredAt": "2026-08-16T09:30:00+08:00",
  "conversationId": 36,
  "userId": 1118,
  "customerName": "追着灵感的云小鹿",
  "channel": "WEB",
  "messageType": "text",
  "messagePreview": "付款后还没有收到授权，请帮我看一下。",
  "conversationUrl": "https://shop.example.com/shop/manage/workbench?customerConversation=36"
}
```

请求头：

- `X-Flyfish-Event`：固定为 `customer.service.offline_message`。
- `X-Flyfish-Delivery`：稳定事件编号，可用于接收方幂等。
- `X-Flyfish-Timestamp`：发送时的 Unix 秒时间戳。
- `X-Flyfish-Signature`：配置密钥时发送，格式为 `sha256=<hex>`。

签名原文是 `timestamp + "." + rawBody`，算法是 HMAC-SHA256。接收方应使用原始请求字节验签、
限制时间戳误差（建议 5 分钟），并按 `X-Flyfish-Delivery` 去重。返回任意 2xx 表示接收成功；
网络错误、超时、429 和 5xx 会按配置重试，其他 4xx 不重试。

## 上线验证

```bash
./mvnw -pl flyfish-shop/flyfish-shop-app -am test
FLYFISH_NATIVE_APPS=shop ./build.sh
```

部署后保持管理员客服离线，使用测试用户发送一条新消息，确认：

1. 客户端出现一条离线自动回复。
2. 管理员邮箱收到包含客户摘要和处理入口的邮件。
3. Webhook 接收器收到事件，签名和事件编号校验通过。
4. 冷却周期内再次发送消息不会重复提醒。
5. 管理员打开客服 WebSocket 后发送消息，不再触发离线提醒。
