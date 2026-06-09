
const exists = a => a;

const first = (...values) => values.find(value => typeof value === 'string' && value.trim())?.trim() || '';

export function maskIdentity(value = '') {
  const text = String(value || '');
  if (!text) return '';
  if (text.length <= 8) return text;
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
}

export function getAuthorization(user, provider) {
  const { authorizations } = user || {};
  return authorizations?.[provider] ?? null;
}

export function getGiteaAuthorization(user) {
  return getAuthorization(user, 'gitea');
}

export function getGithubAuthorization(user) {
  return getAuthorization(user, 'github');
}

export function getGiteeAuthorization(user) {
  return getAuthorization(user, 'gitee');
}

export function getGiteaUserId(user) {
  const authorization = getGiteaAuthorization(user);
  const id = authorization?.openid ?? authorization?.userInfo?.id;
  return id == null ? '' : String(id);
}

export function isMaintainer(user) {
  return Boolean(user?.id && getGiteaUserId(user) === '1');
}

export function getAuthorizationDisplay(authorization) {
  const info = authorization?.userInfo || {};
  const openid = first(authorization?.openid, info.openid, info.id);
  const login = first(authorization?.login, authorization?.loginName, info.login, info.username, info.login_name);
  const nickname = first(authorization?.nickname, info.nickname, info.name, info.full_name);
  const displayName = first(
    authorization?.displayName,
    nickname,
    info.display_name,
    info.full_name,
    info.name,
    login
  );
  const email = first(authorization?.email, info.primary_email, info.email);
  const unionId = first(authorization?.unionId, info.union_id, info.unionid);
  return {
    openid,
    unionId,
    name: displayName || (openid ? `已绑定账号 ${maskIdentity(openid)}` : '已绑定账号'),
    login,
    nickname,
    email,
    avatar: first(authorization?.avatar, authorization?.avatarUrl, info.avatar_url, info.picture_url, info.headimgurl),
    profileUrl: first(authorization?.profileUrl, info.profile_url, info.html_url, info.url),
    authTime: first(authorization?.authTime, info.captured_at),
    maskedOpenid: maskIdentity(openid),
    maskedUnionId: maskIdentity(unionId)
  };
}

export function getGiteaDisplay(user) {
  return getAuthorizationDisplay(getGiteaAuthorization(user));
}

export function getGithubDisplay(user) {
  return getAuthorizationDisplay(getGithubAuthorization(user));
}

export function getGiteeDisplay(user) {
  return getAuthorizationDisplay(getGiteeAuthorization(user));
}

export function getUserDetail(user) {
  const { authorizations } = user || {};
  if (authorizations) {
    const detail = Object.values(authorizations)
      .filter(exists)
      .map(({ userInfo }) => {
        return userInfo;
      })
      .find(exists)
    return detail ?? null;
  }
  return null
}
