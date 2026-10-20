import { computed, unref } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  CrownOutlined,
  FireOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue';
import { deliveryModeText, shopItemTypeText } from '@/modules/shop/utils/shopDelivery.js';
import { getShopItemHighlight } from '@/modules/shop/utils/shopItemEffects.js';
import {
  isPureDonationType,
  resolveShopItemCover,
  setShopImageFallback
} from '@/modules/shop/utils/shopCovers.js';
import { resolveLocalizedShopItem } from '@/modules/shop/utils/shopI18n.js';
import { resolveShopItemPromotion } from './useShopItemPromotion.js';

const highlightIcons = {
  crown: CrownOutlined,
  badge: SafetyCertificateOutlined,
  spark: ThunderboltOutlined,
  fire: FireOutlined
};

const toNumber = value => {
  const result = Number(value || 0);
  return Number.isFinite(result) ? result : 0;
};

const formatUsdAmount = value => toNumber(value).toFixed(2).replace(/\.00$/, '');

export function useShopProductCard(itemSource) {
  const { locale, t } = useI18n();
  const item = computed(() => resolveLocalizedShopItem(unref(itemSource), locale.value));
  const usdDisplay = computed(() => String(locale.value || '').toLowerCase().startsWith('en'));
  const usdAmount = computed(() => item.value?.minEffectiveUsdPrice || item.value?.effectiveUsdPrice || 0);
  const promotion = computed(() => usdDisplay.value
    ? {
        active: false,
        originalAmount: usdAmount.value,
        payableAmount: usdAmount.value,
        discountAmount: '0.00',
        rangeSuffix: ''
      }
    : resolveShopItemPromotion(item.value));
  const highlight = computed(() => getShopItemHighlight(item.value, t));
  const itemType = computed(() => shopItemTypeText(item.value?.type, t));
  const deliveryMode = computed(() => deliveryModeText(item.value?.deliveryMode, t));
  const displayTags = computed(() => {
    const used = new Set([
      item.value?.typeName,
      item.value?.deliveryModeName,
      itemType.value,
      deliveryMode.value
    ].filter(Boolean));
    return (item.value?.tags || []).filter(tag => {
      if (!tag || used.has(tag)) {
        return false;
      }
      used.add(tag);
      return true;
    });
  });
  const priceText = computed(() => {
    if (isPureDonationType(item.value?.type)) {
      return t('shop.donationStart', {
        symbol: usdDisplay.value ? '$' : '¥',
        amount: usdDisplay.value
          ? formatUsdAmount(Math.max(1, toNumber(usdAmount.value)))
          : promotion.value.payableAmount
      });
    }
    if (usdDisplay.value) {
      const price = `$${formatUsdAmount(usdAmount.value)}`;
      const hasRange = toNumber(item.value?.maxEffectiveUsdPrice || usdAmount.value) > toNumber(usdAmount.value);
      return hasRange ? `${t('shop.priceStartSuffix')} ${price}` : price;
    }
    const price = `¥${promotion.value.payableAmount}`;
    return promotion.value.rangeSuffix ? `${price}${promotion.value.rangeSuffix}` : price;
  });
  const cardClasses = computed(() => [
    'shop-product-card',
    highlight.value.style?.className,
    { 'item-highlighted': highlight.value.active }
  ]);

  return {
    cardClasses,
    cover: computed(() => resolveShopItemCover(item.value)),
    deliveryMode,
    displayTags,
    highlightIcon: computed(() => highlightIcons[highlight.value.icon]),
    highlightLabel: computed(() => highlight.value.style?.label),
    item,
    itemType,
    onImageError: event => setShopImageFallback(event, item.value?.type),
    priceText,
    promotion,
    t
  };
}
