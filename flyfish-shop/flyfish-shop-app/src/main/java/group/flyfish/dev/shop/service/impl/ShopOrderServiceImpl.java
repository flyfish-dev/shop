package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.converter.impl.ShopOrderFormParamValue;
import group.flyfish.dev.shop.domain.dto.ShopCouponApplyDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDeliveryDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.domain.qo.ShopOrderListQo;
import group.flyfish.dev.shop.domain.po.ShopTransaction;
import group.flyfish.dev.shop.domain.vo.ShopCouponApplyVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderCreateVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryDownloadVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryFileVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderVo;
import group.flyfish.dev.shop.domain.vo.ShopPurchaseAvailabilityVo;
import group.flyfish.dev.shop.pricing.ShopPricingService;
import group.flyfish.dev.shop.service.CouponDiscount;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.shop.repository.ShopTransactionRepository;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.PayService;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopDeliveryService;
import group.flyfish.dev.shop.service.ShopOrderService;
import group.flyfish.dev.shop.service.checker.GitRepositoryAccessOrderChecker;
import group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuSigner;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuNotifyDto;
import group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties;
import group.flyfish.dev.shop.service.support.stripe.bean.StripeCheckoutSessionDto;
import group.flyfish.dev.shop.support.ShopAuthorizationUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;

import tools.jackson.core.type.TypeReference;

@Service
@RequiredArgsConstructor
public class ShopOrderServiceImpl implements ShopOrderService {

    private static final DateTimeFormatter PAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long UNPAID_ORDER_TTL_MINUTES = 15;
    private static final String EXPIRED_ORDER_MESSAGE = "订单超过15分钟未支付，已自动关闭";
    private static final String ORDER_PROPERTY_DONATION_AMOUNT = "donationAmount";
    private static final String ORDER_PROPERTY_CURRENCY = "currency";
    private static final String CURRENCY_CNY = "CNY";
    private static final String CURRENCY_USD = "USD";
    private static final String LICENSE_FILE_CODE = "lic";
    private static final String LICENSE_JSON_FILE_CODE = "json";
    private static final String LICENSE_PAYLOAD_FILE_CODE = "payload";
    private static final String LICENSE_CONTENT_TYPE = "application/octet-stream";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String STRIPE_PROVIDER = "stripe";
    private static final String STRIPE_CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";
    private static final String STRIPE_CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED =
            "checkout.session.async_payment_succeeded";
    private static final String STRIPE_CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED =
            "checkout.session.async_payment_failed";
    private static final String STRIPE_CHECKOUT_SESSION_EXPIRED = "checkout.session.expired";
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg", "rwf",
            "ugx", "vnd", "vuv", "xaf", "xof", "xpf"
    );
    private static final Comparator<ShopOrder> ORDER_TIME_DESC = Comparator
            .comparing(ShopOrderServiceImpl::orderSortTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ShopOrder::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ShopOrderRepository shopOrderRepository;
    private final ShopTransactionRepository shopTransactionRepository;
    private final ShopItemRepository shopItemRepository;
    private final ShopItemSkuRepository shopItemSkuRepository;
    private final ShopOrderDeliveryRepository shopOrderDeliveryRepository;
    private final ShopLicenseKeyPairRepository shopLicenseKeyPairRepository;
    private final PayService payService;
    private final ShopDeliveryService shopDeliveryService;
    private final AuthUserGateway authUserGateway;
    private final ShopCouponService shopCouponService;
    private final H5ZhiFuProperties h5ZhiFuProperties;
    private final GitRepositoryAccessOrderChecker gitRepositoryAccessOrderChecker;
    private final ShopContractService shopContractService;
    private final ShopPricingService shopPricingService;

    @Override
    @Transactional
    public Mono<ShopOrderCreateVo> createOrder(ShopOrderDto dto, PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        Long itemId = parseItemId(dto.getItemId());
        return shopItemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> resolvePurchaseContext(item, dto.getSkuId())
                        .flatMap(context -> {
                            ensureItemEnabled(context.effectiveItem());
                            return ensurePurchasable(context.effectiveItem(), buyer)
                                    .then(shopContractService.requireSigned(context.item().getId(), context.skuId(), buyer,
                                            dto.getContractSignToken()))
                                    .then(Mono.defer(() -> {
                                        ShopItem saleItem = context.effectiveItem();
                                        int count = normalizeCount(dto.getCount(), saleItem);
                                        String currency = resolvePaymentCurrency(dto.getPaymentCurrency(), saleItem);
                                        ensurePaymentProviderSupportsCurrency(dto, currency);
                                        ensureCouponSupportsCurrency(dto.getCouponCode(), currency);
                                        BigDecimal originalAmount = calculateOriginalAmount(saleItem, count,
                                                dto.getDonationAmount(), currency);
                                        Map<String, Object> orderFormValues = ShopOrderFormParamValue.fromItem(saleItem)
                                                .validateSubmitted(dto);
                                        return shopCouponService.applyCoupon(dto.getCouponCode(), originalAmount)
                                                .map(discount -> buildOrder(dto, buyer, context, count, currency,
                                                        discount, orderFormValues))
                                                .flatMap(shopOrderRepository::save)
                                                .flatMap(saved -> shopContractService
                                                        .bindOrder(dto.getContractSignToken(), saved.getOrderNo(),
                                                                context.item().getId(), context.skuId(), buyer.getId())
                                                        .thenReturn(saved))
                                                .flatMap(saved -> payService.pay(saved, saleItem, dto)
                                                        .flatMap(payment -> shopOrderRepository.save(applyPayment(saved, payment))
                                                                .flatMap(paying -> toCreateVo(paying, saleItem, payment)))
                                                        .onErrorResume(e -> markOrderFailed(saved, e)));
                                    }));
                        }));
    }

    @Override
    public Mono<ShopCouponApplyVo> applyCoupon(ShopCouponApplyDto dto, PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        if (StringUtils.isBlank(dto.getCouponCode())) {
            return Mono.error(new BusinessException("COUPON_CODE_REQUIRED", "优惠券编码不能为空"));
        }
        Long itemId = parseItemId(dto.getItemId());
        return shopItemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> resolvePurchaseContext(item, dto.getSkuId()))
                .flatMap(context -> {
                    ensureItemEnabled(context.effectiveItem());
                    int count = normalizeCount(dto.getCount(), context.effectiveItem());
                    String currency = resolvePaymentCurrency(dto.getPaymentCurrency(), context.effectiveItem());
                    ensureCouponSupportsCurrency(dto.getCouponCode(), currency);
                    BigDecimal originalAmount = calculateOriginalAmount(context.effectiveItem(), count,
                            dto.getDonationAmount(), currency);
                    return shopCouponService.applyCoupon(dto.getCouponCode(), originalAmount);
                })
                .map(ShopCouponApplyVo::new);
    }

    @Override
    public Mono<ShopPurchaseAvailabilityVo> checkPurchaseAvailability(Long itemId, PortalUserVo buyer) {
        return checkPurchaseAvailability(itemId, null, buyer);
    }

    @Override
    public Mono<ShopPurchaseAvailabilityVo> checkPurchaseAvailability(Long itemId, Long skuId, PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return shopItemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> resolvePurchaseContext(item, skuId == null ? null : String.valueOf(skuId)))
                .flatMap(context -> gitRepositoryAccessOrderChecker.check(context.effectiveItem(), buyer));
    }

    @Override
    public Flux<ShopOrderVo> getOrders(PortalUserVo buyer, ShopOrderListQo qo) {
        ShopAuthorizationUtils.requireLogin(buyer);
        ShopOrderListQo query = normalizeOrderListQo(buyer, qo);
        Flux<ShopOrder> orders = shopOrderRepository.findAll(query, query.sorts());
        return closeExpiredUnpaidOrders().thenMany(orders).sort(ORDER_TIME_DESC).concatMap(this::toVo);
    }

    @Override
    public Flux<ShopOrderVo> getMyOrders(PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return closeExpiredUnpaidOrders()
                .thenMany(buyerOrders(buyer.getId(), null))
                .sort(ORDER_TIME_DESC)
                .concatMap(this::toVo);
    }

    private Flux<ShopOrder> buyerOrders(Long buyerId, Long itemId) {
        return itemId == null
                ? shopOrderRepository.findAllByBuyerIdOrderByCreateTimeDesc(buyerId)
                : shopOrderRepository.findAllByBuyerIdAndItemIdOrderByCreateTimeDesc(buyerId, itemId);
    }

    private ShopOrderListQo normalizeOrderListQo(PortalUserVo buyer, ShopOrderListQo qo) {
        ShopOrderListQo query = qo == null ? new ShopOrderListQo() : qo;
        if (!ShopAuthorizationUtils.isShopMaintainer(buyer)) {
            query.setBuyerId(buyer.getId());
        }
        return query;
    }

    @Override
    public Mono<ShopOrderVo> getOrder(PortalUserVo buyer, String orderNo) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return closeExpiredUnpaidOrders().then(shopOrderRepository.findByOrderNo(orderNo))
                .filter(order -> buyer.getId().equals(order.getBuyerId()) || ShopAuthorizationUtils.isShopMaintainer(buyer))
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(this::toVo);
    }

    @Override
    @Transactional
    public Mono<ShopOrderDeliveryExtractVo> extractDelivery(PortalUserVo buyer, String orderNo) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return closeExpiredUnpaidOrders()
                .then(shopOrderRepository.findByOrderNo(orderNo))
                .filter(order -> buyer.getId().equals(order.getBuyerId()) || ShopAuthorizationUtils.isShopMaintainer(buyer))
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(this::ensureExtractable)
                .flatMap(order -> shopOrderDeliveryRepository.findAllByOrderNoOrderByCreateTimeAsc(order.getOrderNo())
                        .collectList()
                        .flatMap(deliveries -> {
                            if (deliveries.isEmpty()) {
                                return Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无可提取内容"));
                            }
                            return Flux.fromIterable(deliveries)
                                    .concatMap(delivery -> markExtractedForBuyer(delivery, order, buyer))
                                    .collectList();
                        }))
                .flatMap(deliveries -> toDeliveryExtractVo(deliveries, false));
    }

    @Override
    public Mono<ShopOrderDeliveryExtractVo> viewDelivery(PortalUserVo user, String orderNo) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return closeExpiredUnpaidOrders()
                .then(shopOrderRepository.findByOrderNo(orderNo))
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> shopOrderDeliveryRepository.findAllByOrderNoOrderByCreateTimeAsc(order.getOrderNo())
                        .collectList())
                .flatMap(deliveries -> {
                    if (deliveries.isEmpty()) {
                        return Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无交付快照"));
                    }
                    return toDeliveryExtractVo(deliveries, true);
                });
    }

    @Override
    @Transactional
    public Mono<ShopOrderDeliveryDownloadVo> downloadDeliveryFile(PortalUserVo user, String orderNo, String fileCode) {
        ShopAuthorizationUtils.requireLogin(user);
        boolean maintainer = ShopAuthorizationUtils.isShopMaintainer(user);
        return closeExpiredUnpaidOrders()
                .then(shopOrderRepository.findByOrderNo(orderNo))
                .filter(order -> user.getId().equals(order.getBuyerId()) || maintainer)
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(this::ensureExtractable)
                .flatMap(order -> shopOrderDeliveryRepository.findByOrderNoAndDeliveryType(order.getOrderNo(),
                                ShopOrderDelivery.DeliveryType.LICENSE.name())
                        .switchIfEmpty(Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无可下载内容")))
                        .flatMap(delivery -> markExtractedForBuyer(delivery, order, user)
                                .flatMap(saved -> toDeliveryDownload(saved, fileCode, maintainer))));
    }

    @Override
    @Transactional
    public Mono<ShopOrderVo> updateDelivery(String orderNo, ShopOrderDeliveryDto dto) {
        return shopOrderRepository.findByOrderNo(orderNo)
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .map(order -> applyDeliveryUpdate(order, dto))
                .flatMap(order -> shopOrderRepository.save(order)
                        .flatMap(saved -> upsertManualDeliveryIfNeeded(saved, dto).thenReturn(saved)))
                .flatMap(this::toVo);
    }

    @Override
    @Transactional
    public Mono<ShopOrderVo> retryDelivery(String orderNo) {
        return shopOrderRepository.findByOrderNo(orderNo)
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> Mono.zip(resolveOrderItem(order), authUserGateway.getById(order.getBuyerId()))
                        .switchIfEmpty(Mono.error(new BusinessException("ORDER_CONTEXT_NOT_FOUND", "订单交付上下文不存在")))
                        .flatMap(tuple -> retryAutomaticDelivery(order, tuple.getT1(), tuple.getT2())))
                .flatMap(this::toVo);
    }

    private ShopOrder applyDeliveryUpdate(ShopOrder order, ShopOrderDeliveryDto dto) {
        if (order.getStatus() == ShopOrder.Status.PENDING || order.getStatus() == ShopOrder.Status.PAYING) {
            throw new BusinessException("ORDER_NOT_PAID", "订单尚未支付");
        }
        if (order.getStatus() == ShopOrder.Status.CLOSED) {
            throw new BusinessException("ORDER_CLOSED", "订单已关闭");
        }
        String message = StringUtils.abbreviate(StringUtils.trimToNull(dto.getDeliveryMessage()), 500);
        if (dto.getDeliveryStatus() == ShopOrder.DeliveryStatus.SUCCESS) {
            order.setStatus(ShopOrder.Status.DELIVERED);
            order.setDeliveryStatus(ShopOrder.DeliveryStatus.SUCCESS);
            order.setDeliveryMessage(StringUtils.defaultIfBlank(message, "人工交付完成"));
            return order;
        }
        if (dto.getDeliveryStatus() == ShopOrder.DeliveryStatus.FAILED) {
            order.setStatus(ShopOrder.Status.FAILED);
            order.setDeliveryStatus(ShopOrder.DeliveryStatus.FAILED);
            order.setDeliveryMessage(StringUtils.defaultIfBlank(message, "人工交付失败"));
            return order;
        }
        throw new BusinessException("DELIVERY_STATUS_INVALID", "交付状态不支持");
    }

    private Mono<ShopOrderDelivery> upsertManualDeliveryIfNeeded(ShopOrder order, ShopOrderDeliveryDto dto) {
        if (!hasManualDeliveryContent(dto) || dto.getDeliveryStatus() != ShopOrder.DeliveryStatus.SUCCESS) {
            return Mono.empty();
        }
        ShopOrderDelivery.DeliveryType deliveryType = dto.getDeliveryType() == null
                ? ShopOrderDelivery.DeliveryType.DIGITAL
                : dto.getDeliveryType();
        List<FileAttachmentVo> attachments = normalizeDeliveryAttachments(dto.getDeliveryAttachments());
        return shopOrderDeliveryRepository.findByOrderNoAndDeliveryType(order.getOrderNo(), deliveryType.name())
                .defaultIfEmpty(new ShopOrderDelivery())
                .map(delivery -> {
                    applyManualDelivery(delivery, order, dto, deliveryType, attachments);
                    return delivery;
                })
                .flatMap(shopOrderDeliveryRepository::save);
    }

    private boolean hasManualDeliveryContent(ShopOrderDeliveryDto dto) {
        return dto != null && (StringUtils.isNotBlank(dto.getDeliveryContent())
                || !normalizeDeliveryAttachments(dto.getDeliveryAttachments()).isEmpty());
    }

    private void applyManualDelivery(ShopOrderDelivery delivery, ShopOrder order, ShopOrderDeliveryDto dto,
                                     ShopOrderDelivery.DeliveryType deliveryType,
                                     List<FileAttachmentVo> attachments) {
        if (delivery.getId() == null) {
            delivery.setCreateBy("manual-delivery");
        }
        delivery.setUpdateBy("manual-delivery");
        delivery.setOrderNo(order.getOrderNo());
        delivery.setItemId(order.getItemId());
        delivery.setBuyerId(order.getBuyerId());
        delivery.setDeliveryType(deliveryType.name());
        delivery.setTitle(StringUtils.left(StringUtils.defaultIfBlank(StringUtils.trimToNull(dto.getDeliveryTitle()),
                deliveryType == ShopOrderDelivery.DeliveryType.LICENSE ? "授权许可补充资源" : "补充交付资源"), 128));
        delivery.setContent(StringUtils.trimToNull(dto.getDeliveryContent()));
        delivery.setAttachments(attachmentsJson(attachments));
        if (deliveryType != ShopOrderDelivery.DeliveryType.LICENSE) {
            delivery.setLicenseNo(null);
        }
    }

    private List<FileAttachmentVo> normalizeDeliveryAttachments(List<FileAttachmentVo> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(this::normalizeDeliveryAttachment)
                .filter(Objects::nonNull)
                .limit(12)
                .toList();
    }

    private FileAttachmentVo normalizeDeliveryAttachment(FileAttachmentVo source) {
        if (source == null || StringUtils.isBlank(source.getUrl())) {
            return null;
        }
        FileAttachmentVo target = new FileAttachmentVo();
        target.setId(source.getId());
        target.setName(StringUtils.left(StringUtils.defaultIfBlank(StringUtils.trimToNull(source.getName()), "附件"), 160));
        target.setUrl(StringUtils.trim(source.getUrl()));
        target.setSize(source.getSize());
        target.setContentType(StringUtils.left(StringUtils.trimToNull(source.getContentType()), 128));
        target.setImage(Boolean.TRUE.equals(source.getImage())
                || StringUtils.startsWithIgnoreCase(target.getContentType(), "image/"));
        return target;
    }

    private String attachmentsJson(List<FileAttachmentVo> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        return JacksonUtils.toJson(attachments);
    }

    @Override
    @Transactional
    public Mono<Void> handlePaymentNotify(H5ZhiFuNotifyDto dto) {
        verifyNotify(dto);
        return shopOrderRepository.findByOrderNo(dto.getOutTradeNo())
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> {
                    if (!CURRENCY_CNY.equalsIgnoreCase(StringUtils.defaultIfBlank(order.getCurrency(), CURRENCY_CNY))) {
                        return Mono.error(new BusinessException("PAYMENT_CURRENCY_INVALID", "国内支付回调币种不匹配"));
                    }
                    verifyAmount(order, dto);
                    if (order.getStatus() == ShopOrder.Status.CLOSED) {
                        return Mono.empty();
                    }
                    boolean paidBefore = order.getPaidTime() != null
                            || order.getStatus() == ShopOrder.Status.PAID
                            || order.getStatus() == ShopOrder.Status.DELIVERED;
                    Mono<ShopOrder> paid = paidBefore
                            ? Mono.just(order)
                            : shopOrderRepository.save(markPaid(order, dto))
                            .flatMap(saved -> saveTransaction(saved, dto).thenReturn(saved))
                            .flatMap(saved -> increaseBuyCount(saved).thenReturn(saved))
                            .flatMap(saved -> shopCouponService.markUsed(saved.getCouponCode()).thenReturn(saved));
                    return paid.flatMap(this::deliverIfNeeded).then();
                });
    }

    @Override
    @Transactional
    public Mono<Void> handleStripeCheckoutSession(StripeCheckoutSessionDto session, String eventType) {
        if (session == null || StringUtils.isBlank(session.getId())
                || StringUtils.isBlank(session.getClientReferenceId())) {
            return Mono.error(new BusinessException("STRIPE_SESSION_INVALID", "Stripe Checkout Session 无效"));
        }
        return switch (StringUtils.defaultString(eventType)) {
            case STRIPE_CHECKOUT_SESSION_COMPLETED, STRIPE_CHECKOUT_SESSION_ASYNC_PAYMENT_SUCCEEDED ->
                    handleStripePaidSession(session);
            case STRIPE_CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED ->
                    closeStripeSessionOrder(session, "Stripe 支付未完成，请重新下单或更换支付方式");
            case STRIPE_CHECKOUT_SESSION_EXPIRED ->
                    closeStripeSessionOrder(session, "Stripe Checkout 已过期，请重新下单");
            default -> Mono.empty();
        };
    }

    @Override
    @Transactional
    public Mono<Integer> closeExpiredUnpaidOrders() {
        return shopOrderRepository.closeExpiredUnpaidOrders(LocalDateTime.now(), EXPIRED_ORDER_MESSAGE);
    }

    private ShopOrder buildOrder(ShopOrderDto dto, PortalUserVo buyer, PurchaseContext context, int count,
                                 String currency, CouponDiscount discount, Map<String, Object> orderFormValues) {
        ShopItem item = context.item();
        ShopItem saleItem = context.effectiveItem();
        ShopOrder order = new ShopOrder();
        order.setOrderNo("FF" + IdGenerators.idString());
        order.setItemId(item.getId());
        order.setSkuId(context.skuId());
        order.setSkuCode(context.sku() == null ? null : context.sku().getCode());
        order.setSkuName(context.sku() == null ? null : context.sku().getName());
        order.setItemName(item.getName());
        order.setItemType(saleItem.getType() == null ? null : saleItem.getType().name());
        order.setShopId(item.getShopId());
        order.setBuyerId(buyer.getId());
        order.setCount(count);
        order.setProperties(JacksonUtils.toJson(orderProperties(dto, context, discount.originalAmount(), orderFormValues)));
        order.setItemSnapshot(JacksonUtils.toJson(item));
        order.setSkuSnapshot(context.sku() == null ? null : JacksonUtils.toJson(context.sku()));
        order.setCurrency(currency);
        order.setOriginalAmount(discount.originalAmount());
        order.setDiscountAmount(discount.discountAmount());
        order.setCouponCode(discount.code());
        order.setAmount(discount.payableAmount());
        order.setPaymentProvider(initialPaymentProvider(dto));
        order.setStatus(ShopOrder.Status.PENDING);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.WAITING);
        order.setExpireTime(LocalDateTime.now().plusMinutes(UNPAID_ORDER_TTL_MINUTES));
        return order;
    }

    private Mono<ShopItem> ensurePurchasable(ShopItem item, PortalUserVo buyer) {
        return gitRepositoryAccessOrderChecker.check(item, buyer)
                .flatMap(availability -> availability.isPurchasable()
                        ? Mono.just(item)
                        : Mono.error(new BusinessException(availability.getReasonCode(), availability.getMessage())));
    }

    private void ensureItemEnabled(ShopItem item) {
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            throw new BusinessException("ITEM_DISABLED", "商品已下架");
        }
    }

    private Mono<PurchaseContext> resolvePurchaseContext(ShopItem item, String rawSkuId) {
        if (!isMultiSku(item)) {
            return Mono.just(new PurchaseContext(item, null, item));
        }
        Long skuId = parseSkuId(rawSkuId);
        return shopItemSkuRepository.findById(skuId)
                .filter(sku -> item.getId().equals(sku.getItemId()))
                .switchIfEmpty(Mono.error(new BusinessException("SKU_NOT_FOUND", "SKU不存在")))
                .map(sku -> new PurchaseContext(item, sku, effectiveItem(item, sku)));
    }

    private Mono<ShopItem> resolveOrderItem(ShopOrder order) {
        ShopItem snapshotItem = parseSnapshot(order.getItemSnapshot(), ShopItem.class);
        ShopItemSku snapshotSku = parseSnapshot(order.getSkuSnapshot(), ShopItemSku.class);
        if (snapshotItem != null && snapshotSku != null) {
            return Mono.just(effectiveItem(snapshotItem, snapshotSku));
        }
        if (snapshotItem != null) {
            return Mono.just(snapshotItem);
        }
        return shopItemRepository.findById(order.getItemId())
                .flatMap(item -> {
                    if (order.getSkuId() == null) {
                        return Mono.just(item);
                    }
                    return shopItemSkuRepository.findById(order.getSkuId())
                            .filter(sku -> item.getId().equals(sku.getItemId()))
                            .map(sku -> effectiveItem(item, sku))
                            .defaultIfEmpty(item);
                });
    }

    private ShopItem effectiveItem(ShopItem item, ShopItemSku sku) {
        ShopItem effective = new ShopItem();
        effective.setId(item.getId());
        effective.setShopId(item.getShopId());
        effective.setGroupId(item.getGroupId());
        effective.setName(displayName(item.getName(), sku.getName()));
        effective.setCover(item.getCover());
        effective.setImages(item.getImages());
        effective.setPrice(sku.getPrice());
        effective.setUsdPrice(sku.getUsdPrice());
        effective.setType(sku.getType());
        effective.setDeliveryMode(sku.getDeliveryMode());
        effective.setTags(StringUtils.defaultIfBlank(sku.getTags(), item.getTags()));
        effective.setParams(sku.getParams());
        effective.setBuyCount(sku.getBuyCount());
        effective.setDescription(StringUtils.defaultIfBlank(sku.getDescription(), item.getDescription()));
        effective.setI18n(StringUtils.defaultIfBlank(sku.getI18n(), item.getI18n()));
        effective.setEnabled(Boolean.TRUE.equals(item.getEnabled()) && Boolean.TRUE.equals(sku.getEnabled()));
        effective.setDefaultCouponEnabled(effectiveCouponEnabled(item, sku));
        effective.setDefaultCouponCode(effectiveCouponCode(item, sku));
        effective.setSkuMode(ShopItem.SkuMode.SINGLE);
        return effective;
    }

    private Boolean effectiveCouponEnabled(ShopItem item, ShopItemSku sku) {
        return sku.getDefaultCouponEnabled() == null ? item.getDefaultCouponEnabled() : sku.getDefaultCouponEnabled();
    }

    private String effectiveCouponCode(ShopItem item, ShopItemSku sku) {
        return Boolean.TRUE.equals(effectiveCouponEnabled(item, sku))
                ? StringUtils.defaultIfBlank(sku.getDefaultCouponEnabled() == null
                ? item.getDefaultCouponCode()
                : sku.getDefaultCouponCode(), null)
                : null;
    }

    private boolean isMultiSku(ShopItem item) {
        return item != null && item.getSkuMode() == ShopItem.SkuMode.MULTI;
    }

    private Long parseSkuId(String rawSkuId) {
        if (StringUtils.isBlank(rawSkuId)) {
            throw new BusinessException("SKU_REQUIRED", "请选择SKU");
        }
        try {
            return Long.parseLong(rawSkuId);
        } catch (Exception e) {
            throw new BusinessException("INVALID_SKU", "SKU不正确");
        }
    }

    private <T> T parseSnapshot(String snapshot, Class<T> type) {
        if (StringUtils.isBlank(snapshot)) {
            return null;
        }
        try {
            return JacksonUtils.readValue(snapshot, type);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readSnapshot(String snapshot) {
        if (StringUtils.isBlank(snapshot)) {
            return Map.of();
        }
        try {
            return JacksonUtils.readValue(snapshot, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String displayName(String itemName, String skuName) {
        if (StringUtils.isBlank(skuName)) {
            return itemName;
        }
        if (StringUtils.isBlank(itemName)) {
            return skuName;
        }
        return itemName + " - " + skuName;
    }

    private ShopItem.Type parseItemType(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return ShopItem.Type.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> orderProperties(ShopOrderDto dto, PurchaseContext context, BigDecimal originalAmount,
                                                Map<String, Object> orderFormValues) {
        Map<String, Object> properties = gitRepositoryAccessOrderChecker.orderProperties(dto, context.effectiveItem());
        if (context.sku() != null) {
            properties.put("skuId", context.sku().getId());
            properties.put("skuCode", context.sku().getCode());
            properties.put("skuName", context.sku().getName());
        }
        if (orderFormValues != null && !orderFormValues.isEmpty()) {
            properties.put(ShopOrderFormParamValue.PARAM_KEY, orderFormValues);
        }
        if (isDonationItem(context.effectiveItem())) {
            properties.put(ORDER_PROPERTY_DONATION_AMOUNT, originalAmount);
            properties.put(ORDER_PROPERTY_CURRENCY, resolvePaymentCurrency(dto.getPaymentCurrency(),
                    context.effectiveItem()));
        }
        return properties;
    }

    private record PurchaseContext(ShopItem item, ShopItemSku sku, ShopItem effectiveItem) {

        private Long skuId() {
            return sku == null ? null : sku.getId();
        }
    }

    private int normalizeCount(Integer count, ShopItem item) {
        int normalized = count == null ? 1 : count;
        if (normalized <= 0) {
            throw new BusinessException("INVALID_COUNT", "购买数量必须大于0");
        }
        if (isDonationItem(item) && normalized != 1) {
            throw new BusinessException("INVALID_COUNT", "打赏商品不支持购买数量");
        }
        return normalized;
    }

    private BigDecimal calculateOriginalAmount(ShopItem item, int count, BigDecimal donationAmount, String currency) {
        if (isDonationItem(item)) {
            return normalizeDonationAmount(item, donationAmount, currency);
        }
        BigDecimal unitPrice = CURRENCY_USD.equals(currency)
                ? shopPricingService.effectiveUsdPrice(item)
                : item.getPrice();
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new BusinessException("ITEM_PRICE_INVALID", "商品价格配置不正确");
        }
        return unitPrice.multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeDonationAmount(ShopItem item, BigDecimal donationAmount, String currency) {
        BigDecimal minimumAmount = CURRENCY_USD.equals(currency)
                ? shopPricingService.minimumDonationUsd(item)
                : money(item.getPrice());
        BigDecimal amount = money(donationAmount == null ? minimumAmount : donationAmount);
        if (amount.compareTo(minimumAmount) < 0) {
            String symbol = CURRENCY_USD.equals(currency) ? "$" : "¥";
            throw new BusinessException("DONATION_AMOUNT_TOO_LOW",
                    "打赏金额不能低于 " + symbol + minimumAmount.toPlainString());
        }
        return amount;
    }

    private String resolvePaymentCurrency(String requestedCurrency, ShopItem item) {
        String currency = StringUtils.defaultIfBlank(StringUtils.trimToNull(requestedCurrency), CURRENCY_CNY)
                .toUpperCase(Locale.ROOT);
        if (!CURRENCY_CNY.equals(currency) && !CURRENCY_USD.equals(currency)) {
            throw new BusinessException("PAYMENT_CURRENCY_UNSUPPORTED", "暂不支持该支付币种");
        }
        return currency;
    }

    private void ensurePaymentProviderSupportsCurrency(ShopOrderDto dto, String currency) {
        if (CURRENCY_USD.equals(currency) && !STRIPE_PROVIDER.equals(initialPaymentProvider(dto))) {
            throw new BusinessException("PAYMENT_PROVIDER_CURRENCY_UNSUPPORTED", "美元支付请使用 Stripe");
        }
    }

    private void ensureCouponSupportsCurrency(String couponCode, String currency) {
        if (CURRENCY_USD.equals(currency) && StringUtils.isNotBlank(couponCode)) {
            throw new BusinessException("COUPON_CURRENCY_UNSUPPORTED", "美元支付暂不支持人民币优惠券");
        }
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("DONATION_AMOUNT_INVALID", "打赏金额必须大于0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isDonationItem(ShopItem item) {
        return item != null && (item.getType() == ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS
                || item.getType() == ShopItem.Type.DONATION);
    }

    private ShopOrder applyPayment(ShopOrder order, ShopOrderPaymentVo payment) {
        order.setStatus(ShopOrder.Status.PAYING);
        order.setTransactionCode(payment.getTradeNo());
        order.setExpireTime(payment.getExpireTime() == null
                ? LocalDateTime.now().plusMinutes(UNPAID_ORDER_TTL_MINUTES)
                : payment.getExpireTime());
        order.setPaymentProvider(payment.getProvider());
        return order;
    }

    private Mono<ShopOrderCreateVo> toCreateVo(ShopOrder order, ShopItem item, ShopOrderPaymentVo payment) {
        return toVo(order, item).map(vo -> {
            ShopOrderCreateVo createVo = new ShopOrderCreateVo();
            createVo.setOrder(vo);
            createVo.setPayment(payment);
            return createVo;
        });
    }

    private Mono<ShopOrderCreateVo> markOrderFailed(ShopOrder order, Throwable error) {
        order.setStatus(ShopOrder.Status.FAILED);
        order.setDeliveryMessage(StringUtils.abbreviate(error.getMessage(), 500));
        return shopOrderRepository.save(order)
                .then(Mono.<ShopOrderCreateVo>error(error));
    }

    private ShopOrder markPaid(ShopOrder order, H5ZhiFuNotifyDto dto) {
        order.setStatus(ShopOrder.Status.PAID);
        order.setTransactionCode(dto.getTradeNo());
        order.setPaidTime(parseTime(dto.getPayTime()));
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.WAITING);
        return order;
    }

    private Mono<Void> saveTransaction(ShopOrder order, H5ZhiFuNotifyDto dto) {
        return shopTransactionRepository.findByCode(dto.getTradeNo())
                .switchIfEmpty(Mono.defer(() -> {
                    ShopTransaction transaction = new ShopTransaction();
                    transaction.setCode(dto.getTradeNo());
                    transaction.setOrderNo(order.getOrderNo());
                    transaction.setShopId(order.getShopId());
                    transaction.setContent(dto.getDescription());
                    transaction.setPayer(dto.getInTradeNo());
                    transaction.setReceiver(String.valueOf(dto.getAppId()));
                    transaction.setAmount(order.getAmount());
                    transaction.setCurrency(StringUtils.defaultIfBlank(order.getCurrency(), CURRENCY_CNY));
                    transaction.setType(ShopTransaction.Type.PAYMENT);
                    transaction.setCreateBy("payment-notify");
                    transaction.setUpdateBy("payment-notify");
                    return shopTransactionRepository.save(transaction);
                }))
                .then();
    }

    private Mono<Void> increaseBuyCount(ShopOrder order) {
        Mono<Void> itemCount = shopItemRepository.findById(order.getItemId())
                .map(item -> {
                    int current = item.getBuyCount() == null ? 0 : item.getBuyCount();
                    item.setBuyCount(current + order.getCount());
                    return item;
                })
                .flatMap(shopItemRepository::save)
                .then();
        if (order.getSkuId() == null) {
            return itemCount;
        }
        Mono<Void> skuCount = shopItemSkuRepository.findById(order.getSkuId())
                .map(sku -> {
                    int current = sku.getBuyCount() == null ? 0 : sku.getBuyCount();
                    sku.setBuyCount(current + order.getCount());
                    return sku;
                })
                .flatMap(shopItemSkuRepository::save)
                .then();
        return itemCount.then(skuCount);
    }

    private Mono<ShopOrder> deliverIfNeeded(ShopOrder order) {
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.SUCCESS) {
            return Mono.just(order);
        }
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.FAILED) {
            return Mono.just(order);
        }
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.PROCESSING) {
            return Mono.just(order);
        }
        return Mono.zip(resolveOrderItem(order), authUserGateway.getById(order.getBuyerId()))
                .flatMap(tuple -> deliverByMode(order, tuple.getT1(), tuple.getT2()));
    }

    private Mono<ShopOrder> retryAutomaticDelivery(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        validateRetryable(order, item);
        return automaticDelivery(order, item, buyer);
    }

    private void validateRetryable(ShopOrder order, ShopItem item) {
        if (order.getStatus() == ShopOrder.Status.PENDING || order.getStatus() == ShopOrder.Status.PAYING) {
            throw new BusinessException("ORDER_NOT_PAID", "订单尚未支付，不能重试交付");
        }
        if (order.getStatus() == ShopOrder.Status.CLOSED) {
            throw new BusinessException("ORDER_CLOSED", "订单已关闭，不能重试交付");
        }
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.PROCESSING) {
            throw new BusinessException("DELIVERY_PROCESSING", "订单正在交付中，请稍后查看");
        }
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.SUCCESS) {
            throw new BusinessException("DELIVERY_ALREADY_SUCCESS", "订单已完成交付，无需重试");
        }
        if (order.getDeliveryStatus() != ShopOrder.DeliveryStatus.FAILED) {
            throw new BusinessException("DELIVERY_NOT_FAILED", "只有交付失败的订单才能重试");
        }
        if (order.getPaidTime() == null && order.getStatus() != ShopOrder.Status.PAID
                && order.getStatus() != ShopOrder.Status.FAILED
                && order.getStatus() != ShopOrder.Status.DELIVERED) {
            throw new BusinessException("ORDER_NOT_PAID", "订单尚未支付，不能重试交付");
        }
        if (resolveDeliveryMode(item) != ShopItem.DeliveryMode.AUTOMATIC) {
            throw new BusinessException("DELIVERY_RETRY_UNSUPPORTED", "该订单不是自动交付，不能重试");
        }
    }

    private Mono<ShopOrder> deliverByMode(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        ShopItem.DeliveryMode deliveryMode = resolveDeliveryMode(item);
        return switch (deliveryMode) {
            case NONE -> completeWithoutDelivery(order);
            case MANUAL -> waitForManualDelivery(order);
            case AUTOMATIC -> automaticDelivery(order, item, buyer);
        };
    }

    private ShopItem.DeliveryMode resolveDeliveryMode(ShopItem item) {
        if (item == null) {
            return ShopItem.DeliveryMode.MANUAL;
        }
        if (item.getType() != null) {
            return item.getType().normalizeDeliveryMode(item.getDeliveryMode());
        }
        if (item.getDeliveryMode() != null) {
            return item.getDeliveryMode();
        }
        return ShopItem.DeliveryMode.MANUAL;
    }

    private Mono<ShopOrder> completeWithoutDelivery(ShopOrder order) {
        order.setStatus(ShopOrder.Status.DELIVERED);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.SUCCESS);
        order.setDeliveryMessage("无需交付");
        return shopOrderRepository.save(order);
    }

    private Mono<ShopOrder> waitForManualDelivery(ShopOrder order) {
        order.setStatus(ShopOrder.Status.PAID);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.WAITING);
        order.setDeliveryMessage("待人工交付");
        return shopOrderRepository.save(order);
    }

    private Mono<ShopOrder> automaticDelivery(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.PROCESSING);
        order.setDeliveryMessage("自动交付中");
        return shopOrderRepository.save(order)
                .flatMap(saved -> shopDeliveryService.deliver(saved, item, buyer)
                .onErrorResume(e -> Mono.just(DeliveryResult.failed(e.getMessage())))
                .flatMap(result -> {
                    saved.setDeliveryStatus(result.isSuccess() ? ShopOrder.DeliveryStatus.SUCCESS : ShopOrder.DeliveryStatus.FAILED);
                    saved.setDeliveryMessage(result.getMessage());
                    if (result.isSuccess()) {
                        saved.setStatus(ShopOrder.Status.DELIVERED);
                    } else {
                        saved.setStatus(ShopOrder.Status.FAILED);
                    }
                    return shopOrderRepository.save(saved);
                }));
    }

    private Mono<ShopOrderVo> toVo(ShopOrder order) {
        return resolveOrderItem(order)
                .flatMap(item -> toVo(order, item))
                .switchIfEmpty(toVo(order, null));
    }

    private Mono<ShopOrderVo> toVo(ShopOrder order, ShopItem item) {
        ShopOrderVo vo = new ShopOrderVo();
        vo.setOrderNo(order.getOrderNo());
        vo.setItemId(order.getItemId());
        vo.setItemName(StringUtils.defaultIfBlank(order.getItemName(), orderItemName(order, item)));
        ShopItem.Type itemType = item == null || item.getType() == null ? parseItemType(order.getItemType()) : item.getType();
        vo.setItemType(StringUtils.defaultIfBlank(order.getItemType(),
                itemType == null ? null : itemType.name()));
        vo.setItemTypeName(itemType == null ? null : itemType.getTitle());
        vo.setSkuId(order.getSkuId());
        vo.setSkuCode(order.getSkuCode());
        vo.setSkuName(order.getSkuName());
        vo.setDisplayName(displayName(vo.getItemName(), vo.getSkuName()));
        ShopItem.DeliveryMode deliveryMode = item == null ? ShopItem.DeliveryMode.MANUAL : resolveDeliveryMode(item);
        vo.setDeliveryMode(deliveryMode.name());
        vo.setDeliveryModeName(deliveryMode.getTitle());
        vo.setExtractable(isExtractable(item, order, deliveryMode));
        vo.setBuyerId(order.getBuyerId());
        vo.setCount(order.getCount());
        vo.setAmount(order.getAmount());
        vo.setCurrency(StringUtils.defaultIfBlank(order.getCurrency(), CURRENCY_CNY));
        vo.setOriginalAmount(order.getOriginalAmount() == null ? order.getAmount() : order.getOriginalAmount());
        vo.setDiscountAmount(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount());
        vo.setCouponCode(order.getCouponCode());
        vo.setProperties(ShopOrderFormParamValue.readProperties(order));
        vo.setItemSnapshot(readSnapshot(order.getItemSnapshot()));
        vo.setSkuSnapshot(readSnapshot(order.getSkuSnapshot()));
        vo.setStatus(order.getStatus());
        vo.setDeliveryStatus(order.getDeliveryStatus());
        vo.setDeliveryMessage(order.getDeliveryMessage());
        vo.setDeliveryRetryable(isDeliveryRetryable(item, order, deliveryMode));
        ShopDeliveryAction failureTask = resolveDeliveryFailureTask(order, item);
        vo.setDeliveryFailureTask(failureTask == null ? null : failureTask.name());
        vo.setDeliveryFailureTaskName(failureTask == null ? null : failureTask.getTitle());
        vo.setPaymentProvider(order.getPaymentProvider());
        vo.setOuterNo(order.getOuterNo());
        vo.setTransactionCode(order.getTransactionCode());
        vo.setCreateTime(order.getCreateTime());
        vo.setPaidTime(order.getPaidTime());
        vo.setExpireTime(order.getExpireTime());
        return authUserGateway.getById(order.getBuyerId())
                .doOnNext(buyer -> {
                    vo.setBuyerName(buyer.getUsername());
                    vo.setBuyerAvatar(buyer.getAvatar());
                    vo.setBuyerPhone(buyer.getPhone());
                    vo.setBuyerEmail(buyer.getEmail());
                })
                .thenReturn(vo)
                .onErrorReturn(vo);
    }

    private String orderItemName(ShopOrder order, ShopItem item) {
        if (item == null) {
            return null;
        }
        String itemName = item.getName();
        String skuName = order.getSkuName();
        if (StringUtils.isBlank(itemName) || StringUtils.isBlank(skuName)) {
            return itemName;
        }
        return StringUtils.removeEnd(itemName, " - " + skuName);
    }

    private void verifyNotify(H5ZhiFuNotifyDto dto) {
        if (StringUtils.isBlank(h5ZhiFuProperties.getKey())) {
            throw new ServiceException("H5支付密钥未配置");
        }
        if (h5ZhiFuProperties.getAppId() != null && !h5ZhiFuProperties.getAppId().equals(dto.getAppId())) {
            throw new BusinessException("PAY_APP_INVALID", "支付应用不匹配");
        }
        String expected = dto.getRawParams() == null
                ? H5ZhiFuSigner.sign(dto, h5ZhiFuProperties.getKey())
                : H5ZhiFuSigner.sign(dto.getRawParams(), h5ZhiFuProperties.getKey());
        if (!StringUtils.equalsIgnoreCase(expected, dto.getSign())) {
            throw new BusinessException("PAY_SIGN_INVALID", "支付回调签名错误");
        }
    }

    private void verifyAmount(ShopOrder order, H5ZhiFuNotifyDto dto) {
        int expected = order.getAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        if (dto.getAmount() == null || expected != dto.getAmount()) {
            throw new BusinessException("PAY_AMOUNT_INVALID", "支付金额不匹配");
        }
    }

    private Mono<Void> handleStripePaidSession(StripeCheckoutSessionDto session) {
        if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
            return Mono.empty();
        }
        return shopOrderRepository.findByOrderNo(session.getClientReferenceId())
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> {
                    verifyStripeSession(order, session);
                    if (order.getStatus() == ShopOrder.Status.CLOSED) {
                        return Mono.empty();
                    }
                    boolean paidBefore = order.getPaidTime() != null
                            || order.getStatus() == ShopOrder.Status.PAID
                            || order.getStatus() == ShopOrder.Status.DELIVERED;
                    Mono<ShopOrder> paid = paidBefore
                            ? Mono.just(order)
                            : shopOrderRepository.save(markStripePaid(order, session))
                            .flatMap(saved -> saveStripeTransaction(saved, session).thenReturn(saved))
                            .flatMap(saved -> increaseBuyCount(saved).thenReturn(saved))
                            .flatMap(saved -> shopCouponService.markUsed(saved.getCouponCode()).thenReturn(saved));
                    return paid.flatMap(this::deliverIfNeeded).then();
                });
    }

    private Mono<Void> closeStripeSessionOrder(StripeCheckoutSessionDto session, String message) {
        return shopOrderRepository.findByOrderNo(session.getClientReferenceId())
                .switchIfEmpty(Mono.empty())
                .flatMap(order -> {
                    verifyStripeSessionIdentity(order, session);
                    if (order.getPaidTime() != null
                            || order.getStatus() == ShopOrder.Status.PAID
                            || order.getStatus() == ShopOrder.Status.DELIVERED
                            || order.getStatus() == ShopOrder.Status.FAILED) {
                        return Mono.empty();
                    }
                    if (order.getStatus() != ShopOrder.Status.PENDING && order.getStatus() != ShopOrder.Status.PAYING) {
                        return Mono.empty();
                    }
                    order.setStatus(ShopOrder.Status.CLOSED);
                    order.setDeliveryStatus(ShopOrder.DeliveryStatus.SKIPPED);
                    order.setDeliveryMessage(message);
                    return shopOrderRepository.save(order).then();
                });
    }

    private ShopOrder markStripePaid(ShopOrder order, StripeCheckoutSessionDto session) {
        order.setStatus(ShopOrder.Status.PAID);
        order.setPaymentProvider(STRIPE_PROVIDER);
        order.setTransactionCode(session.getId());
        order.setOuterNo(StringUtils.left(session.getPaymentIntent(), 64));
        order.setPaidTime(LocalDateTime.now());
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.WAITING);
        return order;
    }

    private Mono<Void> saveStripeTransaction(ShopOrder order, StripeCheckoutSessionDto session) {
        String code = StringUtils.defaultIfBlank(session.getPaymentIntent(), session.getId());
        return shopTransactionRepository.findByCode(code)
                .switchIfEmpty(Mono.defer(() -> {
                    ShopTransaction transaction = new ShopTransaction();
                    transaction.setCode(code);
                    transaction.setOrderNo(order.getOrderNo());
                    transaction.setShopId(order.getShopId());
                    transaction.setContent(StringUtils.left("Stripe Checkout " + session.getId(), 512));
                    transaction.setPayer(StringUtils.defaultIfBlank(session.getCustomerEmail(), order.getOrderNo()));
                    transaction.setReceiver(STRIPE_PROVIDER);
                    transaction.setAmount(order.getAmount());
                    transaction.setCurrency(StringUtils.defaultIfBlank(order.getCurrency(), CURRENCY_CNY));
                    transaction.setType(ShopTransaction.Type.PAYMENT);
                    transaction.setCreateBy("stripe-webhook");
                    transaction.setUpdateBy("stripe-webhook");
                    return shopTransactionRepository.save(transaction);
                }))
                .then();
    }

    private void verifyStripeSession(ShopOrder order, StripeCheckoutSessionDto session) {
        verifyStripeSessionIdentity(order, session);
        String currency = normalizeCurrency(session.getCurrency());
        if (StringUtils.isBlank(currency)) {
            throw new BusinessException("STRIPE_CURRENCY_INVALID", "Stripe 支付币种缺失");
        }
        String orderCurrency = normalizeCurrency(StringUtils.defaultIfBlank(order.getCurrency(), CURRENCY_CNY));
        if (!orderCurrency.equals(currency)) {
            throw new BusinessException("STRIPE_CURRENCY_INVALID", "Stripe 支付币种不匹配");
        }
        long expected = toMinor(order.getAmount(), currency);
        if (session.getAmountTotal() == null || expected != session.getAmountTotal()) {
            throw new BusinessException("STRIPE_AMOUNT_INVALID", "Stripe 支付金额不匹配");
        }
    }

    private void verifyStripeSessionIdentity(ShopOrder order, StripeCheckoutSessionDto session) {
        if (!STRIPE_PROVIDER.equalsIgnoreCase(StringUtils.defaultString(order.getPaymentProvider()))) {
            throw new BusinessException("STRIPE_ORDER_PROVIDER_INVALID", "订单支付方式不匹配");
        }
        if (StringUtils.isNotBlank(order.getTransactionCode())
                && !StringUtils.equals(order.getTransactionCode(), session.getId())) {
            throw new BusinessException("STRIPE_SESSION_MISMATCH", "Stripe Checkout Session 不匹配");
        }
    }

    private long toMinor(BigDecimal amount, String currency) {
        int scale = ZERO_DECIMAL_CURRENCIES.contains(currency) ? 0 : 2;
        BigDecimal multiplier = scale == 0 ? BigDecimal.ONE : BigDecimal.valueOf(100);
        return amount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private String normalizeCurrency(String currency) {
        return StringUtils.trimToEmpty(currency).toLowerCase(Locale.ROOT);
    }

    private String initialPaymentProvider(ShopOrderDto dto) {
        String provider = StringUtils.trimToEmpty(dto == null ? null : dto.getPaymentProvider())
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        String payType = StringUtils.trimToEmpty(dto == null ? null : dto.getPayType())
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        return STRIPE_PROVIDER.equals(provider) || STRIPE_PROVIDER.equals(payType) ? STRIPE_PROVIDER : "h5zhifu";
    }

    private Long parseItemId(String itemId) {
        try {
            return Long.parseLong(itemId);
        } catch (Exception e) {
            throw new BusinessException("INVALID_ITEM", "商品id不正确");
        }
    }

    private LocalDateTime parseTime(String value) {
        if (StringUtils.isBlank(value)) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value, PAY_TIME_FORMATTER);
    }

    private static LocalDateTime orderSortTime(ShopOrder order) {
        if (order.getCreateTime() != null) {
            return order.getCreateTime();
        }
        return order.getUpdateTime();
    }

    private Mono<ShopOrder> ensureExtractable(ShopOrder order) {
        if (order.getStatus() != ShopOrder.Status.DELIVERED
                || order.getDeliveryStatus() != ShopOrder.DeliveryStatus.SUCCESS) {
            return Mono.error(new BusinessException("ORDER_NOT_DELIVERED", "订单尚未完成交付"));
        }
        return Mono.just(order);
    }

    private Mono<ShopOrderDelivery> markExtractedForBuyer(ShopOrderDelivery delivery, ShopOrder order, PortalUserVo user) {
        if (delivery.getExtractedTime() != null || !user.getId().equals(order.getBuyerId())) {
            return Mono.just(delivery);
        }
        delivery.setExtractedTime(LocalDateTime.now());
        return shopOrderDeliveryRepository.save(delivery);
    }

    private Mono<ShopOrderDeliveryExtractVo> toDeliveryExtractVo(ShopOrderDelivery delivery, boolean includeAuditFile) {
        if (!isLicenseDelivery(delivery) || shopLicenseKeyPairRepository == null) {
            return Mono.just(new ShopOrderDeliveryExtractVo(delivery));
        }
        return shopLicenseKeyPairRepository.findByOrderNo(delivery.getOrderNo())
                .map(license -> new ShopOrderDeliveryExtractVo(delivery, licenseFileMetadata(license, includeAuditFile)))
                .defaultIfEmpty(new ShopOrderDeliveryExtractVo(delivery));
    }

    private Mono<ShopOrderDeliveryExtractVo> toDeliveryExtractVo(List<ShopOrderDelivery> deliveries,
                                                                 boolean includeAuditFile) {
        if (deliveries == null || deliveries.isEmpty()) {
            return Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无交付快照"));
        }
        String orderNo = deliveries.get(0).getOrderNo();
        return Flux.fromIterable(deliveries)
                .concatMap(delivery -> toDeliveryExtractVo(delivery, includeAuditFile))
                .collectList()
                .map(items -> ShopOrderDeliveryExtractVo.combine(orderNo, items))
                .switchIfEmpty(Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无交付快照")));
    }

    private Mono<ShopOrderDeliveryDownloadVo> toDeliveryDownload(ShopOrderDelivery delivery, String fileCode,
                                                                boolean includeAuditFile) {
        if (!isLicenseDelivery(delivery)) {
            return Mono.error(new BusinessException("DELIVERY_FILE_UNSUPPORTED", "该交付内容不支持文件下载"));
        }
        if (shopLicenseKeyPairRepository == null) {
            return Mono.error(new BusinessException("DELIVERY_FILE_NOT_FOUND", "授权文件不存在"));
        }
        return shopLicenseKeyPairRepository.findByOrderNo(delivery.getOrderNo())
                .switchIfEmpty(Mono.error(new BusinessException("DELIVERY_FILE_NOT_FOUND", "授权文件不存在")))
                .map(license -> buildDeliveryDownload(license, fileCode, includeAuditFile));
    }

    private ShopOrderDeliveryDownloadVo buildDeliveryDownload(ShopLicenseKeyPair license, String fileCode,
                                                              boolean includeAuditFile) {
        String normalizedCode = StringUtils.lowerCase(StringUtils.trimToEmpty(fileCode));
        if (LICENSE_PAYLOAD_FILE_CODE.equals(normalizedCode) && !includeAuditFile) {
            throw new BusinessException("DELIVERY_FILE_FORBIDDEN", "无权下载该文件");
        }
        String content = deliveryFileContent(license, normalizedCode);
        ShopOrderDeliveryDownloadVo file = new ShopOrderDeliveryDownloadVo();
        file.setName(licenseFileName(license, normalizedCode));
        file.setContentType(deliveryFileContentType(license, normalizedCode));
        file.setContent(content.getBytes(StandardCharsets.UTF_8));
        file.setSize((long) file.getContent().length);
        return file;
    }

    private List<ShopOrderDeliveryFileVo> licenseFileMetadata(ShopLicenseKeyPair license, boolean includeAuditFile) {
        boolean brandRemovalStatement = isBrandRemovalStatement(license);
        ShopOrderDeliveryFileVo licenseFile = licenseFile(license, LICENSE_FILE_CODE,
                brandRemovalStatement ? "授权声明文件" : "授权许可文件",
                LICENSE_CONTENT_TYPE, StringUtils.defaultString(license.getSignature()));
        ShopOrderDeliveryFileVo jsonFile = licenseFile(license, LICENSE_JSON_FILE_CODE,
                brandRemovalStatement ? "授权声明 JSON 文件" : "部署 JSON 文件",
                JSON_CONTENT_TYPE, StringUtils.defaultString(license.getSignature()));
        if (!includeAuditFile) {
            return List.of(licenseFile, jsonFile);
        }
        ShopOrderDeliveryFileVo payloadFile = licenseFile(license, LICENSE_PAYLOAD_FILE_CODE,
                brandRemovalStatement ? "声明审计 Payload" : "授权审计 Payload",
                JSON_CONTENT_TYPE, StringUtils.defaultString(license.getCertificate()));
        return List.of(licenseFile, jsonFile, payloadFile);
    }

    private String deliveryFileContent(ShopLicenseKeyPair license, String normalizedCode) {
        return switch (normalizedCode) {
            case LICENSE_FILE_CODE, LICENSE_JSON_FILE_CODE -> StringUtils.defaultString(license.getSignature());
            case LICENSE_PAYLOAD_FILE_CODE -> StringUtils.defaultString(license.getCertificate());
            default -> throw new BusinessException("DELIVERY_FILE_NOT_FOUND", "授权文件不存在");
        };
    }

    private String deliveryFileContentType(ShopLicenseKeyPair license, String normalizedCode) {
        return LICENSE_FILE_CODE.equals(normalizedCode) ? LICENSE_CONTENT_TYPE : JSON_CONTENT_TYPE;
    }

    private ShopOrderDeliveryFileVo licenseFile(ShopLicenseKeyPair license, String code, String description,
                                                String contentType, String content) {
        ShopOrderDeliveryFileVo file = new ShopOrderDeliveryFileVo();
        file.setCode(code);
        file.setName(licenseFileName(license, code));
        file.setDescription(description);
        file.setContentType(contentType);
        file.setSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        return file;
    }

    private String licenseFileName(ShopLicenseKeyPair license, String fileCode) {
        String prefix = isBrandRemovalStatement(license)
                ? "flyfish-viewer-brand-removal-statement"
                : "license";
        return switch (fileCode) {
            case LICENSE_FILE_CODE -> prefix + ".lic";
            case LICENSE_JSON_FILE_CODE -> prefix + "-license.json";
            case LICENSE_PAYLOAD_FILE_CODE -> prefix + "-payload.json";
            default -> prefix + "-license.dat";
        };
    }

    private boolean isLicenseDelivery(ShopOrderDelivery delivery) {
        return delivery != null
                && StringUtils.equalsIgnoreCase(ShopOrderDelivery.DeliveryType.LICENSE.name(), delivery.getDeliveryType());
    }

    private boolean isBrandRemovalStatement(ShopLicenseKeyPair license) {
        return license != null
                && (StringUtils.containsIgnoreCase(license.getSignature(), "flyfish-viewer-brand-removal-statement")
                || StringUtils.containsIgnoreCase(license.getCertificate(), "brand-removal"));
    }

    private boolean isExtractable(ShopItem item, ShopOrder order, ShopItem.DeliveryMode deliveryMode) {
        if (item == null || item.getType() == null || order == null) {
            return false;
        }
        if (order.getStatus() != ShopOrder.Status.DELIVERED
                || order.getDeliveryStatus() != ShopOrder.DeliveryStatus.SUCCESS) {
            return false;
        }
        return deliveryMode == ShopItem.DeliveryMode.AUTOMATIC
                && (ShopItemDeliveryPlan.hasAction(item, ShopDeliveryAction.DIGITAL_DOWNLOAD)
                || ShopItemDeliveryPlan.hasAction(item, ShopDeliveryAction.LICENSE));
    }

    private boolean isDeliveryRetryable(ShopItem item, ShopOrder order, ShopItem.DeliveryMode deliveryMode) {
        if (item == null || order == null) {
            return false;
        }
        if (deliveryMode != ShopItem.DeliveryMode.AUTOMATIC
                || order.getDeliveryStatus() != ShopOrder.DeliveryStatus.FAILED) {
            return false;
        }
        return order.getPaidTime() != null
                || order.getStatus() == ShopOrder.Status.PAID
                || order.getStatus() == ShopOrder.Status.FAILED
                || order.getStatus() == ShopOrder.Status.DELIVERED;
    }

    private ShopDeliveryAction resolveDeliveryFailureTask(ShopOrder order, ShopItem item) {
        if (order == null || order.getDeliveryStatus() != ShopOrder.DeliveryStatus.FAILED || item == null) {
            return null;
        }
        String message = StringUtils.defaultString(order.getDeliveryMessage());
        if (StringUtils.containsAnyIgnoreCase(message, "授权", "license")) {
            return ShopDeliveryAction.LICENSE;
        }
        if (StringUtils.containsAnyIgnoreCase(message, "仓库", "Git", "Github", "Gitea", "Gitee")) {
            return ShopDeliveryAction.GIT_REPOSITORY_ACCESS;
        }
        if (StringUtils.containsAnyIgnoreCase(message, "数字", "提货", "下载")) {
            return ShopDeliveryAction.DIGITAL_DOWNLOAD;
        }
        var actions = ShopItemDeliveryPlan.actions(item);
        return actions.size() == 1 ? actions.getFirst() : null;
    }
}
