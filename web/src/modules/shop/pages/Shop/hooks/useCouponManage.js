import { computed, onMounted, reactive, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { message } from 'ant-design-vue';
import { createCoupon, deleteCoupon, getCoupons, updateCoupon } from '../apis/manage.js';

const DEFAULT_FORM = {
  code: '',
  name: '',
  type: 'REDUCTION',
  discountValue: 1,
  thresholdAmount: 0,
  maxDiscountAmount: 0,
  totalCount: 0,
  enabled: true,
  validRange: []
};

export const useCouponManage = () => {
  const loading = ref(false);
  const saving = ref(false);
  const modalVisible = ref(false);
  const editingId = ref(null);
  const formRef = ref();
  const coupons = ref([]);
  const formState = reactive({ ...DEFAULT_FORM });

  const modalTitle = computed(() => editingId.value ? '编辑优惠券' : '生成优惠券');
  const discountLabel = computed(() => formState.type === 'DISCOUNT' ? '折扣值' : '满减金额');
  const showMaxDiscount = computed(() => formState.type === 'DISCOUNT');

  const rules = {
    name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
    type: [{ required: true, message: '请选择优惠类型', trigger: 'change' }],
    discountValue: [{ required: true, message: '请输入优惠值', trigger: 'change' }]
  };

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
    Object.assign(formState, DEFAULT_FORM, { validRange: [] });
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
      maxDiscountAmount: Number(record.maxDiscountAmount || 0),
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
      maxDiscountAmount: formState.type === 'DISCOUNT' ? (formState.maxDiscountAmount || 0) : 0,
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

  watch(
    () => formState.type,
    type => {
      if (type !== 'DISCOUNT') {
        formState.maxDiscountAmount = 0;
      }
    }
  );

  onMounted(loadCoupons);

  return {
    loading,
    saving,
    modalVisible,
    editingId,
    formRef,
    coupons,
    formState,
    modalTitle,
    discountLabel,
    showMaxDiscount,
    rules,
    loadCoupons,
    openCreate,
    openEdit,
    generateCode,
    submit,
    removeCoupon
  };
};
