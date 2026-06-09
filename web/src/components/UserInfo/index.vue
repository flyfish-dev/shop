<script setup>
import useClientStore from '@/modules/auth/store/client.js';
import { storeToRefs } from 'pinia';
import { computed, h, onMounted } from 'vue';
import {
  DownOutlined,
  FileTextOutlined,
  LoginOutlined,
  LogoutOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import { useRouter } from '@/router/index.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const props = defineProps({
  compact: {
    type: Boolean,
    default: false
  }
});

const store = useClientStore();
const { user, initialized } = storeToRefs(store);
const router = useRouter();
const { hasShop, loadPortalCapabilities } = usePortalCapabilities();

onMounted(async () => {
  if (!initialized.value) {
    await store.loadUser();
  }
  loadPortalCapabilities().catch(() => {});
});

const avatar = computed(() => user.value?.avatar || '');
const username = computed(() => user.value?.username || '未登录');
const showName = computed(() => !props.compact);

const login = () => {
  router.push('/login');
};

const goProfile = () => {
  router.push('/account/profile');
};

const goOrders = () => {
  router.push('/account/orders');
};

const goTickets = () => {
  router.push('/account/tickets', { create: '1' });
};

const menuItems = computed(() => [
  {
    key: 'tickets',
    label: '提交工单',
    capability: 'shop',
    icon: () => h(FileTextOutlined)
  },
  {
    key: 'orders',
    label: '我的订单',
    capability: 'shop',
    icon: () => h(ShoppingCartOutlined)
  },
  {
    key: 'profile',
    label: '个人信息维护',
    icon: () => h(SettingOutlined)
  },
  {
    type: 'divider'
  },
  {
    key: 'logout',
    label: '退出登录',
    danger: true,
    icon: () => h(LogoutOutlined)
  }
].filter(item => !item.capability || (item.capability === 'shop' && hasShop.value)));

const handleMenuClick = ({ key }) => {
  if (key === 'tickets') {
    goTickets();
    return;
  }
  if (key === 'orders') {
    goOrders();
    return;
  }
  if (key === 'profile') {
    goProfile();
    return;
  }
  if (key === 'logout') {
    store.logout(router);
  }
};
</script>

<template>
  <div class='user-container' :class='{ compact }'>
    <a-dropdown v-if='user?.id' trigger='click' placement='bottomRight'>
      <button class='user-trigger' type='button'>
        <a-avatar v-if='avatar' class='user-avatar' :src='avatar' :size='compact ? 38 : 42' />
        <a-avatar v-else class='user-avatar fallback-avatar' :size='compact ? 38 : 42'>
          <user-outlined />
        </a-avatar>
        <span v-if='showName' class='user-name'>{{ username }}</span>
        <down-outlined class='arrow' />
      </button>
      <template #overlay>
        <a-menu :items='menuItems' @click='handleMenuClick' />
      </template>
    </a-dropdown>
    <a-button
      v-else
      type='link'
      class='login-button'
      :icon='h(LoginOutlined)'
      @click='login'
    >
      去登录
    </a-button>
  </div>
</template>

<style scoped lang='less'>
.user-container {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  font-weight: 600;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  max-width: 210px;
  height: 46px;
  padding: 2px 9px 2px 2px;
  border: 0;
  border-radius: 999px;
  color: #26384f;
  background: transparent;
  box-shadow: none;
  cursor: pointer;
  transition: background .18s ease, color .18s ease;

  &:hover {
    color: #1677ff;
    background: rgba(22, 119, 255, .07);
  }
}

.user-avatar {
  flex: none;
  background: #c9cdd4;
}

.fallback-avatar {
  :deep(.anticon) {
    font-size: 21px;
  }
}

.user-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  letter-spacing: 0;
}

.arrow {
  flex: none;
  color: #6b7d90;
  font-size: 12px;
}

.login-button {
  display: inline-flex;
  align-items: center;
  height: 36px;
  padding: 0 4px;
  color: #1677ff;
  font-weight: 600;
}

.compact {
  .user-trigger {
    width: 42px;
    height: 42px;
    justify-content: center;
    padding: 0;
  }

  .arrow {
    display: none;
  }
}

@media only screen and (max-width: 640px) {
  .user-trigger {
    width: 42px;
    height: 42px;
    justify-content: center;
    padding: 0;
  }

  .user-name,
  .arrow {
    display: none;
  }
}
</style>
