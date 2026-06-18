package group.flyfish.dev.portal.api;

/**
 * 门户能力声明。
 *
 * @param code   能力编码
 * @param name   能力名称
 * @param path   默认入口
 * @param status 状态
 */
public record PortalCapability(String code, String name, String path, String status) {
}
