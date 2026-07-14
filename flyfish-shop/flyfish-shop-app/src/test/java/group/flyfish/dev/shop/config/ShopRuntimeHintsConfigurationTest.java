package group.flyfish.dev.shop.config;

import group.flyfish.dev.customer.domain.dto.CustomerSocketCommand;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.domain.dto.ShopItemCreateDto;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.qo.ShopOrderListQo;
import group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuService;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopRuntimeHintsConfigurationTest {

    @Test
    void registersOnlyModelsBoundOutsideSpringWeb() {
        RuntimeHints hints = new RuntimeHints();
        new ShopRuntimeHintsConfiguration.ShopRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());

        assertBindingHint(hints.reflection().getTypeHint(GitRepositoryAccessParamValue.class));
        assertBindingHint(hints.reflection().getTypeHint(CustomerSocketCommand.class));
        assertBindingHint(hints.reflection().getTypeHint(ShopItemGroupListQo.class));
        assertBindingHint(hints.reflection().getTypeHint(ShopItemListQo.class));
        assertBindingHint(hints.reflection().getTypeHint(ShopOrderListQo.class));
        assertBindingHint(hints.reflection().getTypeHint(H5ZhiFuPayDto.class));
        assertTrue(hints.proxies().jdkProxyHints().anyMatch(proxy -> proxy.getProxiedInterfaces().stream()
                .anyMatch(type -> type.getName().equals(H5ZhiFuService.class.getName()))));
        assertNull(hints.reflection().getTypeHint(ShopItemCreateDto.class));
    }

    private void assertBindingHint(TypeHint hint) {
        assertNotNull(hint);
        assertTrue(hint.getMemberCategories().contains(MemberCategory.ACCESS_DECLARED_FIELDS));
        assertTrue(hint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertFalse(hint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_METHODS));
        assertFalse(hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));
    }
}
