<script setup>
import {
  AlipayCircleOutlined,
  CheckCircleFilled,
  CreditCardOutlined,
  WechatOutlined
} from '@ant-design/icons-vue';

defineProps({
  modelValue: {
    type: String,
    required: true
  },
  options: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:modelValue']);

const icons = {
  'h5zhifu-wechat': WechatOutlined,
  'h5zhifu-alipay': AlipayCircleOutlined,
  stripe: CreditCardOutlined
};
</script>

<template>
  <div class="shop-payment-method-selector">
    <a-radio-group
      :value="modelValue"
      class="payment-method-group"
      :disabled="disabled"
      @change="event => emit('update:modelValue', event.target.value)"
    >
      <a-radio-button
        v-for="option in options"
        :key="option.value"
        :value="option.value"
        :class="['payment-method-option', `method-${option.value}`, { 'has-badge': option.badge }]"
      >
        <span class="method-icon-wrap">
          <component :is="icons[option.value]" class="method-icon" />
        </span>
        <span class="method-copy">
          <strong>{{ option.label }}</strong>
          <small>{{ option.description }}</small>
        </span>
        <span v-if="option.badge" class="method-badge">{{ option.badge }}</span>
        <check-circle-filled class="selected-indicator" />
      </a-radio-button>
    </a-radio-group>
  </div>
</template>

<style scoped lang="less">
.shop-payment-method-selector {
  width: 100%;
}

.payment-method-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: min(100%, 520px);
}

:deep(.payment-method-option) {
  position: relative;
  display: flex;
  height: auto;
  min-height: 58px;
  min-width: 0;
  align-items: center;
  padding: 8px 34px 8px 10px;
  border: 1px solid #d9dfe8;
  border-radius: 6px;
  background: #fff;
  color: #1f2937;
  line-height: 1.2;
  white-space: normal;
  transition: border-color .18s ease, background-color .18s ease, box-shadow .18s ease;

  &::before {
    display: none;
  }

  > span:not(.ant-radio-button) {
    display: flex;
    width: 100%;
    min-width: 0;
    align-items: center;
    gap: 9px;
  }

  &:not(.ant-radio-button-wrapper-disabled):hover {
    border-color: #91caff;
    background: #fbfdff;
  }

  &:focus,
  &:focus-within {
    outline: 2px solid rgba(22, 119, 255, .32);
    outline-offset: 2px;
  }

  &.ant-radio-button-wrapper-checked {
    border-color: #1677ff !important;
    background: #f6f9ff !important;
    color: #1f2937 !important;
    box-shadow: 0 0 0 1px rgba(22, 119, 255, .12);

    .selected-indicator {
      opacity: 1;
      transform: scale(1);
    }
  }

  &.ant-radio-button-wrapper-disabled {
    background: #f7f8fa;
    opacity: .72;
  }

  .method-icon-wrap {
    display: inline-flex;
    width: 32px;
    height: 32px;
    flex: 0 0 32px;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    background: #f2f6fb;
  }

  .method-icon {
    flex: 0 0 auto;
    color: #1677ff;
    font-size: 19px;
  }

  .method-copy {
    display: grid;
    min-width: 0;
    gap: 2px;
    overflow: hidden;
  }

  strong {
    color: #172033;
    overflow: hidden;
    font-size: 14px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    overflow: hidden;
    color: #667085;
    font-size: 12px;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .method-badge {
    position: absolute;
    top: 6px;
    right: 7px;
    padding: 1px 5px;
    border: 1px solid #b7ebc6;
    border-radius: 999px;
    background: #f2fbf5;
    color: #24824b;
    font-size: 10px;
    line-height: 16px;
  }

  .selected-indicator {
    position: absolute;
    right: 9px;
    bottom: 7px;
    color: #1677ff;
    font-size: 14px;
    opacity: 0;
    transform: scale(.8);
    transition: opacity .18s ease, transform .18s ease;
  }

  &.method-h5zhifu-wechat {
    .method-icon-wrap {
      background: #effaf3;
    }

    .method-icon {
      color: #09a94f;
    }
  }

  &.method-h5zhifu-alipay {
    .method-icon-wrap {
      background: #eef6ff;
    }
  }

  &.method-stripe {
    .method-icon-wrap {
      background: #f2f1ff;
    }

    .method-icon {
      color: #635bff;
    }
  }
}

@media only screen and (max-width: 520px) {
  .payment-method-group {
    gap: 6px;
  }

  :deep(.payment-method-option) {
    min-height: 52px;
    padding: 7px 26px 7px 8px;

    > span:not(.ant-radio-button) {
      gap: 7px;
    }

    .method-icon-wrap {
      width: 30px;
      height: 30px;
      flex-basis: 30px;
    }

    .method-icon {
      font-size: 18px;
    }

    strong {
      font-size: 13px;
    }

    small,
    .method-badge {
      display: none;
    }

    .selected-indicator {
      right: 8px;
      bottom: 50%;
      transform: translateY(50%) scale(.8);
    }

    &.ant-radio-button-wrapper-checked .selected-indicator {
      transform: translateY(50%) scale(1);
    }
  }
}
</style>
