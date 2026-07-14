package group.flyfish.dev.git.config;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/** Native binding hints for properties resolved through the programmatic Binder API. */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(GitRuntimeHintsConfiguration.GitRuntimeHints.class)
public class GitRuntimeHintsConfiguration {

    public static class GitRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(),
                    GiteaProperties.class,
                    GithubProperties.class,
                    GiteeProperties.class);
        }
    }
}
