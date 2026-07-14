package group.flyfish.dev.git.config;

import group.flyfish.dev.git.client.GitRepositoryApiRepo;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitRuntimeHintsConfigurationTest {

    @Test
    void registersOnlyProgrammaticallyBoundProperties() {
        RuntimeHints hints = new RuntimeHints();
        new GitRuntimeHintsConfiguration.GitRuntimeHints()
                .registerHints(hints, getClass().getClassLoader());

        assertBindingHint(hints.reflection().getTypeHint(GiteaProperties.class));
        assertBindingHint(hints.reflection().getTypeHint(GithubProperties.class));
        assertBindingHint(hints.reflection().getTypeHint(GiteeProperties.class));
        assertNull(hints.reflection().getTypeHint(GitRepositoryApiRepo.class));
    }

    private void assertBindingHint(TypeHint hint) {
        assertNotNull(hint);
        assertTrue(hint.getMemberCategories().contains(MemberCategory.ACCESS_DECLARED_FIELDS));
        assertTrue(hint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertFalse(hint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_METHODS));
        assertFalse(hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));
    }
}
