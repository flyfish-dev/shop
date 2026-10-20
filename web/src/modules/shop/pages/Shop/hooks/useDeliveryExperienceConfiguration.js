export const DELIVERY_EXPERIENCE_KIND_OPTIONS = [
  { label: '代码仓库', value: 'REPOSITORY' },
  { label: '在线演示', value: 'DEMO' },
  { label: '使用文档', value: 'DOCUMENTATION' },
  { label: '技术支持', value: 'SUPPORT' },
  { label: '资源下载', value: 'DOWNLOAD' },
  { label: '其他链接', value: 'LINK' }
];

const MAX_LINKS = 8;
const VALID_KINDS = new Set(DELIVERY_EXPERIENCE_KIND_OPTIONS.map(option => option.value));

export const defaultDeliveryExperienceLink = () => ({
  title: '',
  titleEn: '',
  description: '',
  descriptionEn: '',
  url: '',
  kind: 'REPOSITORY',
  primary: false
});

export const defaultDeliveryExperience = () => ({
  enabled: false,
  title: '开始运行与验证',
  titleEn: 'Run and Validate',
  description: '',
  descriptionEn: '',
  links: []
});

const parseObject = value => {
  if (!value) {
    return {};
  }
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value;
  }
  try {
    const parsed = JSON.parse(value);
    return typeof parsed === 'string' ? JSON.parse(parsed) : parsed;
  } catch {
    return {};
  }
};

const normalizeLink = link => ({
  ...defaultDeliveryExperienceLink(),
  ...(link || {}),
  kind: VALID_KINDS.has(String(link?.kind || '').toUpperCase())
    ? String(link.kind).toUpperCase()
    : 'LINK',
  primary: link?.primary === true
});

export const normalizeDeliveryExperience = value => {
  const parsed = parseObject(value);
  const links = Array.isArray(parsed.links) ? parsed.links.map(normalizeLink) : [];
  if (links.length && !links.some(link => link.primary)) {
    links[0].primary = true;
  }
  return {
    ...defaultDeliveryExperience(),
    ...parsed,
    enabled: parsed.enabled === true,
    links
  };
};

export const parseDeliveryExperience = params => {
  const parsed = parseObject(params);
  return normalizeDeliveryExperience(parsed?.deliveryExperience);
};

const trimmed = value => String(value || '').trim();

const compactLink = (link, index, primaryIndex) => {
  const title = trimmed(link?.title) || trimmed(link?.titleEn);
  const url = trimmed(link?.url);
  if (!title) {
    throw new Error(`第 ${index + 1} 个交付体验入口缺少名称`);
  }
  let parsedUrl;
  try {
    parsedUrl = new URL(url);
  } catch {
    throw new Error(`第 ${index + 1} 个交付体验入口地址不正确`);
  }
  if (parsedUrl.protocol !== 'https:' || parsedUrl.username || parsedUrl.password) {
    throw new Error('交付体验入口必须使用完整的 HTTPS 地址');
  }
  return {
    title,
    titleEn: trimmed(link?.titleEn),
    description: trimmed(link?.description),
    descriptionEn: trimmed(link?.descriptionEn),
    url: parsedUrl.toString(),
    kind: VALID_KINDS.has(String(link?.kind || '').toUpperCase())
      ? String(link.kind).toUpperCase()
      : 'LINK',
    primary: index === primaryIndex
  };
};

export const compactDeliveryExperience = value => {
  const normalized = normalizeDeliveryExperience(value);
  if (!normalized.enabled) {
    return null;
  }
  if (!normalized.links.length) {
    throw new Error('启用交付体验后至少需要配置一个入口');
  }
  if (normalized.links.length > MAX_LINKS) {
    throw new Error(`交付体验入口不能超过 ${MAX_LINKS} 个`);
  }
  const primaryIndex = Math.max(0, normalized.links.findIndex(link => link.primary));
  return {
    enabled: true,
    title: trimmed(normalized.title) || '开始运行与验证',
    titleEn: trimmed(normalized.titleEn) || 'Run and Validate',
    description: trimmed(normalized.description),
    descriptionEn: trimmed(normalized.descriptionEn),
    links: normalized.links.map((link, index) => compactLink(link, index, primaryIndex))
  };
};

export function useDeliveryExperienceConfiguration() {
  return {
    kindOptions: DELIVERY_EXPERIENCE_KIND_OPTIONS,
    defaultDeliveryExperience,
    defaultDeliveryExperienceLink,
    normalizeDeliveryExperience,
    parseDeliveryExperience,
    compactDeliveryExperience
  };
}
