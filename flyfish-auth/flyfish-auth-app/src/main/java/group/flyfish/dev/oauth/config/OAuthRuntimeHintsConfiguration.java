package group.flyfish.dev.oauth.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/** Native-image resources resolved by the reactive OAuth view renderer. */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(OAuthRuntimeHintsConfiguration.OAuthRuntimeHints.class)
public class OAuthRuntimeHintsConfiguration {

    public static class OAuthRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("templates/oauth/**");
        }
    }
}
