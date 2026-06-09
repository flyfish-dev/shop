<script setup>
import { computed } from 'vue';
import { BellOutlined, FileTextOutlined, RightOutlined } from '@ant-design/icons-vue';
import { useRouter } from '@/router/use.js';

const props = defineProps({
  variant: {
    type: String,
    default: 'button'
  }
});

const router = useRouter();

const isBanner = computed(() => props.variant === 'banner');
const isInline = computed(() => props.variant === 'inline');

const goTicket = () => {
  router.push('/account/tickets', { create: '1' });
};
</script>

<template>
  <button
    v-if='!isBanner && !isInline'
    type='button'
    class='support-entry support-entry-button'
    aria-label='提交工单'
    @click='goTicket'
  >
    <file-text-outlined />
    <span class='support-entry-label'>提交工单</span>
  </button>

  <section v-else-if='isBanner' class='support-entry support-entry-banner'>
    <span class='support-entry-mark'>
      <bell-outlined />
    </span>
    <div class='support-entry-copy'>
      <strong>遇到支付、开通或使用问题，都可以提交工单</strong>
      <span>工单进度会通过邮件和公众号消息同步，方便随时追踪处理结果。</span>
    </div>
    <a-button type='primary' class='support-entry-action' @click='goTicket'>
      提交工单
      <right-outlined />
    </a-button>
  </section>

  <section v-else class='support-entry support-entry-inline'>
    <span class='support-entry-inline-icon'>
      <file-text-outlined />
    </span>
    <span class='support-entry-inline-copy'>
      <strong>售后与开通问题</strong>
      <small>提交工单后会收到处理通知</small>
    </span>
    <button type='button' class='support-entry-inline-action' @click='goTicket'>提交</button>
  </section>
</template>

<style scoped lang='less'>
.support-entry {
  box-sizing: border-box;
}

.support-entry-button {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid rgba(22, 119, 255, .22);
  border-radius: 8px;
  background: #1677ff;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 10px 24px rgba(22, 119, 255, .18);
  transition: transform .18s ease, box-shadow .18s ease, background .18s ease;

  &:hover {
    transform: translateY(-1px);
    background: #1267d8;
    box-shadow: 0 14px 30px rgba(22, 119, 255, .24);
  }
}

.support-entry-banner {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  margin: 0 0 16px;
  padding: 12px 14px;
  border: 1px solid rgba(51, 162, 4, .14);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(244, 255, 246, .96), rgba(246, 251, 255, .96)),
    #fff;
  box-shadow: 0 10px 24px rgba(42, 111, 81, .07);
}

.support-entry-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #fff;
  color: #33a204;
  font-size: 20px;
  box-shadow: 0 8px 20px rgba(51, 162, 4, .1);
}

.support-entry-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  text-align: left;

  strong {
    color: #1d2f24;
    font-size: 16px;
    line-height: 1.35;
  }

  span {
    color: #5f6f66;
    font-size: 13px;
    line-height: 1.55;
  }
}

.support-entry-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  border-radius: 8px;
  font-weight: 700;
}

.support-entry-inline {
  display: inline-grid;
  grid-template-columns: 30px minmax(0, auto) auto;
  align-items: center;
  width: auto;
  max-width: 100%;
  margin: 12px 0 0;
  padding: 8px 9px;
  gap: 9px;
  border: 1px solid rgba(51, 162, 4, .13);
  border-radius: 8px;
  background: #f8fcf9;
  color: #40534a;
  line-height: 1.35;
  text-align: left;

  .support-entry-inline-copy {
    display: grid;
    gap: 2px;
    min-width: 0;

    strong {
      overflow: hidden;
      color: #203626;
      font-size: 13px;
      font-weight: 700;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    small {
      overflow: hidden;
      color: #6e7f74;
      font-size: 12px;
      line-height: 1.3;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.support-entry-inline-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #fff;
  color: #33a204;
  font-size: 16px;
  box-shadow: inset 0 0 0 1px rgba(51, 162, 4, .12);
}

.support-entry-inline-action {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(51, 162, 4, .22);
  border-radius: 8px;
  background: #fff;
  color: #228b22;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  transition: background .18s ease, border-color .18s ease, color .18s ease;

  &:hover {
    border-color: rgba(51, 162, 4, .42);
    background: #f0faef;
    color: #1f7c1f;
  }
}

@media only screen and (max-width: 760px) {
  .support-entry-button {
    width: 38px;
    height: 38px;
    padding: 0;
    border-radius: 8px;
    font-size: 17px;

    .support-entry-label {
      display: none;
    }
  }

  .support-entry-banner {
    grid-template-columns: 38px minmax(0, 1fr);
    gap: 10px;
    padding: 12px;
  }

  .support-entry-mark {
    width: 38px;
    height: 38px;
    font-size: 20px;
  }

  .support-entry-copy {
    strong {
      font-size: 15px;
    }

    span {
      font-size: 12px;
    }
  }

  .support-entry-action {
    grid-column: 1 / -1;
    width: 100%;
  }

  .support-entry-inline {
    grid-template-columns: 30px minmax(0, 1fr) auto;
    width: 100%;
    align-items: flex-start;
  }
}
</style>
