<script setup>
import FooterBar from '@/layouts/FooterBar.vue';
import UserInfo from '@/components/UserInfo';
import ShopHomeLink from './components/ShopHomeLink.vue';
import ShopSupportEntry from './components/ShopSupportEntry.vue';
import CustomerServiceWidget from '@/modules/shop/components/CustomerService/CustomerServiceWidget.vue';
import shopLogo from '@/assets/shop/flyfish-shop-logo.png';
import { RightOutlined, SettingOutlined } from '@ant-design/icons-vue';
import { storeToRefs } from 'pinia';
import useClientStore from '@/modules/auth/store/client.js';
import { computed, onMounted, ref } from 'vue';
import { isShopMaintainer } from '@/modules/shop/authority.js';
import RouterLink from '@/components/RouterLink/index.vue';
import { useRouter } from '@/router/use.js';
import { useI18n } from 'vue-i18n';

const store = useClientStore();
const { user } = storeToRefs(store);
const router = useRouter();
const { t } = useI18n();
const userLoading = ref(true);

const canManage = computed(() => isShopMaintainer(user.value));
const isManageRoute = computed(() => router.currentRoute.value.startsWith('/shop/manage'));
const isCatalogRoute = computed(() => router.currentRoute.value.replace(/\/$/, '') === '/shop/item-list');
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
      <div
        class='shop-container'
        :class="{
          'shop-container-catalog': isCatalogRoute,
          'shop-container-manage': isManageRoute
        }"
      >
        <div class='shop-header'>
          <router-link href='/shop' class='shop-brand' :aria-label='t("shop.shell.brandHome")'>
            <span class='brand-mark'>
              <img class='brand-logo' :src='shopLogo' alt='' aria-hidden='true' />
            </span>
            <span class='brand-copy'>
              <span class='brand-title'>{{ t('shop.shell.brandName') }}</span>
              <span class='brand-subtitle'>{{ t('shop.shell.brandSubtitle') }}</span>
            </span>
          </router-link>
          <div class="header-right">
            <shop-home-link />
            <shop-support-entry />
            <a-dropdown v-if="canManage">
              <a-button class='manage-button'>
                <setting-outlined />
                <span class='manage-label'>{{ t('shop.shell.manage') }}</span>
                <right-outlined class='manage-arrow' />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="workbench">
                    <router-link href="/shop/manage/workbench">{{ t('shop.shell.workbench') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="shop">
                    <router-link href="/shop/manage/shops">{{ t('shop.shell.shops') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="groups">
                    <router-link href="/shop/manage/groups">{{ t('shop.shell.groups') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="items">
                    <router-link href="/shop/manage/items">{{ t('shop.shell.items') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="repositories">
                    <router-link href="/shop/manage/repositories">{{ t('shop.shell.repositories') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="orders">
                    <router-link href="/shop/manage/orders">{{ t('shop.shell.orders') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="users">
                    <router-link href="/shop/manage/users">{{ t('shop.shell.users') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="coupons">
                    <router-link href="/shop/manage/coupons">{{ t('shop.shell.coupons') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="contracts">
                    <router-link href="/shop/manage/contracts">{{ t('shop.shell.contracts') }}</router-link>
                  </a-menu-item>
                  <a-menu-item key="tickets">
                    <router-link href="/shop/manage/tickets">{{ t('shop.shell.tickets') }}</router-link>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <user-info />
          </div>
        </div>
        <div v-if="userLoading && isManageRoute" class="shop-auth-state">
          <a-spin :tip="t('shop.shell.checkingAccess')" />
        </div>
        <a-result
          v-else-if="showManageGuard"
          class="shop-auth-state"
          status="403"
          :title="t('shop.shell.accessDenied')"
        >
          <template #extra>
            <router-link href="/shop/item-list">
              <a-button type="primary">{{ t('shop.shell.returnStore') }}</a-button>
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

    &.shop-container-catalog {
      max-width: 1508px;
    }

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
        flex: 1 1 auto;
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
        background: rgba(255, 255, 255, .8);
        box-shadow: 0 12px 30px rgba(38, 125, 74, .12);

        .brand-logo {
          display: block;
          width: 100%;
          height: 100%;
          object-fit: contain;
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
          gap: 6px;
          max-width: max-content;
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
        gap: 8px;

        .shop-brand {
          gap: 8px;
        }

        .brand-mark {
          width: 40px;
          height: 40px;
        }

        .brand-title {
          font-size: 20px;
        }

        .brand-subtitle {
          display: none;
        }

        .header-right {
          gap: 5px;
        }
      }
    }
  }
}
</style>
