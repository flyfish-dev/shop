import { normalizeLocale } from '@/i18n/index.js';

export const SHOP_ITEM_I18N_LOCALES = ['en-US'];

const emptyI18nValue = () => ({
  name: '',
  tags: [],
  description: ''
});

export function defaultShopItemI18n() {
  return {
    'en-US': emptyI18nValue()
  };
}

export function normalizeShopItemI18n(value) {
  const source = value && typeof value === 'object' ? value : {};
  return SHOP_ITEM_I18N_LOCALES.reduce((result, locale) => {
    const item = source[locale] || {};
    result[locale] = {
      name: String(item.name || ''),
      tags: Array.isArray(item.tags) ? item.tags.filter(Boolean) : [],
      description: String(item.description || '')
    };
    return result;
  }, {});
}

export function compactShopItemI18n(value) {
  return Object.entries(normalizeShopItemI18n(value)).reduce((result, [locale, item]) => {
    const tags = Array.isArray(item.tags) ? item.tags.filter(Boolean) : [];
    const next = {
      name: String(item.name || '').trim(),
      tags,
      description: String(item.description || '').trim()
    };
    if (next.name || next.description || next.tags.length) {
      result[locale] = next;
    }
    return result;
  }, {});
}

export function resolveLocalizedShopItem(item, locale) {
  if (!item) {
    return item;
  }
  const currentLocale = normalizeLocale(locale);
  const localized = item.i18n?.[currentLocale] || {};
  return {
    ...item,
    name: localized.name || item.name,
    tags: localized.tags?.length ? localized.tags : item.tags,
    description: localized.description || item.description,
    originalName: item.name,
    originalTags: item.tags,
    originalDescription: item.description
  };
}
