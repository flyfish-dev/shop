const positiveId = value => /^\d+$/.test(String(value)) && BigInt(value) > 0n ? String(value) : null;

// Archived products retain a local catalog destination so shared checkout links stay usable.
export function resolveShopCatalogRedirect(item, query = {}) {
  let params;
  try {
    params = typeof item?.params === 'string' ? JSON.parse(item.params) : item?.params;
  } catch {
    return null;
  }
  const redirect = params?.catalogRedirect;
  const targetId = positiveId(redirect?.itemId);
  if (!targetId || targetId === String(item?.id)) return null;
  const requestedSku = positiveId(query.sku);
  const skuId = positiveId(redirect.skuIds?.[requestedSku])
    || requestedSku || positiveId(redirect.skuId);
  const nextQuery = { ...query };
  if (skuId) nextQuery.sku = skuId;
  const search = new URLSearchParams(nextQuery).toString();
  return `/shop/detail/${targetId}${search ? `?${search}` : ''}`;
}
