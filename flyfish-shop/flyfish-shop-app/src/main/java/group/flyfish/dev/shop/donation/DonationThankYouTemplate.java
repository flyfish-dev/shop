package group.flyfish.dev.shop.donation;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.RoundingMode;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 感谢邮件的中英文模板。所有动态内容在进入 HTML 前统一转义。 */
public final class DonationThankYouTemplate {

    private DonationThankYouTemplate() {
    }

    public static Message render(ShopOrder order, PortalUserVo user, String portalBaseUrl) {
        boolean english = english(order);
        String nickname = StringUtils.defaultIfBlank(StringUtils.trimToNull(user.getUsername()),
                english ? "Flyfish supporter" : "飞鱼的支持者");
        String item = StringUtils.defaultIfBlank(order.getItemName(), english ? "Support Flyfish" : "支持飞鱼");
        String amount = StringUtils.defaultIfBlank(order.getCurrency(), "CNY").toUpperCase(Locale.ROOT)
                + " " + order.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String paid = order.getPaidTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String title = english ? "Thank you for supporting Flyfish" : "感谢你对飞鱼的支持";
        String greeting = english ? "Hi " + nickname + "," : nickname + "，你好：";
        String intro = english
                ? "We've received your contribution. Thank you for supporting Flyfish and the work behind it."
                : "你的打赏已收到。谢谢你愿意支持飞鱼，也谢谢你对我们持续开发与维护的认可。";
        String appreciation = english
                ? "Your encouragement means a great deal to us and helps us keep improving our tools. We hope they continue to make your work a little easier."
                : "每一份支持都很珍贵，也让我们更有动力把工具打磨好。希望飞鱼能继续为你的工作带来便利。";
        String closing = english ? "With gratitude,\nThe Flyfish team" : "再次感谢你的心意！\n飞鱼团队";
        String action = english ? "View my orders" : "查看我的订单";
        String url = orderUrl(portalBaseUrl);
        String[] labels = english ? new String[]{"Contribution", "Amount received", "Order", "Paid at"}
                : new String[]{"支持项目", "实付金额", "订单编号", "支付时间"};
        String[] values = {item, amount, order.getOrderNo(), paid};
        StringBuilder rows = new StringBuilder();
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            details.append(labels[i]).append(": ").append(values[i]).append('\n');
            rows.append("<tr><td style=\"padding:8px 12px 8px 0;color:#667085;vertical-align:top\">")
                    .append(escape(labels[i])).append("</td><td style=\"padding:8px 0;overflow-wrap:anywhere\">")
                    .append(escape(values[i])).append("</td></tr>");
        }
        String text = String.join("\n\n", greeting, intro, appreciation, details.toString().strip(),
                action + ": " + url, closing);
        String html = """
                <!doctype html>
                <html lang="%s"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;background:#f5f7fa;color:#253342;font:16px/1.7 Arial,sans-serif">
                <div style="max-width:600px;margin:24px auto;background:#fff;border-top:4px solid #248451;padding:28px 24px;box-sizing:border-box;overflow-wrap:anywhere">
                  <div style="font-size:14px;color:#248451;font-weight:700">FLYFISH</div>
                  <h1 style="font-size:24px;line-height:1.4;margin:12px 0 24px">%s</h1>
                  <p>%s</p><p>%s</p><p>%s</p>
                  <table role="presentation" style="width:100%%;border-top:1px solid #e6ebef;border-bottom:1px solid #e6ebef;margin:24px 0;font-size:14px;border-collapse:collapse">%s</table>
                  <p><a href="%s" style="display:inline-block;padding:10px 22px;background:#248451;border-radius:6px;color:#fff;text-decoration:none">%s</a></p>
                  <p style="margin-top:28px">%s</p>
                </div></body></html>
                """.formatted(english ? "en" : "zh-CN", escape(title), escape(greeting), escape(intro),
                escape(appreciation), rows, escape(url), escape(action), escape(closing).replace("\n", "<br>"));
        return new Message(title + " | Flyfish", text, html);
    }

    static boolean english(ShopOrder order) {
        if (StringUtils.isBlank(order.getProperties())) {
            return false;
        }
        try {
            return JacksonUtils.readTree(order.getProperties()).path("paymentLocale").asText("")
                    .toLowerCase(Locale.ROOT).startsWith("en");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String orderUrl(String baseUrl) {
        URI base = URI.create(StringUtils.defaultIfBlank(baseUrl, "https://shop.example.com"));
        if (!("https".equals(base.getScheme()) || "http".equals(base.getScheme())) || base.getHost() == null) {
            throw new IllegalArgumentException("Invalid donation thank-you portal URL");
        }
        return base.resolve("/account/orders").toString();
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(StringUtils.defaultString(value), "UTF-8");
    }

    public record Message(String subject, String text, String html) {
    }
}
