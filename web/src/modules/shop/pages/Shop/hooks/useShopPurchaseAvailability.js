import { computed, ref } from 'vue';
import { message } from 'ant-design-vue';
import { checkPurchaseAvailability } from '../apis/api.js';
import { isGitRepositoryAccessType } from '@/modules/shop/utils/shopCovers.js';

export function useShopPurchaseAvailability({ item, user, gitAuthorization }) {
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
      ? '您已购买过'
      : availability.value?.reasonCode === 'GIT_REPOSITORY_ALREADY_OPENED'
        ? '已开通仓库权限'
        : '暂不可购买'
  ));

  const purchaseBlockMessage = computed(() => availability.value?.message || '当前商品暂时无法购买');

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
      const result = await checkPurchaseAvailability(itemId);
      if (requestSeq === availabilityRequestSeq && item.value?.id === itemId) {
        availability.value = result;
      }
    } catch (e) {
      if (requestSeq === availabilityRequestSeq && item.value?.id === itemId) {
        availability.value = { purchasable: true };
        availabilityError.value = e.message || '购买状态检查失败';
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
