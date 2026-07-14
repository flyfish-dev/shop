package group.flyfish.dev.shop.service.support.payment;

import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties;
import group.flyfish.dev.shop.service.support.stripe.config.StripePaymentProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMethodAvailabilityServiceTest {

    @Test
    void exposesOnlyConfiguredMerchantMethods() {
        H5ZhiFuProperties h5ZhiFu = configuredH5ZhiFu();
        StripePaymentProperties stripe = configuredStripe();
        PaymentMethodAvailabilityService service = new PaymentMethodAvailabilityService(h5ZhiFu, stripe);

        assertEquals(List.of("h5zhifu-wechat", "stripe"), service.getAvailableMethods());
    }

    @Test
    void exposesAlipayOnlyAfterItIsExplicitlyEnabled() {
        H5ZhiFuProperties h5ZhiFu = configuredH5ZhiFu();
        h5ZhiFu.setEnabledPayTypes(Set.of("wechat", "alipay"));
        StripePaymentProperties stripe = configuredStripe();
        PaymentMethodAvailabilityService service = new PaymentMethodAvailabilityService(h5ZhiFu, stripe);

        assertEquals(List.of("h5zhifu-wechat", "h5zhifu-alipay", "stripe"), service.getAvailableMethods());
    }

    @Test
    void hidesStripeUntilWebhookIsConfigured() {
        H5ZhiFuProperties h5ZhiFu = configuredH5ZhiFu();
        StripePaymentProperties stripe = configuredStripe();
        stripe.setWebhookSecret(null);
        PaymentMethodAvailabilityService service = new PaymentMethodAvailabilityService(h5ZhiFu, stripe);

        assertEquals(List.of("h5zhifu-wechat"), service.getAvailableMethods());
    }

    private H5ZhiFuProperties configuredH5ZhiFu() {
        H5ZhiFuProperties properties = new H5ZhiFuProperties();
        properties.setAppId(1000000001L);
        properties.setKey("test-secret");
        properties.setNotifyUrl("https://api.flyfish.group/shops/payments/h5zhifu/notify");
        return properties;
    }

    private StripePaymentProperties configuredStripe() {
        StripePaymentProperties properties = new StripePaymentProperties();
        properties.setEnabled(true);
        properties.setSecretKey("stripe-test-placeholder");
        properties.setWebhookSecret("webhook-test-placeholder");
        return properties;
    }
}
