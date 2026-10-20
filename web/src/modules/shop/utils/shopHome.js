export const SHOP_HOME_SECTION_LIMIT = 4;

const enabled = item => item?.enabled !== false;

const uniqueById = items => Array.from(new Map(
  items.filter(enabled).map(item => [String(item.id), item])
).values());

export const sortShopGroups = groups => [...(groups || [])]
  .filter(enabled)
  .sort((left, right) => Number(left.sort || 0) - Number(right.sort || 0));

export const selectFeaturedShopItems = (items, limit = SHOP_HOME_SECTION_LIMIT) => {
  const source = uniqueById(items || []);
  return uniqueById([
    ...source.filter(item => item.recommended),
    ...source.filter(item => item.pinned),
    ...source
  ]).slice(0, limit);
};

export const buildShopHomeSections = (groups, items, limit = SHOP_HOME_SECTION_LIMIT) => {
  const source = uniqueById(items || []);
  return sortShopGroups(groups).map(group => {
    const groupItems = source.filter(item => String(item.groupId) === String(group.id));
    return {
      ...group,
      itemCount: groupItems.length,
      items: groupItems.slice(0, limit)
    };
  }).filter(group => group.itemCount > 0);
};
