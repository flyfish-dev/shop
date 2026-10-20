import { message } from 'ant-design-vue';
import { OFFICE_LICENSE_KIND } from '@/modules/shop/utils/shopDelivery.js';

const deploymentModeOptions = [
  { label: '具体域名 / IP', value: 'specific' },
  { label: 'SaaS 泛域名', value: 'saas' }
];

/**
 * 商品维护端的商业授权域名配置。
 *
 * 这里只维护表单的即时一致性，后端 LicenseOriginPolicy 仍会在保存和签发时做最终校验。
 */
export function useCommercialLicenseConfiguration() {
  const commercialSaasOrigin = license => (
    Array.isArray(license?.allowedOrigins) ? String(license.allowedOrigins[0] || '') : ''
  );

  const updateCommercialSaasOrigin = (license, value) => {
    if (!license) {
      return;
    }
    const origin = String(value || '').trim();
    license.allowedOrigins = origin ? [origin] : [];
    license.maxDeployments = 1;
  };

  const updateCommercialSpecificOrigins = (license, values) => {
    if (!license) {
      return;
    }
    const origins = Array.from(new Set(
      (values || []).map(value => String(value || '').trim()).filter(Boolean)
    ));
    if (origins.length > 5) {
      message.warning('商业版具体域名模式最多配置 5 个域名或 IP');
    }
    license.allowedOrigins = origins.slice(0, 5);
    license.maxDeployments = 5;
  };

  const updateOtherEditionOrigins = (license, values) => {
    if (license) {
      license.allowedOrigins = values || [];
    }
  };

  const handleCommercialDeploymentModeChange = (license, mode) => {
    if (!license) {
      return;
    }
    license.deploymentMode = mode === 'saas' ? 'saas' : 'specific';
    license.allowedOrigins = [];
    license.maxDeployments = license.deploymentMode === 'saas' ? 1 : 5;
  };

  const applyLicenseEditionDefaults = (license, edition) => {
    if (!license) {
      return;
    }
    if (edition === 'personal') {
      license.commercialUse = false;
      license.maxDeployments = 1;
      license.deploymentMode = 'specific';
      if (license.licenseKind === OFFICE_LICENSE_KIND.REGISTRAR_GRANT) {
        license.licenseKind = OFFICE_LICENSE_KIND.RUNTIME;
      }
      return;
    }
    if (edition === 'commercial') {
      license.commercialUse = true;
      license.deploymentMode = license.deploymentMode === 'saas' ? 'saas' : 'specific';
      license.maxDeployments = license.deploymentMode === 'saas' ? 1 : 5;
      if (license.licenseKind === OFFICE_LICENSE_KIND.REGISTRAR_GRANT) {
        license.licenseKind = OFFICE_LICENSE_KIND.RUNTIME;
      }
      return;
    }
    license.commercialUse = true;
    if (!license.maxDeployments || license.maxDeployments < 1) {
      license.maxDeployments = 1;
    }
  };

  return {
    commercialDeploymentModeOptions: deploymentModeOptions,
    commercialSaasOrigin,
    updateCommercialSaasOrigin,
    updateCommercialSpecificOrigins,
    updateOtherEditionOrigins,
    handleCommercialDeploymentModeChange,
    applyLicenseEditionDefaults
  };
}
