package group.flyfish.dev.customer.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 客服离线邮件提醒发送器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerOfflineMailNotifier {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final CustomerOfflineNotificationProperties properties;

    public Mono<Void> send(CustomerOfflineNotification notification) {
        List<String> recipients = normalizeEmails(properties.getMail().getRecipients());
        if (!properties.isEnabled() || !properties.getMail().isEnabled() || recipients.isEmpty()) {
            return Mono.empty();
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("未配置 Spring Mail，跳过客服离线邮件提醒。eventId={}",
                    notification.payload().eventId());
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> mailSender.send(toMail(recipients, notification)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(ignored -> log.info("客服离线邮件提醒发送成功。eventId={}, recipientCount={}",
                        notification.payload().eventId(), recipients.size()))
                .onErrorResume(error -> {
                    log.warn("客服离线邮件提醒发送失败，已降级跳过。eventId={}, error={}",
                            notification.payload().eventId(), error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    List<String> normalizeEmails(Collection<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return emails.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .filter(email -> EMAIL_PATTERN.matcher(email).matches())
                .distinct()
                .toList();
    }

    private SimpleMailMessage toMail(List<String> recipients, CustomerOfflineNotification notification) {
        SimpleMailMessage mail = new SimpleMailMessage();
        if (StringUtils.isNotBlank(properties.getMail().getFrom())) {
            mail.setFrom(properties.getMail().getFrom().trim());
        }
        mail.setTo(recipients.toArray(String[]::new));
        mail.setSubject(notification.subject());
        mail.setText(notification.mailBody());
        return mail;
    }
}
