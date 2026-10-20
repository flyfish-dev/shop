package group.flyfish.dev.shop.service.support;

import group.flyfish.dev.shop.domain.po.ShopContractFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopContractLocalizationTest {

    private final ShopContractLocalization localization = new ShopContractLocalization();

    @Test
    void selectsRequestedLanguageOncePerLogicalDocument() {
        ShopContractFile chinese = file(1L, "purchase-agreement", "zh-CN", 1);
        ShopContractFile english = file(2L, "purchase-agreement", "en-US", 1);

        List<ShopContractFile> selected = localization.selectLocaleVariants(
                List.of(chinese, english), "en-GB");

        assertEquals(List.of(2L), selected.stream().map(ShopContractFile::getId).toList());
    }

    @Test
    void fallsBackToChineseWhenEnglishVariantIsMissing() {
        ShopContractFile chinese = file(1L, "license-certificate", "zh-CN", 1);

        List<ShopContractFile> selected = localization.selectLocaleVariants(
                List.of(chinese), "en-US");

        assertEquals(List.of(1L), selected.stream().map(ShopContractFile::getId).toList());
    }

    @Test
    void onlyReturnsVariantsFromLatestRevision() {
        ShopContractFile oldChinese = file(1L, "purchase-agreement", "zh-CN", 1);
        ShopContractFile currentChinese = file(2L, "purchase-agreement", "zh-CN", 2);
        ShopContractFile currentEnglish = file(3L, "purchase-agreement", "en-US", 2);

        List<ShopContractFile> variants = localization.currentVariants(
                List.of(oldChinese, currentChinese, currentEnglish));

        assertEquals(List.of(2L, 3L), variants.stream().map(ShopContractFile::getId).toList());
    }

    @Test
    void addingMissingTranslationKeepsRevisionAndReplacingItAdvancesRevision() {
        ShopContractFile chinese = file(1L, "purchase-agreement", "zh-CN", 2);
        assertEquals(2, localization.nextVersion(
                List.of(chinese), "purchase-agreement", "en-US"));

        ShopContractFile english = file(2L, "purchase-agreement", "en-US", 2);
        assertEquals(3, localization.nextVersion(
                List.of(chinese, english), "purchase-agreement", "en-US"));
    }

    private ShopContractFile file(Long id, String documentKey, String locale, int version) {
        ShopContractFile file = new ShopContractFile();
        file.setId(id);
        file.setDocumentKey(documentKey);
        file.setLocale(locale);
        file.setFileVersion(version);
        file.setEnabled(true);
        file.setSort(0);
        return file;
    }
}
