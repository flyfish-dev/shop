<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import dayjs from 'dayjs';
import { message } from 'ant-design-vue';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue';
import { createCoupon, deleteCoupon, getCoupons, updateCoupon } from '../../apis/manage.js';

const loading = ref(false);
const saving = ref(false);
const modalVisible = ref(false);
const editingId = ref(null);
const formRef = ref();
const coupons = ref([]);

const formState = reactive({
  code: '',
  name: '',
  type: 'REDUCTION',
  discountValue: 1,
  thresholdAmount: 0,
  totalCount: 0,
  enabled: true,
  validRange: []
});

const columns = [
  { title: '优惠券', dataIndex: 'name', key: 'name', width: 240 },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '规则', dataIndex: 'displayRule', width: 180 },
  { title: '数量', key: 'quota', width: 160 },
  { title: '状态', key: 'status', width: 120 },
  { title: '有效期', key: 'validTime', width: 260 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' }
];

const rules = {
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择优惠类型', trigger: 'change' }],
  discountValue: [{ required: true, message: '请输入优惠值', trigger: 'change' }]
};

const modalTitle = computed(() => editingId.value ? '编辑优惠券' : '生成优惠券');
const discountLabel = computed(() => formState.type === 'DISCOUNT' ? '折扣值' : '满减金额');

const loadCoupons = async () => {
  loading.value = true;
  try {
    coupons.value = await getCoupons();
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  editingId.value = null;
  Object.assign(formState, {
    code: '',
    name: '',
    type: 'REDUCTION',
    discountValue: 1,
    thresholdAmount: 0,
    totalCount: 0,
    enabled: true,
    validRange: []
  });
  formRef.value?.clearValidate?.();
};

const openCreate = () => {
  resetForm();
  modalVisible.value = true;
};

const openEdit = record => {
  editingId.value = record.id;
  Object.assign(formState, {
    code: record.code,
    name: record.name,
    type: record.type,
    discountValue: Number(record.discountValue || 0),
    thresholdAmount: Number(record.thresholdAmount || 0),
    totalCount: record.totalCount || 0,
    enabled: record.enabled !== false,
    validRange: record.startTime || record.endTime
      ? [record.startTime ? dayjs(record.startTime) : null, record.endTime ? dayjs(record.endTime) : null]
      : []
  });
  modalVisible.value = true;
};

const generateCode = () => {
  formState.code = `FF${Math.random().toString(36).slice(2, 10).toUpperCase()}`;
};

const buildPayload = () => {
  const [startTime, endTime] = formState.validRange || [];
  return {
    code: formState.code?.trim() || undefined,
    name: formState.name?.trim(),
    type: formState.type,
    discountValue: formState.discountValue,
    thresholdAmount: formState.thresholdAmount || 0,
    totalCount: formState.totalCount || 0,
    enabled: formState.enabled,
    startTime: startTime ? dayjs(startTime).format('YYYY-MM-DD HH:mm:ss') : null,
    endTime: endTime ? dayjs(endTime).format('YYYY-MM-DD HH:mm:ss') : null
  };
};

const submit = async () => {
  await formRef.value?.validate?.();
  saving.value = true;
  try {
    const payload = buildPayload();
    if (editingId.value) {
      await updateCoupon(editingId.value, payload);
      message.success('优惠券已更新');
    } else {
      await createCoupon(payload);
      message.success('优惠券已生成');
    }
    modalVisible.value = false;
    await loadCoupons();
  } catch (e) {
    message.error(e.message || '保存失败');
  } finally {
    saving.value = false;
  }
};

const removeCoupon = async record => {
  await deleteCoupon(record.id);
  message.success('优惠券已删除');
  await loadCoupons();
};

onMounted(loadCoupons);
</script>

<template>
  <div class="coupon-manage">
    <a-card title="优惠券管理">
      <template #extra>
        <a-space>
          <a-button @click="loadCoupons">
            <template #icon><reload-outlined /></template>
            刷新
          </a-button>
          <a-button type="primary" @click="openCreate">
            <template #icon><plus-outlined /></template>
            生成优惠券
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="coupons"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :scroll="{ x: 1200 }"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="coupon-name">
              <strong>{{ record.name }}</strong>
              <a-typography-text copyable class="coupon-code">{{ record.code }}</a-typography-text>
            </div>
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag :color="record.type === 'DISCOUNT' ? 'blue' : 'green'">{{ record.typeName }}</a-tag>
          </template>
          <template v-else-if="column.key === 'quota'">
            <span v-if="record.totalCount && record.totalCount > 0">
              {{ record.usedCount || 0 }} / {{ record.totalCount }}
            </span>
            <span v-else>不限量</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-space :size="6">
              <a-tag :color="record.enabled ? 'green' : 'default'">
                {{ record.enabled ? '启用' : '停用' }}
              </a-tag>
              <a-tag :color="record.validNow ? 'blue' : 'orange'">
                {{ record.validNow ? '可用' : '不可用' }}
              </a-tag>
            </a-space>
          </template>
          <template v-else-if="column.key === 'validTime'">
            <span>{{ record.startTime || '立即' }} 至 {{ record.endTime || '长期' }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEdit(record)">
                <template #icon><edit-outlined /></template>
                编辑
              </a-button>
              <a-popconfirm title="确认删除该优惠券？" @confirm="removeCoupon(record)">
                <a-button type="link" danger size="small">
                  <template #icon><delete-outlined /></template>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="saving"
      width="560px"
      @ok="submit"
    >
      <a-form ref="formRef" layout="vertical" :model="formState" :rules="rules">
        <a-form-item label="优惠券名称" name="name">
          <a-input v-model:value="formState.name" :maxlength="64" />
        </a-form-item>
        <a-form-item label="优惠券编码" name="code">
          <a-input-group compact>
            <a-input v-model:value="formState.code" class="code-input" :maxlength="64" />
            <a-button @click="generateCode">生成</a-button>
          </a-input-group>
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="优惠类型" name="type">
              <a-select v-model:value="formState.type">
                <a-select-option value="REDUCTION">满减</a-select-option>
                <a-select-option value="DISCOUNT">折扣</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="discountLabel" name="discountValue">
              <a-input-number v-model:value="formState.discountValue" :min="0.01" :precision="2" class="full-input" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="使用门槛" name="thresholdAmount">
              <a-input-number v-model:value="formState.thresholdAmount" :min="0" :precision="2" class="full-input" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发放总量" name="totalCount">
              <a-input-number v-model:value="formState.totalCount" :min="0" :precision="0" class="full-input" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="有效期" name="validRange">
          <a-range-picker v-model:value="formState.validRange" show-time class="full-input" />
        </a-form-item>
        <a-form-item label="启用状态" name="enabled">
          <a-switch v-model:checked="formState.enabled" checked-children="启用" un-checked-children="停用" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.coupon-manage {
  min-width: 0;
  padding: 20px;
}

.coupon-name {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;

  strong {
    color: #24364d;
  }
}

.coupon-code {
  color: #7b8794;
  font-size: 12px;
}

.code-input {
  width: calc(100% - 74px);
}

.full-input {
  width: 100%;
}

@media only screen and (max-width: 640px) {
  .coupon-manage {
    padding: 0;
  }
}
</style>
