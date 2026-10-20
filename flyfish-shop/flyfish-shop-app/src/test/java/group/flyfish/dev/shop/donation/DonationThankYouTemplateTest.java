package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DonationThankYouTemplateTest {

    @Test
    void rendersPersonalizedChineseAndEnglishTemplatesAndEscapesHtml() throws Exception {
        ShopOrder order = paidOrder();
        order.setItemName("支持飞鱼 <工具>");
        PortalUserVo user = donor("supporter@example.test");
        user.setUsername("Alex <script>alert(1)</script> & 王");
        var zh = DonationThankYouTemplate.render(order, user, "https://dev.flyfish.group");
        assertThat(zh.subject()).contains("感谢");
        assertThat(zh.text()).contains(user.getUsername(), "CNY 18.88", "FFTHANKS1", "2026-10-19 10:30");
        assertThat(zh.html()).contains("&lt;script&gt;", "&lt;工具&gt;", "https://dev.flyfish.group/account/orders")
                .doesNotContain("<script>", "null");
        order.setProperties("{\"paymentLocale\":\"en-US\"}");
        order.setCurrency("USD");
        user.setUsername("Alex");
        var en = DonationThankYouTemplate.render(order, user, "https://dev.flyfish.group/");
        assertThat(en.subject()).contains("Thank you");
        assertThat(en.text()).contains("Hi Alex,", "USD 18.88", "View my orders");
        Path previews = Path.of("target", "donation-mail-preview");
        Files.createDirectories(previews);
        Files.writeString(previews.resolve("zh.html"), DonationThankYouTemplate.render(paidOrder(),
                donor("supporter@example.test"), "https://dev.flyfish.group").html());
        Files.writeString(previews.resolve("en.html"), en.html());
    }

    @Test
    void handlesEmptyNicknameAndRejectsUnsafePortalUrls() {
        PortalUserVo user = donor(null);
        user.setUsername(" ");
        ShopOrder order = paidOrder();
        order.setProperties("invalid json");
        assertThat(DonationThankYouTemplate.render(order, user, null).text()).contains("飞鱼的支持者");
        assertThatThrownBy(() -> DonationThankYouTemplate.render(order, user, "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyPaidPositiveDonationOrdersAreEligible() {
        ShopOrder order = paidOrder();
        assertThat(DonationThankYouService.eligible(order)).isTrue();
        order.setItemType(ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.name());
        order.setStatus(ShopOrder.Status.FAILED);
        assertThat(DonationThankYouService.eligible(order)).isTrue();
        order.setItemType(ShopItem.Type.LICENSE.name());
        assertThat(DonationThankYouService.eligible(order)).isFalse();
        order.setItemType(ShopItem.Type.DONATION.name());
        order.setAmount(BigDecimal.ZERO);
        assertThat(DonationThankYouService.eligible(order)).isFalse();
        order.setAmount(BigDecimal.ONE);
        order.setStatus(ShopOrder.Status.CLOSED);
        assertThat(DonationThankYouService.eligible(order)).isFalse();
        order.setStatus(ShopOrder.Status.PAYING);
        order.setPaidTime(null);
        assertThat(DonationThankYouService.eligible(order)).isFalse();
    }

    static ShopOrder paidOrder() {
        ShopOrder order = new ShopOrder();
        order.setId(101L);
        order.setOrderNo("FFTHANKS1");
        order.setBuyerId(100L);
        order.setItemName("支持飞鱼");
        order.setItemType(ShopItem.Type.DONATION.name());
        order.setAmount(new BigDecimal("18.88"));
        order.setCurrency("CNY");
        order.setPaidTime(LocalDateTime.of(2026, 10, 19, 10, 30));
        order.setStatus(ShopOrder.Status.PAID);
        return order;
    }

    static PortalUserVo donor(String email) {
        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setUsername("小明");
        user.setEmail(email);
        return user;
    }
}
