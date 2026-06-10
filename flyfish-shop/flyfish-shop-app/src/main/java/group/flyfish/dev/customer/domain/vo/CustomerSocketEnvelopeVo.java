package group.flyfish.dev.customer.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class CustomerSocketEnvelopeVo {

    /**
     * WebSocket 下发消息类型，例如 CONNECTED、STATE、ERROR。
     */
    private String type;

    /**
     * 错误编码，仅 ERROR 类型使用。
     */
    private String code;

    /**
     * 错误提示或状态说明。
     */
    private String message;

    /**
     * 当前登录人的客服消息与工单摘要。
     */
    private CustomerServiceSummaryVo summary;

    /**
     * 管理员可见的会话列表；普通用户通常为空列表。
     */
    private List<CustomerConversationVo> conversations;

    /**
     * 当前打开的会话详情。
     */
    private CustomerConversationDetailVo detail;

    public static CustomerSocketEnvelopeVo state(CustomerServiceSummaryVo summary,
                                                 List<CustomerConversationVo> conversations,
                                                 CustomerConversationDetailVo detail) {
        CustomerSocketEnvelopeVo envelope = new CustomerSocketEnvelopeVo();
        envelope.setType("STATE");
        envelope.setSummary(summary);
        envelope.setConversations(conversations == null ? List.of() : conversations);
        envelope.setDetail(detail);
        return envelope;
    }

    public static CustomerSocketEnvelopeVo connected() {
        CustomerSocketEnvelopeVo envelope = new CustomerSocketEnvelopeVo();
        envelope.setType("CONNECTED");
        return envelope;
    }

    public static CustomerSocketEnvelopeVo error(String code, String message) {
        CustomerSocketEnvelopeVo envelope = new CustomerSocketEnvelopeVo();
        envelope.setType("ERROR");
        envelope.setCode(code);
        envelope.setMessage(message);
        return envelope;
    }
}
