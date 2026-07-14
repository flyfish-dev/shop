package group.flyfish.dev.shop.service.support.stripe.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

@Data
public class StripePaymentProperties {

    private boolean enabled = false;

    private String apiBaseUrl = "https://api.stripe.com";

    private String secretKey;

    private String webhookSecret;

    private String defaultCurrency = "cny";

    private String portalBaseUrl = "https://shop.example.com";

    private boolean automaticTaxEnabled = false;

    private int checkoutExpiresMinutes = 30;

    private String userAgent = "FlyfishDevShop/stripe-checkout";

    public String authorizationHeader() {
        return "Bearer " + StringUtils.trimToEmpty(secretKey);
    }

    public String normalizedApiBaseUrl() {
        String url = StringUtils.defaultIfBlank(StringUtils.trimToNull(apiBaseUrl), "https://api.stripe.com");
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public String normalizedCurrency() {
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(defaultCurrency), "cny").toLowerCase(Locale.ROOT);
    }

    public String normalizedPortalBaseUrl() {
        String url = StringUtils.defaultIfBlank(StringUtils.trimToNull(portalBaseUrl), "https://shop.example.com");
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public int normalizedCheckoutExpiresMinutes() {
        return Math.max(30, checkoutExpiresMinutes);
    }

    public boolean isCheckoutConfigured() {
        return enabled && StringUtils.isNotBlank(secretKey);
    }

    public boolean isAvailable() {
        return isCheckoutConfigured() && StringUtils.isNotBlank(webhookSecret);
    }
}
