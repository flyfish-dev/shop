const ORDER_FORM_KEY = 'orderForm';
const LICENSE_ALLOWED_ORIGINS_TARGET = 'license.allowedOrigins';

const normalizeLocale = locale => {
  const value = String(locale || '').replace('_', '-').toLowerCase();
  return value.startsWith('en') ? 'en-US' : 'zh-CN';
};

const translatedText = (t, key, params = {}, fallback = '') => {
  if (typeof t !== 'function') {
    return fallback;
  }
  const value = t(key, params);
  return value && value !== key ? value : fallback;
};

const localizedFieldsByKey = fields => {
  const entries = Array.isArray(fields)
    ? fields.map(field => [field?.key, field])
    : Object.entries(fields || {});
  return new Map(entries.filter(([key]) => key));
};

const builtInEnglishCopy = (raw, t) => {
  const fields = Array.isArray(raw?.fields) ? raw.fields : [];
  const keys = new Set(fields.map(field => field?.key).filter(Boolean));
  const originField = fields.find(field => field?.target === LICENSE_ALLOWED_ORIGINS_TARGET);
  const multiOrigin = Number(originField?.maxItems || 0) > 1 || originField?.type === 'textarea';
  const fieldCopy = {};

  if (originField) {
    if (originField.key === 'websiteOrigin') {
      const nonCommercial = keys.has('nonCommercialCommitment');
      fieldCopy[originField.key] = {
        label: translatedText(
          t,
          nonCommercial
            ? 'shop.orderForm.localized.nonCommercialWebsite'
            : 'shop.orderForm.localized.commercialWebsite',
          {},
          originField.label
        ),
        help: translatedText(t, 'shop.orderForm.localized.websiteHelp', {}, originField.help),
        placeholder: 'https://example.com'
      };
    } else {
      fieldCopy[originField.key] = {
        label: translatedText(
          t,
          multiOrigin
            ? 'shop.orderForm.localized.deploymentOrigins'
            : 'shop.orderForm.localized.deploymentOrigin',
          {},
          originField.label
        ),
        help: translatedText(
          t,
          multiOrigin
            ? 'shop.orderForm.localized.deploymentOriginsHelp'
            : 'shop.orderForm.localized.deploymentOriginHelp',
          { count: originField.maxItems || 1 },
          originField.help
        )
      };
    }
  }

  if (keys.has('nonCommercialCommitment')) {
    fieldCopy.nonCommercialCommitment = {
      label: translatedText(
        t,
        'shop.orderForm.localized.nonCommercialCommitment',
        {},
        fields.find(field => field.key === 'nonCommercialCommitment')?.label
      )
    };
  }

  if (keys.has('licenseeName')) {
    fieldCopy.licenseeName = {
      label: translatedText(t, 'shop.orderForm.localized.licenseeName'),
      placeholder: translatedText(t, 'shop.orderForm.localized.licenseePlaceholder'),
      help: translatedText(t, 'shop.orderForm.localized.licenseeHelp')
    };
  }
  if (keys.has('usageScenario')) {
    fieldCopy.usageScenario = {
      label: translatedText(t, 'shop.orderForm.localized.usageScenario'),
      placeholder: translatedText(t, 'shop.orderForm.localized.usagePlaceholder')
    };
  }
  if (keys.has('attributionAcknowledgement')) {
    fieldCopy.attributionAcknowledgement = {
      label: translatedText(t, 'shop.orderForm.localized.attributionAcknowledgement')
    };
  }

  if (keys.has('licenseeName')) {
    return {
      title: translatedText(t, 'shop.orderForm.localized.declarationTitle', {}, raw?.title),
      description: translatedText(t, 'shop.orderForm.localized.declarationDescription', {}, raw?.description),
      fields: fieldCopy
    };
  }
  if (originField?.key === 'websiteOrigin') {
    return {
      title: translatedText(t, 'shop.orderForm.localized.websiteTitle', {}, raw?.title),
      description: raw?.description || '',
      fields: fieldCopy
    };
  }
  if (originField) {
    return {
      title: translatedText(
        t,
        multiOrigin
          ? 'shop.orderForm.localized.enterpriseTitle'
          : 'shop.orderForm.localized.deploymentTitle',
        {},
        raw?.title
      ),
      description: translatedText(
        t,
        multiOrigin
          ? 'shop.orderForm.localized.enterpriseDescription'
          : 'shop.orderForm.localized.deploymentDescription',
        { count: originField.maxItems || 1 },
        raw?.description
      ),
      fields: fieldCopy
    };
  }
  return { fields: fieldCopy };
};

export const parseShopParams = params => {
  if (!params) {
    return {};
  }
  try {
    const parsed = typeof params === 'string' ? JSON.parse(params) : params;
    return typeof parsed === 'string' ? JSON.parse(parsed) : (parsed || {});
  } catch {
    return {};
  }
};

const normalizeField = (field, localized = {}) => {
  const key = String(field?.key || '').trim();
  if (!key) {
    return null;
  }
  const type = ['text', 'textarea', 'url', 'checkbox', 'select'].includes(field?.type)
    ? field.type
    : 'text';
  const localizedOptions = new Map((localized.options || []).map(option => [String(option?.value), option]));
  return {
    key,
    label: String(localized.label || field?.label || key).trim(),
    type,
    required: field?.required === true,
    requiredValue: field?.requiredValue,
    target: field?.target || '',
    normalize: field?.normalize || '',
    placeholder: localized.placeholder || field?.placeholder || '',
    help: localized.help || field?.help || '',
    maxLength: Number(field?.maxLength || 512),
    maxItems: Number(field?.maxItems || 0) || null,
    options: Array.isArray(field?.options)
      ? field.options
        .filter(option => option?.value !== undefined)
        .map(option => ({
          ...option,
          label: localizedOptions.get(String(option.value))?.label || option.label || option.value
        }))
      : []
  };
};

export const parseShopOrderFormConfig = (params, options = {}) => {
  const parsed = parseShopParams(params);
  const raw = parsed?.[ORDER_FORM_KEY];
  const locale = normalizeLocale(options.locale);
  const inlineCopy = raw?.i18n?.[locale] || {};
  const builtInCopy = locale === 'en-US' ? builtInEnglishCopy(raw, options.t) : { fields: {} };
  const inlineFields = localizedFieldsByKey(inlineCopy.fields);
  const fields = Array.isArray(raw?.fields)
    ? raw.fields.map(field => normalizeField(field, {
      ...(builtInCopy.fields?.[field?.key] || {}),
      ...(inlineFields.get(field?.key) || {})
    })).filter(Boolean)
    : [];
  return {
    enabled: raw?.enabled === true && fields.length > 0,
    title: inlineCopy.title || builtInCopy.title || raw?.title || '',
    description: inlineCopy.description || builtInCopy.description || raw?.description || '',
    fields
  };
};

export const defaultOrderFormValues = config => {
  const values = {};
  (config?.fields || []).forEach(field => {
    values[field.key] = field.type === 'checkbox' ? false : undefined;
  });
  return values;
};

const errorText = (t, key, params, fallback) => typeof t === 'function' ? t(key, params) : fallback;

const parseHttpUrl = (value, label, t) => {
  try {
    const url = new URL(String(value || '').trim());
    if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) {
      throw new Error('invalid-url');
    }
    return url;
  } catch {
    return { error: errorText(t, 'shop.orderForm.invalidUrl', { label }, `${label}必须是 http 或 https 地址`) };
  }
};

const shouldNormalizeOrigin = field => field.normalize === 'origin' || field.target === LICENSE_ALLOWED_ORIGINS_TARGET;

const normalizeUrlValue = (value, field, t) => {
  const parsed = parseHttpUrl(value, field.label, t);
  if (parsed.error) {
    return parsed;
  }
  if (shouldNormalizeOrigin(field)) {
    return {
      value: `${parsed.protocol}//${parsed.host}`.toLowerCase()
    };
  }
  return {
    value: parsed.toString()
  };
};

const normalizeOriginListValue = (value, field, t) => {
  const origins = String(value || '')
    .split(/[\s,，]+/)
    .map(item => item.trim())
    .filter(Boolean);
  if (!origins.length) {
    return field.required
      ? { error: errorText(t, 'shop.orderForm.required', { label: field.label }, `${field.label}不能为空`) }
      : { value: undefined };
  }
  if (field.maxItems && origins.length > field.maxItems) {
    return {
      error: errorText(
        t,
        'shop.orderForm.tooMany',
        { label: field.label, count: field.maxItems },
        `${field.label}不能超过 ${field.maxItems} 个`
      )
    };
  }
  const normalized = [];
  for (const origin of origins) {
    const result = normalizeUrlValue(origin, field, t);
    if (result.error) {
      return result;
    }
    normalized.push(result.value);
  }
  return { value: Array.from(new Set(normalized)) };
};

const normalizeCheckboxValue = (value, field, t) => {
  const checked = value === true;
  const requiredValue = field.requiredValue === undefined ? true : field.requiredValue;
  if (field.required && checked !== requiredValue) {
    return { error: errorText(t, 'shop.orderForm.required', { label: field.label }, `${field.label}不能为空`) };
  }
  return { value: checked };
};

const normalizeFieldValue = (value, field, t) => {
  if (field.type === 'checkbox') {
    return normalizeCheckboxValue(value, field, t);
  }
  const text = String(value || '').trim();
  if (!text) {
    return field.required
      ? { error: errorText(t, 'shop.orderForm.required', { label: field.label }, `${field.label}不能为空`) }
      : { value: undefined };
  }
  if (text.length > field.maxLength) {
    return { error: errorText(t, 'shop.orderForm.tooLong', { label: field.label }, `${field.label}长度过长`) };
  }
  if (field.type === 'textarea' && shouldNormalizeOrigin(field)) {
    return normalizeOriginListValue(text, field, t);
  }
  if (field.type === 'url') {
    return normalizeUrlValue(text, field, t);
  }
  if (field.type === 'select' && field.options.length) {
    const matched = field.options.some(option => String(option.value) === text);
    if (!matched) {
      return { error: errorText(t, 'shop.orderForm.invalidOption', { label: field.label }, `${field.label}不在可选范围内`) };
    }
  }
  return { value: text };
};

export const normalizeOrderFormValues = (config, rawValues, t) => {
  if (!config?.enabled) {
    return { ok: true, values: {} };
  }
  const values = {};
  for (const field of config.fields) {
    const result = normalizeFieldValue(rawValues?.[field.key], field, t);
    if (result.error) {
      return { ok: false, message: result.error };
    }
    if (result.value !== undefined) {
      values[field.key] = result.value;
    }
  }
  return { ok: true, values };
};

export const buildOrderFormProperties = values => ({
  [ORDER_FORM_KEY]: values || {}
});
