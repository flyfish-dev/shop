package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopLicenseRoot;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.license.ExternalLicenseIssuer;
import group.flyfish.dev.shop.license.IssuedLicenseDocument;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopLicenseRootRepository;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalAndLicenseDeliveryServiceTest {

    @Test
    void digitalDownloadDeliveryCreatesExtractableSnapshot() {
        ShopOrderDeliveryRepository deliveryRepository = mock(ShopOrderDeliveryRepository.class);
        when(deliveryRepository.findByOrderNo("FF1001")).thenReturn(Mono.empty());
        when(deliveryRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        DigitalDownloadDeliveryService service = new DigitalDownloadDeliveryService(deliveryRepository);

        StepVerifier.create(service.deliver(order(), digitalItem(), buyer()))
                .assertNext(result -> {
                    assertTrue(result.isSuccess());
                    assertTrue(result.getMessage().contains("提取"));
                })
                .verifyComplete();

        ArgumentCaptor<ShopOrderDelivery> captor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        ShopOrderDelivery saved = captor.getValue();
        assertEquals("FF1001", saved.getOrderNo());
        assertEquals(ShopOrderDelivery.DeliveryType.DIGITAL.name(), saved.getDeliveryType());
        assertEquals("下载地址", saved.getTitle());
        assertTrue(saved.getContent().contains("https://download.example.com"));
    }

    @Test
    void licenseDeliveryCreatesKeyPairFromExternalSigner() {
        ShopLicenseRootRepository rootRepository = mock(ShopLicenseRootRepository.class);
        ShopLicenseKeyPairRepository keyPairRepository = mock(ShopLicenseKeyPairRepository.class);
        ShopOrderDeliveryRepository deliveryRepository = mock(ShopOrderDeliveryRepository.class);
        when(keyPairRepository.findByOrderNo("FF1001")).thenReturn(Mono.empty());
        when(rootRepository.findByName("external-license-provider")).thenReturn(Mono.empty());
        when(rootRepository.save(any())).thenAnswer(invocation -> {
            ShopLicenseRoot root = invocation.getArgument(0);
            root.setId(1L);
            return Mono.just(root);
        });
        when(keyPairRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deliveryRepository.findByOrderNo("FF1001")).thenReturn(Mono.empty());
        when(deliveryRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        LicenseDeliveryService service = new LicenseDeliveryService(rootRepository, keyPairRepository,
                deliveryRepository, testIssuer());

        StepVerifier.create(service.deliver(order(), licenseItem(), buyer()))
                .assertNext(result -> {
                    assertTrue(result.isSuccess());
                    assertTrue(result.getMessage().contains("授权文件"));
                })
                .verifyComplete();

        ArgumentCaptor<ShopLicenseKeyPair> licenseCaptor = ArgumentCaptor.forClass(ShopLicenseKeyPair.class);
        verify(keyPairRepository).save(licenseCaptor.capture());
        ShopLicenseKeyPair license = licenseCaptor.getValue();
        assertNotNull(license.getLicenseNo());
        assertNotNull(license.getPublicKey());
        assertNotNull(license.getPrivateKey());
        assertNotNull(license.getSignature());
        assertTrue(license.getSignature().contains("demo-license-envelope"));
        assertTrue(license.getCertificate().contains("\"scope\":\"product:viewer\""));
        assertTrue(license.getCertificate().contains("\"allowedOrigins\":[\"https://customer.example.com\"]"));

        ArgumentCaptor<ShopOrderDelivery> deliveryCaptor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        assertEquals(ShopOrderDelivery.DeliveryType.LICENSE.name(), deliveryCaptor.getValue().getDeliveryType());
        assertTrue(deliveryCaptor.getValue().getContent().contains("license.lic"));
        assertFalse(deliveryCaptor.getValue().getContent().contains("demo-license-envelope"));
    }

    @Test
    void gitRepositoryProductCanIssueNestedLicense() {
        ShopLicenseRootRepository rootRepository = mock(ShopLicenseRootRepository.class);
        ShopLicenseKeyPairRepository keyPairRepository = mock(ShopLicenseKeyPairRepository.class);
        ShopOrderDeliveryRepository deliveryRepository = mock(ShopOrderDeliveryRepository.class);
        when(keyPairRepository.findByOrderNo("FF1001")).thenReturn(Mono.empty());
        when(rootRepository.findByName("external-license-provider")).thenReturn(Mono.empty());
        when(rootRepository.save(any())).thenAnswer(invocation -> {
            ShopLicenseRoot root = invocation.getArgument(0);
            root.setId(2L);
            return Mono.just(root);
        });
        when(keyPairRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deliveryRepository.findByOrderNo("FF1001")).thenReturn(Mono.empty());
        when(deliveryRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        LicenseDeliveryService service = new LicenseDeliveryService(rootRepository, keyPairRepository,
                deliveryRepository, testIssuer());
        ShopItem item = compositeLicenseItem();

        assertTrue(service.supports(item));
        StepVerifier.create(service.deliver(order(), item, buyer()))
                .assertNext(result -> assertTrue(result.isSuccess()))
                .verifyComplete();

        ArgumentCaptor<ShopLicenseKeyPair> licenseCaptor = ArgumentCaptor.forClass(ShopLicenseKeyPair.class);
        verify(keyPairRepository).save(licenseCaptor.capture());
        ShopLicenseKeyPair license = licenseCaptor.getValue();
        assertTrue(license.getCertificate().contains("\"scope\":\"repo:flyfish-dev/document-render-demo\""));
        assertTrue(license.getCertificate().contains("\"docx\""));
        assertTrue(license.getCertificate().contains("\"virtual-excel\""));

        ArgumentCaptor<ShopOrderDelivery> deliveryCaptor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(deliveryRepository).save(deliveryCaptor.capture());
        assertEquals(ShopOrderDelivery.DeliveryType.LICENSE.name(), deliveryCaptor.getValue().getDeliveryType());
        assertTrue(deliveryCaptor.getValue().getContent().contains("Document Preview Commercial License"));
    }

    private ShopOrder order() {
        ShopOrder order = new ShopOrder();
        order.setOrderNo("FF1001");
        order.setBuyerId(100L);
        return order;
    }

    private ShopItem digitalItem() {
        ShopItem item = item(ShopItem.Type.DIGITAL_DOWNLOAD);
        item.setParams("""
                {"title":"下载地址","content":"https://download.example.com/package.zip"}
                """);
        return item;
    }

    private ShopItem licenseItem() {
        ShopItem item = item(ShopItem.Type.LICENSE);
        item.setParams("""
                {"licenseName":"Viewer 授权","scope":"product:viewer","edition":"commercial","allowedOrigins":["https://customer.example.com"],"validDays":365}
                """);
        return item;
    }

    private ShopItem compositeLicenseItem() {
        ShopItem item = item(ShopItem.Type.GIT_REPOSITORY_ACCESS);
        item.setName("Document Preview 商业版");
        item.setParams("""
                {
                  "provider": "github",
                  "repositories": [
                    { "provider": "github", "owner": "flyfish-dev", "repo": "document-render-demo", "permission": "read" },
                    { "provider": "github", "owner": "flyfish-dev", "repo": "docx-viewer", "permission": "read" },
                    { "provider": "github", "owner": "flyfish-dev", "repo": "msdoc-viewer", "permission": "read" },
                    { "provider": "github", "owner": "flyfish-dev", "repo": "ppt-viewer", "permission": "read" },
                    { "provider": "github", "owner": "flyfish-dev", "repo": "pptx-viewer", "permission": "read" },
                    { "provider": "github", "owner": "flyfish-dev", "repo": "excel-viewer", "permission": "read" },
                    { "provider": "github", "owner": "flyfish-dev", "repo": "hucre", "permission": "read" }
                  ],
                  "deliveryActions": ["GIT_REPOSITORY_ACCESS", "LICENSE"],
                  "licenseDelivery": {
                    "licenseName": "Document Preview Commercial License",
                    "scope": "repo:flyfish-dev/document-render-demo",
                    "product": "license-product",
                    "edition": "commercial",
                    "allowedOrigins": ["https://preview.customer.com"],
                    "features": ["doc", "docx", "ppt", "pptx", "xls", "xlsx", "xlsb", "virtual-excel"],
                    "maxDeployments": 1,
                    "commercialUse": true
                  }
                }
                """);
        return item;
    }

    private ExternalLicenseIssuer testIssuer() {
        ExternalLicenseIssuer issuer = mock(ExternalLicenseIssuer.class);
        when(issuer.issue(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            String licenseNo = invocation.getArgument(0, String.class);
            LicenseDeliveryParamValue param = invocation.getArgument(4, LicenseDeliveryParamValue.class);
            String origins = param.getAllowedOrigins().stream()
                    .map(value -> "\"" + value + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            String features = param.getFeatures().stream()
                    .map(value -> "\"" + value + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            String payload = """
                    {"scope":"%s","allowedOrigins":%s,"features":%s}
                    """.formatted(param.getScope(), origins, features);
            return new IssuedLicenseDocument(licenseNo, "license.lic", payload,
                    "demo-license-envelope", "external-license-provider", "EXTERNAL");
        });
        return issuer;
    }

    private ShopItem item(ShopItem.Type type) {
        ShopItem item = new ShopItem();
        item.setId(9L);
        item.setName("测试商品");
        item.setType(type);
        item.setPrice(BigDecimal.ONE);
        return item;
    }

    private PortalUserVo buyer() {
        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setUsername("tester");
        return user;
    }
}
