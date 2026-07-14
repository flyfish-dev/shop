package group.flyfish.dev.shop.service.support.payment;

import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties;
import group.flyfish.dev.shop.service.support.stripe.config.StripePaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentMethodAvailabilityService {

    public static final String H5ZHIFU_WECHAT = "h5zhifu-wechat";
    public static final String H5ZHIFU_ALIPAY = "h5zhifu-alipay";
    public static final String STRIPE = "stripe";

    private final H5ZhiFuProperties h5ZhiFuProperties;
    private final StripePaymentProperties stripePaymentProperties;

    public List<String> getAvailableMethods() {
        List<String> methods = new ArrayList<>();
        if (h5ZhiFuProperties.isConfigured()) {
            if (h5ZhiFuProperties.isPayTypeEnabled("wechat")) {
                methods.add(H5ZHIFU_WECHAT);
            }
            if (h5ZhiFuProperties.isPayTypeEnabled("alipay")) {
                methods.add(H5ZHIFU_ALIPAY);
            }
        }
        if (stripePaymentProperties.isAvailable()) {
            methods.add(STRIPE);
        }
        return List.copyOf(methods);
    }
}
