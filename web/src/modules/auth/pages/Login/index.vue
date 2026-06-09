<script setup>
// 是否显示注册
import { computed, onMounted, watch } from 'vue';
import LoginForm from './components/LoginForm'
import useClientStore from '@/modules/auth/store/client.js';
import { storeToRefs } from 'pinia';
import { useRouter } from '@/router/use.js';
import { usePortalCapabilities } from '@/modules/portal/usePortalCapabilities.js';

const store = useClientStore();
const router = useRouter();
const { initialized, isAuthenticated } = storeToRefs(store);
const { portalTitle, loadPortalCapabilities } = usePortalCapabilities();
const showLogin = computed(() => initialized.value && !isAuthenticated.value);
const loginTitle = computed(() => portalTitle.value);

const redirectIfAuthenticated = () => {
  if (isAuthenticated.value) {
    router.replace('/');
  }
};

onMounted(async () => {
  loadPortalCapabilities().catch(() => {});
  await store.loadUser();
  redirectIfAuthenticated();
});

watch(isAuthenticated, redirectIfAuthenticated);
</script>

<template>
  <div class='container'>
    <div class='top'>
      <div class='header'>
        <a>
          <img src='@/assets/logo.svg' class='logo' />
          <span class='title'>{{ loginTitle }}</span>
        </a>
      </div>
    </div>
    <div class='main'>
      <login-form v-if='showLogin' />
      <a-spin v-else />
    </div>
  </div>
</template>

<style scoped lang='less'>
.container {
  position: relative;
  width: 100%;
  min-height: 100vh;
  padding: 110px 0 144px;
  background-image: url("@/assets/background.svg");
  background-repeat: no-repeat;
  background-position: center 110px;
  background-size: 100%;

  .top {
    margin-bottom: 40px;
    text-align: center;
    .header {
      height: 44px;
      line-height: 44px;
      .logo {
        height: 44px;
        margin-right: 16px;
        vertical-align: top;
        border-radius: 22px;
      }
      .title {
        position: relative;
        top: 2px;
        color: rgba(0,0,0,.85);
        font-weight: 600;
        font-size: 33px;
        font-family: Avenir,Helvetica Neue,Arial,Helvetica,sans-serif;
      }
    }
  }
  .main {
    width: 388px;
    margin: 0 auto;
  }
}
</style>
