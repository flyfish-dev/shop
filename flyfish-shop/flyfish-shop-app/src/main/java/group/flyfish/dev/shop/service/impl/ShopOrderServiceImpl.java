package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.domain.dto.ShopCouponApplyDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDeliveryDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.domain.po.ShopTransaction;
import group.flyfish.dev.shop.domain.vo.ShopCouponApplyVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderCreateVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryDownloadVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryFileVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.domain.vo.ShopOrderVo;
import group.flyfish.dev.shop.domain.vo.ShopPurchaseAvailabilityVo;
import group.flyfish.dev.shop.service.CouponDiscount;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
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

@Service
@RequiredArgsConstructor
public class ShopOrderServiceImpl implements ShopOrderService {

    private static final DateTimeFormatter PAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long UNPAID_ORDER_TTL_MINUTES = 15;
    private static final String EXPIRED_ORDER_MESSAGE = "订单超过15分钟未支付，已自动关闭";
    private static final String ORDER_PROPERTY_DONATION_AMOUNT = "donationAmount";
    private static final String LICENSE_FILE_CODE = "lic";
    private static final String LICENSE_JSON_FILE_CODE = "json";
    private static final String LICENSE_PAYLOAD_FILE_CODE = "payload";
    private static final String LICENSE_CONTENT_TYPE = "application/octet-stream";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final Comparator<ShopOrder> ORDER_TIME_DESC = Comparator
            .comparing(ShopOrderServiceImpl::orderSortTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ShopOrder::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final ShopOrderRepository shopOrderRepository;
    private final ShopTransactionRepository shopTransactionRepository;
    private final ShopItemRepository shopItemRepository;
    private final ShopOrderDeliveryRepository shopOrderDeliveryRepository;
    private final ShopLicenseKeyPairRepository shopLicenseKeyPairRepository;
    private final PayService payService;
    private final ShopDeliveryService shopDeliveryService;
    private final AuthUserGateway authUserGateway;
    private final ShopCouponService shopCouponService;
    private final H5ZhiFuProperties h5ZhiFuProperties;
    private final GitRepositoryAccessOrderChecker gitRepositoryAccessOrderChecker;
    private final ShopContractService shopContractService;

    @Override
    @Transactional
    public Mono<ShopOrderCreateVo> createOrder(ShopOrderDto dto, PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        Long itemId = parseItemId(dto.getItemId());
        return shopItemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> ensurePurchasable(item, buyer)
                        .then(shopContractService.requireSigned(item.getId(), buyer, dto.getContractSignToken()))
                        .then(Mono.defer(() -> {
                    int count = normalizeCount(dto.getCount(), item);
                    BigDecimal originalAmount = calculateOriginalAmount(item, count, dto.getDonationAmount());
                    return shopCouponService.applyCoupon(dto.getCouponCode(), originalAmount)
                            .map(discount -> buildOrder(dto, buyer, item, count, discount))
                            .flatMap(shopOrderRepository::save)
                            .flatMap(saved -> shopContractService
                                    .bindOrder(dto.getContractSignToken(), saved.getOrderNo(), item.getId(), buyer.getId())
                                    .thenReturn(saved))
                            .flatMap(saved -> payService.pay(saved, item, dto)
                                    .flatMap(payment -> shopOrderRepository.save(applyPayment(saved, payment))
                                            .flatMap(paying -> toCreateVo(paying, item, payment)))
                                    .onErrorResume(e -> markOrderFailed(saved, e)));
                })));
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
                .flatMap(item -> {
                    ensureItemEnabled(item);
                    int count = normalizeCount(dto.getCount(), item);
                    BigDecimal originalAmount = calculateOriginalAmount(item, count, dto.getDonationAmount());
                    return shopCouponService.applyCoupon(dto.getCouponCode(), originalAmount);
                })
                .map(ShopCouponApplyVo::new);
    }

    @Override
    public Mono<ShopPurchaseAvailabilityVo> checkPurchaseAvailability(Long itemId, PortalUserVo buyer) {
        ShopAuthorizationUtils.requireLogin(buyer);
        return shopItemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> gitRepositoryAccessOrderChecker.check(item, buyer));
    }

    @Override
    public Flux<ShopOrderVo> getOrders(PortalUserVo buyer, Long itemId) {
        ShopAuthorizationUtils.requireLogin(buyer);
        Flux<ShopOrder> orders = ShopAuthorizationUtils.isShopMaintainer(buyer)
                ? adminOrders(itemId)
                : buyerOrders(buyer.getId(), itemId);
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

    private Flux<ShopOrder> adminOrders(Long itemId) {
        return itemId == null
                ? shopOrderRepository.findAllOrderByCreateTimeDesc()
                : shopOrderRepository.findAllByItemIdOrderByCreateTimeDesc(itemId);
    }

    private Flux<ShopOrder> buyerOrders(Long buyerId, Long itemId) {
        return itemId == null
                ? shopOrderRepository.findAllByBuyerIdOrderByCreateTimeDesc(buyerId)
                : shopOrderRepository.findAllByBuyerIdAndItemIdOrderByCreateTimeDesc(buyerId, itemId);
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
                .flatMap(order -> shopOrderDeliveryRepository.findByOrderNo(order.getOrderNo())
                        .switchIfEmpty(Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无可提取内容"))))
                .flatMap(delivery -> {
                    if (delivery.getExtractedTime() != null) {
                        return Mono.just(delivery);
                    }
                    delivery.setExtractedTime(LocalDateTime.now());
                    return shopOrderDeliveryRepository.save(delivery);
                })
                .flatMap(delivery -> toDeliveryExtractVo(delivery, false));
    }

    @Override
    public Mono<ShopOrderDeliveryExtractVo> viewDelivery(PortalUserVo user, String orderNo) {
        ShopAuthorizationUtils.requireShopMaintainer(user);
        return closeExpiredUnpaidOrders()
                .then(shopOrderRepository.findByOrderNo(orderNo))
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> shopOrderDeliveryRepository.findByOrderNo(order.getOrderNo())
                        .switchIfEmpty(Mono.error(new BusinessException("DELIVERY_NOT_FOUND", "该订单暂无交付快照"))))
                .flatMap(delivery -> toDeliveryExtractVo(delivery, true));
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
                .flatMap(order -> shopOrderDeliveryRepository.findByOrderNo(order.getOrderNo())
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
                .flatMap(shopOrderRepository::save)
                .flatMap(this::toVo);
    }

    @Override
    @Transactional
    public Mono<ShopOrderVo> retryDelivery(String orderNo) {
        return shopOrderRepository.findByOrderNo(orderNo)
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> Mono.zip(shopItemRepository.findById(order.getItemId()),
                                authUserGateway.getById(order.getBuyerId()))
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

    @Override
    @Transactional
    public Mono<Void> handlePaymentNotify(H5ZhiFuNotifyDto dto) {
        verifyNotify(dto);
        return shopOrderRepository.findByOrderNo(dto.getOutTradeNo())
                .switchIfEmpty(Mono.error(new BusinessException("ORDER_NOT_FOUND", "订单不存在")))
                .flatMap(order -> {
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
    public Mono<Integer> closeExpiredUnpaidOrders() {
        return shopOrderRepository.closeExpiredUnpaidOrders(LocalDateTime.now(), EXPIRED_ORDER_MESSAGE);
    }

    private ShopOrder buildOrder(ShopOrderDto dto, PortalUserVo buyer, ShopItem item, int count,
                                 CouponDiscount discount) {
        ShopOrder order = new ShopOrder();
        order.setOrderNo("FF" + IdGenerators.idString());
        order.setItemId(item.getId());
        order.setShopId(item.getShopId());
        order.setBuyerId(buyer.getId());
        order.setCount(count);
        order.setProperties(JacksonUtils.toJson(orderProperties(dto, item, discount.originalAmount())));
        order.setOriginalAmount(discount.originalAmount());
        order.setDiscountAmount(discount.discountAmount());
        order.setCouponCode(discount.code());
        order.setAmount(discount.payableAmount());
        order.setPaymentProvider("h5zhifu");
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

    private Map<String, Object> orderProperties(ShopOrderDto dto, ShopItem item, BigDecimal originalAmount) {
        Map<String, Object> properties = gitRepositoryAccessOrderChecker.orderProperties(dto, item);
        if (isDonationAccess(item)) {
            properties.put(ORDER_PROPERTY_DONATION_AMOUNT, originalAmount);
        }
        return properties;
    }

    private int normalizeCount(Integer count, ShopItem item) {
        int normalized = count == null ? 1 : count;
        if (normalized <= 0) {
            throw new BusinessException("INVALID_COUNT", "购买数量必须大于0");
        }
        if (isDonationAccess(item) && normalized != 1) {
            throw new BusinessException("INVALID_COUNT", "打赏开通商品不支持购买数量");
        }
        return normalized;
    }

    private BigDecimal calculateOriginalAmount(ShopItem item, int count, BigDecimal donationAmount) {
        if (isDonationAccess(item)) {
            return normalizeDonationAmount(item, donationAmount);
        }
        return item.getPrice().multiply(BigDecimal.valueOf(count));
    }

    private BigDecimal normalizeDonationAmount(ShopItem item, BigDecimal donationAmount) {
        BigDecimal minimumAmount = money(item.getPrice());
        BigDecimal amount = money(donationAmount == null ? minimumAmount : donationAmount);
        if (amount.compareTo(minimumAmount) < 0) {
            throw new BusinessException("DONATION_AMOUNT_TOO_LOW",
                    "打赏金额不能低于 ¥" + minimumAmount.toPlainString());
        }
        return amount;
    }

    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("DONATION_AMOUNT_INVALID", "打赏金额必须大于0");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isDonationAccess(ShopItem item) {
        return item != null && item.getType() == ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS;
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
                    transaction.setType(ShopTransaction.Type.PAYMENT);
                    transaction.setCreateBy("payment-notify");
                    transaction.setUpdateBy("payment-notify");
                    return shopTransactionRepository.save(transaction);
                }))
                .then();
    }

    private Mono<Void> increaseBuyCount(ShopOrder order) {
        return shopItemRepository.findById(order.getItemId())
                .map(item -> {
                    int current = item.getBuyCount() == null ? 0 : item.getBuyCount();
                    item.setBuyCount(current + order.getCount());
                    return item;
                })
                .flatMap(shopItemRepository::save)
                .then();
    }

    private Mono<ShopOrder> deliverIfNeeded(ShopOrder order) {
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.SUCCESS) {
            return Mono.just(order);
        }
        if (order.getDeliveryStatus() == ShopOrder.DeliveryStatus.FAILED) {
            return Mono.just(order);
        }
        return Mono.zip(shopItemRepository.findById(order.getItemId()), authUserGateway.getById(order.getBuyerId()))
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
        return shopItemRepository.findById(order.getItemId())
                .flatMap(item -> toVo(order, item))
                .switchIfEmpty(toVo(order, null));
    }

    private Mono<ShopOrderVo> toVo(ShopOrder order, ShopItem item) {
        ShopOrderVo vo = new ShopOrderVo();
        vo.setOrderNo(order.getOrderNo());
        vo.setItemId(order.getItemId());
        vo.setItemName(item == null ? null : item.getName());
        vo.setItemType(item == null || item.getType() == null ? null : item.getType().name());
        vo.setItemTypeName(item == null || item.getType() == null ? null : item.getType().getTitle());
        ShopItem.DeliveryMode deliveryMode = item == null ? ShopItem.DeliveryMode.MANUAL : resolveDeliveryMode(item);
        vo.setDeliveryMode(deliveryMode.name());
        vo.setDeliveryModeName(deliveryMode.getTitle());
        vo.setExtractable(isExtractable(item, order, deliveryMode));
        vo.setBuyerId(order.getBuyerId());
        vo.setCount(order.getCount());
        vo.setAmount(order.getAmount());
        vo.setOriginalAmount(order.getOriginalAmount() == null ? order.getAmount() : order.getOriginalAmount());
        vo.setDiscountAmount(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount());
        vo.setCouponCode(order.getCouponCode());
        vo.setStatus(order.getStatus());
        vo.setDeliveryStatus(order.getDeliveryStatus());
        vo.setDeliveryMessage(order.getDeliveryMessage());
        vo.setDeliveryRetryable(isDeliveryRetryable(item, order, deliveryMode));
        ShopDeliveryAction failureTask = resolveDeliveryFailureTask(order, item);
        vo.setDeliveryFailureTask(failureTask == null ? null : failureTask.name());
        vo.setDeliveryFailureTaskName(failureTask == null ? null : failureTask.getTitle());
        vo.setPaymentProvider(order.getPaymentProvider());
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
        String content = switch (normalizedCode) {
            case LICENSE_FILE_CODE, LICENSE_JSON_FILE_CODE -> StringUtils.defaultString(license.getSignature());
            case LICENSE_PAYLOAD_FILE_CODE -> StringUtils.defaultString(license.getCertificate());
            default -> throw new BusinessException("DELIVERY_FILE_NOT_FOUND", "授权文件不存在");
        };
        ShopOrderDeliveryDownloadVo file = new ShopOrderDeliveryDownloadVo();
        file.setName(licenseFileName(license, normalizedCode));
        file.setContentType(LICENSE_FILE_CODE.equals(normalizedCode) ? LICENSE_CONTENT_TYPE : JSON_CONTENT_TYPE);
        file.setContent(content.getBytes(StandardCharsets.UTF_8));
        file.setSize((long) file.getContent().length);
        return file;
    }

    private List<ShopOrderDeliveryFileVo> licenseFileMetadata(ShopLicenseKeyPair license, boolean includeAuditFile) {
        ShopOrderDeliveryFileVo licenseFile = licenseFile(license, LICENSE_FILE_CODE, "授权许可文件",
                LICENSE_CONTENT_TYPE, StringUtils.defaultString(license.getSignature()));
        ShopOrderDeliveryFileVo jsonFile = licenseFile(license, LICENSE_JSON_FILE_CODE, "部署 JSON 文件",
                JSON_CONTENT_TYPE, StringUtils.defaultString(license.getSignature()));
        if (!includeAuditFile) {
            return List.of(licenseFile, jsonFile);
        }
        ShopOrderDeliveryFileVo payloadFile = licenseFile(license, LICENSE_PAYLOAD_FILE_CODE, "授权审计 Payload",
                JSON_CONTENT_TYPE, StringUtils.defaultString(license.getCertificate()));
        return List.of(licenseFile, jsonFile, payloadFile);
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
        return switch (fileCode) {
            case LICENSE_FILE_CODE -> "license.lic";
            case LICENSE_JSON_FILE_CODE -> "license.json";
            case LICENSE_PAYLOAD_FILE_CODE -> "license-payload.json";
            default -> "license.dat";
        };
    }

    private boolean isLicenseDelivery(ShopOrderDelivery delivery) {
        return delivery != null
                && StringUtils.equalsIgnoreCase(ShopOrderDelivery.DeliveryType.LICENSE.name(), delivery.getDeliveryType());
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
        if (StringUtils.containsAnyIgnoreCase(message, "授权", "license", "Office 预览授权")) {
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
