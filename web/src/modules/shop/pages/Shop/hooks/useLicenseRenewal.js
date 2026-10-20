import { computed, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { useI18n } from 'vue-i18n';
import { getLicenseRenewalEligibility } from '../apis/api.js';
import { parseShopParams } from '@/modules/shop/utils/shopOrderForm.js';

/**
 * 服务与代码更新续费 SKU 的资格、时间和计价状态。
 *
 * 页面只提交来源许可证、最终生效日期和年数；资格、单价和应付金额由后端再次校验。
 */
export function useLicenseRenewal({ item, user, paymentCurrency }) {
  const { t } = useI18n();
  const loading = ref(false);
  const eligibility = ref(null);
  const sourceLicenseNo = ref('');
  const startDate = ref('');
  const years = ref(1);
  let requestSeq = 0;

  const config = computed(() => {
    const params = parseShopParams(item.value?.params);
    const license = params.licenseDelivery || params;
    return license.licenseRenewal || {};
  });
  const enabled = computed(() => config.value.enabled === true);
  const eligible = computed(() => enabled.value && eligibility.value?.eligible === true);
  const usdPayment = computed(() => String(paymentCurrency?.value || '').toUpperCase() === 'USD');
  const annualPrice = computed(() => usdPayment.value
    ? Number(item.value?.effectiveUsdPrice || 0)
    : Number(eligibility.value?.annualPrice || config.value.annualPrice || 199));
  const maxYears = computed(() => Number(eligibility.value?.maxYears || config.value.maxYears || 10));
  const amount = computed(() => enabled.value
    ? Number((annualPrice.value * Math.max(1, Number(years.value || 1))).toFixed(2))
    : 0);
  const licenses = computed(() => eligibility.value?.licenses || []);
  const selectedLicense = computed(() => licenses.value.find(
    license => license.licenseNo === sourceLicenseNo.value
  ) || null);
  const unavailableMessage = computed(() => {
    const reason = eligibility.value?.reasonCode;
    if (reason === 'LICENSE_RENEWAL_NOT_ELIGIBLE') {
      return t('shop.licenseRenewal.notEligible');
    }
    if (reason === 'NOT_LICENSE_RENEWAL_SKU') {
      return t('shop.licenseRenewal.notRenewal');
    }
    return eligibility.value?.message || t('shop.licenseRenewal.notEligible');
  });

  const reset = () => {
    eligibility.value = null;
    sourceLicenseNo.value = '';
    startDate.value = '';
    years.value = 1;
  };

  const load = async () => {
    const seq = ++requestSeq;
    reset();
    if (!enabled.value || !item.value?.id || !user.value?.id) {
      return;
    }
    loading.value = true;
    try {
      const result = await getLicenseRenewalEligibility(item.value.id, item.value.skuId);
      if (seq !== requestSeq) {
        return;
      }
      eligibility.value = result;
      sourceLicenseNo.value = result.defaultLicenseNo || result.licenses?.[0]?.licenseNo || '';
      startDate.value = result.defaultStartDate || result.licenses?.[0]?.suggestedStartDate || '';
    } catch (error) {
      if (seq === requestSeq) {
        eligibility.value = {
          eligible: false,
          message: error.message || t('shop.licenseRenewal.loadFailed')
        };
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false;
      }
    }
  };

  const validate = () => {
    if (!enabled.value) {
      return true;
    }
    if (loading.value) {
      message.info(t('shop.licenseRenewal.checking'));
      return false;
    }
    if (!eligible.value) {
      message.warning(unavailableMessage.value);
      return false;
    }
    if (!sourceLicenseNo.value || !startDate.value) {
      message.warning(t('shop.licenseRenewal.completeDetails'));
      return false;
    }
    const normalizedYears = Number(years.value);
    if (!Number.isInteger(normalizedYears) || normalizedYears < 1 || normalizedYears > maxYears.value) {
      message.warning(t('shop.licenseRenewal.invalidYears', { max: maxYears.value }));
      return false;
    }
    return true;
  };

  const orderPayload = () => enabled.value
    ? {
        sourceLicenseNo: sourceLicenseNo.value,
        renewalStartDate: startDate.value,
        renewalYears: Number(years.value)
      }
    : {};

  watch(
    [() => item.value?.id, () => item.value?.skuId, () => user.value?.id, enabled],
    load,
    { immediate: true }
  );

  watch(sourceLicenseNo, value => {
    const license = licenses.value.find(candidate => candidate.licenseNo === value);
    if (license?.suggestedStartDate) {
      startDate.value = license.suggestedStartDate;
    }
  });

  return {
    renewalEnabled: enabled,
    renewalLoading: loading,
    renewalEligible: eligible,
    renewalEligibility: eligibility,
    renewalLicenses: licenses,
    renewalSourceLicenseNo: sourceLicenseNo,
    renewalSelectedLicense: selectedLicense,
    renewalStartDate: startDate,
    renewalYears: years,
    renewalAnnualPrice: annualPrice,
    renewalMaxYears: maxYears,
    renewalAmount: amount,
    renewalUnavailableMessage: unavailableMessage,
    loadLicenseRenewal: load,
    validateLicenseRenewal: validate,
    licenseRenewalOrderPayload: orderPayload
  };
}
