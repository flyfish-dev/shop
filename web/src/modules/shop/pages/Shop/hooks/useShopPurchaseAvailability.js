import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { checkPurchaseAvailability } from '../apis/api.js';
import { isGitRepositoryAccessType } from '@/modules/shop/utils/shopCovers.js';
import { useI18n } from 'vue-i18n';

export function useShopPurchaseAvailability({ item, user, gitAuthorization }) {
  const { t } = useI18n();
  const availability = ref({ purchasable: true });
  const availabilityLoading = ref(false);
  const availabilityError = ref('');
  let availabilityRequestSeq = 0;

  const shouldCheckAvailability = computed(() => Boolean(
    user.value?.id
    && item.value?.id
    && isGitRepositoryAccessType(item.value?.type)
    && gitAuthorization.value
  ));

  const purchaseBlocked = computed(() => shouldCheckAvailability.value
    && availability.value?.purchasable === false);

  const purchaseBlockTitle = computed(() => (
    availability.value?.reasonCode === 'GIT_REPOSITORY_ALREADY_PURCHASED'
      ? t('shop.availability.alreadyPurchased')
      : availability.value?.reasonCode === 'GIT_REPOSITORY_ALREADY_OPENED'
        ? t('shop.availability.alreadyOpened')
        : t('shop.availability.unavailable')
  ));

  const purchaseBlockMessage = computed(() => availability.value?.message || t('shop.availability.unavailableMessage'));

  const availabilityNotice = computed(() => (
    shouldCheckAvailability.value && !purchaseBlocked.value
      ? availability.value?.message || ''
      : ''
  ));

  const loadPurchaseAvailability = async () => {
    const requestSeq = ++availabilityRequestSeq;
    availabilityError.value = '';
    if (!shouldCheckAvailability.value) {
      availability.value = { purchasable: true };
      availabilityLoading.value = false;
      return;
    }
    availabilityLoading.value = true;
    const itemId = item.value.id;
    try {
      const result = await checkPurchaseAvailability(itemId, item.value?.skuId);
      if (requestSeq === availabilityRequestSeq && item.value?.id === itemId) {
        availability.value = result;
      }
    } catch (e) {
      if (requestSeq === availabilityRequestSeq && item.value?.id === itemId) {
        availability.value = { purchasable: true };
        availabilityError.value = e.message || t('shop.availability.checkFailed');
      }
    } finally {
      if (requestSeq === availabilityRequestSeq) {
        availabilityLoading.value = false;
      }
    }
  };

  const validatePurchaseAvailability = () => {
    if (purchaseBlocked.value) {
      message.warning(purchaseBlockMessage.value);
      return false;
    }
    return true;
  };

  return {
    availability,
    availabilityLoading,
    availabilityError,
    purchaseBlocked,
    purchaseBlockTitle,
    purchaseBlockMessage,
    availabilityNotice,
    loadPurchaseAvailability,
    validatePurchaseAvailability
  };
}
