package group.flyfish.dev.shop.license;

/**
 * 外部授权服务返回的授权文件。
 */
public record IssuedLicenseDocument(
        String licenseNo,
        String fileName,
        String payloadJson,
        String licenseJson,
        String providerKeyId,
        String providerAlgorithm
) {
}
