package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryExperienceServiceTest {

    @Test
    void createsLocalizedExperienceSnapshotAfterDelivery() {
        ShopOrderDeliveryRepository repository = mock(ShopOrderDeliveryRepository.class);
        when(repository.findByOrderNoAndDeliveryType("FF-EXPERIENCE-1", "EXPERIENCE"))
                .thenReturn(Mono.empty());
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        DeliveryExperienceService service = new DeliveryExperienceService(repository);

        StepVerifier.create(service.deliver(order(), item(), new PortalUserVo()))
                .assertNext(result -> assertTrue(result.isSuccess()))
                .verifyComplete();

        ArgumentCaptor<ShopOrderDelivery> captor = ArgumentCaptor.forClass(ShopOrderDelivery.class);
        verify(repository).save(captor.capture());
        ShopOrderDelivery saved = captor.getValue();
        assertEquals("EXPERIENCE", saved.getDeliveryType());
        assertTrue(saved.getMetadata().contains("office-render-demo"));

        ShopOrderDeliveryExtractVo vo = new ShopOrderDeliveryExtractVo(saved);
        assertNotNull(vo.getExperience());
        assertEquals("Run the integrated demo", vo.getExperience().getTitleEn());
        assertEquals("https://github.com/flyfish-dev/office-render-demo",
                vo.getExperience().getLinks().get(0).getUrl());
        assertTrue(vo.getExperience().getLinks().get(0).isPrimary());
    }

    @Test
    void rejectsUnsafeExperienceUrl() {
        ShopItem item = item();
        item.setParams("""
                {
                  "deliveryExperience": {
                    "enabled": true,
                    "links": [{"title":"错误入口","url":"javascript:alert(1)"}]
                  }
                }
                """);

        assertThrows(ServiceException.class,
                () -> ShopItemParamValue.deliveryExperience(item.getParams()));
    }

    private ShopOrder order() {
        ShopOrder order = new ShopOrder();
        order.setOrderNo("FF-EXPERIENCE-1");
        order.setBuyerId(88L);
        return order;
    }

    private ShopItem item() {
        ShopItem item = new ShopItem();
        item.setId(1007L);
        item.setType(ShopItem.Type.GIT_REPOSITORY_ACCESS);
        item.setParams("""
                {
                  "deliveryActions": ["GIT_REPOSITORY_ACCESS", "LICENSE"],
                  "deliveryExperience": {
                    "enabled": true,
                    "title": "运行综合 Demo",
                    "titleEn": "Run the integrated demo",
                    "description": "源码权限开通后，从综合 Demo 开始验证。",
                    "descriptionEn": "Start with the integrated demo after repository access is granted.",
                    "links": [{
                      "title": "打开 Office 综合 Demo 仓库",
                      "titleEn": "Open the Office demo repository",
                      "url": "https://github.com/flyfish-dev/office-render-demo",
                      "kind": "REPOSITORY",
                      "primary": true
                    }]
                  }
                }
                """);
        return item;
    }
}
