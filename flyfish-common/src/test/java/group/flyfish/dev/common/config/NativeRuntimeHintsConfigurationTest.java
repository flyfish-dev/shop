package group.flyfish.dev.common.config;

import group.flyfish.dev.common.repository.impl.DefaultReactiveRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ResourcePatternHint;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeRuntimeHintsConfigurationTest {

    @Test
    void keepsRepositoryReflectionLimitedToRuntimeInvocation() {
        RuntimeHints hints = runtimeHints();

        TypeHint repositoryHint = hints.reflection().getTypeHint(DefaultReactiveRepositoryImpl.class);

        assertNotNull(repositoryHint);
        assertEquals(Set.of(
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS), repositoryHint.getMemberCategories());
    }

    @Test
    void registersOnlySharedClasspathResources() {
        List<ResourcePatternHint> patterns = runtimeHints().resources().resourcePatternHints()
                .flatMap(resourceHints -> resourceHints.getIncludes().stream())
                .toList();

        assertTrue(matches(patterns, "config/flyfish-common.yml"));
        assertTrue(matches(patterns, "schema/00-common.sql"));
        assertTrue(matches(patterns, "dialect/mysql/00-common.sql"));
        assertTrue(matches(patterns, "static/index.html"));
        assertFalse(matches(patterns, "templates/oauth/redirect.html"));
        assertFalse(matches(patterns, "public/unused.txt"));
    }

    private RuntimeHints runtimeHints() {
        RuntimeHints hints = new RuntimeHints();
        new NativeRuntimeHintsConfiguration.FlyfishRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    private boolean matches(List<ResourcePatternHint> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
