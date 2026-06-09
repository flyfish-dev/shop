package group.flyfish.dev.common.config;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.common.repository.impl.DefaultReactiveRepositoryImpl;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native Image runtime hints for common infrastructure classes that are instantiated
 * by framework reflection after repository metadata has been resolved.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeRuntimeHintsConfiguration.FlyfishRuntimeHints.class)
public class NativeRuntimeHintsConfiguration {

    public static class FlyfishRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(DefaultReactiveRepositoryImpl.class,
                    MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INTROSPECT_DECLARED_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(DefaultReactiveRepository.class,
                    MemberCategory.INTROSPECT_PUBLIC_METHODS);
            registerClasspathResources(hints);
            registerSpringBindingTypes(hints, classLoader);
        }

        private void registerClasspathResources(RuntimeHints hints) {
            hints.resources()
                    .registerPattern("config/*.yml")
                    .registerPattern("schema/*.sql")
                    .registerPattern("dialect/**/*.sql")
                    .registerPattern("banner.txt")
                    .registerPattern("static/**")
                    .registerPattern("public/**")
                    .registerPattern("templates/**");
        }

        private void registerSpringBindingTypes(RuntimeHints hints, ClassLoader classLoader) {
            RuntimeHintsSupport.registerReflectiveTypes(hints, classLoader,
                    "group.flyfish.dev.common.base.reactive.BaseQo",
                    "group.flyfish.dev.common.base.reactive.PageableQo",
                    "group.flyfish.dev.common.bean.page.qo.PagedQo",
                    "group.flyfish.dev.common.bean.qo.SimpleQo",
                    "group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo");
        }
    }
}
