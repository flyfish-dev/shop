package group.flyfish.dev.support.domain.vo;

import group.flyfish.dev.support.domain.po.SupportTicket;
import group.flyfish.dev.user.domain.po.PortalUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SupportTicketDetailVo extends SupportTicketVo {

    private List<SupportTicketMessageVo> messages;

    public SupportTicketDetailVo(SupportTicket ticket, PortalUser creator, List<SupportTicketMessageVo> messages) {
        this(ticket, creator, messages, false);
    }

    public SupportTicketDetailVo(SupportTicket ticket, PortalUser creator, List<SupportTicketMessageVo> messages,
                                 boolean managerView) {
        super(ticket, creator, managerView);
        this.messages = messages == null ? List.of() : messages;
    }
}
