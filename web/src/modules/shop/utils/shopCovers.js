import defaultGenericCover from '@/assets/shop/default-generic.webp';
import defaultGitAccessCover from '@/assets/shop/default-git-access.webp';
import defaultDigitalDownloadCover from '@/assets/shop/default-digital-download.webp';
import defaultServicePackageCover from '@/assets/shop/default-service-package.webp';
import defaultLicenseCover from '@/assets/shop/default-license.webp';

export const SHOP_ITEM_DEFAULT_COVERS = {
  GIT_REPOSITORY_ACCESS: defaultGitAccessCover,
  GIT_REPOSITORY_DONATION_ACCESS: defaultGitAccessCover,
  DIGITAL_DOWNLOAD: defaultDigitalDownloadCover,
  SERVICE_PACKAGE: defaultServicePackageCover,
  LICENSE: defaultLicenseCover,
};

export const GIT_REPOSITORY_ACCESS_TYPES = [
  'GIT_REPOSITORY_ACCESS',
  'GIT_REPOSITORY_DONATION_ACCESS'
];

export const isGitRepositoryAccessType = type => GIT_REPOSITORY_ACCESS_TYPES.includes(type);

export const isGitRepositoryDonationAccessType = type => type === 'GIT_REPOSITORY_DONATION_ACCESS';

export const getShopItemDefaultCover = type => SHOP_ITEM_DEFAULT_COVERS[type] || defaultGenericCover;

const isUsableUrl = url => typeof url === 'string' && url.trim().length > 0;

export const normalizeShopImages = images => {
  if (Array.isArray(images)) {
    return images.filter(isUsableUrl).map(url => url.trim());
  }
  if (isUsableUrl(images)) {
    return images.split(',').map(url => url.trim()).filter(Boolean);
  }
  return [];
};

export const resolveShopItemCover = item => {
  if (isUsableUrl(item?.cover)) {
    return item.cover.trim();
  }
  return getShopItemDefaultCover(item?.type);
};

export const resolveShopItemImages = item => {
  const images = normalizeShopImages(item?.images);
  if (images.length) {
    return images;
  }
  if (isUsableUrl(item?.cover)) {
    return [item.cover.trim()];
  }
  return [getShopItemDefaultCover(item?.type)];
};

export const setShopImageFallback = (event, type) => {
  const image = event?.target;
  if (!image || image.dataset.fallback === 'true') {
    return;
  }
  image.dataset.fallback = 'true';
  image.src = getShopItemDefaultCover(type);
};
