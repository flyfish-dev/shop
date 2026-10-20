export function normalizeShopTags(value) {
  let tags = value;
  const serialized = Array.isArray(value) ? value.join(',') : value;
  if (typeof serialized === 'string' && serialized.trim().startsWith('[')) {
    try {
      const parsed = JSON.parse(serialized);
      if (Array.isArray(parsed)) tags = parsed;
    } catch {
      // Preserve ordinary labels that happen to start with a bracket.
    }
  }
  return Array.isArray(tags)
    ? [...new Set(tags.filter(tag => typeof tag === 'string').map(tag => tag.trim()).filter(Boolean))]
    : [];
}
