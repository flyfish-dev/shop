import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { downloadOrderDeliveryFile } from '@/modules/shop/pages/Shop/apis/api.js';

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
  const downloadingFileCode = ref('');

  const downloadDeliveryFile = async (delivery, file) => {
    if (!delivery?.orderNo || !file?.code) {
      message.warning('缺少下载文件信息');
      return;
    }
    const key = fileKey(delivery, file);
    downloadingFileCode.value = key;
    try {
      const blob = await downloadOrderDeliveryFile(delivery.orderNo, file.code);
      saveBlob(blob, file.name);
      message.success('文件已开始下载');
    } catch (e) {
      message.error(e.message || '文件下载失败');
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
