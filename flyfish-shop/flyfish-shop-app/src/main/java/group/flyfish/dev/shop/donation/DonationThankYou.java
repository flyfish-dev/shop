package group.flyfish.dev.shop.donation;

import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** 打赏感谢通知的持久化任务，不保存收件邮箱或授权信息。 */
@Getter
@Setter
@Table("shop_donation_thank_you")
public class DonationThankYou extends AuditDomain {

    /** 打赏订单号，同时作为通知的唯一幂等键。 */
    private String orderNo;

    /** 任务状态：PENDING、PROCESSING、SENT、TICKET、SKIPPED。 */
    private String state;

    /** 已领取的处理次数。 */
    private Integer attempts;

    /** 下次重试时间；处理中表示租约失效时间。 */
    private LocalDateTime nextAttemptTime;

    /** 本次处理租约标识，防止旧任务覆盖新结果。 */
    private String claimToken;

    /** 最近一次错误类别，不记录邮箱、SMTP 凭据或错误响应正文。 */
    private String lastError;

    /** 转人工感谢时关联的工单编号。 */
    private String ticketNo;

    /** 邮件发送或人工跟进工单创建完成时间。 */
    private LocalDateTime completedTime;
}
