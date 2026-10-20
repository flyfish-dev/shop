import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { useI18n } from 'vue-i18n';
import { parseShopParams } from '@/modules/shop/utils/shopOrderForm.js';

/**
 * 商业版和企业版首购维护协议确认。
 *
 * 首年权益已经包含在商品价格中，这里只管理用户是否主动确认协议，
 * 不参与任何金额计算。
 */
export function useLicenseMaintenanceAgreement({ item }) {
  const { t } = useI18n();
  const selected = ref(false);

  const config = computed(() => {
    const params = parseShopParams(item.value?.params);
    const license = params.licenseDelivery || params;
    const agreement = license.maintenanceAgreement || {};
    const enabled = ['commercial', 'enterprise'].includes(license.edition)
      && agreement.enabled === true;
    return {
      enabled,
      required: enabled && agreement.required !== false
    };
  });

  const available = computed(() => config.value.enabled);

  const orderPayload = () => ({
    maintenanceAgreement: selected.value && available.value
  });

  const validate = () => {
    if (available.value && config.value.required && !selected.value) {
      message.warning(t('shop.licenseMaintenance.agreementRequired'));
      return false;
    }
    return true;
  };

  watch(
    [() => item.value?.id, () => item.value?.skuId, available],
    () => {
      selected.value = false;
    }
  );

  return {
    maintenanceAvailable: available,
    maintenanceConfig: config,
    maintenanceSelected: selected,
    maintenanceOrderPayload: orderPayload,
    validateMaintenanceAgreement: validate
  };
}
