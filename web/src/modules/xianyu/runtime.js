export const isXianyuRuntime = import.meta.env?.VITE_CHANNEL === 'xianyu';
export const storageKey = key => isXianyuRuntime ? `xianyu:${key}` : key;

export function resolveApiUrl(path, base = import.meta.env?.VITE_XIANYU_API_BASE || '') {
  if (!isXianyuRuntime || !path.startsWith('/')) return path;
  if (!base) throw new Error('渠道服务地址尚未配置');
  const target = new URL(base);
  if (target.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(target.hostname)) {
    throw new Error('渠道服务地址必须使用 HTTPS');
  }
  return new URL(path, target).href;
}

export const contractViewerBase = () => isXianyuRuntime
  ? new URL('./file-viewer/index.html', document.baseURI).href : '/file-viewer/index.html';

let authorizationTask;
export async function authorizeXianyu(setToken) {
  if (authorizationTask) return authorizationTask;
  authorizationTask = (async () => {
    if (!window.xy?.authorize || !window.WindVane?.call) throw new Error('请在闲鱼中打开');
    const authorized = await window.xy.authorize({});
    const code = authorized.authCode || authorized.code;
    if (!code) throw new Error('授权结果不完整');
    const invoke = async (path, body) => {
      const response = await fetch(resolveApiUrl(path), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      });
      const data = await response.json();
      if (!response.ok || !data.success) throw new Error(data.message || '授权失败');
      return data.result;
    };
    const { ticket } = await invoke('/xianyu/auth/authorize', { code });
    const session = await invoke('/xianyu/auth/exchange', { ticket });
    setToken(session.token);
    return session;
  })().finally(() => { authorizationTask = null; });
  return authorizationTask;
}

export async function payXianyu(payment, sdk = window.xy) {
  if (payment.action !== 'XIANYU_TRADE_PAY' || typeof payment.platformOrderId !== 'string'
    || !/^\d+$/.test(payment.platformOrderId)) throw new Error('订单支付信息尚未就绪');
  if (!sdk?.requestPayment) throw new Error('请在闲鱼中完成支付');
  try {
    await sdk.requestPayment({ orderInfo: payment.platformOrderId });
    return 'refresh';
  } catch (error) {
    if (String(error.status || error.code) === '6001') return 'cancelled';
    throw new Error(error.message || '支付结果待确认，请刷新订单');
  }
}
