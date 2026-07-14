import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { downloadOrderDeliveryFile } from '@/modules/shop/pages/Shop/apis/api.js';
import { useI18n } from 'vue-i18n';

const fileKey = (delivery, file) => `${delivery?.orderNo || ''}:${file?.code || ''}`;

const saveBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || 'delivery-file';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

export const useDeliveryFiles = () => {
  const { t } = useI18n();
  const downloadingFileCode = ref('');

  const downloadDeliveryFile = async (delivery, file) => {
    if (!delivery?.orderNo || !file?.code) {
      message.warning(t('orders.missingDownload'));
      return;
    }
    const key = fileKey(delivery, file);
    downloadingFileCode.value = key;
    try {
      const blob = await downloadOrderDeliveryFile(delivery.orderNo, file.code);
      saveBlob(blob, file.name);
      message.success(t('orders.downloadStarted'));
    } catch (e) {
      message.error(e.message || t('orders.downloadFailed'));
    } finally {
      downloadingFileCode.value = '';
    }
  };

  return {
    downloadingFileCode,
    downloadDeliveryFile,
    fileKey
  };
};
