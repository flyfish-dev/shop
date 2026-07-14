package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.git.client.GiteaRepositoryClient;
import group.flyfish.dev.git.client.GiteeRepositoryClient;
import group.flyfish.dev.git.client.GithubRepositoryClient;
import group.flyfish.dev.git.config.GiteaProperties;
import group.flyfish.dev.git.config.GithubProperties;
import group.flyfish.dev.git.service.GitAccessTokenService;
import group.flyfish.dev.shop.git.GitRepositoryAccessResolver;
import group.flyfish.dev.shop.git.ResolvedGitRepository;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.dto.ShopOrderDto;
import group.flyfish.dev.shop.domain.dto.ShopOrderDeliveryDto;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.shop.pricing.ShopPricingProperties;
import group.flyfish.dev.shop.pricing.ShopPricingService;
import group.flyfish.dev.shop.service.CouponDiscount;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.PayService;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopCouponService;
import group.flyfish.dev.shop.service.ShopDeliveryService;
import group.flyfish.dev.shop.service.checker.GitRepositoryAccessOrderChecker;
import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopOrderServiceImplTest {

    @Test
    void closesExpiredUnpaidOrders() {
        ShopOrderRepository repository = mock(ShopOrderRepository.class);
        when(repository.closeExpiredUnpaidOrders(any(LocalDateTime.class), eq("订单超过15分钟未支付，已自动关闭")))
                .thenReturn(Mono.just(2));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                repository,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        StepVerifier.create(service.closeExpiredUnpaidOrders())
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    void returnsDuplicateAvailabilityForGithubRepository() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem currentItem = gitItem(10L, "github", "wybaby168", "office-render-demo", "pull");
        ShopItem purchasedItem = gitItem(11L, "github", "wybaby168", "office-render-demo", "pull");
        ShopOrder purchasedOrder = paidOrder("FF1001", 11L);
        purchasedOrder.setProperties("""
                {"gitProvider":"github","gitRepositories":["wybaby168/office-render-demo"]}
                """);
        when(itemRepository.findById(10L)).thenReturn(Mono.just(currentItem));
        when(itemRepository.findById(11L)).thenReturn(Mono.just(purchasedItem));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.just(purchasedOrder));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.checkPurchaseAvailability(10L, oauthBuyer(OAuthType.GITHUB, "github")))
                .assertNext(availability -> {
                    assertFalse(availability.isPurchasable());
                    assertEquals("GIT_REPOSITORY_ALREADY_PURCHASED", availability.getReasonCode());
                    assertEquals("FF1001", availability.getConflictOrderNo());
                    assertEquals("wybaby168/office-render-demo", availability.getConflictRepositories().get(0));
                    assertEquals("您已购买过 GitHub 仓库 wybaby168/office-render-demo，无需重复购买；可在我的订单中查看开通记录。",
                            availability.getMessage());
                })
                .verifyComplete();
    }

    @Test
    void blocksDuplicateWhenPaidOrderUsesSameItem() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem currentItem = gitItem(30L, "github", "wybaby168", "office-render-demo", "pull");
        when(itemRepository.findById(30L)).thenReturn(Mono.just(currentItem));
        ShopOrder purchasedOrder = paidOrder("FF3001", 30L);
        purchasedOrder.setProperties("""
                {"gitProvider":"github","gitRepositories":["wybaby168/office-render-demo"]}
                """);
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.just(purchasedOrder));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.checkPurchaseAvailability(30L, oauthBuyer(OAuthType.GITHUB, "github")))
                .assertNext(availability -> {
                    assertFalse(availability.isPurchasable());
                    assertEquals("FF3001", availability.getConflictOrderNo());
                    assertEquals("wybaby168/office-render-demo", availability.getConflictRepositories().get(0));
                })
                .verifyComplete();
    }

    @Test
    void blocksDuplicateFromOrderRepositorySnapshot() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem currentItem = gitItem(40L, "github", "wybaby168", "office-render-demo", "pull");
        ShopOrder purchasedOrder = paidOrder("FF4001", 41L);
        purchasedOrder.setProperties("""
                {"gitProvider":"github","gitRepositories":["wybaby168/office-render-demo"]}
                """);
        when(itemRepository.findById(40L)).thenReturn(Mono.just(currentItem));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.just(purchasedOrder));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.checkPurchaseAvailability(40L, oauthBuyer(OAuthType.GITHUB, "github")))
                .assertNext(availability -> {
                    assertFalse(availability.isPurchasable());
                    assertEquals("GIT_REPOSITORY_ALREADY_PURCHASED", availability.getReasonCode());
                    assertEquals("FF4001", availability.getConflictOrderNo());
                    assertEquals("wybaby168/office-render-demo", availability.getConflictRepositories().get(0));
                })
                .verifyComplete();
    }

    @Test
    void blocksDuplicateGiteaRepositoryBeforePayment() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem currentItem = gitItem(20L, "gitea", "flyfish", "viewer", "read");
        ShopItem purchasedItem = gitItem(21L, "gitea", "flyfish", "viewer", "read");
        ShopOrder purchasedOrder = paidOrder("FF2001", 21L);
        purchasedOrder.setProperties("""
                {"gitProvider":"gitea","gitRepositories":["flyfish/viewer"]}
                """);
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("20");
        when(itemRepository.findById(20L)).thenReturn(Mono.just(currentItem));
        when(itemRepository.findById(21L)).thenReturn(Mono.just(purchasedItem));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.just(purchasedOrder));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.createOrder(dto, oauthBuyer(OAuthType.GITEA, "gitea")))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("GIT_REPOSITORY_ALREADY_PURCHASED", exception.getCode());
                })
                .verify();
    }

    @Test
    void createsDonationRepositoryAccessOrderWithCustomAmount() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        PayService payService = mock(PayService.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopCouponService couponService = mock(ShopCouponService.class);
        ShopItem item = donationGitItem(50L, "github", "wybaby168", "office-render-demo", "pull");
        item.setPrice(new BigDecimal("5.00"));
        item.setShopId(1L);
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("50");
        dto.setDonationAmount(new BigDecimal("25.00"));

        when(itemRepository.findById(50L)).thenReturn(Mono.just(item));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());
        when(couponService.applyCoupon(isNull(), eq(new BigDecimal("25.00"))))
                .thenReturn(Mono.just(new CouponDiscount(null, new BigDecimal("25.00"),
                        BigDecimal.ZERO, new BigDecimal("25.00"))));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("h5zhifu");
        payment.setTradeNo("T100");
        when(payService.pay(any(ShopOrder.class), eq(item), eq(dto))).thenReturn(Mono.just(payment));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository, payService,
                authUserGateway, couponService);

        StepVerifier.create(service.createOrder(dto, buyer))
                .assertNext(result -> {
                    assertEquals(new BigDecimal("25.00"), result.getOrder().getOriginalAmount());
                    assertEquals(new BigDecimal("25.00"), result.getOrder().getAmount());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getAllValues().get(0);
        Map<?, ?> properties = JacksonUtils.readValue(saved.getProperties(), Map.class);
        assertEquals("github", properties.get("gitProvider"));
        assertEquals(List.of("wybaby168/office-render-demo"), properties.get("gitRepositories"));
        assertEquals(0, new BigDecimal(String.valueOf(properties.get("donationAmount")))
                .compareTo(new BigDecimal("25.00")));
    }

    @Test
    void createsPureDonationOrderWithCustomAmountWithoutGitBinding() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        PayService payService = mock(PayService.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopCouponService couponService = mock(ShopCouponService.class);
        ShopItem item = pureDonationItem(55L);
        item.setPrice(new BigDecimal("1.00"));
        item.setShopId(1L);
        PortalUserVo buyer = new PortalUserVo();
        buyer.setId(100L);
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("55");
        dto.setDonationAmount(new BigDecimal("66.66"));

        when(itemRepository.findById(55L)).thenReturn(Mono.just(item));
        when(couponService.applyCoupon(isNull(), eq(new BigDecimal("66.66"))))
                .thenReturn(Mono.just(new CouponDiscount(null, new BigDecimal("66.66"),
                        BigDecimal.ZERO, new BigDecimal("66.66"))));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("h5zhifu");
        payment.setTradeNo("T200");
        when(payService.pay(any(ShopOrder.class), eq(item), eq(dto))).thenReturn(Mono.just(payment));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository, payService,
                authUserGateway, couponService);

        StepVerifier.create(service.createOrder(dto, buyer))
                .assertNext(result -> {
                    assertEquals(new BigDecimal("66.66"), result.getOrder().getOriginalAmount());
                    assertEquals(new BigDecimal("66.66"), result.getOrder().getAmount());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getAllValues().get(0);
        Map<?, ?> properties = JacksonUtils.readValue(saved.getProperties(), Map.class);
        assertEquals(0, new BigDecimal(String.valueOf(properties.get("donationAmount")))
                .compareTo(new BigDecimal("66.66")));
        assertFalse(properties.containsKey("gitProvider"));
        assertFalse(properties.containsKey("gitRepositories"));
    }

    @Test
    void createsUsdPureDonationOrderFromOneDollarWithStripe() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        PayService payService = mock(PayService.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopCouponService couponService = mock(ShopCouponService.class);
        ShopItem item = pureDonationItem(56L);
        item.setPrice(new BigDecimal("5.00"));
        item.setShopId(1L);
        PortalUserVo buyer = new PortalUserVo();
        buyer.setId(100L);
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("56");
        dto.setDonationAmount(new BigDecimal("1.00"));
        dto.setPaymentCurrency("USD");
        dto.setPaymentProvider("stripe");

        when(itemRepository.findById(56L)).thenReturn(Mono.just(item));
        when(couponService.applyCoupon(isNull(), eq(new BigDecimal("1.00"))))
                .thenReturn(Mono.just(new CouponDiscount(null, new BigDecimal("1.00"),
                        BigDecimal.ZERO, new BigDecimal("1.00"))));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("stripe");
        payment.setTradeNo("cs_test_usd");
        when(payService.pay(any(ShopOrder.class), eq(item), eq(dto))).thenReturn(Mono.just(payment));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository, payService,
                authUserGateway, couponService);

        StepVerifier.create(service.createOrder(dto, buyer))
                .assertNext(result -> {
                    assertEquals(new BigDecimal("1.00"), result.getOrder().getAmount());
                    assertEquals("USD", result.getOrder().getCurrency());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getAllValues().get(0);
        assertEquals("USD", saved.getCurrency());
        Map<?, ?> properties = JacksonUtils.readValue(saved.getProperties(), Map.class);
        assertEquals("USD", properties.get("currency"));
    }

    @Test
    void rejectsUsdDonationWithDomesticPaymentProvider() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem item = pureDonationItem(57L);
        item.setPrice(new BigDecimal("5.00"));
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("57");
        dto.setDonationAmount(new BigDecimal("1.00"));
        dto.setPaymentCurrency("USD");
        dto.setPaymentProvider("h5zhifu");

        when(itemRepository.findById(57L)).thenReturn(Mono.just(item));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.createOrder(dto, oauthBuyer(OAuthType.GITHUB, "github")))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("PAYMENT_PROVIDER_CURRENCY_UNSUPPORTED", exception.getCode());
                })
                .verify();
    }

    @Test
    void createsUsdOrderForRegularItemUsingManualPrice() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        PayService payService = mock(PayService.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopCouponService couponService = mock(ShopCouponService.class);
        ShopItem item = new ShopItem();
        item.setId(58L);
        item.setShopId(1L);
        item.setName("Commercial license");
        item.setType(ShopItem.Type.LICENSE);
        item.setDeliveryMode(ShopItem.DeliveryMode.MANUAL);
        item.setEnabled(true);
        item.setPrice(new BigDecimal("399.00"));
        item.setUsdPrice(new BigDecimal("58.88"));
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("58");
        dto.setCount(2);
        dto.setPaymentCurrency("USD");
        dto.setPaymentProvider("stripe");

        when(itemRepository.findById(58L)).thenReturn(Mono.just(item));
        when(couponService.applyCoupon(isNull(), eq(new BigDecimal("117.76"))))
                .thenReturn(Mono.just(new CouponDiscount(null, new BigDecimal("117.76"),
                        BigDecimal.ZERO, new BigDecimal("117.76"))));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("stripe");
        payment.setTradeNo("cs_test_regular_usd");
        when(payService.pay(any(ShopOrder.class), eq(item), eq(dto))).thenReturn(Mono.just(payment));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository, payService,
                authUserGateway, couponService);

        StepVerifier.create(service.createOrder(dto, buyer))
                .assertNext(result -> {
                    assertEquals(new BigDecimal("117.76"), result.getOrder().getAmount());
                    assertEquals("USD", result.getOrder().getCurrency());
                })
                .verifyComplete();
    }

    @Test
    void createsOrderWithValidatedOrderFormProperties() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        PayService payService = mock(PayService.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopCouponService couponService = mock(ShopCouponService.class);
        ShopItem item = orderFormItem(56L);
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("56");
        dto.setProperties(Map.of("orderForm", Map.of(
                "websiteOrigin", "https://curleyg.xyz/docs/index.html",
                "nonCommercialCommitment", true
        )));

        when(itemRepository.findById(56L)).thenReturn(Mono.just(item));
        when(couponService.applyCoupon(isNull(), eq(new BigDecimal("599.00"))))
                .thenReturn(Mono.just(new CouponDiscount(null, new BigDecimal("599.00"),
                        BigDecimal.ZERO, new BigDecimal("599.00"))));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("h5zhifu");
        payment.setTradeNo("T300");
        when(payService.pay(any(ShopOrder.class), eq(item), eq(dto))).thenReturn(Mono.just(payment));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository, payService,
                authUserGateway, couponService);

        StepVerifier.create(service.createOrder(dto, buyer))
                .assertNext(result -> assertEquals(new BigDecimal("599.00"), result.getOrder().getAmount()))
                .verifyComplete();

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getAllValues().get(0);
        Map<?, ?> properties = JacksonUtils.readValue(saved.getProperties(), Map.class);
        Map<?, ?> orderForm = (Map<?, ?>) properties.get("orderForm");
        assertEquals("https://curleyg.xyz", orderForm.get("websiteOrigin"));
        assertEquals(Boolean.TRUE, orderForm.get("nonCommercialCommitment"));
    }

    @Test
    void rejectsMultiSkuOrderWithoutSkuSelection() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem item = multiSkuItem(100L);
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("100");

        when(itemRepository.findById(100L)).thenReturn(Mono.just(item));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.createOrder(dto, oauthBuyer(OAuthType.GITHUB, "github")))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("SKU_REQUIRED", exception.getCode());
                })
                .verify();
    }

    @Test
    void createsOrderWithSelectedSkuSnapshot() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItemSkuRepository skuRepository = mock(ShopItemSkuRepository.class);
        PayService payService = mock(PayService.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopCouponService couponService = mock(ShopCouponService.class);
        ShopItem item = multiSkuItem(101L);
        ShopItemSku sku = serviceSku(1001L, 101L, "SOURCE", "源码授权", new BigDecimal("1299.00"));
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("101");
        dto.setSkuId("1001");

        when(itemRepository.findById(101L)).thenReturn(Mono.just(item));
        when(skuRepository.findById(1001L)).thenReturn(Mono.just(sku));
        when(couponService.applyCoupon(isNull(), eq(new BigDecimal("1299.00"))))
                .thenReturn(Mono.just(new CouponDiscount(null, new BigDecimal("1299.00"),
                        BigDecimal.ZERO, new BigDecimal("1299.00"))));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        ShopOrderPaymentVo payment = new ShopOrderPaymentVo();
        payment.setProvider("h5zhifu");
        payment.setTradeNo("T-SKU-1");
        when(payService.pay(any(ShopOrder.class), any(ShopItem.class), eq(dto))).thenReturn(Mono.just(payment));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository, skuRepository, payService,
                authUserGateway, couponService);

        StepVerifier.create(service.createOrder(dto, buyer))
                .assertNext(result -> {
                    assertEquals("Word 编辑器授权 - 源码授权", result.getOrder().getDisplayName());
                    assertEquals(1001L, result.getOrder().getSkuId());
                    assertEquals("SOURCE", result.getOrder().getSkuCode());
                    assertEquals(new BigDecimal("1299.00"), result.getOrder().getAmount());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(orderRepository, atLeastOnce()).save(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getAllValues().get(0);
        assertEquals(1001L, saved.getSkuId());
        assertEquals("SOURCE", saved.getSkuCode());
        assertEquals("源码授权", saved.getSkuName());
        assertEquals("Word 编辑器授权", saved.getItemName());
        assertEquals("SERVICE_PACKAGE", saved.getItemType());
        assertTrue(saved.getItemSnapshot().contains("Word 编辑器授权"));
        assertTrue(saved.getSkuSnapshot().contains("SOURCE"));
        Map<?, ?> properties = JacksonUtils.readValue(saved.getProperties(), Map.class);
        assertEquals(1001, ((Number) properties.get("skuId")).intValue());
        assertEquals("源码授权", properties.get("skuName"));
    }

    @Test
    void retriesDeliveryWithSkuSnapshotAfterSkuChanged() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItemSkuRepository skuRepository = mock(ShopItemSkuRepository.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopDeliveryService deliveryService = mock(ShopDeliveryService.class);
        ShopOrder order = paidOrder("FFSKU7001", 102L);
        order.setSkuId(1002L);
        order.setSkuCode("DEPLOY");
        order.setSkuName("部署授权");
        order.setStatus(ShopOrder.Status.FAILED);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.FAILED);
        order.setPaidTime(LocalDateTime.now());
        ShopItem itemSnapshot = multiSkuItem(102L);
        ShopItemSku skuSnapshot = serviceSku(1002L, 102L, "DEPLOY", "部署授权", new BigDecimal("699.00"));
        skuSnapshot.setType(ShopItem.Type.LICENSE);
        skuSnapshot.setDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC);
        skuSnapshot.setParams("{\"licenseName\":\"部署授权\",\"allowedOrigins\":[\"https://demo.example.com\"]}");
        order.setItemSnapshot(JacksonUtils.toJson(itemSnapshot));
        order.setSkuSnapshot(JacksonUtils.toJson(skuSnapshot));
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");

        when(orderRepository.findByOrderNo("FFSKU7001")).thenReturn(Mono.just(order));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));
        when(deliveryService.deliver(any(ShopOrder.class), any(ShopItem.class), eq(buyer)))
                .thenReturn(Mono.just(DeliveryResult.ok("部署授权已签发")));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
                null,
                itemRepository,
                skuRepository,
                null,
                null,
                null,
                deliveryService,
                authUserGateway,
                null,
                null,
                null,
                null,
                null
        );

        StepVerifier.create(service.retryDelivery("FFSKU7001"))
                .assertNext(vo -> {
                    assertEquals(ShopOrder.Status.DELIVERED, vo.getStatus());
                    assertEquals(ShopOrder.DeliveryStatus.SUCCESS, vo.getDeliveryStatus());
                    assertEquals("部署授权已签发", vo.getDeliveryMessage());
                    assertEquals("Word 编辑器授权 - 部署授权", vo.getDisplayName());
                })
                .verifyComplete();

        ArgumentCaptor<ShopItem> deliveredItem = ArgumentCaptor.forClass(ShopItem.class);
        verify(deliveryService).deliver(any(ShopOrder.class), deliveredItem.capture(), eq(buyer));
        assertEquals("Word 编辑器授权 - 部署授权", deliveredItem.getValue().getName());
        assertEquals(new BigDecimal("699.00"), deliveredItem.getValue().getPrice());
        assertEquals(ShopItem.Type.LICENSE, deliveredItem.getValue().getType());
        assertEquals(ShopItem.DeliveryMode.AUTOMATIC, deliveredItem.getValue().getDeliveryMode());
        verify(itemRepository, never()).findById(102L);
        verify(skuRepository, never()).findById(1002L);
    }

    @Test
    void rejectsOrderWhenRequiredOrderFormCheckboxIsMissing() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem item = orderFormItem(57L);
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("57");
        dto.setProperties(Map.of("orderForm", Map.of("websiteOrigin", "https://curleyg.xyz")));

        when(itemRepository.findById(57L)).thenReturn(Mono.just(item));

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.createOrder(dto, oauthBuyer(OAuthType.GITHUB, "github")))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("ORDER_FORM_REQUIRED", exception.getCode());
                })
                .verify();
    }

    @Test
    void rejectsDonationRepositoryAccessOrderBelowMinimumAmount() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem item = donationGitItem(60L, "github", "wybaby168", "office-render-demo", "pull");
        item.setPrice(new BigDecimal("10.00"));
        ShopOrderDto dto = new ShopOrderDto();
        dto.setItemId("60");
        dto.setDonationAmount(new BigDecimal("9.99"));

        when(itemRepository.findById(60L)).thenReturn(Mono.just(item));
        when(orderRepository.findPaidOrDeliveredByBuyerId(100L)).thenReturn(Flux.empty());

        ShopOrderServiceImpl service = service(orderRepository, itemRepository);

        StepVerifier.create(service.createOrder(dto, oauthBuyer(OAuthType.GITHUB, "github")))
                .expectErrorSatisfies(error -> {
                    BusinessException exception = (BusinessException) error;
                    assertEquals("DONATION_AMOUNT_TOO_LOW", exception.getCode());
                })
                .verify();
    }

    @Test
    void retriesFailedAutomaticDeliveryAndCompletesOrder() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopDeliveryService deliveryService = mock(ShopDeliveryService.class);
        ShopOrder order = paidOrder("FF7001", 70L);
        order.setStatus(ShopOrder.Status.FAILED);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.FAILED);
        order.setDeliveryMessage("自动交付失败：Office 预览授权签发密钥未配置或无法读取");
        order.setPaidTime(LocalDateTime.now());
        ShopItem item = new ShopItem();
        item.setId(70L);
        item.setName("Office Preview 商业版");
        item.setType(ShopItem.Type.LICENSE);
        item.setDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC);
        item.setPrice(BigDecimal.ONE);
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");

        when(orderRepository.findByOrderNo("FF7001")).thenReturn(Mono.just(order));
        when(itemRepository.findById(70L)).thenReturn(Mono.just(item));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));
        when(deliveryService.deliver(any(ShopOrder.class), eq(item), eq(buyer)))
                .thenReturn(Mono.just(DeliveryResult.ok("授权许可已签发，可在我的订单中提取授权文件")));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
                null,
                itemRepository,
                null,
                null,
                null,
                null,
                deliveryService,
                authUserGateway,
                null,
                null,
                null,
                null,
                null
        );

        StepVerifier.create(service.retryDelivery("FF7001"))
                .assertNext(vo -> {
                    assertEquals(ShopOrder.Status.DELIVERED, vo.getStatus());
                    assertEquals(ShopOrder.DeliveryStatus.SUCCESS, vo.getDeliveryStatus());
                    assertEquals("授权许可已签发，可在我的订单中提取授权文件", vo.getDeliveryMessage());
                })
                .verifyComplete();
    }

    @Test
    void maintainerViewsDeliverySnapshotWithoutMarkingExtracted() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopOrderDeliveryRepository deliveryRepository = mock(ShopOrderDeliveryRepository.class);
        ShopOrder order = paidOrder("FF8001", 80L);
        ShopOrderDelivery delivery = new ShopOrderDelivery();
        delivery.setOrderNo("FF8001");
        delivery.setDeliveryType("LICENSE");
        delivery.setTitle("授权文件");
        delivery.setContent("license-content");
        delivery.setLicenseNo("LIC-8001");

        when(orderRepository.closeExpiredUnpaidOrders(any(LocalDateTime.class), eq("订单超过15分钟未支付，已自动关闭")))
                .thenReturn(Mono.just(0));
        when(orderRepository.findByOrderNo("FF8001")).thenReturn(Mono.just(order));
        when(deliveryRepository.findAllByOrderNoOrderByCreateTimeAsc("FF8001")).thenReturn(Flux.just(delivery));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                deliveryRepository,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        StepVerifier.create(service.viewDelivery(oauthBuyer(OAuthType.GITEA, "gitea"), "FF8001"))
                .assertNext(vo -> {
                    assertEquals("FF8001", vo.getOrderNo());
                    assertEquals("LICENSE", vo.getDeliveryType());
                    assertEquals("授权文件", vo.getTitle());
                    assertTrue(vo.getSensitive());
                    assertTrue(vo.getContent().contains("不在页面直接展示授权正文"));
                    assertTrue(vo.getFiles().isEmpty());
                    assertEquals("LIC-8001", vo.getLicenseNo());
                })
                .verifyComplete();

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void downloadsLicenseFileAndMarksBuyerExtracted() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopOrderDeliveryRepository deliveryRepository = mock(ShopOrderDeliveryRepository.class);
        ShopLicenseKeyPairRepository keyPairRepository = mock(ShopLicenseKeyPairRepository.class);
        ShopOrder order = paidOrder("FF9001", 90L);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.SUCCESS);
        ShopOrderDelivery delivery = new ShopOrderDelivery();
        delivery.setOrderNo("FF9001");
        delivery.setDeliveryType("LICENSE");
        delivery.setTitle("授权文件");
        delivery.setLicenseNo("LIC-9001");
        ShopLicenseKeyPair license = new ShopLicenseKeyPair();
        license.setOrderNo("FF9001");
        license.setSignature("{\"format\":\"demo-license-envelope\"}");
        license.setCertificate("{\"scope\":\"product:viewer\"}");

        when(orderRepository.closeExpiredUnpaidOrders(any(LocalDateTime.class), eq("订单超过15分钟未支付，已自动关闭")))
                .thenReturn(Mono.just(0));
        when(orderRepository.findByOrderNo("FF9001")).thenReturn(Mono.just(order));
        when(deliveryRepository.findByOrderNoAndDeliveryType("FF9001", "LICENSE")).thenReturn(Mono.just(delivery));
        when(deliveryRepository.save(any(ShopOrderDelivery.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrderDelivery.class)));
        when(keyPairRepository.findByOrderNo("FF9001")).thenReturn(Mono.just(license));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                deliveryRepository,
                keyPairRepository,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        StepVerifier.create(service.downloadDeliveryFile(oauthBuyer(OAuthType.GITHUB, "github"), "FF9001", "lic"))
                .assertNext(file -> {
                    assertEquals("license.lic", file.getName());
                    assertEquals("application/octet-stream", file.getContentType());
                    assertEquals("{\"format\":\"demo-license-envelope\"}",
                            new String(file.getContent(), StandardCharsets.UTF_8));
                    assertEquals(file.getContent().length, file.getSize());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrderDelivery> deliveryCaptor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        assertTrue(deliveryCaptor.getValue().getExtractedTime() != null);
    }

    @Test
    void maintainerCanAddSupplementalDigitalResourcesWhenMarkingDelivered() {
        ShopOrderRepository orderRepository = mock(ShopOrderRepository.class);
        ShopOrderDeliveryRepository deliveryRepository = mock(ShopOrderDeliveryRepository.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopOrder order = paidOrder("FF9101", 91L);
        order.setDeliveryStatus(ShopOrder.DeliveryStatus.WAITING);
        order.setAmount(new BigDecimal("3980.00"));
        order.setItemSnapshot(JacksonUtils.toJson(snapshotItem(91L, ShopItem.Type.LICENSE)));
        FileAttachmentVo attachment = new FileAttachmentVo();
        attachment.setName("word-editor-commercial-demo.zip");
        attachment.setUrl("/images/deliveries/FF9101/word-editor-commercial-demo.zip");
        attachment.setSize(1024L);
        attachment.setContentType("application/zip");
        ShopOrderDeliveryDto dto = new ShopOrderDeliveryDto();
        dto.setDeliveryStatus(ShopOrder.DeliveryStatus.SUCCESS);
        dto.setDeliveryMessage("线下支付补单，商业 demo 已交付");
        dto.setDeliveryTitle("栗小兔专属商业 demo");
        dto.setDeliveryContent("授权 IP：192.168.30.7");
        dto.setDeliveryAttachments(List.of(attachment));

        when(orderRepository.findByOrderNo("FF9101")).thenReturn(Mono.just(order));
        when(orderRepository.save(any(ShopOrder.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrder.class)));
        when(deliveryRepository.findByOrderNoAndDeliveryType("FF9101", "DIGITAL")).thenReturn(Mono.empty());
        when(deliveryRepository.save(any(ShopOrderDelivery.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrderDelivery.class)));
        PortalUserVo buyer = oauthBuyer(OAuthType.GITHUB, "github");
        buyer.setUsername("会调接口的栗小兔");
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(buyer));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
                null,
                null,
                null,
                deliveryRepository,
                null,
                null,
                null,
                authUserGateway,
                null,
                null,
                null,
                null,
                null
        );

        StepVerifier.create(service.updateDelivery("FF9101", dto))
                .assertNext(vo -> {
                    assertEquals(ShopOrder.Status.DELIVERED, vo.getStatus());
                    assertEquals(ShopOrder.DeliveryStatus.SUCCESS, vo.getDeliveryStatus());
                    assertEquals("线下支付补单，商业 demo 已交付", vo.getDeliveryMessage());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrderDelivery> deliveryCaptor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        ShopOrderDelivery saved = deliveryCaptor.getValue();
        assertEquals("FF9101", saved.getOrderNo());
        assertEquals("DIGITAL", saved.getDeliveryType());
        assertEquals("栗小兔专属商业 demo", saved.getTitle());
        assertTrue(saved.getContent().contains("192.168.30.7"));
        assertNotNull(saved.getAttachments());
        assertTrue(saved.getAttachments().contains("word-editor-commercial-demo.zip"));
    }

    private ShopOrderServiceImpl service(ShopOrderRepository orderRepository, ShopItemRepository itemRepository) {
        return service(orderRepository, itemRepository, null, null, null);
    }

    private ShopOrderServiceImpl service(ShopOrderRepository orderRepository, ShopItemRepository itemRepository,
                                         PayService payService, AuthUserGateway authUserGateway,
                                         ShopCouponService couponService) {
        return service(orderRepository, itemRepository, mock(ShopItemSkuRepository.class), payService,
                authUserGateway, couponService);
    }

    private ShopOrderServiceImpl service(ShopOrderRepository orderRepository, ShopItemRepository itemRepository,
                                         ShopItemSkuRepository skuRepository, PayService payService,
                                         AuthUserGateway authUserGateway, ShopCouponService couponService) {
        ShopContractService contractService = mock(ShopContractService.class);
        when(contractService.requireSigned(any(), any(), any())).thenReturn(Mono.empty());
        when(contractService.requireSigned(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(contractService.bindOrder(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(contractService.bindOrder(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        return new ShopOrderServiceImpl(
                orderRepository,
                null,
                itemRepository,
                skuRepository,
                null,
                null,
                payService,
                null,
                authUserGateway,
                couponService,
                null,
                new GitRepositoryAccessOrderChecker(
                        orderRepository,
                        testGitResolver(),
                        testGitTokenService(),
                        mock(GiteaRepositoryClient.class),
                        mock(GithubRepositoryClient.class),
                        mock(GiteeRepositoryClient.class)
                ),
                contractService,
                testPricingService()
        );
    }

    private ShopPricingService testPricingService() {
        ShopPricingProperties properties = new ShopPricingProperties();
        properties.setCnyPerUsd(new BigDecimal("6.78"));
        return new ShopPricingService(properties);
    }

    private GitRepositoryAccessResolver testGitResolver() {
        GitRepositoryAccessResolver resolver = mock(GitRepositoryAccessResolver.class);
        when(resolver.resolve(any(GitRepositoryAccessParamValue.class))).thenAnswer(invocation -> {
            GitRepositoryAccessParamValue param = invocation.getArgument(0);
            return Flux.fromIterable(param.getRepositories().stream()
                    .map(repository -> new ResolvedGitRepository(
                            repository.getRepositoryId(),
                            repository.getProvider(),
                            null,
                            repository.getOwner(),
                            repository.getRepo(),
                            repository.fullName(),
                            repository.getPermission(),
                            null))
                    .toList());
        });
        return resolver;
    }

    private GitAccessTokenService testGitTokenService() {
        GitAccessTokenService tokenService = mock(GitAccessTokenService.class);
        when(tokenService.resolveTokenValue(eq("github"), isNull())).thenReturn(Mono.just("github-admin"));
        when(tokenService.resolveTokenValue(eq("gitea"), isNull())).thenReturn(Mono.just("gitea-admin"));
        return tokenService;
    }

    private ShopItem gitItem(Long id, String provider, String owner, String repo, String permission) {
        return gitItem(id, ShopItem.Type.GIT_REPOSITORY_ACCESS, provider, owner, repo, permission);
    }

    private ShopItem donationGitItem(Long id, String provider, String owner, String repo, String permission) {
        return gitItem(id, ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS, provider, owner, repo, permission);
    }

    private ShopItem pureDonationItem(Long id) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setType(ShopItem.Type.DONATION);
        item.setEnabled(true);
        item.setDeliveryMode(ShopItem.DeliveryMode.NONE);
        item.setPrice(BigDecimal.ONE);
        return item;
    }

    private ShopItem multiSkuItem(Long id) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setName("Word 编辑器授权");
        item.setShopId(1L);
        item.setType(ShopItem.Type.SERVICE_PACKAGE);
        item.setSkuMode(ShopItem.SkuMode.MULTI);
        item.setEnabled(true);
        item.setDeliveryMode(ShopItem.DeliveryMode.MANUAL);
        item.setPrice(new BigDecimal("399.00"));
        item.setDescription("Word 编辑器授权");
        return item;
    }

    private ShopItemSku serviceSku(Long id, Long itemId, String code, String name, BigDecimal price) {
        ShopItemSku sku = new ShopItemSku();
        sku.setId(id);
        sku.setItemId(itemId);
        sku.setCode(code);
        sku.setName(name);
        sku.setType(ShopItem.Type.SERVICE_PACKAGE);
        sku.setDeliveryMode(ShopItem.DeliveryMode.MANUAL);
        sku.setPrice(price);
        sku.setEnabled(true);
        sku.setDefaultSelected(true);
        sku.setBuyCount(0);
        return sku;
    }

    private ShopItem orderFormItem(Long id) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setName("Office Preview 开发版");
        item.setType(ShopItem.Type.SERVICE_PACKAGE);
        item.setEnabled(true);
        item.setShopId(1L);
        item.setDeliveryMode(ShopItem.DeliveryMode.MANUAL);
        item.setPrice(new BigDecimal("599.00"));
        item.setParams("""
                {
                  "orderForm": {
                    "enabled": true,
                    "fields": [
                      {
                        "key": "websiteOrigin",
                        "label": "非盈利性网站地址",
                        "type": "url",
                        "required": true,
                        "target": "license.allowedOrigins",
                        "normalize": "origin"
                      },
                      {
                        "key": "nonCommercialCommitment",
                        "label": "承诺非商业化用途",
                        "type": "checkbox",
                        "required": true,
                        "requiredValue": true
                      }
                    ]
                  }
                }
                """);
        return item;
    }

    private ShopItem gitItem(Long id, ShopItem.Type type, String provider, String owner, String repo, String permission) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setType(type);
        item.setEnabled(true);
        item.setPrice(BigDecimal.ONE);
        item.setParams("""
                {"provider":"%s","repositories":[{"owner":"%s","repo":"%s","permission":"%s"}]}
                """.formatted(provider, owner, repo, permission));
        return item;
    }

    private ShopOrder paidOrder(String orderNo, Long itemId) {
        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNo);
        order.setItemId(itemId);
        order.setBuyerId(100L);
        order.setStatus(ShopOrder.Status.DELIVERED);
        return order;
    }

    private ShopItem snapshotItem(Long id, ShopItem.Type type) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setName("Word 编辑器商业版");
        item.setType(type);
        item.setDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC);
        return item;
    }

    private PortalUserVo oauthBuyer(OAuthType type, String code) {
        PortalUserOauthVo oauth = PortalUserOauthVo.of(100L, type, "1", """
                {"id":"1","login":"octocat","username":"octocat","name":"Mona"}
                """, null, "octocat", "octocat", "Mona", null, null, null, null);

        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setAuthorizations(Map.of(code, oauth));
        return user;
    }
}
