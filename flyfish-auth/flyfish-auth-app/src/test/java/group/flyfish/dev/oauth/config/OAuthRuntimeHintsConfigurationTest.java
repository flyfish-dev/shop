package group.flyfish.dev.oauth.config;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.ResourcePatternHint;
import org.springframework.aot.hint.RuntimeHints;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthRuntimeHintsConfigurationTest {

    @Test
    void limitsTemplateResourcesToOauthViews() {
        RuntimeHints hints = new RuntimeHints();
        new OAuthRuntimeHintsConfiguration.OAuthRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());
        List<ResourcePatternHint> patterns = hints.resources().resourcePatternHints()
                .flatMap(resourceHints -> resourceHints.getIncludes().stream())
                .toList();

        assertTrue(matches(patterns, "templates/oauth/redirect.html"));
        assertFalse(matches(patterns, "templates/shop/unused.html"));
    }

    private boolean matches(List<ResourcePatternHint> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pattern.matches(path));
    }
}
