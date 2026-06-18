package group.flyfish.dev.support.domain.vo;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.support.domain.po.SupportTicket;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class SupportTicketDetailVo extends SupportTicketVo {

    private List<SupportTicketMessageVo> messages;

    public SupportTicketDetailVo(SupportTicket ticket, PortalUserVo creator, List<SupportTicketMessageVo> messages) {
        this(ticket, creator, messages, false);
    }

    public SupportTicketDetailVo(SupportTicket ticket, PortalUserVo creator, List<SupportTicketMessageVo> messages,
                                 boolean managerView) {
        super(ticket, creator, managerView);
        this.messages = messages == null ? List.of() : messages;
    }
}
