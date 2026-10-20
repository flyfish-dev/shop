export const isLowcodeApp = import.meta.env?.VITE_APP_TARGET === 'lowcode';

// Site boundaries are full navigations. Never pass credentials in these links.
export const lowcodeSiteUrl = import.meta.env?.VITE_LOWCODE_URL
  || (import.meta.env?.DEV ? 'http://127.0.0.1:9998/' : '/lowcode/');
export const shopSiteUrl = import.meta.env?.VITE_SHOP_URL
  || (import.meta.env?.DEV ? 'http://127.0.0.1:9999/' : '/');

export function applicationPath(path) {
  if (!isLowcodeApp) return path;
  const url = new URL(window.location.href);
  url.searchParams.delete('_oauth');
  return `${url.pathname}${url.search}#${path}`;
}

export function lowcodeLink(path = '/', base = lowcodeSiteUrl) {
  const url = new URL(base, window.location.origin);
  url.hash = path;
  return url.href;
}

export function isSeparateApplicationPath(path) {
  if (isLowcodeApp) return false;
  const target = new URL(lowcodeSiteUrl, window.location.origin);
  return target.origin === window.location.origin && target.pathname !== '/'
    && (path === target.pathname.replace(/\/$/, '') || path.startsWith(target.pathname));
}
