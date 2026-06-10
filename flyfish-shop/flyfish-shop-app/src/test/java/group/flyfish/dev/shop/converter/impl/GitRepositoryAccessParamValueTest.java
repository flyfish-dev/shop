package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.shop.converter.ShopItemParamValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitRepositoryAccessParamValueTest {

    @Test
    void parsesNestedLegacyJson() {
        GitRepositoryAccessParamValue value = ShopItemParamValue.gitRepositoryAccess(
                "\"\\\"{\\\\\\\"provider\\\\\\\":\\\\\\\"github\\\\\\\",\\\\\\\"owner\\\\\\\":\\\\\\\"wybaby168\\\\\\\",\\\\\\\"repo\\\\\\\":\\\\\\\"pptx-viewer\\\\\\\",\\\\\\\"permission\\\\\\\":\\\\\\\"read\\\\\\\"}\\\"\"");

        assertEquals("github", value.getProvider());
        assertEquals("wybaby168", value.getRepositories().getFirst().getOwner());
        assertEquals("pptx-viewer", value.getRepositories().getFirst().getRepo());
        assertEquals("pull", value.getRepositories().getFirst().getPermission());
        assertTrue(value.hasRepository());
    }

    @Test
    void keepsStandardRepositoryValues() {
        GitRepositoryAccessParamValue value = ShopItemParamValue.gitRepositoryAccess("""
                {"provider":"github","owner":"wybaby168","repo":"pptx-viewer.git","permission":"maintain"}
                """);

        assertEquals("github", value.getProvider());
        assertEquals("wybaby168", value.getRepositories().getFirst().getOwner());
        assertEquals("pptx-viewer", value.getRepositories().getFirst().getRepo());
        assertEquals("maintain", value.getRepositories().getFirst().getPermission());
    }

    @Test
    void keepsMultipleStandardRepositories() {
        GitRepositoryAccessParamValue value = ShopItemParamValue.gitRepositoryAccess("""
                {"provider":"github","repositories":[
                  {"owner":"wybaby168","repo":"docx-viewer","permission":"read"},
                  {"owner":"wybaby168","repo":"pptx-viewer.git","permission":"push"}
                ]}
                """);

        assertEquals("github", value.getProvider());
        assertEquals(2, value.getRepositories().size());
        assertEquals("docx-viewer", value.getRepositories().get(0).getRepo());
        assertEquals("pull", value.getRepositories().get(0).getPermission());
        assertEquals("pptx-viewer", value.getRepositories().get(1).getRepo());
        assertEquals("push", value.getRepositories().get(1).getPermission());
    }

    @Test
    void keepsGiteePermissionValuesAlignedWithOfficialApi() {
        GitRepositoryAccessParamValue value = ShopItemParamValue.gitRepositoryAccess("""
                {"provider":"gitee","repositories":[
                  {"owner":"flyfish","repo":"viewer","permission":"write"},
                  {"owner":"flyfish","repo":"admin-viewer","permission":"admin"}
                ]}
                """);

        assertEquals("gitee", value.getProvider());
        assertEquals("push", value.getRepositories().getFirst().getPermission());
        assertEquals("admin", value.getRepositories().get(1).getPermission());
    }

    @Test
    void keepsRepositoryLevelProviderForMixedPlatformDelivery() {
        GitRepositoryAccessParamValue value = ShopItemParamValue.gitRepositoryAccess("""
                {"provider":"gitea","repositories":[
                  {"provider":"github","repositoryId":11,"owner":"flyfish-dev","repo":"rtsp-source","permission":"pull"},
                  {"provider":"gitea","repositoryId":12,"owner":"flyfish","repo":"viewer","permission":"read"}
                ]}
                """);

        assertEquals("github", value.getProvider());
        assertEquals("pull", value.getPermission());
        assertEquals("github", value.getRepositories().getFirst().getProvider());
        assertEquals("pull", value.getRepositories().getFirst().getPermission());
        assertEquals("gitea", value.getRepositories().get(1).getProvider());
        assertEquals("read", value.getRepositories().get(1).getPermission());
    }
}
