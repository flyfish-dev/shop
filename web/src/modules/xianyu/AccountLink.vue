<script setup>
import { ref } from 'vue';
import { post } from '@/network/request.js';
import { isXianyuRuntime } from './runtime.js';
import useClientStore from '@/modules/auth/store/client.js';
import { message } from 'ant-design-vue';
const code=ref('');const busy=ref(false);const generated=ref('');const confirmation=ref(false);
const store=useClientStore();
const issue=async()=>{busy.value=true;try{const result=await post('/portal/users/xianyu/link-ticket',{credential:true});generated.value=result.code;}catch(e){message.error(e.message);}finally{busy.value=false;}};
const link=async(confirmed=false)=>{busy.value=true;try{const result=await post(`/xianyu/account/${confirmed?'link-confirm':'link'}`,{credential:true,body:{code:code.value,confirmed}});confirmation.value=!!result.requiresConfirmation;if(!confirmation.value){await store.refreshUser();message.success('账号已关联');}}catch(e){message.error(e.message);}finally{busy.value=false;}};
const copy=async()=>{await navigator.clipboard.writeText(generated.value);message.success('已复制');};
</script>
<template>
  <section class="account-link"><h2>小铺账号关联</h2>
    <template v-if="isXianyuRuntime"><a-form layout="vertical" @finish="link(false)"><a-form-item label="关联码" required><a-input v-model:value="code" autocomplete="off" /></a-form-item><a-button html-type="submit" type="primary" :loading="busy">关联账号</a-button></a-form></template>
    <template v-else><a-button :loading="busy" @click="issue">生成关联码</a-button><div v-if="generated" class="code"><a-input :value="generated" readonly /><a-button @click="copy">复制</a-button></div><p v-if="generated">关联码有效期为10分钟。</p></template>
    <a-modal :open="confirmation" title="确认账号关联" @ok="link(true)" @cancel="confirmation=false" :confirm-loading="busy"><p>确认将当前闲鱼账号关联至已登录的小铺账号？</p></a-modal>
  </section>
</template>
<style scoped>.account-link{text-align:left;max-width:640px;margin:24px auto;padding:20px}.account-link h2{font-size:20px}.code{display:flex;gap:8px;margin-top:16px}.account-link p{color:#666;font-size:13px}</style>
