import dayjs from 'dayjs';
import { createI18n } from 'vue-i18n';
import zhCN from './locales/zh-CN.js';
import enUS from './locales/en-US.js';

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'];
export const DEFAULT_LOCALE = 'zh-CN';
export const LOCALE_STORAGE_KEY = 'flyfish.locale';

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS
};

export function normalizeLocale(locale) {
  const value = String(locale || '').replace('_', '-').toLowerCase();
  if (!value) {
    return DEFAULT_LOCALE;
  }
  if (value.startsWith('zh')) {
    return 'zh-CN';
  }
  if (value.startsWith('en')) {
    return 'en-US';
  }
  return DEFAULT_LOCALE;
}

function detectBrowserLocale() {
  const candidates = [
    ...(navigator.languages || []),
    navigator.language
  ].filter(Boolean);
  const matched = candidates
    .map(normalizeLocale)
    .find(locale => SUPPORTED_LOCALES.includes(locale));
  return matched || DEFAULT_LOCALE;
}

function initialLocale() {
  const saved = localStorage.getItem(LOCALE_STORAGE_KEY);
  return saved ? normalizeLocale(saved) : detectBrowserLocale();
}

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: initialLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages
});

export function getCurrentLocale() {
  return normalizeLocale(i18n.global.locale.value);
}

export function setLocale(locale) {
  const nextLocale = normalizeLocale(locale);
  i18n.global.locale.value = nextLocale;
  localStorage.setItem(LOCALE_STORAGE_KEY, nextLocale);
  document.documentElement.lang = nextLocale;
  dayjs.locale(nextLocale === 'zh-CN' ? 'zh-cn' : 'en');
  return nextLocale;
}

export function installI18n(app) {
  setLocale(getCurrentLocale());
  app.use(i18n);
}
