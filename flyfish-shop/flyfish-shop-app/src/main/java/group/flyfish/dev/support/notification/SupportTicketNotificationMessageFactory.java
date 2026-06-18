package group.flyfish.dev.support.notification;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.support.domain.po.SupportTicketMessage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportTicketNotificationMessageFactory {

    private static final int MAIL_CONTENT_MAX_LENGTH = 1200;
    private static final int WECHAT_CONTENT_MAX_LENGTH = 420;

    private final SupportNotificationProperties properties;

    public SupportTicketNotificationMessage userTicketCreated(SupportTicket ticket, PortalUserVo creator) {
        String subject = subject("工单已提交", ticket);
        String body = """
                您好，%s：

                您提交的工单已进入飞鱼小铺客服处理队列，我们会尽快跟进。

                工单编号：%s
                工单标题：%s
                当前状态：%s
                优先级：%s

                您可以在「我的工单」中查看处理进度：
                %s

                感谢您的反馈。
                飞鱼小铺客服
                """.formatted(creatorName(creator, ticket.getCreatorId()), ticket.getTicketNo(),
                safe(ticket.getTitle()), statusText(ticket.getStatus()), priorityText(ticket.getPriority()),
                userTicketUrl());
        String wechat = """
                您的工单已提交
                编号：%s
                标题：%s
                状态：%s
                查看：%s
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()), statusText(ticket.getStatus()),
                userTicketUrl());
        return new SupportTicketNotificationMessage(subject, body, clamp(wechat, WECHAT_CONTENT_MAX_LENGTH));
    }

    public SupportTicketNotificationMessage adminTicketCreated(SupportTicket ticket, SupportTicketMessage message,
                                                               PortalUserVo creator) {
        String subject = subject("新工单待处理", ticket);
        String body = """
                有新的客户工单需要处理。

                工单编号：%s
                工单标题：%s
                客户：%s
                联系方式：%s
                分类：%s
                优先级：%s
                当前状态：%s

                客户描述：
                %s

                管理入口：
                %s
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()), creatorName(creator, ticket.getCreatorId()),
                safe(ticket.getContact()), categoryText(ticket.getCategory()), priorityText(ticket.getPriority()),
                statusText(ticket.getStatus()), clamp(safe(message.getContent()), MAIL_CONTENT_MAX_LENGTH),
                adminTicketUrl());
        String wechat = """
                新工单待处理
                编号：%s
                标题：%s
                客户：%s
                优先级：%s
                内容：%s
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()), creatorName(creator, ticket.getCreatorId()),
                priorityText(ticket.getPriority()), clamp(safe(message.getContent()), 120));
        return new SupportTicketNotificationMessage(subject, body, clamp(wechat, WECHAT_CONTENT_MAX_LENGTH));
    }

    public SupportTicketNotificationMessage adminUserReplied(SupportTicket ticket, SupportTicketMessage message,
                                                             PortalUserVo creator) {
        String subject = subject("客户补充了工单信息", ticket);
        String body = """
                客户补充了工单信息，请及时查看。

                工单编号：%s
                工单标题：%s
                客户：%s
                当前状态：%s

                最新回复：
                %s

                管理入口：
                %s
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()), creatorName(creator, ticket.getCreatorId()),
                statusText(ticket.getStatus()), clamp(safe(message.getContent()), MAIL_CONTENT_MAX_LENGTH),
                adminTicketUrl());
        String wechat = """
                客户补充了工单信息
                编号：%s
                标题：%s
                内容：%s
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()), clamp(safe(message.getContent()), 160));
        return new SupportTicketNotificationMessage(subject, body, clamp(wechat, WECHAT_CONTENT_MAX_LENGTH));
    }

    public SupportTicketNotificationMessage userAdminReplied(SupportTicket ticket, SupportTicketMessage message,
                                                             PortalUserVo creator) {
        String subject = subject("工单有新的客服回复", ticket);
        String body = """
                您好，%s：

                您的工单有新的客服回复，请查看并确认处理结果。

                工单编号：%s
                工单标题：%s
                当前状态：%s

                客服回复：
                %s

                查看工单：
                %s

                飞鱼小铺客服
                """.formatted(creatorName(creator, ticket.getCreatorId()), ticket.getTicketNo(), safe(ticket.getTitle()),
                statusText(ticket.getStatus()), clamp(safe(message.getContent()), MAIL_CONTENT_MAX_LENGTH),
                userTicketUrl());
        String wechat = """
                您的工单有新的客服回复
                编号：%s
                标题：%s
                回复：%s
                查看：%s
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()), clamp(safe(message.getContent()), 120),
                userTicketUrl());
        return new SupportTicketNotificationMessage(subject, body, clamp(wechat, WECHAT_CONTENT_MAX_LENGTH));
    }

    public SupportTicketNotificationMessage userTicketResolved(SupportTicket ticket, PortalUserVo creator) {
        String subject = subject("工单已标记解决", ticket);
        String body = """
                您好，%s：

                您的工单已标记解决。

                工单编号：%s
                工单标题：%s
                当前状态：%s

                如问题仍未完全解决，您可以在「我的工单」中继续补充信息。
                %s

                飞鱼小铺客服
                """.formatted(creatorName(creator, ticket.getCreatorId()), ticket.getTicketNo(), safe(ticket.getTitle()),
                statusText(ticket.getStatus()), userTicketUrl());
        String wechat = """
                您的工单已标记解决
                编号：%s
                标题：%s
                如需继续沟通，可在我的工单中补充信息。
                """.formatted(ticket.getTicketNo(), safe(ticket.getTitle()));
        return new SupportTicketNotificationMessage(subject, body, clamp(wechat, WECHAT_CONTENT_MAX_LENGTH));
    }

    private String subject(String action, SupportTicket ticket) {
        return "【飞鱼小铺】%s - %s".formatted(action, ticket.getTicketNo());
    }

    private String userTicketUrl() {
        return baseUrl() + "/account/tickets";
    }

    private String adminTicketUrl() {
        return baseUrl() + "/shop/manage/tickets";
    }

    private String baseUrl() {
        return StringUtils.removeEnd(StringUtils.defaultIfBlank(properties.getPortalBaseUrl(),
                "https://shop.example.com"), "/");
    }

    private String creatorName(PortalUserVo creator, Long fallbackId) {
        if (creator == null) {
            return fallbackId == null ? "用户" : "用户 " + fallbackId;
        }
        return StringUtils.defaultIfBlank(creator.getUsername(), "用户 " + creator.getId());
    }

    private String categoryText(String category) {
        return switch (StringUtils.defaultString(category).toUpperCase()) {
            case "DELIVERY" -> "交付/开通";
            case "PAYMENT" -> "支付/订单";
            case "ACCOUNT" -> "账号/登录";
            case "BUG" -> "功能异常";
            default -> "常规问题";
        };
    }

    private String priorityText(String priority) {
        return switch (StringUtils.defaultString(priority).toUpperCase()) {
            case "LOW" -> "低";
            case "HIGH" -> "高";
            case "URGENT" -> "紧急";
            default -> "普通";
        };
    }

    private String statusText(String status) {
        return switch (StringUtils.defaultString(status).toUpperCase()) {
            case "OPEN" -> "待处理";
            case "PROCESSING" -> "处理中";
            case "WAITING_USER" -> "等待用户确认";
            case "RESOLVED" -> "已解决";
            case "CLOSED" -> "已关闭";
            default -> "未知";
        };
    }

    private String safe(String value) {
        return StringUtils.defaultIfBlank(value, "-");
    }

    private String clamp(String value, int maxLength) {
        String text = safe(value).trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
