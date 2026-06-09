import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { isGitRepositoryDonationAccessType } from '@/modules/shop/utils/shopCovers.js';

const toMoneyNumber = value => {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return 0;
  }
  return Math.round(number * 100) / 100;
};

const formatMoney = value => toMoneyNumber(value).toFixed(2);

export function useShopDonationAmount({ item }) {
  const donationAmount = ref();
  const donationEnabled = computed(() => isGitRepositoryDonationAccessType(item.value?.type));
  const minimumDonationAmount = computed(() => toMoneyNumber(item.value?.price));
  const payableBaseAmount = computed(() => donationEnabled.value
    ? formatMoney(donationAmount.value || minimumDonationAmount.value)
    : item.value?.price);

  const resetDonationAmount = () => {
    donationAmount.value = minimumDonationAmount.value || undefined;
  };

  const validateDonationAmount = () => {
    if (!donationEnabled.value) {
      return true;
    }
    const amount = toMoneyNumber(donationAmount.value);
    if (amount < minimumDonationAmount.value) {
      message.warning(`打赏金额不能低于 ¥${formatMoney(minimumDonationAmount.value)}`);
      return false;
    }
    return true;
  };

  const orderAmountPayload = () => donationEnabled.value
    ? { donationAmount: formatMoney(donationAmount.value || minimumDonationAmount.value) }
    : {};

  watch(() => [item.value?.id, item.value?.price, item.value?.type], resetDonationAmount, { immediate: true });

  return {
    donationAmount,
    donationEnabled,
    minimumDonationAmount,
    payableBaseAmount,
    validateDonationAmount,
    orderAmountPayload
  };
}
