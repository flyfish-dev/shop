package group.flyfish.dev.shop.domain.po;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopItemDeliveryModeTest {

    @Test
    void productTypesDeclareProductionDeliveryPolicy() {
        assertEquals(ShopItem.DeliveryMode.AUTOMATIC, ShopItem.Type.GIT_REPOSITORY_ACCESS.getDefaultDeliveryMode());
        assertTrue(ShopItem.Type.GIT_REPOSITORY_ACCESS.requiresAutomaticDelivery());
        assertTrue(ShopItem.Type.GIT_REPOSITORY_ACCESS.supportsDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC));
        assertFalse(ShopItem.Type.GIT_REPOSITORY_ACCESS.supportsDeliveryMode(ShopItem.DeliveryMode.MANUAL));
        assertFalse(ShopItem.Type.GIT_REPOSITORY_ACCESS.supportsDeliveryMode(ShopItem.DeliveryMode.NONE));
        assertTrue(ShopItem.Type.GIT_REPOSITORY_ACCESS.usesGitRepositoryAccessParams());

        assertEquals(ShopItem.DeliveryMode.AUTOMATIC, ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.getDefaultDeliveryMode());
        assertTrue(ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.requiresAutomaticDelivery());
        assertTrue(ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.supportsDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC));
        assertFalse(ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.supportsDeliveryMode(ShopItem.DeliveryMode.MANUAL));
        assertTrue(ShopItem.Type.GIT_REPOSITORY_DONATION_ACCESS.usesGitRepositoryAccessParams());

        assertEquals(ShopItem.DeliveryMode.AUTOMATIC, ShopItem.Type.DIGITAL_DOWNLOAD.getDefaultDeliveryMode());
        assertTrue(ShopItem.Type.DIGITAL_DOWNLOAD.supportsDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC));
        assertTrue(ShopItem.Type.DIGITAL_DOWNLOAD.supportsDeliveryMode(ShopItem.DeliveryMode.MANUAL));
        assertTrue(ShopItem.Type.DIGITAL_DOWNLOAD.supportsDeliveryMode(ShopItem.DeliveryMode.NONE));

        assertEquals(ShopItem.DeliveryMode.MANUAL, ShopItem.Type.SERVICE_PACKAGE.getDefaultDeliveryMode());
        assertTrue(ShopItem.Type.SERVICE_PACKAGE.supportsDeliveryMode(ShopItem.DeliveryMode.MANUAL));
        assertTrue(ShopItem.Type.SERVICE_PACKAGE.supportsDeliveryMode(ShopItem.DeliveryMode.NONE));

        assertEquals(ShopItem.DeliveryMode.AUTOMATIC, ShopItem.Type.LICENSE.getDefaultDeliveryMode());
        assertTrue(ShopItem.Type.LICENSE.supportsDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC));
        assertTrue(ShopItem.Type.LICENSE.supportsDeliveryMode(ShopItem.DeliveryMode.MANUAL));
        assertTrue(ShopItem.Type.LICENSE.supportsDeliveryMode(ShopItem.DeliveryMode.NONE));
    }

    @Test
    void repositoryAccessAlwaysNormalizesToAutomaticDelivery() {
        assertEquals(ShopItem.DeliveryMode.AUTOMATIC,
                ShopItem.Type.GIT_REPOSITORY_ACCESS.normalizeDeliveryMode(null));
        assertEquals(ShopItem.DeliveryMode.AUTOMATIC,
                ShopItem.Type.GIT_REPOSITORY_ACCESS.normalizeDeliveryMode(ShopItem.DeliveryMode.MANUAL));
        assertEquals(ShopItem.DeliveryMode.AUTOMATIC,
                ShopItem.Type.GIT_REPOSITORY_ACCESS.normalizeDeliveryMode(ShopItem.DeliveryMode.NONE));
    }

    @Test
    void supportedDeliveryModesArePreserved() {
        assertEquals(ShopItem.DeliveryMode.AUTOMATIC,
                ShopItem.Type.DIGITAL_DOWNLOAD.normalizeDeliveryMode(ShopItem.DeliveryMode.AUTOMATIC));
        assertEquals(ShopItem.DeliveryMode.NONE,
                ShopItem.Type.SERVICE_PACKAGE.normalizeDeliveryMode(ShopItem.DeliveryMode.NONE));
    }
}
