package group.flyfish.dev.shop.license;

import java.time.Instant;

/**
 * 显式许可证生效时间窗。
 *
 * @param notBefore        生效时间
 * @param expiresAt        失效时间
 * @param previousLicenseNo 前序许可证号，用于续费链路追溯
 */
public record ProductLicensePeriod(Instant notBefore, Instant expiresAt, String previousLicenseNo) {
}
