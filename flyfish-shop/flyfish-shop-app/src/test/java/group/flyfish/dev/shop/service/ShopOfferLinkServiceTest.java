package group.flyfish.dev.shop.service;

import group.flyfish.dev.auth.api.client.AuthUserGateway;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.config.ShopOfferLinkProperties;
import group.flyfish.dev.shop.domain.dto.ShopOfferLinkCreateDto;
import group.flyfish.dev.shop.domain.dto.ShopOfferLinkPreviewDto;
import group.flyfish.dev.shop.domain.po.ShopCoupon;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOfferLink;
import group.flyfish.dev.shop.pricing.ShopPricingService;
import group.flyfish.dev.shop.repository.ShopCouponRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import group.flyfish.dev.shop.repository.ShopOfferLinkRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopOfferLinkServiceTest {

    @Test
    void createsFiveDayFreeLinkAndPersistsOnlyTokenHash() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopCouponRepository couponRepository = mock(ShopCouponRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopItem item = item(101L, "Office Preview 商业版", "899.00");
        ShopOfferLinkCreateDto dto = new ShopOfferLinkCreateDto();
        dto.setName("客户专享免费交付");
        dto.setType(ShopOfferLink.Type.FREE);
        dto.setValidDays(null);
        when(itemRepository.findById(101L)).thenReturn(Mono.just(item));
        when(couponRepository.findByCode(anyString())).thenReturn(Mono.empty());
        when(couponRepository.save(any(ShopCoupon.class))).thenAnswer(invocation -> {
            ShopCoupon coupon = invocation.getArgument(0, ShopCoupon.class);
            coupon.setId(201L);
            return Mono.just(coupon);
        });
        when(offerRepository.save(any(ShopOfferLink.class))).thenAnswer(invocation -> {
            ShopOfferLink link = invocation.getArgument(0, ShopOfferLink.class);
            link.setId(301L);
            link.setCreateTime(LocalDateTime.now());
            return Mono.just(link);
        });
        LocalDateTime before = LocalDateTime.now();

        StepVerifier.create(service(offerRepository, couponRepository, itemRepository).create(101L, dto))
                .assertNext(result -> {
                    assertTrue(result.getShareUrl().startsWith("https://dev.flyfish.group/shop/detail/101?"));
                    assertTrue(result.getShareUrl().contains("offer="));
                    assertEquals("客户专享免费交付", result.getName());
                    assertEquals(ShopOfferLink.Type.FREE, result.getType());
                    assertTrue(result.getExpiresAt().isAfter(before.plusDays(5).minusSeconds(2)));
                    assertTrue(result.getExpiresAt().isBefore(LocalDateTime.now().plusDays(5).plusSeconds(2)));
                })
                .verifyComplete();

        ArgumentCaptor<ShopOfferLink> linkCaptor = ArgumentCaptor.forClass(ShopOfferLink.class);
        ArgumentCaptor<ShopCoupon> couponCaptor = ArgumentCaptor.forClass(ShopCoupon.class);
        verify(offerRepository).save(linkCaptor.capture());
        verify(couponRepository).save(couponCaptor.capture());
        ShopOfferLink savedLink = linkCaptor.getValue();
        assertEquals("客户专享免费交付", savedLink.getName());
        assertNotNull(savedLink.getTokenHash());
        assertEquals(64, savedLink.getTokenHash().length());
        assertFalse(savedLink.getTokenHash().contains(savedLink.getTokenHint()));
        assertEquals(ShopCoupon.Source.OFFER_LINK, couponCaptor.getValue().getSource());
        assertEquals(1, couponCaptor.getValue().getTotalCount());
    }

    @Test
    void previewsFreeLinkAsZeroWithoutReservingIt() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopCouponRepository couponRepository = mock(ShopCouponRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopOfferLink link = activeLink(101L, ShopOfferLink.Type.FREE, "899.00");
        when(offerRepository.findByTokenHash(anyString())).thenReturn(Mono.just(link));
        when(itemRepository.findById(101L)).thenReturn(Mono.just(item(101L, "Office Preview 商业版", "899.00")));
        ShopOfferLinkPreviewDto dto = new ShopOfferLinkPreviewDto();
        dto.setToken("one-time-token");
        dto.setItemId(101L);
        dto.setPaymentCurrency("CNY");

        StepVerifier.create(service(offerRepository, couponRepository, itemRepository).preview(dto))
                .assertNext(result -> {
                    assertEquals(0, new BigDecimal("899.00").compareTo(result.getOriginalAmount()));
                    assertEquals(0, new BigDecimal("899.00").compareTo(result.getDiscountAmount()));
                    assertEquals(0, BigDecimal.ZERO.compareTo(result.getPayableAmount()));
                })
                .verifyComplete();
    }

    @Test
    void createsRecipientBoundSignedPaidLinkWithoutBackingCoupon() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopCouponRepository couponRepository = mock(ShopCouponRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopItem item = item(101L, "Office Preview 企业版", "4999.00");
        PortalUserVo recipient = new PortalUserVo();
        recipient.setId(100L);
        recipient.setUsername("已付款客户");
        ShopOfferLinkCreateDto dto = new ShopOfferLinkCreateDto();
        dto.setName("企业客户直达交付");
        dto.setPurpose(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT);
        dto.setRecipientUserId(100L);
        dto.setContractReference("CONTRACT-2026-001");
        dto.setPaymentReference("PAYMENT-2026-001");
        dto.setConfirmationNote("合同及款项已由管理员核验");
        dto.setSettlementAmount(new BigDecimal("4300.00"));
        dto.setSettlementCurrency("CNY");
        when(itemRepository.findById(101L)).thenReturn(Mono.just(item));
        when(authUserGateway.getById(100L)).thenReturn(Mono.just(recipient));
        when(offerRepository.save(any(ShopOfferLink.class))).thenAnswer(invocation -> {
            ShopOfferLink link = invocation.getArgument(0, ShopOfferLink.class);
            link.setId(301L);
            return Mono.just(link);
        });

        StepVerifier.create(service(offerRepository, couponRepository, itemRepository, authUserGateway)
                        .create(101L, dto))
                .assertNext(result -> {
                    assertEquals(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT, result.getPurpose());
                    assertEquals(100L, result.getRecipientUserId());
                    assertEquals("已付款客户", result.getRecipientName());
                    assertEquals(0, new BigDecimal("4300.00").compareTo(result.getSettlementAmount()));
                    assertEquals("CNY", result.getSettlementCurrency());
                    assertTrue(result.getShareUrl().contains("offer="));
                })
                .verifyComplete();

        ArgumentCaptor<ShopOfferLink> captor = ArgumentCaptor.forClass(ShopOfferLink.class);
        verify(offerRepository).save(captor.capture());
        assertEquals(null, captor.getValue().getCouponCode());
        assertEquals("CONTRACT-2026-001", captor.getValue().getContractReference());
        assertEquals("PAYMENT-2026-001", captor.getValue().getPaymentReference());
        verifyNoInteractions(couponRepository);
    }

    @Test
    void createsOneTimeSignedPaidLinkForUnregisteredRecipient() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopCouponRepository couponRepository = mock(ShopCouponRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        AuthUserGateway authUserGateway = mock(AuthUserGateway.class);
        ShopItem item = item(101L, "Office Preview 企业版", "4999.00");
        ShopOfferLinkCreateDto dto = new ShopOfferLinkCreateDto();
        dto.setPurpose(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT);
        dto.setRecipientName("浙江鸿翼");
        dto.setContractReference("CONTRACT-2026-002");
        dto.setPaymentReference("PAYMENT-2026-002");
        dto.setSettlementAmount(new BigDecimal("4999.00"));
        when(itemRepository.findById(101L)).thenReturn(Mono.just(item));
        when(offerRepository.save(any(ShopOfferLink.class))).thenAnswer(invocation -> {
            ShopOfferLink link = invocation.getArgument(0, ShopOfferLink.class);
            link.setId(302L);
            return Mono.just(link);
        });

        StepVerifier.create(service(offerRepository, couponRepository, itemRepository, authUserGateway)
                        .create(101L, dto))
                .assertNext(result -> {
                    assertEquals(null, result.getRecipientUserId());
                    assertEquals("浙江鸿翼", result.getRecipientName());
                    assertEquals(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT, result.getPurpose());
                    assertTrue(result.getShareUrl().contains("offer="));
                })
                .verifyComplete();

        verifyNoInteractions(authUserGateway, couponRepository);
    }

    @Test
    void rejectsSignedPaidLinkUsedByAnotherCustomer() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopOfferLink link = activeLink(101L, null, null);
        link.setPurpose(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT);
        link.setRecipientUserId(100L);
        link.setSettlementAmount(new BigDecimal("899.00"));
        link.setSettlementCurrency("CNY");
        when(offerRepository.findByTokenHash(anyString())).thenReturn(Mono.just(link));

        StepVerifier.create(service(offerRepository, mock(ShopCouponRepository.class),
                        mock(ShopItemRepository.class)).apply(
                        "one-time-token", new BigDecimal("899.00"), 101L, null, "CNY", 200L))
                .expectErrorSatisfies(error -> assertEquals(
                        "OFFER_LINK_RECIPIENT_MISMATCH", ((BusinessException) error).getCode()))
                .verify();
    }

    @Test
    void allowsSignedInCustomerToUseUnregisteredRecipientLink() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopOfferLink link = activeLink(101L, null, null);
        link.setPurpose(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT);
        link.setRecipientName("浙江鸿翼");
        link.setSettlementAmount(new BigDecimal("4999.00"));
        link.setSettlementCurrency("CNY");
        when(offerRepository.findByTokenHash(anyString())).thenReturn(Mono.just(link));

        StepVerifier.create(service(offerRepository, mock(ShopCouponRepository.class),
                        mock(ShopItemRepository.class)).apply(
                        "one-time-token", new BigDecimal("4999.00"), 101L, null, "CNY", 200L))
                .assertNext(result -> {
                    assertEquals(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT, result.purpose());
                    assertEquals(0, new BigDecimal("4999.00").compareTo(result.discount().payableAmount()));
                })
                .verifyComplete();
    }

    @Test
    void rejectsSignedPaidPreviewForAnotherSignedInCustomer() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopItemRepository itemRepository = mock(ShopItemRepository.class);
        ShopOfferLink link = activeLink(101L, null, null);
        link.setPurpose(ShopOfferLink.Purpose.SIGNED_PAID_FULFILLMENT);
        link.setRecipientUserId(100L);
        link.setSettlementAmount(new BigDecimal("899.00"));
        link.setSettlementCurrency("CNY");
        when(offerRepository.findByTokenHash(anyString())).thenReturn(Mono.just(link));
        when(itemRepository.findById(101L)).thenReturn(Mono.just(item(101L, "Office Preview 商业版", "899.00")));
        ShopOfferLinkPreviewDto dto = new ShopOfferLinkPreviewDto();
        dto.setToken("one-time-token");
        dto.setItemId(101L);

        StepVerifier.create(service(offerRepository, mock(ShopCouponRepository.class), itemRepository)
                        .preview(dto, 200L))
                .expectErrorSatisfies(error -> assertEquals(
                        "OFFER_LINK_RECIPIENT_MISMATCH", ((BusinessException) error).getCode()))
                .verify();
    }

    @Test
    void rejectsASecondReservationWhenTheAtomicUpdateNoLongerMatches() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        when(offerRepository.reserve(any(), anyString(), any(), any())).thenReturn(Mono.just(0));

        StepVerifier.create(service(offerRepository, mock(ShopCouponRepository.class),
                        mock(ShopItemRepository.class)).reserve(301L, "FF-SECOND", 100L))
                .expectErrorSatisfies(error -> assertEquals(
                        "OFFER_LINK_UNAVAILABLE", ((BusinessException) error).getCode()))
                .verify();
    }

    @Test
    void rejectsOversizedTokenBeforeQueryingTheDatabase() {
        ShopOfferLinkRepository offerRepository = mock(ShopOfferLinkRepository.class);
        ShopOfferLinkPreviewDto dto = new ShopOfferLinkPreviewDto();
        dto.setToken("x".repeat(129));
        dto.setItemId(101L);

        StepVerifier.create(service(offerRepository, mock(ShopCouponRepository.class),
                        mock(ShopItemRepository.class)).preview(dto))
                .expectErrorSatisfies(error -> assertEquals(
                        "OFFER_LINK_TOKEN_INVALID", ((BusinessException) error).getCode()))
                .verify();

        verifyNoInteractions(offerRepository);
    }

    private ShopOfferLinkService service(ShopOfferLinkRepository offerRepository,
                                         ShopCouponRepository couponRepository,
                                         ShopItemRepository itemRepository) {
        return service(offerRepository, couponRepository, itemRepository, mock(AuthUserGateway.class));
    }

    private ShopOfferLinkService service(ShopOfferLinkRepository offerRepository,
                                         ShopCouponRepository couponRepository,
                                         ShopItemRepository itemRepository,
                                         AuthUserGateway authUserGateway) {
        ShopOfferLinkProperties properties = new ShopOfferLinkProperties();
        properties.setPortalBaseUrl("https://dev.flyfish.group");
        return new ShopOfferLinkService(
                offerRepository,
                couponRepository,
                itemRepository,
                mock(ShopItemSkuRepository.class),
                mock(ShopPricingService.class),
                properties,
                authUserGateway
        );
    }

    private ShopItem item(Long id, String name, String price) {
        ShopItem item = new ShopItem();
        item.setId(id);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setEnabled(true);
        item.setSkuMode(ShopItem.SkuMode.SINGLE);
        item.setType(ShopItem.Type.LICENSE);
        return item;
    }

    private ShopOfferLink activeLink(Long itemId, ShopOfferLink.Type type, String value) {
        ShopOfferLink link = new ShopOfferLink();
        link.setId(301L);
        link.setItemId(itemId);
        link.setPurpose(ShopOfferLink.Purpose.PROMOTION);
        link.setType(type);
        link.setDiscountValue(value == null ? null : new BigDecimal(value));
        link.setMaxDiscountAmount(BigDecimal.ZERO);
        link.setCouponCode("FFLINKINTERNAL");
        link.setStatus(ShopOfferLink.Status.ACTIVE);
        link.setExpiresAt(LocalDateTime.now().plusDays(5));
        return link;
    }
}
