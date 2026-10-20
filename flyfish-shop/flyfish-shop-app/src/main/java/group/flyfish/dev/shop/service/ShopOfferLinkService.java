package group.flyfish.dev.shop.service;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.config.ShopOfferLinkProperties;
import group.flyfish.dev.shop.domain.dto.ShopOfferLinkCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopOfferLinkPreviewDto;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import group.flyfish.dev.shop.domain.vo.ShopOfferLinkCreateVo;
import group.flyfish.dev.shop.domain.vo.ShopOfferLinkPreviewVo;
import group.flyfish.dev.shop.domain.vo.ShopOfferLinkVo;
import group.flyfish.dev.shop.pricing.ShopPricingService;
import group.flyfish.dev.shop.repository.ShopCouponRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopOfferLinkRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 限时一次性专享购买链接服务。
 *
 * <p>链接在订单创建时通过条件更新完成预约，支付成功后再核销。未支付订单关闭后释放预约，
 * 因此同一时刻只有一个订单能使用该链接，同时支付失败也不会永久浪费客户的专享权益。</p>
 */
@Service
@RequiredArgsConstructor
public class ShopOfferLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final String CURRENCY_CNY = "CNY";
    private static final String CURRENCY_USD = "USD";

    private final ShopOfferLinkRepository offerLinkRepository;
    private final ShopCouponRepository couponRepository;
    private final ShopItemRepository itemRepository;
    private final ShopItemSkuRepository skuRepository;
    private final ShopPricingService pricingService;
    private final ShopOfferLinkProperties properties;
    private final AuthUserGateway authUserGateway;

    public Flux<ShopOfferLinkVo> list(Long itemId) {
        return offerLinkRepository.findAllByItemId(itemId).map(ShopOfferLinkVo::new);
    }

    @Transactional
    public Mono<ShopOfferLinkCreateVo> create(Long itemId, ShopOfferLinkCreateDto dto) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(normalizeValidDays(dto.getValidDays()));
        return resolveProduct(itemId, dto.getSkuId())
                .flatMap(product -> validateCreateRequest(dto, product)
                        .then(resolveRecipient(dto))
                        .flatMap(recipient -> isExternalFulfillment(dto)
                                ? saveOfferLink(dto, product, null, recipient, expiresAt)
                                : createBackingCoupon(dto, product, now, expiresAt)
                                        .flatMap(coupon -> saveOfferLink(
                                                dto, product, coupon, recipient, expiresAt))));
    }

    public Mono<ShopOfferLinkPreviewVo> preview(ShopOfferLinkPreviewDto dto) {
        return preview(dto, null);
    }

    /**
     * 预览专享链接。访客可以先看到链接用途并完成登录；登录后必须按当前用户再次校验，
     * 防止绑定到指定客户的交付链接被转发后由其他账号使用。
     */
    public Mono<ShopOfferLinkPreviewVo> preview(ShopOfferLinkPreviewDto dto, Long buyerId) {
        return resolveActive(dto.getToken(), dto.getItemId(), dto.getSkuId(), buyerId)
                .flatMap(link -> resolveProduct(dto.getItemId(), dto.getSkuId())
                        .map(product -> {
                            String currency = resolveSettlementCurrency(link, dto.getPaymentCurrency());
                            BigDecimal original = purposeOf(link) == ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT
                                    ? link.getSettlementAmount()
                                    : originalAmount(product, dto.getDonationAmount(), currency);
                            CouponDiscount discount = calculateDiscount(link, original, currency);
                            return new ShopOfferLinkPreviewVo(link, discount, currency);
                        }));
    }

    public Mono<OfferLinkDiscount> apply(String token, BigDecimal originalAmount, Long itemId, Long skuId,
                                         String currency, Long buyerId) {
        return resolveActive(token, itemId, skuId, buyerId)
                .map(link -> {
                    String settlementCurrency = resolveSettlementCurrency(link, currency);
                    CouponDiscount discount = calculateDiscount(link, money(originalAmount), settlementCurrency);
                    return new OfferLinkDiscount(
                            link.getId(),
                            discount,
                            purposeOf(link),
                            settlementCurrency,
                            link.getContractReference(),
                            link.getPaymentReference(),
                            link.getConfirmationNote(),
                            link.getCreateBy()
                    );
                });
    }

    public Mono<Void> reserve(Long offerLinkId, String orderNo, Long buyerId) {
        if (offerLinkId == null) {
            return Mono.empty();
        }
        return offerLinkRepository.reserve(offerLinkId, orderNo, buyerId, LocalDateTime.now())
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new BusinessException("OFFER_LINK_UNAVAILABLE", "专享购买链接已被使用或已失效")));
    }

    public Mono<Void> consume(Long offerLinkId, String orderNo, Long buyerId) {
        if (offerLinkId == null) {
            return Mono.empty();
        }
        return offerLinkRepository.consume(offerLinkId, orderNo, buyerId, LocalDateTime.now())
                .flatMap(updated -> updated == 1
                        ? Mono.empty()
                        : Mono.error(new BusinessException("OFFER_LINK_STATE_CHANGED", "专享购买链接核销状态异常")));
    }

    public Mono<Void> release(String orderNo) {
        return StringUtils.isBlank(orderNo)
                ? Mono.empty()
                : offerLinkRepository.releaseByOrderNo(orderNo).then();
    }

    public Mono<Void> releaseClosedOrderReservations() {
        return offerLinkRepository.releaseClosedOrderReservations().then();
    }

    @Transactional
    public Mono<Void> revoke(Long id) {
        return offerLinkRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("OFFER_LINK_NOT_FOUND", "专享购买链接不存在")))
                .flatMap(link -> offerLinkRepository.revoke(id, LocalDateTime.now())
                        .flatMap(updated -> updated == 1
                                ? disableBackingCoupon(link.getCouponCode())
                                : Mono.error(new BusinessException("OFFER_LINK_CANNOT_REVOKE", "仅未使用的专享链接可以作废"))));
    }

    private Mono<ShopOfferLinkCreateVo> saveOfferLink(ShopOfferLinkCreateDto dto, OfferProduct product,
                                                       ShopCoupon coupon, OfferRecipient recipient,
                                                       LocalDateTime expiresAt) {
        String token = generateToken();
        boolean externalFulfillment = isExternalFulfillment(dto);
        ShopOfferLink link = new ShopOfferLink();
        link.setName(StringUtils.defaultIfBlank(StringUtils.trimToNull(dto.getName()),
                product.item().getName() + (externalFulfillment ? "直达交付链接" : "专享购买链接")));
        link.setTokenHash(hashToken(token));
        link.setTokenHint(StringUtils.right(token, 6));
        link.setCouponCode(coupon == null ? null : coupon.getCode());
        link.setPurpose(purposeOf(dto));
        link.setItemId(product.item().getId());
        link.setItemName(product.item().getName());
        link.setSkuId(product.sku() == null ? null : product.sku().getId());
        link.setSkuName(product.sku() == null ? null : product.sku().getName());
        link.setType(externalFulfillment ? null : dto.getType());
        link.setDiscountValue(externalFulfillment ? null : resolveDiscountValue(dto, product));
        link.setMaxDiscountAmount(!externalFulfillment && dto.getType() == ShopOfferLink.Type.DISCOUNT
                ? money(defaultMoney(dto.getMaxDiscountAmount())) : zero());
        link.setRecipientUserId(recipient.userId());
        link.setRecipientName(recipient.name());
        link.setContractReference(trim(dto.getContractReference()));
        link.setPaymentReference(trim(dto.getPaymentReference()));
        link.setConfirmationNote(trim(dto.getConfirmationNote()));
        if (externalFulfillment) {
            String currency = normalizeCurrency(dto.getSettlementCurrency());
            link.setSettlementCurrency(currency);
            link.setSettlementAmount(resolveSettlementAmount(product, currency, dto.getSettlementAmount()));
        }
        link.setStatus(ShopOfferLink.Status.ACTIVE);
        link.setExpiresAt(expiresAt);
        return offerLinkRepository.save(link)
                .map(saved -> new ShopOfferLinkCreateVo(saved, buildShareUrl(saved, token)));
    }

    private Mono<ShopCoupon> createBackingCoupon(ShopOfferLinkCreateDto dto, OfferProduct product,
                                                   LocalDateTime startTime, LocalDateTime endTime) {
        return uniqueCouponCode().flatMap(code -> {
            ShopCoupon coupon = new ShopCoupon();
            coupon.setCode(code);
            coupon.setName(StringUtils.defaultIfBlank(StringUtils.trimToNull(dto.getName()),
                    product.item().getName() + "专享购买链接"));
            coupon.setSource(ShopCoupon.Source.OFFER_LINK);
            coupon.setItemId(product.item().getId());
            coupon.setSkuId(product.sku() == null ? null : product.sku().getId());
            coupon.setType(dto.getType() == ShopOfferLink.Type.DISCOUNT
                    ? ShopCoupon.Type.DISCOUNT : ShopCoupon.Type.REDUCTION);
            coupon.setDiscountValue(dto.getType() == ShopOfferLink.Type.FREE
                    ? BigDecimal.ONE.setScale(2) : money(dto.getDiscountValue()));
            coupon.setThresholdAmount(zero());
            coupon.setMaxDiscountAmount(dto.getType() == ShopOfferLink.Type.DISCOUNT
                    ? money(defaultMoney(dto.getMaxDiscountAmount())) : zero());
            coupon.setTotalCount(1);
            coupon.setUsedCount(0);
            coupon.setEnabled(true);
            coupon.setStartTime(startTime);
            coupon.setEndTime(endTime);
            return couponRepository.save(coupon);
        });
    }

    private Mono<Void> disableBackingCoupon(String couponCode) {
        if (StringUtils.isBlank(couponCode)) {
            return Mono.empty();
        }
        return couponRepository.findByCode(couponCode)
                .flatMap(coupon -> {
                    coupon.setEnabled(false);
                    return couponRepository.save(coupon);
                })
                .then();
    }

    private Mono<ShopOfferLink> resolveActive(String token, Long itemId, Long skuId) {
        return resolveActive(token, itemId, skuId, null);
    }

    private Mono<ShopOfferLink> resolveActive(String token, Long itemId, Long skuId, Long buyerId) {
        String normalizedToken = StringUtils.trim(token);
        if (StringUtils.isBlank(normalizedToken)) {
            return Mono.error(new BusinessException("OFFER_LINK_TOKEN_REQUIRED", "专享购买链接令牌不能为空"));
        }
        if (normalizedToken.length() > MAX_TOKEN_LENGTH) {
            return Mono.error(new BusinessException("OFFER_LINK_TOKEN_INVALID", "专享购买链接令牌格式无效"));
        }
        return offerLinkRepository.findByTokenHash(hashToken(normalizedToken))
                .switchIfEmpty(Mono.error(new BusinessException("OFFER_LINK_NOT_FOUND", "专享购买链接不存在或已失效")))
                .map(link -> {
                    ensureUsable(link, itemId, skuId, buyerId);
                    return link;
                });
    }

    private void ensureUsable(ShopOfferLink link, Long itemId, Long skuId, Long buyerId) {
        if (link.getStatus() != ShopOfferLink.Status.ACTIVE) {
            throw new BusinessException("OFFER_LINK_UNAVAILABLE", "专享购买链接已被使用或已作废");
        }
        if (link.getExpiresAt() == null || LocalDateTime.now().isAfter(link.getExpiresAt())) {
            throw new BusinessException("OFFER_LINK_EXPIRED", "专享购买链接已过期");
        }
        if (!link.getItemId().equals(itemId)) {
            throw new BusinessException("OFFER_LINK_ITEM_MISMATCH", "专享购买链接不适用于当前商品");
        }
        if (link.getSkuId() != null && !link.getSkuId().equals(skuId)) {
            throw new BusinessException("OFFER_LINK_SKU_MISMATCH", "请切换到专享链接指定的商品规格");
        }
        if (buyerId != null && purposeOf(link) == ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT
                && link.getRecipientUserId() != null
                && !Objects.equals(link.getRecipientUserId(), buyerId)) {
            throw new BusinessException("OFFER_LINK_RECIPIENT_MISMATCH", "该交付链接仅限指定客户使用");
        }
    }

    private CouponDiscount calculateDiscount(ShopOfferLink link, BigDecimal originalAmount, String currency) {
        if (purposeOf(link) == ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT) {
            BigDecimal settled = money(link.getSettlementAmount());
            if (settled == null || settled.signum() <= 0) {
                throw new BusinessException("OFFER_LINK_SETTLEMENT_INVALID", "已确认结算金额无效");
            }
            return new CouponDiscount(null, settled, zero(), settled);
        }
        if (originalAmount == null || originalAmount.signum() < 0) {
            throw new BusinessException("ORDER_AMOUNT_INVALID", "订单金额无效");
        }
        BigDecimal discount;
        if (link.getType() == ShopOfferLink.Type.FREE) {
            discount = originalAmount;
        } else if (link.getType() == ShopOfferLink.Type.DISCOUNT) {
            BigDecimal rate = link.getDiscountValue().divide(TEN, 4, RoundingMode.HALF_UP);
            discount = originalAmount.subtract(money(originalAmount.multiply(rate)));
            BigDecimal cap = convertCnyAmount(link.getMaxDiscountAmount(), currency);
            if (cap.signum() > 0) {
                discount = discount.min(cap);
            }
        } else {
            discount = convertCnyAmount(link.getDiscountValue(), currency);
        }
        discount = money(discount.max(BigDecimal.ZERO).min(originalAmount));
        return new CouponDiscount(link.getCouponCode(), originalAmount, discount,
                money(originalAmount.subtract(discount)));
    }

    private BigDecimal originalAmount(OfferProduct product, BigDecimal donationAmount, String currency) {
        BigDecimal base = CURRENCY_USD.equals(currency)
                ? pricingService.effectiveUsdPrice(product.price(), product.usdPrice())
                : money(product.price());
        if (base == null) {
            throw new BusinessException("ITEM_PRICE_INVALID", "商品价格无效");
        }
        if (!isDonation(product.item())) {
            return base;
        }
        BigDecimal submitted = donationAmount == null ? base : money(donationAmount);
        if (submitted.compareTo(base) < 0) {
            throw new BusinessException("DONATION_AMOUNT_TOO_LOW", "打赏金额不能低于商品起始金额");
        }
        return submitted;
    }

    private Mono<OfferProduct> resolveProduct(Long itemId, Long skuId) {
        return itemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> {
                    boolean multiSku = item.getSkuMode() == ShopItem.SkuMode.MULTI;
                    if (multiSku && skuId == null) {
                        return Mono.error(new BusinessException("OFFER_LINK_SKU_REQUIRED", "多规格商品必须选择一个SKU"));
                    }
                    if (skuId == null) {
                        return Mono.just(new OfferProduct(item, null, item.getPrice(), item.getUsdPrice()));
                    }
                    return skuRepository.findById(skuId)
                            .filter(sku -> itemId.equals(sku.getItemId()) && Boolean.TRUE.equals(sku.getEnabled()))
                            .switchIfEmpty(Mono.error(new BusinessException("SKU_NOT_FOUND", "SKU不存在或已停用")))
                            .map(sku -> new OfferProduct(item, sku, sku.getPrice(), sku.getUsdPrice()));
                });
    }

    private Mono<Void> validateCreateRequest(ShopOfferLinkCreateDto dto, OfferProduct product) {
        if (!Boolean.TRUE.equals(product.item().getEnabled())) {
            return Mono.error(new BusinessException("ITEM_DISABLED", "下架商品不能生成专享购买链接"));
        }
        if (isExternalFulfillment(dto)) {
            if ((dto.getRecipientUserId() == null || dto.getRecipientUserId() <= 0)
                    && StringUtils.isBlank(dto.getRecipientName())) {
                return Mono.error(new BusinessException(
                        "OFFER_LINK_RECIPIENT_REQUIRED", "请选择已有客户或填写未注册客户名称"));
            }
            if (StringUtils.isBlank(dto.getContractReference())) {
                return Mono.error(new BusinessException("OFFER_LINK_CONTRACT_REFERENCE_REQUIRED", "请填写合同确认编号"));
            }
            if (StringUtils.isBlank(dto.getPaymentReference())) {
                return Mono.error(new BusinessException("OFFER_LINK_PAYMENT_REFERENCE_REQUIRED", "请填写付款确认编号"));
            }
            if (isDonation(product.item())) {
                return Mono.error(new BusinessException(
                        "OFFER_LINK_EXTERNAL_DONATION_UNSUPPORTED", "浮动金额商品不能生成已付款直达链接"));
            }
            resolveSettlementAmount(product, normalizeCurrency(dto.getSettlementCurrency()), dto.getSettlementAmount());
            return Mono.empty();
        }
        if (dto.getType() == null) {
            return Mono.error(new BusinessException("OFFER_LINK_TYPE_REQUIRED", "请选择专享优惠类型"));
        }
        if (dto.getType() != ShopOfferLink.Type.FREE
                && (dto.getDiscountValue() == null || dto.getDiscountValue().signum() <= 0)) {
            return Mono.error(new BusinessException("OFFER_LINK_VALUE_INVALID", "优惠值必须大于0"));
        }
        if (dto.getType() == ShopOfferLink.Type.DISCOUNT
                && dto.getDiscountValue().compareTo(TEN) >= 0) {
            return Mono.error(new BusinessException("OFFER_LINK_DISCOUNT_INVALID", "折扣值必须小于10"));
        }
        if (dto.getType() == ShopOfferLink.Type.FREE && isDonation(product.item())) {
            return Mono.error(new BusinessException("OFFER_LINK_FREE_DONATION_UNSUPPORTED", "浮动金额商品不能生成免费专享链接"));
        }
        if (product.price() == null || product.price().signum() <= 0) {
            return Mono.error(new BusinessException("ITEM_PRICE_INVALID", "商品价格无效"));
        }
        return Mono.empty();
    }

    private Mono<OfferRecipient> resolveRecipient(ShopOfferLinkCreateDto dto) {
        if (!isExternalFulfillment(dto)) {
            return Mono.just(OfferRecipient.none());
        }
        if (dto.getRecipientUserId() == null || dto.getRecipientUserId() <= 0) {
            return Mono.just(new OfferRecipient(null, StringUtils.trim(dto.getRecipientName())));
        }
        return authUserGateway.getById(dto.getRecipientUserId())
                .switchIfEmpty(Mono.error(new BusinessException("OFFER_LINK_RECIPIENT_NOT_FOUND", "指定客户不存在")))
                .map(this::toRecipient);
    }

    private OfferRecipient toRecipient(PortalUserVo user) {
        return new OfferRecipient(user.getId(), StringUtils.defaultIfBlank(
                StringUtils.trimToNull(user.getUsername()), "用户 " + user.getId()));
    }

    private BigDecimal resolveSettlementAmount(OfferProduct product, String currency, BigDecimal submittedAmount) {
        BigDecimal amount = submittedAmount == null
                ? (CURRENCY_USD.equals(currency)
                    ? pricingService.effectiveUsdPrice(product.price(), product.usdPrice())
                    : money(product.price()))
                : money(submittedAmount);
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("ITEM_PRICE_INVALID", "商品未配置有效的" + currency + "价格");
        }
        return money(amount);
    }

    private BigDecimal resolveDiscountValue(ShopOfferLinkCreateDto dto, OfferProduct product) {
        return dto.getType() == ShopOfferLink.Type.FREE ? money(product.price()) : money(dto.getDiscountValue());
    }

    private Mono<String> uniqueCouponCode() {
        String code = "FFLINK" + RandomStringUtils.secure().nextAlphanumeric(12).toUpperCase();
        return couponRepository.findByCode(code).hasElement()
                .flatMap(exists -> exists ? uniqueCouponCode() : Mono.just(code));
    }

    private String buildShareUrl(ShopOfferLink link, String token) {
        String baseUrl = StringUtils.removeEnd(StringUtils.defaultIfBlank(
                StringUtils.trimToNull(properties.getPortalBaseUrl()), "https://shop.example.com"), "/");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/shop/detail/").path(link.getItemId().toString());
        if (link.getSkuId() != null) {
            builder.queryParam("sku", link.getSkuId());
        }
        return builder.queryParam("offer", token).build().encode().toUriString();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK不支持SHA-256", e);
        }
    }

    private BigDecimal convertCnyAmount(BigDecimal value, String currency) {
        BigDecimal amount = money(defaultMoney(value));
        if (!CURRENCY_USD.equals(currency)) {
            return amount;
        }
        return amount.divide(pricingService.cnyPerUsd(), 2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(currency, CURRENCY_CNY));
        if (!CURRENCY_CNY.equals(normalized) && !CURRENCY_USD.equals(normalized)) {
            throw new BusinessException("PAYMENT_CURRENCY_INVALID", "专享链接仅支持CNY或USD");
        }
        return normalized;
    }

    private String resolveSettlementCurrency(ShopOfferLink link, String requestedCurrency) {
        if (purposeOf(link) == ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT) {
            return normalizeCurrency(link.getSettlementCurrency());
        }
        return normalizeCurrency(requestedCurrency);
    }

    private ShopOfferLink.Purpose purposeOf(ShopOfferLinkCreateDto dto) {
        return dto.getPurpose() == null ? ShopOfferLink.Purpose.PROMOTION : dto.getPurpose();
    }

    private ShopOfferLink.Purpose purposeOf(ShopOfferLink link) {
        return link.getPurpose() == null ? ShopOfferLink.Purpose.PROMOTION : link.getPurpose();
    }

    private boolean isExternalFulfillment(ShopOfferLinkCreateDto dto) {
        return purposeOf(dto) == ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT;
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    private int normalizeValidDays(Integer value) {
        return value == null ? 5 : value;
    }

    private boolean isDonation(ShopItem item) {
        return item.getType() == ShopItem.Type.DONATION
                || item.getType() == ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private record OfferProduct(ShopItem item, ShopItemSku sku, BigDecimal price, BigDecimal usdPrice) {
    }

    private record OfferRecipient(Long userId, String name) {

        private static OfferRecipient none() {
            return new OfferRecipient(null, null);
        }
    }
}
