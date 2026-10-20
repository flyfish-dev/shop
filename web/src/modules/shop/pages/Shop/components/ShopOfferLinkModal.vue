<script setup>
import { computed } from 'vue';
import {
  CopyOutlined,
  LinkOutlined,
  StopOutlined
} from '@ant-design/icons-vue';
import { isSignedPaidFulfillment } from '@/modules/shop/utils/shopOfferLink.js';
import { useShopOfferLinkManage } from '../hooks/useShopOfferLinkManage.js';

const props = defineProps({
  open: Boolean,
  item: {
    type: Object,
    default: () => ({})
  }
});
const emit = defineEmits(['update:open']);

const visible = computed({
  get: () => props.open,
  set: value => emit('update:open', value)
});
const itemRef = computed(() => props.item || {});
const openRef = computed(() => props.open);

const {
  detail,
  links,
  form,
  loading,
  submitting,
  generatedUrl,
  generatedPurpose,
  recipientLoading,
  recipientOptions,
  skuOptions,
  skuRequired,
  searchRecipients,
  create,
  revoke,
  copyGeneratedUrl
} = useShopOfferLinkManage({ item: itemRef, open: openRef });
const directMode = computed(() => isSignedPaidFulfillment(form.value));

const statusMeta = status => ({
  ACTIVE: { text: '可使用', color: 'green' },
  RESERVED: { text: '已预约', color: 'blue' },
  USED: { text: '已使用', color: 'default' },
  REVOKED: { text: '已作废', color: 'default' },
  EXPIRED: { text: '已过期', color: 'orange' }
}[status] || { text: status || '未知', color: 'default' });

const typeText = record => {
  if (record.purpose === 'SIGNED_PAID_FULFILLMENT') {
    const symbol = record.settlementCurrency === 'USD' ? '$' : '¥';
    return `已付款 ${symbol}${Number(record.settlementAmount || 0).toFixed(2)}`;
  }
  if (record.type === 'FREE') {
    return '免费领取';
  }
  if (record.type === 'REDUCTION') {
    return `减 ¥${Number(record.discountValue || 0).toFixed(2)}`;
  }
  const cap = Number(record.maxDiscountAmount || 0);
  return `${Number(record.discountValue || 0).toFixed(1)} 折${cap > 0 ? `，最高减 ¥${cap.toFixed(2)}` : ''}`;
};

const purposeText = record => record.purpose === 'SIGNED_PAID_FULFILLMENT'
  ? '直达交付'
  : '专享优惠';

const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-';
</script>

<template>
  <a-modal
    v-model:open="visible"
    width="760px"
    :footer="null"
    :destroy-on-close="true"
    wrap-class-name="shop-offer-link-modal"
  >
    <template #title>
      <a-space>
        <link-outlined />
        <span>生成专享购买链接</span>
      </a-space>
    </template>

    <a-spin :spinning="loading">
      <div class="offer-product-heading">
        <strong>{{ detail.name || item.name }}</strong>
        <span>链接仅限该商品和指定套餐使用</span>
      </div>

      <a-form :model="form" layout="vertical" class="offer-form" @finish="create">
        <a-form-item class="offer-form-full" label="链接用途" name="purpose" :rules="[{ required: true }]">
          <a-segmented
            v-model:value="form.purpose"
            :options="[
              { label: '专享优惠', value: 'PROMOTION' },
              { label: '已签约已付款', value: 'SIGNED_PAID_FULFILLMENT' }
            ]"
          />
        </a-form-item>

        <a-alert
          v-if="directMode"
          class="offer-form-full direct-mode-alert"
          type="warning"
          show-icon
          message="仅用于合同与款项均已核验的客户"
          description="可绑定已有账号；未注册客户凭私发的一次性链接在登录后领取。客户确认后系统直接创建真实金额订单并进入标准交付流程，不再重复签约或支付。"
        />

        <a-form-item label="链接备注">
          <a-input v-model:value="form.name" :maxlength="64" />
        </a-form-item>

        <a-form-item
          v-if="skuRequired"
          label="商品套餐"
          name="skuId"
          :rules="[{ required: true, message: '请选择商品套餐' }]"
        >
          <a-select v-model:value="form.skuId" :options="skuOptions" />
        </a-form-item>

        <a-form-item v-if="!directMode" label="优惠方式" name="type" :rules="[{ required: true }]">
          <a-segmented
            v-model:value="form.type"
            :options="[
              { label: '折扣', value: 'DISCOUNT' },
              { label: '立减', value: 'REDUCTION' },
              { label: '免费', value: 'FREE' }
            ]"
          />
        </a-form-item>

        <a-form-item
          v-if="!directMode && form.type !== 'FREE'"
          :label="form.type === 'DISCOUNT' ? '折扣值' : '减免金额'"
          name="discountValue"
          :rules="[{ required: true, message: '请输入优惠值' }]"
        >
          <a-input-number
            v-model:value="form.discountValue"
            :min="0.01"
            :max="form.type === 'DISCOUNT' ? 9.99 : undefined"
            :precision="2"
          >
            <template #addonAfter>{{ form.type === 'DISCOUNT' ? '折' : '元' }}</template>
          </a-input-number>
        </a-form-item>

        <a-form-item v-if="!directMode && form.type === 'DISCOUNT'" label="最高减免">
          <a-input-number v-model:value="form.maxDiscountAmount" :min="0" :precision="2">
            <template #addonAfter>元</template>
          </a-input-number>
        </a-form-item>

        <a-form-item
          v-if="directMode"
          class="offer-form-full"
          label="已有客户账号（可选）"
          name="recipientUserId"
        >
          <a-select
            v-model:value="form.recipientUserId"
            show-search
            allow-clear
            placeholder="输入昵称、邮箱或用户 ID 检索"
            :filter-option="false"
            :options="recipientOptions"
            :loading="recipientLoading"
            @search="searchRecipients"
          />
        </a-form-item>

        <a-form-item
          v-if="directMode && !form.recipientUserId"
          class="offer-form-full"
          label="客户名称"
          name="recipientName"
          :rules="[{ required: true, message: '请填写接收交付的客户名称' }]"
        >
          <a-input v-model:value="form.recipientName" :maxlength="128" />
        </a-form-item>

        <a-form-item
          v-if="directMode"
          label="合同确认编号"
          name="contractReference"
          :rules="[{ required: true, message: '请填写合同确认编号' }]"
        >
          <a-input v-model:value="form.contractReference" :maxlength="128" />
        </a-form-item>

        <a-form-item
          v-if="directMode"
          label="付款确认编号"
          name="paymentReference"
          :rules="[{ required: true, message: '请填写付款确认编号' }]"
        >
          <a-input v-model:value="form.paymentReference" :maxlength="128" />
        </a-form-item>

        <a-form-item v-if="directMode" label="已确认结算币种" name="settlementCurrency">
          <a-segmented
            v-model:value="form.settlementCurrency"
            :options="[
              { label: '人民币', value: 'CNY' },
              { label: '美元', value: 'USD' }
            ]"
          />
        </a-form-item>

        <a-form-item
          v-if="directMode"
          label="已确认付款金额"
          name="settlementAmount"
          :rules="[{ required: true, message: '请填写实际确认的付款金额' }]"
        >
          <a-input-number v-model:value="form.settlementAmount" :min="0.01" :precision="2">
            <template #addonAfter>{{ form.settlementCurrency === 'USD' ? 'USD' : 'CNY' }}</template>
          </a-input-number>
        </a-form-item>

        <a-form-item v-if="directMode" class="offer-form-full" label="确认备注">
          <a-textarea
            v-model:value="form.confirmationNote"
            :maxlength="500"
            :auto-size="{ minRows: 2, maxRows: 4 }"
          />
        </a-form-item>

        <a-form-item
          label="有效天数"
          name="validDays"
          :rules="[{ required: true, message: '请输入有效天数' }]"
        >
          <a-input-number v-model:value="form.validDays" :min="1" :max="30">
            <template #addonAfter>天</template>
          </a-input-number>
        </a-form-item>

        <div class="offer-create-action">
          <span>{{ directMode ? '链接仅限对应客户首次登录后接收一次交付' : '每条链接仅能成功购买一次' }}</span>
          <a-button type="primary" html-type="submit" :loading="submitting">
            <template #icon><link-outlined /></template>
            生成链接
          </a-button>
        </div>
      </a-form>

      <a-alert
        v-if="generatedUrl"
        class="generated-link"
        type="success"
        show-icon
        :message="generatedPurpose === 'SIGNED_PAID_FULFILLMENT'
          ? '直达交付链接已生成，请私发给指定客户'
          : '链接已生成，请立即复制'"
        description="完整链接仅在本次生成后展示，关闭窗口后无法再次查看。"
      >
        <template #action>
          <a-button type="primary" size="small" @click="copyGeneratedUrl">
            <template #icon><copy-outlined /></template>
            复制
          </a-button>
        </template>
      </a-alert>
      <a-input-group v-if="generatedUrl" compact class="generated-link-value">
        <a-input :value="generatedUrl" readonly />
        <a-button @click="copyGeneratedUrl"><copy-outlined /></a-button>
      </a-input-group>

      <a-divider orientation="left">生成记录</a-divider>
      <a-empty v-if="!links.length" description="暂无专享链接" />
      <a-list v-else :data-source="links" class="offer-history">
        <template #renderItem="{ item: record }">
          <a-list-item>
            <template #actions>
              <a-popconfirm
                v-if="['ACTIVE', 'EXPIRED'].includes(record.status)"
                title="作废后该链接将无法使用，是否继续？"
                @confirm="revoke(record)"
              >
                <a-button type="link" danger size="small">
                  <template #icon><stop-outlined /></template>
                  作废
                </a-button>
              </a-popconfirm>
            </template>
            <a-list-item-meta>
              <template #title>
                <a-space :size="6" wrap>
                  <strong>{{ record.name }}</strong>
                  <span v-if="record.skuName" class="offer-sku-name">{{ record.skuName }}</span>
                  <a-tag :color="statusMeta(record.status).color">
                    {{ statusMeta(record.status).text }}
                  </a-tag>
                  <a-tag :color="record.purpose === 'SIGNED_PAID_FULFILLMENT' ? 'purple' : 'blue'">
                    {{ purposeText(record) }}
                  </a-tag>
                  <a-tag color="blue">{{ typeText(record) }}</a-tag>
                </a-space>
              </template>
              <template #description>
                <span v-if="record.purpose === 'SIGNED_PAID_FULFILLMENT'">
                  客户 {{ record.recipientName }}<template v-if="record.recipientUserId">（ID {{ record.recipientUserId }}）</template>
                  · 合同 {{ record.contractReference }}
                  · 付款 {{ record.paymentReference }}
                  <template v-if="record.confirmationNote"> · {{ record.confirmationNote }}</template>
                </span>
                <span>{{ record.purpose === 'SIGNED_PAID_FULFILLMENT' ? ' · ' : '' }}尾号 {{ record.tokenHint }} · 有效至 {{ formatTime(record.expiresAt) }}</span>
                <span v-if="record.usedOrderNo"> · 订单 {{ record.usedOrderNo }}</span>
                <span v-else-if="record.reservedOrderNo"> · 订单 {{ record.reservedOrderNo }}</span>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
    </a-spin>
  </a-modal>
</template>

<style scoped lang="less">
.offer-product-heading {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf2ef;

  strong {
    min-width: 0;
    overflow: hidden;
    color: #183323;
    font-size: 16px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #7a8981;
    font-size: 12px;
  }
}

.offer-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;

  :deep(.ant-input-number),
  :deep(.ant-segmented) {
    width: 100%;
  }
}

.offer-form-full {
  grid-column: 1 / -1;
}

.direct-mode-alert {
  margin-bottom: 18px;
}

.offer-create-action {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  span {
    color: #718079;
    font-size: 12px;
  }
}

.generated-link {
  margin-top: 18px;
}

.generated-link-value {
  display: flex;
  margin-top: 10px;

  :deep(.ant-input) {
    min-width: 0;
  }
}

.offer-history {
  max-height: 280px;
  overflow-y: auto;

  :deep(.ant-list-item-meta-description) {
    overflow-wrap: anywhere;
  }
}

.offer-sku-name {
  color: #718079;
  font-size: 12px;
}

@media only screen and (max-width: 640px) {
  .offer-product-heading,
  .offer-create-action {
    align-items: stretch;
    flex-direction: column;
  }

  .offer-form {
    grid-template-columns: minmax(0, 1fr);
  }

  .offer-create-action {
    :deep(.ant-btn) {
      width: 100%;
    }
  }

  .offer-history {
    max-height: 240px;
  }
}
</style>

<style lang="less">
.shop-offer-link-modal .ant-modal {
  max-width: calc(100vw - 24px);
}
</style>
