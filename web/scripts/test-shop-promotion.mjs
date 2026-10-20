import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveShopItemPromotion } from '../src/modules/shop/pages/Shop/hooks/useShopItemPromotion.js';

test('keeps a fully discounted default coupon active', () => {
  const promotion = resolveShopItemPromotion({
    price: '50.00',
    defaultCouponCode: 'FREE50',
    defaultCouponPreview: {
      couponCode: 'FREE50',
      originalAmount: '50.00',
      discountAmount: '50.00',
      payableAmount: '0.00'
    }
  });

  assert.equal(promotion.active, true);
  assert.equal(promotion.originalAmount, '50.00');
  assert.equal(promotion.discountAmount, '50.00');
  assert.equal(promotion.payableAmount, '0.00');
});

test('does not activate a coupon without a real discount', () => {
  const promotion = resolveShopItemPromotion({
    price: '50.00',
    defaultCouponCode: 'INVALID',
    defaultCouponPreview: {
      couponCode: 'INVALID',
      originalAmount: '50.00',
      discountAmount: '0.00',
      payableAmount: '0.00'
    }
  });

  assert.equal(promotion.active, false);
  assert.equal(promotion.payableAmount, '50.00');
});
