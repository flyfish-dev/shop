<script setup>
import { computed, toRef } from 'vue';
import { PushpinFilled, StarFilled } from '@ant-design/icons-vue';
import { deliveryModeColor } from '@/modules/shop/utils/shopDelivery.js';
import { useShopProductCard } from '../hooks/useShopProductCard.js';

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  variant: {
    type: String,
    default: 'catalog'
  }
});
const emit = defineEmits(['select']);

const {
  cardClasses,
  cover,
  deliveryMode,
  displayTags,
  highlightIcon,
  highlightLabel,
  item,
  itemType,
  onImageError,
  priceText,
  promotion,
  t
} = useShopProductCard(toRef(props, 'item'));

const classes = computed(() => [...cardClasses.value, `shop-product-card-${props.variant}`]);
const visibleTags = computed(() => props.variant === 'home' ? displayTags.value.slice(0, 1) : displayTags.value);
const activate = () => emit('select', item.value.id);
</script>

<template>
  <a-card
    hoverable
    :bordered='false'
    :class='classes'
    role='link'
    tabindex='0'
    :aria-label='item.name'
    @click='activate'
    @keydown.enter='activate'
    @keydown.space.prevent='activate'
  >
    <template #cover>
      <div class='cover-box'>
        <div v-if='item.pinned || item.recommended' class='status-badges'>
          <a-tooltip v-if='item.pinned' :title='t("shop.pinned")'>
            <span class='status-icon status-icon-pinned' role='img' :aria-label='t("shop.pinned")'>
              <pushpin-filled />
            </span>
          </a-tooltip>
          <a-tooltip v-if='item.recommended' :title='t("shop.recommended")'>
            <span class='status-icon status-icon-recommended' role='img' :aria-label='t("shop.recommended")'>
              <star-filled />
            </span>
          </a-tooltip>
        </div>
        <div v-if='highlightLabel || highlightIcon' class='highlight-badge'>
          <component v-if='highlightIcon' :is='highlightIcon' />
          <span v-if='highlightLabel'>{{ highlightLabel }}</span>
        </div>
        <img :src='cover' alt='' loading='lazy' decoding='async' @error='onImageError' />
      </div>
    </template>
    <div class='card-body'>
      <div class='card-content'>
        <div class='item-name' :title='item.name'>{{ item.name }}</div>
        <div class='tag-list'>
          <a-tag v-if='item.type && variant !== "home"' color='blue'>{{ itemType }}</a-tag>
          <a-tag :color='deliveryModeColor(item.deliveryMode)'>{{ deliveryMode }}</a-tag>
          <a-tag v-for='tag in visibleTags' :key='tag' color='green'>{{ tag }}</a-tag>
        </div>
      </div>
      <div class='price-box'>
        <span class='price-main' :class='{ discounted: promotion.active }'>
          <span v-if='promotion.active' class='original-price'>¥{{ promotion.originalAmount }}</span>
          <span class='price'>{{ priceText }}</span>
          <a-tag v-if='promotion.active' color='red' class='coupon-tag'>{{ t('shop.postDiscount') }}</a-tag>
        </span>
        <span class='count'>{{ t('shop.soldCount', { count: item.buyCount ?? 0 }) }}</span>
      </div>
    </div>
  </a-card>
</template>

<style scoped lang='less'>
.shop-product-card {
  width: 100%;
  height: 100%;
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(31, 122, 54, .08);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 20px rgba(32, 54, 38, .055);
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;

  :deep(.ant-card-cover) {
    overflow: hidden;
  }

  :deep(.ant-card-body) {
    display: block;
    padding: 12px 12px 13px;
  }

  &:hover,
  &:focus-visible {
    transform: translateY(-2px);
    border-color: rgba(51, 162, 4, .2);
    outline: none;
    box-shadow: 0 14px 30px rgba(32, 54, 38, .095);
  }

  &.shop-product-card-catalog {
    max-width: 268px;
  }

  &.shop-product-card-home {
    display: flex;
    height: auto;
    flex-direction: column;
    border-color: #e6ece7;
    box-shadow: none;
    background: #fff;

    :deep(.ant-card-body) { display: flex; flex: 1; flex-direction: column; padding: 17px 16px 16px; }
    .card-body { flex: 1; }
    .cover-box, .cover-box img { background: #f3f6f4; }
    .cover-box img { transition: transform .5s cubic-bezier(.2,.7,.2,1); }
    &:hover { box-shadow: 0 12px 28px #1e402b0c; }
    &:hover .cover-box img { transform: scale(1.035); }
    .item-name { color: #263c30; font-weight: 650; line-height: 1.45; }
    .card-content { min-height: 88px; }
    .tag-list :deep(.ant-tag) { background: #f6f8f7; border: 0; color: #7a8b80; padding: 0 6px; font-size: 11px; }
    .price-box .price { color: #246d4a; font-size: 22px; }
    .price-box .count { color: #9ba69e; font-size: 11px; }
    @media (prefers-reduced-motion: reduce) {
      &, .cover-box img { transition: none; }
      &:hover, &:hover .cover-box img { transform: none; }
    }
  }

  &.item-highlighted {
    border-color: rgba(226, 167, 35, .32);
    box-shadow: 0 12px 28px rgba(170, 120, 25, .12);
  }

  &.item-highlight-commercial {
    background: linear-gradient(180deg, rgba(255, 251, 235, .96), #fff 45%);
  }

  &.item-highlight-enterprise {
    border-color: rgba(96, 75, 190, .32);
    background: linear-gradient(180deg, rgba(245, 243, 255, .96), #fff 45%);
    box-shadow: 0 12px 30px rgba(83, 65, 170, .13);
  }

  &.item-highlight-hot {
    border-color: rgba(229, 72, 61, .3);
    background: linear-gradient(180deg, rgba(255, 241, 240, .95), #fff 45%);
  }

  .cover-box {
    position: relative;
    width: 100%;
    overflow: hidden;
    aspect-ratio: 1.32 / 1;
    background: linear-gradient(135deg, #f8fcf9, #f4f8ff);

    img {
      position: absolute;
      inset: 0;
      display: block;
      width: 100%;
      height: 100%;
      background: linear-gradient(135deg, #f8fcf9, #f4f8ff);
      object-fit: cover;
      color: transparent;
      font-size: 0;
    }
  }

  .status-badges {
    position: absolute;
    z-index: 1;
    top: 8px;
    left: 8px;
    display: flex;
    max-width: calc(100% - 72px);
    gap: 5px;

    .status-icon {
      display: inline-grid;
      width: 26px;
      height: 26px;
      flex: 0 0 26px;
      border: 1px solid rgba(255, 255, 255, .82);
      border-radius: 50%;
      place-items: center;
      color: #fff;
      font-size: 13px;
      box-shadow: 0 6px 16px rgba(24, 55, 37, .16);
      backdrop-filter: blur(8px);
    }

    .status-icon-pinned {
      background: rgba(218, 54, 51, .9);
    }

    .status-icon-recommended {
      background: rgba(217, 119, 6, .9);
    }
  }

  .highlight-badge {
    position: absolute;
    z-index: 2;
    right: 8px;
    bottom: 8px;
    display: inline-flex;
    max-width: calc(100% - 16px);
    min-height: 24px;
    padding: 0 8px;
    align-items: center;
    gap: 4px;
    overflow: hidden;
    border: 1px solid rgba(255, 255, 255, .76);
    border-radius: 999px;
    background: rgba(24, 55, 37, .86);
    color: #fff;
    font-size: 12px;
    font-weight: 650;
    line-height: 1;
    white-space: nowrap;
    box-shadow: 0 8px 18px rgba(24, 55, 37, .16);
    backdrop-filter: blur(8px);

    span {
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  &.item-highlight-commercial .highlight-badge {
    background: rgba(217, 119, 6, .88);
  }

  &.item-highlight-enterprise .highlight-badge {
    background: rgba(91, 68, 166, .9);
  }

  &.item-highlight-hot .highlight-badge {
    background: rgba(218, 54, 51, .88);
  }

  .card-body {
    display: grid;
    grid-template-rows: minmax(104px, 1fr) minmax(34px, auto);
    min-width: 0;
    min-height: 150px;
    gap: 10px;
    text-align: left;
  }

  &.shop-product-card-home .card-body {
    grid-template-rows: minmax(84px, 1fr) minmax(34px, auto);
    min-height: 132px;
  }

  .card-content {
    display: flex;
    min-width: 0;
    min-height: 0;
    align-self: start;
    flex-direction: column;
    gap: 8px;
    overflow: hidden;
  }

  .item-name {
    display: -webkit-box;
    overflow: hidden;
    color: #203626;
    font-size: 16px;
    font-weight: 650;
    line-height: 1.3;
    text-overflow: ellipsis;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
  }

  &.shop-product-card-home .item-name {
    -webkit-line-clamp: 2;
  }

  .tag-list {
    display: flex;
    max-height: 58px;
    flex-wrap: wrap;
    align-content: flex-start;
    gap: 6px 5px;
    overflow: hidden;

    :deep(.ant-tag) {
      display: inline-flex;
      max-width: calc(100% - 2px);
      min-height: 22px;
      padding: 0 8px;
      margin-inline-end: 0;
      align-items: center;
      overflow: hidden;
      border-radius: 6px;
      font-size: 12px;
      line-height: 20px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &.shop-product-card-home .tag-list {
    max-height: 24px;
    flex-wrap: nowrap;
  }

  .price-box {
    display: flex;
    min-height: 34px;
    align-self: end;
    align-items: flex-end;
    justify-content: space-between;
    gap: 10px;
    line-height: 1;

    .price-main {
      display: inline-flex;
      min-width: 0;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 5px 7px;
    }

    .original-price {
      flex: 0 1 auto;
      overflow: hidden;
      color: #9aa59e;
      font-size: 12px;
      text-decoration: line-through;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .price {
      color: #e5483d;
      font-size: 21px;
      font-weight: 700;
    }

    .coupon-tag {
      min-height: 20px;
      margin-inline-end: 0;
      border-radius: 999px;
      font-size: 12px;
      line-height: 18px;
    }

    .count {
      flex: 0 0 auto;
      color: #8a958e;
      font-size: 12px;
      white-space: nowrap;
    }
  }
}

@media only screen and (max-width: 640px) {
  .shop-product-card {
    &.shop-product-card-catalog {
      max-width: none;
    }

    &.shop-product-card-home {
      .card-body {
        min-height: 126px;
      }

      .item-name {
        font-size: 15px;
      }
    }
  }
}
</style>
