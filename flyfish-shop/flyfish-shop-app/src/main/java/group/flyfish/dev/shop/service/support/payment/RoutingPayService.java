package group.flyfish.dev.shop.service.support.payment;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.service.PayService;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按订单请求选择具体支付通道。
 */
public class RoutingPayService implements PayService {

    private static final String PROVIDER = "routing";
    private static final String DEFAULT_PROVIDER = "h5zhifu";

    private final Map<String, PayService> providers;

    public RoutingPayService(List<PayService> providers) {
        this.providers = providers.stream()
                .filter(provider -> !PROVIDER.equals(provider.providerCode()))
                .collect(Collectors.toUnmodifiableMap(PayService::providerCode, Function.identity()));
    }

    @Override
    public String providerCode() {
        return PROVIDER;
    }

    @Override
    public Mono<ShopOrderPaymentVo> pay(ShopOrder order, ShopItem item, ShopOrderDto request) {
        String providerCode = resolveProvider(request);
        PayService provider = providers.get(providerCode);
        if (provider == null) {
            throw new ServiceException("暂不支持该支付方式");
        }
        return provider.pay(order, item, request);
    }

    private String resolveProvider(ShopOrderDto request) {
        String provider = normalize(request == null ? null : request.getPaymentProvider());
        String payType = normalize(request == null ? null : request.getPayType());
        if (StringUtils.isBlank(provider) && "stripe".equals(payType)) {
            provider = "stripe";
        }
        if (StringUtils.isBlank(provider)
                || "wechat".equals(provider)
                || "alipay".equals(provider)
                || "native".equals(provider)
                || "h5".equals(provider)
                || "jsapi".equals(provider)) {
            provider = DEFAULT_PROVIDER;
        }
        return provider;
    }

    private String normalize(String value) {
        return StringUtils.trimToEmpty(value).replace('-', '_').toLowerCase(Locale.ROOT);
    }
}
