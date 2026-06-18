package group.flyfish.dev.common.upload.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UploadServiceImplTest {

    @Test
    void keepsChineseDisplayNameAndUsesAsciiStorageName() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String displayName = UploadServiceImpl.safeFilename("企业版源码购买与交付合同.docx");

        assertEquals("企业版源码购买与交付合同.docx", displayName);
        assertEquals(hash + ".docx", UploadServiceImpl.storageFilename(hash, displayName));
    }

    @Test
    void stripsBrowserProvidedDirectoryPartWithoutPathApi() {
        String displayName = UploadServiceImpl.safeFilename("C:\\fakepath\\企业版源码购买与交付合同.docx");

        assertEquals("企业版源码购买与交付合同.docx", displayName);
    }

    @Test
    void ignoresUnsafeExtensionInStorageName() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String displayName = UploadServiceImpl.safeFilename("合同.超长中文扩展名");

        assertEquals("合同.超长中文扩展名", displayName);
        assertEquals(hash, UploadServiceImpl.storageFilename(hash, displayName));
    }
}
