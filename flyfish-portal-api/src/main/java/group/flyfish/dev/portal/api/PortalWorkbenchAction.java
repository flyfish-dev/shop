package group.flyfish.dev.portal.api;

/**
 * 门户工作台扩展操作。
 *
 * @param name   操作名称
 * @param path   操作路径
 * @param status 操作状态
 */
public record PortalWorkbenchAction(String name, String path, String status) {
}
