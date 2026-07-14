import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { isDonationType } from '@/modules/shop/utils/shopCovers.js';
import { shopCurrencySymbol } from '@/modules/shop/utils/shopMoney.js';

const toMoneyNumber = value => {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return 0;
  }
  return Math.round(number * 100) / 100;
};

const formatMoney = value => toMoneyNumber(value).toFixed(2);

export function useShopDonationAmount({ item }) {
  const { locale, t } = useI18n();
  const donationAmount = ref();
  const donationEnabled = computed(() => isDonationType(item.value?.type));
  const paymentCurrency = computed(() => String(locale.value || '').toLowerCase().startsWith('en')
    ? 'USD'
    : 'CNY');
  const currencySymbol = computed(() => shopCurrencySymbol(paymentCurrency.value));
  const minimumDonationAmount = computed(() => paymentCurrency.value === 'USD'
    ? Math.max(1, toMoneyNumber(item.value?.effectiveUsdPrice))
    : toMoneyNumber(item.value?.price));
  const unitPrice = computed(() => paymentCurrency.value === 'USD'
    ? item.value?.effectiveUsdPrice
    : item.value?.price);
  const payableBaseAmount = computed(() => donationEnabled.value
    ? formatMoney(donationAmount.value || minimumDonationAmount.value)
    : unitPrice.value);

  const resetDonationAmount = () => {
    donationAmount.value = minimumDonationAmount.value || undefined;
  };

  const validateDonationAmount = () => {
    if (!donationEnabled.value) {
      return true;
    }
    const amount = toMoneyNumber(donationAmount.value);
    if (amount < minimumDonationAmount.value) {
      message.warning(t('shop.donationMinimumWarning', {
        symbol: currencySymbol.value,
        amount: formatMoney(minimumDonationAmount.value)
      }));
      return false;
    }
    return true;
  };

  const orderAmountPayload = () => ({
    paymentCurrency: paymentCurrency.value,
    ...(donationEnabled.value
      ? {
        donationAmount: formatMoney(donationAmount.value || minimumDonationAmount.value),
      }
      : {})
  });

  watch(() => [item.value?.id, item.value?.price, item.value?.effectiveUsdPrice,
      item.value?.type, paymentCurrency.value],
    resetDonationAmount, { immediate: true });

  return {
    donationAmount,
    donationEnabled,
    paymentCurrency,
    currencySymbol,
    minimumDonationAmount,
    payableBaseAmount,
    validateDonationAmount,
    orderAmountPayload
  };
}
