package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.support.notification.SupportNotificationProperties;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DonationThankYouMailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SupportNotificationProperties properties;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    public Optional<String> recipient(PortalUserVo user) {
        String email = StringUtils.trimToNull(user.getEmail());
        if (email == null || email.contains("\r") || email.contains("\n")) {
            return Optional.empty();
        }
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            return email.equals(address.getAddress()) && email.contains("@")
                    ? Optional.of(email) : Optional.empty();
        } catch (jakarta.mail.internet.AddressException ignored) {
            return Optional.empty();
        }
    }

    public Mono<Void> send(ShopOrder order, PortalUserVo user, String email) {
        return Mono.fromCallable(() -> {
                    JavaMailSender sender = mailSenderProvider.getIfAvailable();
                    String from = StringUtils.firstNonBlank(properties.getMail().getFrom(), defaultFrom);
                    if (sender == null || StringUtils.isBlank(from)) {
                        throw new IllegalStateException("Donation thank-you mail sender is not configured");
                    }
                    var content = DonationThankYouTemplate.render(order, user, properties.getPortalBaseUrl());
                    var mail = sender.createMimeMessage();
                    var helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
                    helper.setFrom(from.trim(), "Flyfish");
                    helper.setTo(email);
                    helper.setSubject(content.subject());
                    helper.setText(content.text(), content.html());
                    sender.send(mail);
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
