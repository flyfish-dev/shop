package group.flyfish.dev.common.http;

import java.util.List;

/**
 * 动态 API no-store 路径提供器。
 *
 * <p>具体业务模块只声明自己的接口前缀，统一的缓存控制过滤器由平台层组合，
 * 避免基础设施模块硬编码业务路径。</p>
 */
public interface ApiNoStorePathProvider {

    List<String> prefixes();
}
