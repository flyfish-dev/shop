package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.json.JacksonUtils;
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
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.domain.vo.ShopOrderPaymentVo;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
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
                deliveryService,
                authUserGateway,
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
        when(deliveryRepository.findByOrderNo("FF8001")).thenReturn(Mono.just(delivery));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
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
        license.setSignature("demo-license-envelope");
        license.setCertificate("{\"scope\":\"product:viewer\"}");

        when(orderRepository.closeExpiredUnpaidOrders(any(LocalDateTime.class), eq("订单超过15分钟未支付，已自动关闭")))
                .thenReturn(Mono.just(0));
        when(orderRepository.findByOrderNo("FF9001")).thenReturn(Mono.just(order));
        when(deliveryRepository.findByOrderNo("FF9001")).thenReturn(Mono.just(delivery));
        when(deliveryRepository.save(any(ShopOrderDelivery.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, ShopOrderDelivery.class)));
        when(keyPairRepository.findByOrderNo("FF9001")).thenReturn(Mono.just(license));

        ShopOrderServiceImpl service = new ShopOrderServiceImpl(
                orderRepository,
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
                null
        );

        StepVerifier.create(service.downloadDeliveryFile(oauthBuyer(OAuthType.GITHUB, "github"), "FF9001", "lic"))
                .assertNext(file -> {
                    assertEquals("license.lic", file.getName());
                    assertEquals("application/octet-stream", file.getContentType());
                    assertEquals("demo-license-envelope", new String(file.getContent(), StandardCharsets.UTF_8));
                    assertEquals(file.getContent().length, file.getSize());
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrderDelivery> deliveryCaptor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        assertTrue(deliveryCaptor.getValue().getExtractedTime() != null);
    }

    private ShopOrderServiceImpl service(ShopOrderRepository orderRepository, ShopItemRepository itemRepository) {
        return service(orderRepository, itemRepository, null, null, null);
    }

    private ShopOrderServiceImpl service(ShopOrderRepository orderRepository, ShopItemRepository itemRepository,
                                         PayService payService, AuthUserGateway authUserGateway,
                                         ShopCouponService couponService) {
        ShopContractService contractService = mock(ShopContractService.class);
        when(contractService.requireSigned(any(), any(), any())).thenReturn(Mono.empty());
        when(contractService.bindOrder(any(), any(), any(), any())).thenReturn(Mono.empty());
        return new ShopOrderServiceImpl(
                orderRepository,
                null,
                itemRepository,
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
                contractService
        );
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
