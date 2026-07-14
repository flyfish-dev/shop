package group.flyfish.dev.common.config;

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
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
            registerClasspathResources(hints);
        }

        private void registerClasspathResources(RuntimeHints hints) {
            hints.resources()
                    .registerPattern("config/*.yml")
                    .registerPattern("schema/*.sql")
                    .registerPattern("dialect/**/*.sql")
                    .registerPattern("banner.txt")
                    .registerPattern("static/**");
        }
    }
}
