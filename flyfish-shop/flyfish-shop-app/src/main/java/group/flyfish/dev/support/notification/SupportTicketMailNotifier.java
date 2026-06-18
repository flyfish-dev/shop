package group.flyfish.dev.support.notification;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportTicketMailNotifier {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final SupportNotificationProperties properties;

    public Mono<Void> send(Collection<String> recipients, SupportTicketNotificationMessage message) {
        List<String> validRecipients = normalizeEmails(recipients);
        if (!properties.isEnabled() || !properties.getMail().isEnabled()) {
            log.debug("工单邮件通知未启用，跳过发送。subject={}", message.subject());
            return Mono.empty();
        }
        if (validRecipients.isEmpty()) {
            log.info("工单邮件通知没有可用收件人，跳过发送。subject={}", message.subject());
            return Mono.empty();
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("未配置 Spring Mail，跳过工单邮件通知。请检查 spring.mail.host/username/password。subject={}",
                    message.subject());
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> mailSender.send(toMail(validRecipients, message)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(ignored -> log.info("工单邮件通知发送成功。recipients={}, subject={}",
                        validRecipients, message.subject()))
                .onErrorResume(e -> {
                    log.warn("工单邮件通知发送失败，已降级跳过。recipients={}, subject={}, error={}",
                            validRecipients, message.subject(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    public List<String> normalizeEmails(Collection<String> emails) {
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

    private SimpleMailMessage toMail(List<String> recipients, SupportTicketNotificationMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        if (StringUtils.isNotBlank(properties.getMail().getFrom())) {
            mail.setFrom(properties.getMail().getFrom().trim());
        }
        mail.setTo(recipients.toArray(String[]::new));
        mail.setSubject(message.subject());
        mail.setText(message.mailBody());
        return mail;
    }
}
