export const OAUTH_LOGIN_INTENT_KEY = 'flyfish_oauth_login_intent';
export const OAUTH_LOGIN_INTENT_TTL = 10 * 60 * 1000;

const normalizePath = (value, origin) => {
  const raw = String(value || '').trim();
  if (!raw.startsWith('/') || raw.startsWith('//')) {
    return '';
  }
  try {
    const url = new URL(raw, origin);
    if (url.origin !== origin) {
      return '';
    }
    url.searchParams.delete('_oauth');
    const entries = [...url.searchParams.entries()]
      .sort(([leftKey, leftValue], [rightKey, rightValue]) =>
        leftKey.localeCompare(rightKey) || leftValue.localeCompare(rightValue));
    url.search = '';
    entries.forEach(([key, entryValue]) => url.searchParams.append(key, entryValue));
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return '';
  }
};

const removeIntent = storage => {
  try {
    storage?.removeItem(OAUTH_LOGIN_INTENT_KEY);
  } catch {
    // Storage may be unavailable in restricted browser contexts.
  }
};

export const rememberOAuthLoginIntent = (storage, intent, options = {}) => {
  const now = options.now ?? Date.now();
  const origin = options.origin || window.location.origin;
  const path = normalizePath(intent?.path, origin);
  const provider = String(intent?.provider || '').trim().toLowerCase();
  if (!path || !provider) {
    return null;
  }
  const payload = {
    path,
    provider,
    skipProfilePrompt: intent?.skipProfilePrompt === true,
    createdAt: now,
    ttl: OAUTH_LOGIN_INTENT_TTL
  };
  try {
    storage?.setItem(OAUTH_LOGIN_INTENT_KEY, JSON.stringify(payload));
    return payload;
  } catch {
    return null;
  }
};

export const readOAuthLoginIntent = (storage, options = {}) => {
  const now = options.now ?? Date.now();
  const origin = options.origin || window.location.origin;
  try {
    const raw = storage?.getItem(OAUTH_LOGIN_INTENT_KEY);
    if (!raw) {
      return null;
    }
    const payload = JSON.parse(raw);
    const path = normalizePath(payload?.path, origin);
    const createdAt = Number(payload?.createdAt || 0);
    const ttl = Number(payload?.ttl || OAUTH_LOGIN_INTENT_TTL);
    if (!path || !payload?.provider || !Number.isFinite(createdAt) || !Number.isFinite(ttl)
      || now < createdAt || now - createdAt > ttl) {
      removeIntent(storage);
      return null;
    }
    return {
      ...payload,
      path,
      provider: String(payload.provider).trim().toLowerCase(),
      skipProfilePrompt: payload.skipProfilePrompt === true,
      createdAt,
      ttl
    };
  } catch {
    removeIntent(storage);
    return null;
  }
};

export const hasOAuthLoginIntent = (storage, provider, path, options = {}) => {
  const origin = options.origin || window.location.origin;
  const intent = readOAuthLoginIntent(storage, options);
  return Boolean(intent
    && intent.provider === String(provider || '').trim().toLowerCase()
    && intent.path === normalizePath(path, origin));
};

export const consumeOAuthLoginIntent = (storage, path, options = {}) => {
  const origin = options.origin || window.location.origin;
  const intent = readOAuthLoginIntent(storage, options);
  if (!intent || intent.path !== normalizePath(path, origin)) {
    return null;
  }
  removeIntent(storage);
  return intent;
};

export const clearOAuthLoginIntent = removeIntent;
