export const isSignedPaidFulfillment = form => form?.purpose === 'SIGNED_PAID_FULFILLMENT';

/**
 * 将两种专享链接表单收敛为互斥的后端契约，避免促销字段混入线下签约付款记录。
 */
export const buildOfferLinkPayload = form => {
  if (isSignedPaidFulfillment(form)) {
    return {
      name: form.name,
      skuId: form.skuId,
      purpose: form.purpose,
      recipientUserId: form.recipientUserId,
      recipientName: form.recipientName,
      contractReference: form.contractReference,
      paymentReference: form.paymentReference,
      confirmationNote: form.confirmationNote,
      settlementAmount: form.settlementAmount,
      settlementCurrency: form.settlementCurrency,
      validDays: form.validDays
    };
  }
  return {
    name: form.name,
    skuId: form.skuId,
    purpose: 'PROMOTION',
    type: form.type,
    discountValue: form.type === 'FREE' ? undefined : form.discountValue,
    maxDiscountAmount: form.type === 'DISCOUNT' ? form.maxDiscountAmount : undefined,
    validDays: form.validDays
  };
};
