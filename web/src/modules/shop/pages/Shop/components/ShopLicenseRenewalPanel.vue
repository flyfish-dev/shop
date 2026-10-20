<script setup>
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  loading: Boolean,
  eligible: Boolean,
  licenses: {
    type: Array,
    default: () => []
  },
  sourceLicenseNo: {
    type: String,
    default: ''
  },
  startDate: {
    type: String,
    default: ''
  },
  years: {
    type: Number,
    default: 1
  },
  annualPrice: {
    type: Number,
    default: 199
  },
  maxYears: {
    type: Number,
    default: 10
  },
  amount: {
    type: Number,
    default: 199
  },
  currency: {
    type: String,
    default: 'CNY'
  },
  unavailableMessage: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:sourceLicenseNo', 'update:startDate', 'update:years']);
const { t } = useI18n();

const licenseOptions = computed(() => props.licenses.map(license => ({
  value: license.licenseNo,
  label: `${license.licenseName || license.editionName} · ${license.licenseNo}`
})));
const dateValue = computed({
  get: () => props.startDate || null,
  set: value => emit('update:startDate', value || '')
});
const usdPayment = computed(() => String(props.currency || '').toUpperCase() === 'USD');
const formatAmount = value => {
  const amount = Number(value || 0);
  if (!Number.isFinite(amount)) {
    return usdPayment.value ? '$0' : '¥0.00';
  }
  return usdPayment.value ? `$${Math.round(amount)}` : `¥${amount.toFixed(2)}`;
};
</script>

<template>
  <a-spin :spinning="loading">
    <section class="license-renewal-panel">
      <p class="renewal-scope-note">{{ t('shop.licenseRenewal.description') }}</p>
      <a-alert
        v-if="!loading && !eligible"
        class="renewal-eligibility-alert"
        :message="t('shop.licenseRenewal.eligibilityRequired')"
        :description="unavailableMessage"
        type="warning"
        show-icon
      />
      <a-form v-else layout="vertical" class="license-renewal-form">
        <a-form-item :label="t('shop.licenseRenewal.sourceLicense')" required>
          <a-select
            :value="sourceLicenseNo"
            :options="licenseOptions"
            @update:value="value => emit('update:sourceLicenseNo', value)"
          />
        </a-form-item>
        <div class="renewal-fields">
          <a-form-item :label="t('shop.licenseRenewal.startDate')" required>
            <a-date-picker
              v-model:value="dateValue"
              value-format="YYYY-MM-DD"
              :allow-clear="false"
            />
          </a-form-item>
          <a-form-item :label="t('shop.licenseRenewal.years')" required>
            <a-input-number
              :value="years"
              :min="1"
              :max="maxYears"
              :precision="0"
              @update:value="value => emit('update:years', Number(value || 1))"
            />
          </a-form-item>
        </div>
        <div class="renewal-summary">
          <span>{{ t('shop.licenseRenewal.priceFormula', {
            price: formatAmount(annualPrice),
            years
          }) }}</span>
          <strong>{{ formatAmount(amount) }}</strong>
        </div>
      </a-form>
    </section>
  </a-spin>
</template>

<style scoped lang="less">
.license-renewal-panel {
  margin: 16px 0 20px;
}

.renewal-scope-note {
  margin: 0 0 12px;
  color: #52665a;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.license-renewal-form {
  padding: 14px 16px;
  border: 1px solid #d9eadf;
  border-radius: 6px;
  background: #f8fcf9;

  :deep(.ant-form-item) {
    margin-bottom: 12px;
  }
}

.renewal-eligibility-alert {
  border-radius: 6px;
}

.renewal-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(120px, 0.45fr);
  gap: 12px;

  :deep(.ant-picker),
  :deep(.ant-input-number) {
    width: 100%;
  }
}

.renewal-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #52665a;

  strong {
    color: #d4380d;
    font-size: 20px;
  }
}

@media (max-width: 575px) {
  .license-renewal-panel {
    margin: 14px 0 18px;
  }

  .license-renewal-form {
    padding: 12px;
  }

  .renewal-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
