<template>
  <section class="market-strip">
    <div class="market-copy">
      <span>Flyfish Market</span>
      <strong>{{ name }}</strong>
      <small>源码 · 部署 · 授权</small>
    </div>
    <a-button class="market-button" type="primary" size="large" @click="router.push(path)">
      <shop-outlined />
      <span>进入小铺</span>
      <arrow-right-outlined />
    </a-button>
  </section>
</template>

<script setup>
import { ArrowRightOutlined, ShopOutlined } from '@ant-design/icons-vue';
import { useRouter } from '@/router/use';

defineProps({
  name: {
    type: String,
    default: 'Flyfish Market'
  },
  path: {
    type: String,
    default: '/'
  }
});

const router = useRouter();
</script>

<style scoped lang="less">
.market-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  width: min(960px, 100%);
  max-width: 100%;
  box-sizing: border-box;
  margin: 40px auto 24px;
  padding: 18px 22px;
  overflow: hidden;
  border: 1px solid rgba(31, 124, 85, .14);
  border-radius: 8px;
  background:
    radial-gradient(circle at 8% 12%, rgba(76, 184, 134, .18), transparent 32%),
    linear-gradient(135deg, #f7fffb 0%, #f7fbff 48%, #fff 100%);
  box-shadow: 0 16px 42px rgba(33, 68, 104, .08);
}

.market-copy {
  display: grid;
  min-width: 0;
  gap: 3px;

  span {
    color: #1677ff;
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0;
    text-transform: uppercase;
  }

  strong {
    color: #183529;
    font-size: 24px;
    line-height: 1.25;
  }

  small {
    color: #64758a;
    font-size: 14px;
    line-height: 1.4;
  }
}

.market-button {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 18px;
  border: 0;
  border-radius: 8px;
  background: linear-gradient(135deg, #15915b, #1677ff);
  box-shadow: 0 14px 28px rgba(21, 145, 91, .2);
  font-weight: 700;

  &:hover,
  &:focus {
    background: linear-gradient(135deg, #128452, #116de8);
  }
}

@media only screen and (max-width: 820px) {
  .market-strip {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
    margin: 24px auto 20px;
    padding: 16px;
  }
}

@media only screen and (max-width: 480px) {
  .market-copy {
    strong {
      font-size: 22px;
    }
  }

  .market-button {
    width: 100%;
    justify-content: center;
  }
}
</style>
