package group.flyfish.dev.git.config;

import group.flyfish.dev.common.config.RuntimeHintsSupport;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(GitRuntimeHintsConfiguration.GitRuntimeHints.class)
public class GitRuntimeHintsConfiguration {

    public static class GitRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.git.client.GiteaRepositoryClient");
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.git.client.GithubRepositoryClient");
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.git.client.GiteeRepositoryClient");
            RuntimeHintsSupport.registerConfigurationProperties(hints, classLoader,
                    "group.flyfish.dev.git.config.GiteaProperties",
                    "group.flyfish.dev.git.config.GithubProperties",
                    "group.flyfish.dev.git.config.GiteeProperties");
            RuntimeHintsSupport.registerReflectiveTypes(hints, classLoader,
                    "group.flyfish.dev.git.client.GitRepositoryApiRepo",
                    "group.flyfish.dev.git.client.GitRepositoryApiRepo$GitRepositoryOwner",
                    "group.flyfish.dev.git.client.GitRepositoryCollaborator",
                    "group.flyfish.dev.git.client.GitRepositoryCollaboratorPermission",
                    "group.flyfish.dev.git.client.GiteaRepositorySearchResponse",
                    "group.flyfish.dev.git.client.GitCollaboratorRequest",
                    "group.flyfish.dev.git.domain.dto.GitAccessTokenCreateDto",
                    "group.flyfish.dev.git.domain.dto.GitAccessTokenUpdateDto",
                    "group.flyfish.dev.git.domain.dto.GitRepositoryCreateDto",
                    "group.flyfish.dev.git.domain.dto.GitRepositoryUpdateDto");
        }
    }
}
