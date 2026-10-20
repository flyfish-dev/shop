<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { useRouter } from '@/router/use.js';
import useClientStore from '@/modules/auth/store/client.js';
import { authorizeXianyu } from './runtime.js';
import logo from '@/assets/shop/flyfish-shop-logo.png';
const store = useClientStore();
const router = useRouter();
const loading = ref(true);
const error = ref('');
const login = async () => {
  error.value = ''; loading.value = true;
  try {
    if (!store.token) await authorizeXianyu(token => store.setToken(token));
    await store.refreshUser();
    await store.syncRouteAuth(router);
  } catch (e) { error.value = e.message; }
  finally { loading.value = false; }
};
const foreground = () => { if (!document.hidden) store.refreshUser().catch(e => message.error(e.message)); };
onMounted(() => { login(); document.addEventListener('visibilitychange', foreground); });
onBeforeUnmount(() => document.removeEventListener('visibilitychange', foreground));
watch(() => router.route.fullPath, () => store.syncRouteAuth(router).catch(e => { error.value = e.message; }));
</script>
<template>
  <a-config-provider :theme="{ token: { colorPrimary: '#338e32', borderRadius: 4 } }">
    <div class="mini-shell">
      <header><img :src="logo" alt="" /><strong>飞鱼小铺</strong></header>
      <nav><router-link href="/shop">商品</router-link><router-link href="/account/orders">我的订单</router-link><router-link href="/account/tickets">工单</router-link><router-link href="/account/channel-link">小铺账号</router-link></nav>
      <a-spin v-if="loading" class="state" />
      <a-result v-else-if="error" status="warning" :title="error"><template #extra><a-button @click="login">重新登录</a-button></template></a-result>
      <main v-else><router-view /></main>
      <div class="claim-entry"><router-link href="/account/native-claim">领取订单</router-link></div>
    </div>
  </a-config-provider>
</template>
<style>
html,body,#app{margin:0;min-height:100%;font-family:Arial,'Microsoft YaHei',sans-serif;letter-spacing:0;background:#fafafa;color:#222}
.mini-shell{max-width:1400px;margin:auto;padding:16px}.mini-shell>header{display:flex;align-items:center;gap:10px;height:48px}.mini-shell>header img{width:40px;height:40px;object-fit:contain}.mini-shell>header strong{font-size:20px}
.mini-shell>nav{display:flex;gap:24px;padding:14px 0;border-bottom:1px solid #ddd;margin-bottom:20px}.mini-shell>nav a{color:#333}.mini-shell .state{display:block;margin:60px auto}.mini-shell>main{min-width:0}@media(max-width:600px){.mini-shell{padding:12px}.mini-shell>nav{gap:18px}}
</style>
