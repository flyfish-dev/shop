<template>
  <div class="home-wrapper">
    <a-spin :spinning="loading">
      <market-entry
        v-if="hasShop"
        :name="shopEntryName"
        :path="shopEntryPath"
      />

      <div class="module-wrapper">
        <a-row :gutter="[20, 20]">
          <a-col v-for="button in moduleCards" :key="button.name" :lg="12" :xs="24">
            <a-card
              hoverable
              class="module-card"
              :class="{ disabled: button.status === 'later' }"
              @click="goModule(button)"
            >
              <a-card-meta :title="button.name" :description="moduleDesc(button)">
                <template #avatar>
                  <img alt="module" class="module-icon" :src="button.icon"/>
                </template>
              </a-card-meta>
              <a-tag v-if="button.status === 'later'" class="module-status">二期开放</a-tag>
            </a-card>
          </a-col>
        </a-row>
      </div>

      <lowcode-workbench v-if="hasLowcode" />
    </a-spin>
  </div>
</template>

<script>
import { computed, defineAsyncComponent, onMounted, ref } from 'vue';
import { useRouter } from '@/router/use';
import { message } from 'ant-design-vue';
import useClientStore from '@/modules/auth/store/client.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const shopIcon = new URL('@/assets/logo.png', import.meta.url);
const MarketEntry = defineAsyncComponent(() => import('@/modules/shop/components/MarketEntry.vue'));
const LowcodeWorkbench = defineAsyncComponent(() => import('@/modules/lowcode/components/LowcodeWorkbench.vue'));

export default {
  name: 'IndexHome',
  components: {
    MarketEntry,
    LowcodeWorkbench
  },
  setup() {
    const router = useRouter();
    const store = useClientStore();
    const loading = ref(false);
    const lowcodeModules = ref([]);
    const { hasLowcode, hasShop, shopEntryName, shopEntryPath, loadPortalCapabilities } = usePortalCapabilities();

    const loadLowcodeModules = async () => {
      if (!hasLowcode.value || lowcodeModules.value.length > 0) {
        return;
      }
      const { lowcodeNavItems } = await import('@/modules/lowcode/nav.js');
      lowcodeModules.value = lowcodeNavItems;
    };
    const moduleCards = computed(() => [
      ...(hasLowcode.value ? lowcodeModules.value.map(item => ({
        ...item,
        path: `/${item.code}`
      })) : []),
      ...(hasShop.value ? [{
        name: shopEntryName.value,
        code: 'shop',
        desc: '商品与服务',
        icon: shopIcon,
        path: shopEntryPath.value
      }] : [])
    ]);

    const loadHome = async () => {
      loading.value = true;
      try {
        await loadPortalCapabilities();
        if (hasLowcode.value) {
          await loadLowcodeModules();
        }
      } catch (e) {
        message.error(e.message || '加载首页失败');
      } finally {
        loading.value = false;
      }
    };

    onMounted(() => {
      const target = store.consumeRedirect('');
      if (target
        && target.startsWith('/')
        && target !== '/'
        && target !== '/login'
        && !target.startsWith('/oauth/')
        && target !== router.currentRoute.value) {
        router.replace(target);
        return;
      }
      loadHome();
    });

    return {
      router,
      loading,
      moduleCards,
      hasLowcode,
      hasShop,
      shopEntryName,
      shopEntryPath,
      moduleDesc: button => button.desc,
      goModule: button => {
        if (button.status === 'later') {
          message.info('二期开放');
          return;
        }
        router.push(button.path || button.code);
      }
    };
  }
}
</script>

<style scoped lang="less">
.home-wrapper {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 0 16px;
  overflow-x: clip;
}

.module-wrapper {
  width: min(800px, 100%);
  max-width: 100%;
  box-sizing: border-box;
  margin: 0 auto 24px;

  :deep(.ant-card-meta-detail) {
    min-width: 0;
    margin-top: 20px;
  }
}

.module-card {
  position: relative;
  min-width: 0;
  min-height: 145px;
  transition: transform .25s ease, border-color .25s ease, opacity .25s ease;

  &:hover {
    transform: translateY(-2px);
  }

  &.disabled {
    opacity: .68;
    cursor: not-allowed;

    :deep(.ant-card-meta-description) {
      color: #8c8c8c;
    }
  }
}

.module-status {
  position: absolute;
  right: 16px;
  bottom: 14px;
  margin: 0;
}

.module-icon {
  width: 100px;
  max-width: 100%;
}

@media only screen and (max-width: 820px) {
  .home-wrapper {
    padding: 0 12px;
  }

  .module-wrapper {
    margin: 0 auto 20px;
  }

  .module-card {
    min-height: 124px;
  }

  .module-icon {
    width: 72px;
  }
}

@media only screen and (max-width: 480px) {
  .home-wrapper {
    padding: 0 10px;
  }

  .module-wrapper {
    :deep(.ant-row) {
      margin-left: -6px !important;
      margin-right: -6px !important;
    }

    :deep(.ant-col) {
      padding-left: 6px !important;
      padding-right: 6px !important;
    }
  }

}
</style>
