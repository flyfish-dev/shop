import useClientStore from '@/modules/auth/store/client.js';
import { stringify } from 'qs';

/**
 * 处理请求选项
 * @param options 原始选项
 * @returns {*} 处理后的选项
 */
function useOptions(options = {}) {
  const { body, credential, headers = {}, urlencoded = false, ...rest } = options;
  const store = useClientStore();

  // 处理认证信息
  if (credential && store.token) {
    headers['Authorization'] = `Bearer ${store.token}`;
  }

  const createOptions = extra => {
    const nextOptions = {
      ...rest,
      ...extra,
      headers,
      credentials: 'include'
    };
    if (credential) {
      // 登录态接口不能复用浏览器缓存，否则 OAuth 回调后的新 token 可能会读到旧的游客响应。
      nextOptions.cache = 'no-store';
    }
    return nextOptions;
  };

  // 处理请求体
  if (body) {
    if (body instanceof FormData) {
      // FormData 不需要设置 Content-Type，浏览器会自动添加正确的 boundary
      return createOptions({
        body,
      });
    } else if (urlencoded) {
      // 处理 application/x-www-form-urlencoded
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      return createOptions({
        body: typeof body === 'string' ? body : stringify(body),
      });
    } else {
      // 默认使用 application/json
      headers['Content-Type'] = 'application/json';
      return createOptions({
        body: JSON.stringify(body),
      });
    }
  }

  return createOptions({});
}

/**
 * 请求
 * @param url 请求地址
 * @param rawOptions 选项
 */
export async function request(url, rawOptions = {}) {
  const store = useClientStore();
  try {
    // 修复options
    const options = useOptions(rawOptions);
    const { authRedirect = true } = options;

    if (options.params) {
      url += `?${stringify(options.params)}`;
    }
    const response = await fetch(url, options);
    if (response.status === 401) {
      store.clearAuth();
      if (authRedirect) {
        store.rememberRedirect();
        window.location.replace('/login');
      }
      throw new Error('请先登录');
    }
    if (options.blob) {
      return response.blob();
    }
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    const { success, message, page, result } = payload;
    if (success) {
      // 存在分页对象，额外返回分页信息
      if (page) {
        result.page = page;
      }
      return result;
    } else {
      // $message.error(message || '请求服务器出现异常！');
      throw new Error(message);
    }
  } catch (e) {
    // $message.error('发送请求出现异常！' + e)
    throw new Error(e.message);
  }
}

export async function get(url, options) {
  return request(url, {
    ...options,
    method: 'GET'
  });
}

export async function post(url, options) {
  return request(url, {
    ...options,
    method: 'POST'
  });
}

export async function patch(url, options) {
  return request(url, {
    ...options,
    method: 'PATCH'
  });
}

export async function put(url, options) {
  return request(url, {
    ...options,
    method: 'PUT'
  });
}

export async function del(url, options) {
  return request(url, {
    ...options,
    method: 'DELETE'
  });
}
