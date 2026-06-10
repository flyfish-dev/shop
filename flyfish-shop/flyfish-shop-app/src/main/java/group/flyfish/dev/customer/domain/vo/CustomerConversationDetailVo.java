package group.flyfish.dev.customer.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class CustomerConversationDetailVo {

    /**
     * 当前会话基础信息。
     */
    private CustomerConversationVo conversation;

    /**
     * 当前会话下按时间正序排列的消息列表。
     */
    private List<CustomerMessageVo> messages;

    public CustomerConversationDetailVo(CustomerConversationVo conversation, List<CustomerMessageVo> messages) {
        this.conversation = conversation;
        this.messages = messages == null ? List.of() : messages;
    }
}
