<script setup>
import { ref } from 'vue';
import { post } from '@/network/request.js';
import { message } from 'ant-design-vue';
import { useRouter } from '@/router/use.js';
const orderId=ref('');const loading=ref(false);const router=useRouter();
const claim=async()=>{loading.value=true;try{await post('/xianyu/native/claim',{body:{orderId:orderId.value},credential:true});router.push('/account/orders');}catch(e){message.error(e.message);}finally{loading.value=false;}};
</script>
<template><section class="claim"><h2>领取订单</h2><a-form layout="vertical" @finish="claim"><a-form-item label="闲鱼订单号" required><a-input v-model:value="orderId" inputmode="numeric" /></a-form-item><a-button html-type="submit" type="primary" :loading="loading">领取</a-button></a-form></section></template>
<style scoped>.claim{max-width:640px;margin:24px auto;text-align:left}.claim h2{font-size:20px}</style>
