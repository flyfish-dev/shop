<script setup>
import { onMounted, reactive, ref } from 'vue';
import { DeleteOutlined, EditOutlined, FileDoneOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue';
import { message, Modal } from 'ant-design-vue';
import {
  createContract,
  deleteContract,
  deleteContractFile,
  getContractSignatures,
  getContracts,
  updateContract,
  updateContractFile,
  uploadContractFile
} from '../../apis/manage.js';

const contractTypeOptions = [
  { label: '采购合同', value: 'PURCHASE_AGREEMENT' },
  { label: '软件许可协议', value: 'SOFTWARE_LICENSE' },
  { label: '服务协议', value: 'SERVICE_AGREEMENT' },
  { label: '订阅服务协议', value: 'SUBSCRIPTION_AGREEMENT' },
  { label: '保密协议', value: 'CONFIDENTIALITY_AGREEMENT' },
  { label: '数据处理协议', value: 'DATA_PROCESSING_AGREEMENT' },
  { label: '验收确认书', value: 'ACCEPTANCE_CONFIRMATION' },
  { label: '其他合同', value: 'CUSTOM' }
];

const contractLocaleOptions = [
  { label: '简体中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
];

const loading = ref(false);
const signatureLoading = ref(false);
const contracts = ref([]);
const signatures = ref([]);
const modalVisible = ref(false);
const saving = ref(false);
const editingId = ref(null);
const formRef = ref();
const formData = reactive({
  name: '',
  type: 'SOFTWARE_LICENSE',
  description: '',
  tags: [],
  i18n: {
    'en-US': {
      name: '',
      description: '',
      tags: []
    }
  },
  enabled: true,
  sort: 0
});
const uploadContexts = reactive({});

const columns = [
  { title: '合同名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'typeName', width: 150 },
  { title: '标签', dataIndex: 'tags', width: 220 },
  { title: '状态', dataIndex: 'enabled', width: 92 },
  { title: '排序', dataIndex: 'sort', width: 86 },
  { title: '文件数', dataIndex: 'files', width: 90 },
  { title: '操作', dataIndex: 'action', width: 160 }
];

const signatureColumns = [
  { title: '订单号', dataIndex: 'orderNo', width: 180 },
  { title: '用户ID', dataIndex: 'buyerId', width: 100 },
  { title: '合同', dataIndex: 'contractName' },
  { title: '文件', dataIndex: 'fileName' },
  { title: '语言', dataIndex: 'contractLocale', width: 92 },
  { title: '版本', dataIndex: 'fileVersion', width: 72 },
  { title: '阅读', dataIndex: 'readPercent', width: 80 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '时间', dataIndex: 'agreedTime', width: 180 }
];

const resetForm = () => {
  editingId.value = null;
  Object.assign(formData, {
    name: '',
    type: 'SOFTWARE_LICENSE',
    description: '',
    tags: [],
    i18n: {
      'en-US': {
        name: '',
        description: '',
        tags: []
      }
    },
    enabled: true,
    sort: 0
  });
};

const loadContracts = async () => {
  loading.value = true;
  try {
    contracts.value = await getContracts();
  } finally {
    loading.value = false;
  }
};

const changeUploadLocale = (contract, locale) => {
  const context = uploadContext(contract);
  context.locale = locale;
  if (locale === 'en-US' && !context.documentKey) {
    context.documentKey = documentOptions(contract, false)[0]?.value || '';
  }
};

const loadSignatures = async () => {
  signatureLoading.value = true;
  try {
    signatures.value = await getContractSignatures();
  } finally {
    signatureLoading.value = false;
  }
};

const openCreate = () => {
  resetForm();
  modalVisible.value = true;
};

const openEdit = record => {
  editingId.value = record.id;
  Object.assign(formData, {
    name: record.name,
    type: record.type || 'SOFTWARE_LICENSE',
    description: record.description || '',
    tags: record.tags || [],
    i18n: {
      'en-US': {
        name: record.i18n?.['en-US']?.name || '',
        description: record.i18n?.['en-US']?.description || '',
        tags: record.i18n?.['en-US']?.tags || []
      }
    },
    enabled: record.enabled !== false,
    sort: record.sort || 0
  });
  modalVisible.value = true;
};

const saveContract = async () => {
  await formRef.value.validate();
  saving.value = true;
  try {
    const payload = {
      ...formData,
      tags: formData.tags || [],
      enabled: formData.enabled === true,
      sort: formData.sort || 0
    };
    if (editingId.value) {
      await updateContract(editingId.value, payload);
      message.success('合同已更新');
    } else {
      await createContract(payload);
      message.success('合同已创建');
    }
    modalVisible.value = false;
    await loadContracts();
  } finally {
    saving.value = false;
  }
};

const removeContract = record => {
  Modal.confirm({
    title: '删除合同',
    content: record.name,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteContract(record.id);
      message.success('合同已删除');
      await loadContracts();
    }
  });
};

const uploadFile = contract => async ({ file, onSuccess, onError }) => {
  try {
    const context = uploadContext(contract);
    if (context.locale === 'en-US' && !context.documentKey) {
      throw new Error('上传英文版本前，请选择它对应的中文文档');
    }
    const data = new FormData();
    data.append('file', file);
    const result = await uploadContractFile(contract.id, data, {
      locale: context.locale,
      ...(context.documentKey ? { documentKey: context.documentKey } : {})
    });
    onSuccess(result);
    message.success('合同文件已上传');
    await loadContracts();
  } catch (e) {
    onError(e);
    message.error(e.message || '文件上传失败');
  }
};

const uploadContext = contract => {
  if (!uploadContexts[contract.id]) {
    uploadContexts[contract.id] = {
      locale: 'zh-CN',
      documentKey: ''
    };
  }
  return uploadContexts[contract.id];
};

const documentOptions = (contract, includeNew = true) => {
  const documents = new Map();
  (contract.files || []).forEach(file => {
    if (file.documentKey && !documents.has(file.documentKey)) {
      documents.set(file.documentKey, {
        label: `${file.fileName} · ${file.documentKey}`,
        value: file.documentKey
      });
    }
  });
  const options = [...documents.values()];
  return includeNew ? [{ label: '新建独立文档', value: '' }, ...options] : options;
};

const localeLabel = locale => contractLocaleOptions.find(option => option.value === locale)?.label || locale;

const toggleFile = async (contract, file) => {
  await updateContractFile(contract.id, file.id, {
    enabled: file.enabled !== true
  });
  await loadContracts();
};

const removeFile = (contract, file) => {
  Modal.confirm({
    title: '删除合同文件',
    content: file.fileName,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await deleteContractFile(contract.id, file.id);
      message.success('文件已删除');
      await loadContracts();
    }
  });
};

onMounted(() => {
  loadContracts();
  loadSignatures();
});
</script>

<template>
  <div class="contract-manage">
    <a-tabs>
      <a-tab-pane key="contracts" tab="合同文档">
        <div class="toolbar">
          <a-button type="primary" @click="openCreate">
            <plus-outlined />
            新增合同
          </a-button>
        </div>
        <a-table
          :columns="columns"
          :data-source="contracts"
          :loading="loading"
          row-key="id"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'tags'">
              <a-space wrap :size="4">
                <a-tag v-for="tag in record.tags" :key="tag" color="green">{{ tag }}</a-tag>
              </a-space>
            </template>
            <template v-else-if="column.dataIndex === 'enabled'">
              <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '停用' }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'files'">
              {{ record.files?.length || 0 }}
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-space>
                <a-button size="small" @click="openEdit(record)">
                  <edit-outlined />
                </a-button>
                <a-button size="small" danger @click="removeContract(record)">
                  <delete-outlined />
                </a-button>
              </a-space>
            </template>
          </template>
          <template #expandedRowRender="{ record }">
            <div class="file-panel">
              <div class="file-upload-bar">
                <a-segmented
                  :value="uploadContext(record).locale"
                  :options="contractLocaleOptions"
                  @change="value => changeUploadLocale(record, value)"
                />
                <a-select
                  v-model:value="uploadContext(record).documentKey"
                  class="document-selector"
                  :options="documentOptions(record, uploadContext(record).locale !== 'en-US')"
                  :placeholder="uploadContext(record).locale === 'en-US' ? '选择对应的中文文档' : '新建或更新文档'"
                />
                <a-upload :show-upload-list="false" :custom-request="uploadFile(record)">
                  <a-button type="primary">
                    <upload-outlined />
                    上传{{ localeLabel(uploadContext(record).locale) }}文件
                  </a-button>
                </a-upload>
              </div>
              <a-alert
                v-if="uploadContext(record).locale === 'en-US'"
                type="info"
                show-icon
                message="英文文件必须关联对应的中文文档，客户只会签署当前语言版本。"
              />
              <a-list :data-source="record.files || []" size="small">
                <template #renderItem="{ item: file }">
                  <a-list-item>
                    <a-list-item-meta>
                      <template #avatar>
                        <file-done-outlined class="file-icon" />
                      </template>
                      <template #title>
                        <a :href="file.fileUrl" target="_blank" rel="noreferrer">{{ file.fileName }}</a>
                      </template>
                      <template #description>
                        <a-space wrap :size="6">
                          <a-tag :color="file.locale === 'en-US' ? 'blue' : 'green'">
                            {{ localeLabel(file.locale) }}
                          </a-tag>
                          <a-tag>v{{ file.fileVersion || 1 }}</a-tag>
                          <span>{{ file.contentType || '文件' }} · {{ file.fileSize || 0 }} bytes</span>
                          <span class="document-key">{{ file.documentKey }}</span>
                        </a-space>
                      </template>
                    </a-list-item-meta>
                    <a-space>
                      <a-switch
                        size="small"
                        :checked="file.enabled"
                        checked-children="启"
                        un-checked-children="停"
                        @change="toggleFile(record, file)"
                      />
                      <a-button size="small" danger @click="removeFile(record, file)">
                        <delete-outlined />
                      </a-button>
                    </a-space>
                  </a-list-item>
                </template>
              </a-list>
            </div>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="signatures" tab="签署留痕">
        <a-table
          :columns="signatureColumns"
          :data-source="signatures"
          :loading="signatureLoading"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'readPercent'">
              {{ record.readPercent || 0 }}%
            </template>
            <template v-else-if="column.dataIndex === 'contractLocale'">
              <a-tag :color="record.contractLocale === 'en-US' ? 'blue' : 'green'">
                {{ localeLabel(record.contractLocale) }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'fileVersion'">
              v{{ record.fileVersion || 1 }}
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag color="green">{{ record.status === 'BOUND' ? '已绑定订单' : '已同意' }}</a-tag>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="modalVisible"
      :title="editingId ? '编辑合同' : '新增合同'"
      :confirm-loading="saving"
      @ok="saveContract"
      @cancel="modalVisible = false"
    >
      <a-form ref="formRef" :model="formData" layout="vertical">
        <a-form-item label="合同类型" name="type" :rules="[{ required: true, message: '请选择合同类型' }]">
          <a-select v-model:value="formData.type" :options="contractTypeOptions" />
        </a-form-item>
        <a-tabs size="small" class="locale-form-tabs">
          <a-tab-pane key="zh-CN" tab="简体中文">
            <a-form-item label="合同名称" name="name" :rules="[{ required: true, message: '请输入合同名称' }]">
              <a-input v-model:value="formData.name" />
            </a-form-item>
            <a-form-item label="描述" name="description">
              <a-textarea v-model:value="formData.description" :auto-size="{ minRows: 3, maxRows: 6 }" />
            </a-form-item>
            <a-form-item label="标签" name="tags">
              <a-select v-model:value="formData.tags" mode="tags" />
            </a-form-item>
          </a-tab-pane>
          <a-tab-pane key="en-US" tab="English">
            <a-form-item label="Contract name">
              <a-input v-model:value="formData.i18n['en-US'].name" />
            </a-form-item>
            <a-form-item label="Description">
              <a-textarea
                v-model:value="formData.i18n['en-US'].description"
                :auto-size="{ minRows: 3, maxRows: 6 }"
              />
            </a-form-item>
            <a-form-item label="Tags">
              <a-select v-model:value="formData.i18n['en-US'].tags" mode="tags" />
            </a-form-item>
          </a-tab-pane>
        </a-tabs>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="排序" name="sort">
              <a-input-number v-model:value="formData.sort" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="启用状态" name="enabled">
              <a-switch
                v-model:checked="formData.enabled"
                checked-children="启用"
                un-checked-children="停用"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.contract-manage {
  padding: 16px;
  border-radius: 8px;
  background: #fff;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.file-panel {
  display: grid;
  gap: 12px;
  padding: 8px 12px;
  border: 1px solid #edf2f7;
  border-radius: 8px;
  background: #fbfdff;
}

.file-upload-bar {
  display: grid;
  grid-template-columns: auto minmax(240px, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.document-selector {
  min-width: 0;
}

.document-key {
  color: #8c9aaa;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.locale-form-tabs {
  margin-top: 4px;
}

.file-icon {
  color: #33a204;
  font-size: 22px;
}

@media (max-width: 720px) {
  .contract-manage {
    padding: 12px;
  }

  .file-upload-bar {
    grid-template-columns: 1fr;
  }

  .document-selector,
  .file-upload-bar :deep(.ant-upload),
  .file-upload-bar :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
