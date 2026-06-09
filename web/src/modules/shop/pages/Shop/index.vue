<script setup>
import FooterBar from '@/layouts/FooterBar.vue';
import UserInfo from '@/components/UserInfo';
import ShopHomeLink from './components/ShopHomeLink.vue';
import ShopSupportEntry from './components/ShopSupportEntry.vue';
import CustomerServiceWidget from '@/modules/shop/components/CustomerService/CustomerServiceWidget.vue';
import { RightOutlined, SettingOutlined, ShopFilled } from '@ant-design/icons-vue';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import { computed, onMounted, ref } from 'vue';
import { isShopMaintainer } from '@/modules/shop/authority.js';
import RouterLink from '@/components/RouterLink/index.vue';
import { useRouter } from '@/router/use.js';

const store = useClientStore();
const { user } = storeToRefs(store);
const router = useRouter();
const userLoading = ref(true);

const canManage = computed(() => isShopMaintainer(user.value));
const isManageRoute = computed(() => router.currentRoute.value.startsWith('/shop/manage'));
const showManageGuard = computed(() => isManageRoute.value && !canManage.value);

onMounted(async () => {
  await store.loadUser();
  userLoading.value = false;
});
</script>

<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#33a204',
      },
    }"
  >
    <div class='shop-page'>
      <div class='shop-container' :class="{ 'shop-container-manage': isManageRoute }">
        <div class='shop-header'>
          <router-link href='/shop/item-list' class='shop-brand' aria-label='飞鱼小铺首页'>
            <span class='brand-mark'>
              <shop-filled />
            </span>
            <span class='brand-copy'>
              <span class='brand-title'>飞鱼小铺</span>
              <span class='brand-subtitle'>Flyfish Market</span>
            </span>
          </router-link>
          <div class="header-right">
            <shop-home-link />
            <shop-support-entry />
            <!-- 管理入口 -->
            <a-dropdown v-if="canManage">
              <a-button class='manage-button'>
                <setting-outlined />
                <span class='manage-label'>管理</span>
                <right-outlined class='manage-arrow' />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="shop">
                    <router-link href="shop/manage/shops">店铺管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="groups">
                    <router-link href="shop/manage/groups">分组管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="items">
                    <router-link href="shop/manage/items">商品管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="repositories">
                    <router-link href="shop/manage/repositories">仓库管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="orders">
                    <router-link href="shop/manage/orders">订单管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="users">
                    <router-link href="shop/manage/users">用户管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="coupons">
                    <router-link href="shop/manage/coupons">优惠券管理</router-link>
                  </a-menu-item>
                  <a-menu-item key="tickets">
                    <router-link href="shop/manage/tickets">工单管理</router-link>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <user-info />
          </div>
        </div>
        <div v-if="userLoading && isManageRoute" class="shop-auth-state">
          <a-spin tip="正在校验维护权限" />
        </div>
        <a-result
          v-else-if="showManageGuard"
          class="shop-auth-state"
          status="403"
          title="无权访问"
        >
          <template #extra>
            <router-link href="/shop/item-list">
              <a-button type="primary">返回小铺</a-button>
            </router-link>
          </template>
        </a-result>
        <keep-alive>
          <router-view v-if="!showManageGuard && !(userLoading && isManageRoute)" />
        </keep-alive>
      </div>
      <footer-bar />
      <customer-service-widget />
    </div>
  </a-config-provider>
</template>

<style scoped lang='less'>
.shop-page {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #f5fbf8 0, #fafafa 260px),
    #fafafa;

  .shop-container {
    width: 100%;
    height: 100%;
    box-sizing: border-box;
    padding: 32px 25px 40px;
    max-width: 1200px;
    margin: 0 auto;

    &.shop-container-manage {
      max-width: 1480px;
      padding-inline: clamp(12px, 2vw, 28px);
    }

    .shop-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 24px;
      min-height: 74px;
      padding: 0 20px 24px;
      border-bottom: 1px solid rgba(43, 137, 95, .14);

      .shop-brand {
        display: inline-flex;
        align-items: center;
        gap: 14px;
        min-width: 0;
        color: inherit;
        text-decoration: none;
      }

      .brand-mark {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 58px;
        height: 58px;
        flex: none;
        border: 1px solid rgba(51, 162, 4, .18);
        border-radius: 8px;
        background:
          linear-gradient(135deg, rgba(255, 255, 255, .92), rgba(239, 255, 245, .86)),
          linear-gradient(135deg, #33a204, #1677ff);
        box-shadow: 0 12px 30px rgba(38, 125, 74, .12);
        color: #219653;
        font-size: 28px;

        &::after {
          content: '';
          position: absolute;
          right: 10px;
          bottom: 10px;
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #1677ff;
          box-shadow: 0 0 0 4px rgba(22, 119, 255, .1);
        }
      }

      .brand-copy {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        min-width: 0;
      }

      .brand-title {
        color: #1d2f24;
        font-size: 30px;
        font-weight: 700;
        line-height: 1.15;
        letter-spacing: 0;
      }

      .brand-subtitle {
        margin-top: 6px;
        color: #6a7b70;
        font-size: 13px;
        font-weight: 600;
        line-height: 1.2;
        letter-spacing: 0;
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 14px;
        flex: none;
      }

      .manage-button {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        height: 34px;
        border-color: rgba(51, 162, 4, .24);
        border-radius: 8px;
        color: #267d4a;
        background: rgba(255, 255, 255, .72);
        box-shadow: 0 6px 18px rgba(38, 125, 74, .08);

        &:hover {
          border-color: #33a204;
          color: #1f7a36;
          background: #fff;
        }
      }
    }

    .shop-auth-state {
      min-height: 420px;
      padding-top: 80px;
      text-align: center;
    }
  }
}

@media only screen and (max-width: 640px) {
  .shop-page {
    .shop-container {
      padding: 18px 12px 30px;

      .shop-header {
        align-items: center;
        min-height: 58px;
        padding: 0 0 16px;
        gap: 10px;

        .brand-mark {
          width: 44px;
          height: 44px;
          font-size: 22px;

          &::after {
            right: 8px;
            bottom: 8px;
            width: 7px;
            height: 7px;
            box-shadow: 0 0 0 3px rgba(22, 119, 255, .1);
          }
        }

        .brand-title {
          max-width: 100%;
          overflow: hidden;
          font-size: 22px;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .brand-subtitle {
          max-width: 100%;
          overflow: hidden;
          font-size: 12px;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .header-right {
          margin-left: auto;
          gap: 8px;
          flex-shrink: 0;
        }

        .manage-button {
          width: 34px;
          padding: 0;
          justify-content: center;

          .manage-label,
          .manage-arrow {
            display: none;
          }
        }
      }
    }
  }
}

@media only screen and (max-width: 360px) {
  .shop-page {
    .shop-container {
      padding-inline: 10px;

      .shop-header {
        .shop-brand {
          gap: 10px;
        }

        .brand-mark {
          width: 40px;
          height: 40px;
          font-size: 20px;
        }

        .brand-title {
          font-size: 20px;
        }

        .header-right {
          gap: 6px;
        }
      }
    }
  }
}
</style>
