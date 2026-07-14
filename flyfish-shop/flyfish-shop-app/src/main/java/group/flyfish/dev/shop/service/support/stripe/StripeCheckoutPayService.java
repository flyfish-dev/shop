package group.flyfish.dev.shop.service.support.stripe;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.service.PayService;
import group.flyfish.dev.shop.service.support.stripe.bean.StripeCheckoutSessionDto;
import group.flyfish.dev.shop.service.support.stripe.config.StripePaymentProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@RequiredArgsConstructor
public class StripeCheckoutPayService implements PayService {

    private static final String PROVIDER = "stripe";
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg", "rwf",
            "ugx", "vnd", "vuv", "xaf", "xof", "xpf"
    );
    private static final Set<String> PAYMENT_RETURN_PARAMS = Set.of(
            "payment", "payment_status", "order_no", "session_id"
    );

    private final StripeCheckoutClient stripeCheckoutClient;
    private final StripePaymentProperties properties;

    @Override
    public String providerCode() {
        return PROVIDER;
    }

    @Override
    public Mono<ShopOrderPaymentVo> pay(ShopOrder order, ShopItem item, ShopOrderDto request) {
        validateConfig();
        String currency = StringUtils.defaultIfBlank(StringUtils.trimToNull(order.getCurrency()),
                properties.normalizedCurrency()).toLowerCase(Locale.ROOT);
        long amountMinor = toMinor(order.getAmount(), currency);
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(properties.normalizedCheckoutExpiresMinutes());
        MultiValueMap<String, String> form = buildCheckoutForm(order, item, request, currency, amountMinor, expireTime);
        return stripeCheckoutClient.createSession(properties.authorizationHeader(),
                        "flyfish:stripe:checkout:" + order.getOrderNo(), form)
                .onErrorMap(WebClientResponseException.class, this::toStripeHttpError)
                .map(StripeCheckoutSessionDto::from)
                .map(session -> convert(session, expireTime));
    }

    private MultiValueMap<String, String> buildCheckoutForm(ShopOrder order, ShopItem item, ShopOrderDto request,
                                                            String currency, long amountMinor,
                                                            LocalDateTime expireTime) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("client_reference_id", order.getOrderNo());
        form.add("success_url", buildReturnUrl(order, request, true));
        form.add("cancel_url", buildReturnUrl(order, request, false));
        form.add("automatic_tax[enabled]", String.valueOf(properties.isAutomaticTaxEnabled()));
        form.add("expires_at", String.valueOf(expireTime.atZone(ZoneId.systemDefault()).toEpochSecond()));
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(amountMinor));
        form.add("line_items[0][price_data][product_data][name]", productName(order, item));
        putMetadata(form, "flyfish_order_no", order.getOrderNo());
        putMetadata(form, "flyfish_order_id", String.valueOf(order.getId()));
        putMetadata(form, "flyfish_item_id", String.valueOf(order.getItemId()));
        putMetadata(form, "flyfish_currency", currency);
        putPaymentIntentMetadata(form, "flyfish_order_no", order.getOrderNo());
        putPaymentIntentMetadata(form, "flyfish_order_id", String.valueOf(order.getId()));
        putPaymentIntentMetadata(form, "flyfish_item_id", String.valueOf(order.getItemId()));
        putPaymentIntentMetadata(form, "flyfish_currency", currency);
        String locale = normalizeStripeLocale(request == null ? null : request.getPaymentLocale());
        if (StringUtils.isNotBlank(locale)) {
            form.add("locale", locale);
        }
        return form;
    }

    private String buildReturnUrl(ShopOrder order, ShopOrderDto request, boolean success) {
        String path = normalizeReturnPath(request == null ? null : request.getPaymentReturnPath());
        String separator = path.contains("?") ? "&" : "?";
        String status = success ? "success" : "cancel";
        String url = properties.normalizedPortalBaseUrl() + path + separator
                + "payment=stripe&payment_status=" + status
                + "&order_no=" + encode(order.getOrderNo());
        return success ? url + "&session_id={CHECKOUT_SESSION_ID}" : url;
    }

    private String normalizeReturnPath(String value) {
        String path = StringUtils.defaultIfBlank(StringUtils.trimToNull(value), "/account/orders");
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        if (!path.startsWith("/") || path.startsWith("//")) {
            return "/account/orders";
        }
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(path);
            PAYMENT_RETURN_PARAMS.forEach(builder::replaceQueryParam);
            return builder.build(true).toUriString();
        } catch (IllegalArgumentException e) {
            return "/account/orders";
        }
    }

    private String normalizeStripeLocale(String value) {
        String locale = StringUtils.trimToEmpty(value).replace('_', '-').toLowerCase(Locale.ROOT);
        if (locale.startsWith("zh")) {
            return "zh";
        }
        if (locale.startsWith("en")) {
            return "en";
        }
        return "";
    }

    private void putMetadata(MultiValueMap<String, String> form, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            form.add("metadata[" + key + "]", value);
        }
    }

    private void putPaymentIntentMetadata(MultiValueMap<String, String> form, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            form.add("payment_intent_data[metadata][" + key + "]", value);
        }
    }

    private ShopOrderPaymentVo convert(StripeCheckoutSessionDto session, LocalDateTime fallbackExpireTime) {
        if (session == null || StringUtils.isBlank(session.getId()) || StringUtils.isBlank(session.getUrl())) {
            throw new ServiceException("Stripe未返回可用的 Checkout Session");
        }
        ShopOrderPaymentVo vo = new ShopOrderPaymentVo();
        vo.setProvider(PROVIDER);
        vo.setTradeType("checkout");
        vo.setPayType(PROVIDER);
        vo.setTradeNo(session.getId());
        vo.setJumpUrl(session.getUrl());
        vo.setExpireTime(session.getExpiresAt() == null
                ? fallbackExpireTime
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(session.getExpiresAt()), ZoneId.systemDefault()));
        return vo;
    }

    private String productName(ShopOrder order, ShopItem item) {
        String name = StringUtils.defaultIfBlank(order.getItemName(), item == null ? null : item.getName());
        if (StringUtils.isNotBlank(order.getSkuName())) {
            name = StringUtils.defaultIfBlank(name, "飞鱼小铺商品") + " - " + order.getSkuName();
        }
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(name, "飞鱼小铺商品"), 120);
    }

    private long toMinor(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("Stripe支付金额必须大于0");
        }
        int scale = ZERO_DECIMAL_CURRENCIES.contains(currency) ? 0 : 2;
        BigDecimal multiplier = scale == 0 ? BigDecimal.ONE : BigDecimal.valueOf(100);
        return amount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private void validateConfig() {
        if (!properties.isCheckoutConfigured()) {
            throw new ServiceException("Stripe支付配置不完整，请配置 shop.payment.stripe.secret-key");
        }
    }

    private ServiceException toStripeHttpError(WebClientResponseException e) {
        String message = e.getResponseBodyAsString();
        try {
            message = JacksonUtils.readTree(message).path("error").path("message").asText(message);
        } catch (Exception ignore) {
            // Keep the original provider response text.
        }
        return new ServiceException("Stripe支付发起失败：" + StringUtils.defaultIfBlank(message, e.getStatusText()));
    }

    private String encode(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }
}
