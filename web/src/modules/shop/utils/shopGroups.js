const GROUP_NAME_KEYS = {
  '开源仓库': 'repositories',
  '数字资料': 'digitalResources',
  '服务套餐': 'services',
  '授权许可': 'licenses',
  '商业套餐': 'commercial'
};

export const resolveShopGroupKey = name => GROUP_NAME_KEYS[name] || 'other';

export const resolveLocalizedShopGroup = (group, t) => {
  const key = resolveShopGroupKey(group?.name);
  const known = key !== 'other';
  return {
    ...group,
    key,
    originalName: group?.name,
    name: known ? t(`shop.groups.${key}`) : group?.name,
    description: known ? t(`shop.groupDescriptions.${key}`) : group?.description
  };
};
