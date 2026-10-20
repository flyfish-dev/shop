export const highlightStyleMeta = {
  commercial: {
    label: '商业版',
    className: 'item-highlight-commercial',
    color: 'gold'
  },
  enterprise: {
    label: '企业版',
    className: 'item-highlight-enterprise',
    color: 'purple'
  },
  hot: {
    label: '热门',
    className: 'item-highlight-hot',
    color: 'red'
  }
};

export const highlightIconMeta = {
  crown: '皇冠',
  badge: '徽章',
  spark: '闪光',
  fire: '热门'
};

export const getShopItemHighlight = (item, t) => {
  const inferredStyle = inferHighlightStyle(item);
  const styleKey = item?.highlightStyle || inferredStyle;
  const sourceStyle = highlightStyleMeta[styleKey] || null;
  const style = sourceStyle ? {
    ...sourceStyle,
    label: typeof t === 'function' ? t(`shop.highlights.${styleKey}`) : sourceStyle.label
  } : null;
  const icon = highlightIconMeta[item?.highlightIcon] ? item.highlightIcon : inferredStyleIcon(inferredStyle);
  return {
    style,
    icon,
    active: Boolean(style || icon)
  };
};

export const getShopItemSkuHighlight = (item, sku, t) => {
  if (!sku) {
    return getShopItemHighlight(item, t);
  }
  const skuStyle = inferHighlightStyle(sku);
  if (!skuStyle) {
    return getShopItemHighlight(item, t);
  }
  return getShopItemHighlight({
    ...item,
    name: sku.name,
    tags: sku.tags,
    highlightStyle: skuStyle,
    highlightIcon: inferredStyleIcon(skuStyle)
  }, t);
};

const inferHighlightStyle = item => {
  const text = `${item?.name || ''} ${(item?.tags || []).join(' ')}`.toLowerCase();
  if (text.includes('企业版') || text.includes('企业授权') || text.includes('enterprise')) {
    return 'enterprise';
  }
  if (text.includes('商业版') || text.includes('商业授权') || text.includes('commercial')) {
    return 'commercial';
  }
  return '';
};

const inferredStyleIcon = style => {
  if (style === 'enterprise') {
    return 'crown';
  }
  if (style === 'commercial') {
    return 'badge';
  }
  return '';
};
