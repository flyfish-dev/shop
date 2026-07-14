package group.flyfish.dev.shop.config;

import group.flyfish.dev.customer.domain.dto.CustomerSocketCommand;
import group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.ShopOrderFormParamValue;
import group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo;
import group.flyfish.dev.shop.domain.qo.ShopItemListQo;
import group.flyfish.dev.shop.domain.qo.ShopOrderListQo;
import group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuService;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayResultDto;
import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuResultDto;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.DecoratingProxy;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ShopRuntimeHintsConfiguration.ShopRuntimeHints.class)
public class ShopRuntimeHintsConfiguration {

    public static class ShopRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.proxies().registerJdkProxy(H5ZhiFuService.class,
                    SpringProxy.class, Advised.class, DecoratingProxy.class);
            new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(),
                    GitRepositoryAccessParamValue.class,
                    GitRepositoryAccessParamValue.Repository.class,
                    DigitalDeliveryParamValue.class,
                    LicenseDeliveryParamValue.class,
                    ShopOrderFormParamValue.class,
                    ShopOrderFormParamValue.Field.class,
                    ShopOrderFormParamValue.Option.class,
                    CustomerSocketCommand.class,
                    ShopItemGroupListQo.class,
                    ShopItemListQo.class,
                    ShopOrderListQo.class,
                    H5ZhiFuPayDto.class,
                    H5ZhiFuPayResultDto.class,
                    H5ZhiFuResultDto.class);
        }
    }
}
