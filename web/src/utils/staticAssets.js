import optimizedImages from '../../image-optimization.json' with { type: 'json' };

const ABSOLUTE_URL_PATTERN = /^(?:[a-z][a-z\d+.-]*:|\/\/|data:|blob:)/i;
const immutableReleaseFiles = new Set([
  ...Object.keys(optimizedImages.files),
  ...(optimizedImages.releaseFiles || [])
]);

const runtimeOrigin = () => {
  if (typeof window === 'undefined') {
    return '';
  }
  return String(window.__FLYFISH_STATIC_RUNTIME__?.origin || '').replace(/\/+$/, '');
};

/**
 * 将公开、内容寻址的静态资源切换到当前浏览器选中的静态线路。
 * 仅处理公开图片；API、附件、合同和授权下载必须继续使用同源鉴权链路。
 */
export const staticAssetUrl = value => {
  const source = typeof value === 'string' ? value.trim() : '';
  if (!source || ABSOLUTE_URL_PATTERN.test(source) || !source.startsWith('/images/')) {
    return source;
  }
  const origin = runtimeOrigin();
  const baseUrl = typeof window === 'undefined' ? '' : window.__FLYFISH_STATIC_RUNTIME__?.baseUrl;
  const pathname = source.split(/[?#]/, 1)[0];
  if (baseUrl && immutableReleaseFiles.has(`public${pathname}`)) {
    return new URL(source.slice(1), baseUrl).href;
  }
  return origin ? `${origin}${source}` : source;
};
