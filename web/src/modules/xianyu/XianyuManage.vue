<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import { ReloadOutlined, PlusOutlined, EditOutlined, UploadOutlined } from '@ant-design/icons-vue';
import { useXianyuManagement } from './useXianyuManagement.js';
import { getShopItems, getShopItemDetail } from '@/modules/shop/pages/Shop/apis/api.js';
const { status, records, loading, error, busy, load, execute } = useXianyuManagement();
const tab = ref('listings');
const dialog = ref('');
const saving = ref(false);
const localItems = ref([]);
const draft = reactive({});
const selection = ref([]);
const diffTargets = ref([]);
const deliveryTasks = ref([]);
const selectedListings = computed(() => records.listings.filter(row=>selection.value.includes(row.key)));
const configured = computed(() => status.value?.configured === true);
const exceptionCounts = computed(() => ({ unknown:status.value?.jobCounts?.UNKNOWN || 0,failed:status.value?.jobCounts?.FAILED || 0,
  expired:records.sellers.filter(s=>s.expiresAt<=Date.now()/1000).length,refund:records.orders.filter(o=>['PENDING','PROCESSING'].includes(o.refundState)).length }));
const sellerOptions = computed(() => records.sellers.map(s => ({ value: s.key, label: `${s.key} (${s.application})` })));
const operationOptions = computed(() => (status.value?.operations || []).map(op => ({ value: op.operation, label: op.method })));
const phase = value => ({ DRAFT:'草稿',SYNCED:'已同步',DOWN:'已下架',READY:'待执行',RUNNING:'执行中',UNKNOWN:'结果待核查',FAILED:'失败',SUCCESS:'完成',WAITING:'等待',WAITING_BUYER:'待买家关联',PREPARING:'准备中',CREATED:'已创建',NONE:'无退款',PENDING:'处理中',PROCESSING:'处理中',PARTIAL:'部分退款',REFUNDED:'已退款',CLOSED:'已关闭',REJECTED:'已拒绝',CONFIRMED:'平台已确认',SUBMITTED:'已提交' }[value] || value);
const platform = value => ({0:'未知',1:'待付款',2:'已付款',3:'已发货',4:'交易成功',5:'已退款',6:'已关闭'}[value]);
const cash = cents => `¥${(Number(cents || 0)/100).toFixed(2)}`;
const comma = text => String(text || '').split(',').map(v=>v.trim()).filter(Boolean);
const localDate = epoch => {
  const date=new Date(epoch*1000);return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);
};
const available = (row,operation) => {
  const seller=records.sellers.find(s=>s.key===row.seller);
  const cap=records.capabilities.find(c=>c.seller===row.seller && c.operation===operation && c.application===seller?.application);
  return !!(cap?.granted && cap?.verified && cap.expiresAt>Date.now()/1000 && seller?.enabled && seller.expiresAt>Date.now()/1000
    && (!operation.startsWith('ITEM_') || cap.categoryIds?.includes(String(row.payload.channel_cat_id))));
};
const syncDiff = computed(()=>diffTargets.value.flatMap(row=>['title','desc','reserve_price','quantity','images','item_sku_list','pv_list'].map(field=>({
  id:`${row.key}:${field}`,title:row.payload.title,field,previous:JSON.stringify(row.remoteContent?.[field] ?? null),next:JSON.stringify(row.payload[field] ?? null)
})).filter(change=>change.previous!==change.next)));
const previewSync = rows => { diffTargets.value=rows; };
const confirmSync = async () => {
  const outcomes=await Promise.allSettled(diffTargets.value.map(row=>execute('listing-command',{key:row.key,operation:row.linked?'ITEM_EDIT':'ITEM_PUBLISH'},row.key)));
  const failed=outcomes.filter(r=>r.status==='rejected').length;
  diffTargets.value=[];selection.value=[];
  if(failed) message.warning(`${failed} 个商品同步未受理，请核查权限和版本`);
};
const chooseItem = async (id, preserve=false) => {
  const item=await getShopItemDetail(id);
  if (preserve) return;
  draft.title=[...(item.name || '')].slice(0,30).join('');draft.description=item.description || '';draft.price=Number(item.price || 0);
  draft.skuList=(item.skus || []).filter(s=>s.enabled!==false).map(s=>({selected:false,outer_id:String(s.id),price:Math.round(Number(s.price)*100),quantity:1,property_list:[{property_text:'规格',value_text:[...s.name].slice(0,30).join('')}]}));
};
const open = async (type,row={}) => {
  Object.keys(draft).forEach(key=>delete draft[key]);dialog.value=type;
  if (type==='listing') {
    Object.assign(draft,{key:row.key || null,seller:row.seller || '',localItemId:row.localItemId || '',revision:row.revision || 0,title:row.payload?.title || '',description:row.payload?.desc || '',category:row.payload?.channel_cat_id || '',price:Number(row.payload?.reserve_price || 0),quantity:row.payload?.quantity || 1,images:(row.payload?.images || []).join(','),nativeEnabled:row.nativeEnabled || false,miniappEnabled:row.payload?.miniapp_enabled || false,claimApproved:row.payload?.claim_entry_approved || false,skuList:JSON.parse(JSON.stringify(row.payload?.item_sku_list || [])).map(s=>({...s,selected:true}))});
    draft.remoteItemId=row.remoteItemId || '';draft.claimEntryUrl=row.payload?.claim_entry_url || '';
    if(row.payload?.single_sku_id) draft.skuList=[{selected:true,outer_id:String(row.payload.single_sku_id),price:Math.round(draft.price*100),quantity:draft.quantity,property_list:[{property_text:'规格',value_text:row.payload.single_sku_name || '默认规格'}]}];
    localItems.value=await getShopItems({page:0,size:200});
  } else if(type==='capability') Object.assign(draft,{seller:row.seller || '',application:row.application || 'commerce',operation:row.operation || '',granted:row.granted || false,verified:row.verified || false,expires:localDate(row.expiresAt || Date.now()/1000+86400),requestKey:row.requestKey || '',categories:(row.categoryIds || []).join(','),topics:(row.topics || []).join(','),verificationEvidence:''});
  else if(type==='seller') Object.assign(draft,{key:'',application:'commerce',code:''});
  else if(type==='resolve') Object.assign(draft,{key:row.key,remoteItemId:'',evidence:''});
  else if(type==='delivery') {
    Object.assign(draft,{key:row.key,taskKey:'',resolution:'SUCCESS',evidence:''});
    deliveryTasks.value=await execute('delivery-tasks',{key:row.key});
  } else Object.assign(draft,{key:row.key,amount:0,maximum:(row.amountCents-row.refundCents)/100,operation:'PART_REFUND',requestId:crypto.randomUUID(),decision:'AGREE_REFUND',reason:''});
};
const submit = async () => {
  saving.value=true;
  try {
    if(dialog.value==='listing') {
      const skus=draft.skuList.filter(s=>s.selected).map(({selected,...sku})=>sku);
      if(draft.skuList.length && !skus.length) throw new Error('请明确选择渠道规格');
      await execute('save-listing',{listing:{key:draft.key,seller:draft.seller,localItemId:String(draft.localItemId),remoteItemId:draft.remoteItemId || null,revision:draft.revision,nativeEnabled:draft.nativeEnabled,payload:{title:draft.title,desc:draft.description,channel_cat_id:String(draft.category),reserve_price:skus.length===1?(skus[0].price/100).toFixed(2):Number(draft.price).toFixed(2),quantity:skus.length===1?skus[0].quantity:draft.quantity,images:comma(draft.images),item_sku_list:skus.length>1?skus:[],...(skus.length===1?{single_sku_id:skus[0].outer_id,single_sku_name:skus[0].property_list[0].value_text}:{}),miniapp_enabled:draft.miniappEnabled,item_biz_type:0,stuff_status:100,claim_entry_approved:draft.claimApproved,claim_entry_url:draft.claimEntryUrl},remoteSnapshot:{}}});
    }
    else if(dialog.value==='capability') await execute('save-capability',{capability:{seller:draft.seller,application:draft.application,operation:draft.operation,granted:draft.granted,verified:draft.verified,expiresAt:Math.floor(new Date(draft.expires).getTime()/1000),requestKey:draft.requestKey || null,categoryIds:comma(draft.categories),topics:comma(draft.topics)},verificationEvidence:draft.verificationEvidence});
    else if(dialog.value==='seller') await execute('authorize-seller',{key:draft.key,application:draft.application,code:draft.code});
    else if(dialog.value==='resolve') await execute('resolve-listing',{key:draft.key,remoteItemId:draft.remoteItemId,evidence:draft.evidence});
    else if(dialog.value==='delivery') await execute('resolve-delivery',{key:draft.key,taskKey:draft.taskKey,resolution:draft.resolution,evidence:draft.evidence});
    else await execute('refund',{key:draft.key,amountCents:Math.round(Number(draft.amount)*100),requestId:draft.requestId,operation:draft.operation,decision:draft.decision,reason:draft.reason});
    dialog.value='';
  } finally { saving.value=false; }
};
const command=(key,operation)=>execute('listing-command',{key,operation},key).catch(()=>{});
const orderCommand=(key,command)=>execute('order-command',{key,command},key).catch(()=>{});
const upload=async ({file,onSuccess,onError})=>{
  try {
    if(!['image/png','image/jpeg'].includes(file.type) || file.size>5*1024*1024) throw new Error('请选择5MB以内的PNG或JPEG图片');
    const content=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(',')[1]);reader.onerror=reject;reader.readAsDataURL(file);});
    const result=await execute('upload-media',{seller:draft.seller,name:file.name,content});draft.images=[...comma(draft.images),String(result.mediaId)].join(',');onSuccess(result);
  }catch(e){onError(e);message.error(e.message);}
};
onMounted(load);
</script>
<template>
  <section class="channel-management">
    <div class="toolbar"><h2>闲鱼渠道</h2><a-tooltip title="刷新"><a-button :loading="loading" @click="load"><reload-outlined /></a-button></a-tooltip></div>
    <a-alert v-if="error" type="error" :message="error" show-icon class="notice" />
    <a-alert v-if="status?.blockers?.length" type="warning" :message="status.blockers.join('；')" show-icon class="notice" />
    <div class="status-line"><a-tag :color="status?.salesEnabled?'green':'default'">{{status?.salesEnabled?'新交易已启用':'新交易未启用'}}</a-tag><a-tag>{{status?.processingEnabled?'订单处理中':'订单处理未启用'}}</a-tag></div>
    <div class="exception-line"><span>待核查 <b>{{exceptionCounts.unknown}}</b></span><span>失败任务 <b>{{exceptionCounts.failed}}</b></span><span>授权过期 <b>{{exceptionCounts.expired}}</b></span><span>退款处理中 <b>{{exceptionCounts.refund}}</b></span></div>
    <a-tabs v-model:activeKey="tab">
      <a-tab-pane key="listings" tab="商品映射">
        <a-space><a-button :disabled="!configured" @click="open('listing')"><plus-outlined />新增映射</a-button><a-button :disabled="!selection.length || !status?.salesEnabled" @click="previewSync(selectedListings)">同步选中商品</a-button></a-space>
        <a-table :data-source="records.listings" :loading="loading" row-key="key" :row-selection="{selectedRowKeys:selection,onChange:keys=>selection=keys}" :scroll="{x:950}" :pagination="{pageSize:10}">
          <a-table-column title="商品" key="item"><template #default="{record}"><strong>{{record.payload.title}}</strong><div class="secondary">本地商品 {{record.localItemId}} · {{record.seller}}</div></template></a-table-column>
          <a-table-column title="价格" key="price"><template #default="{record}">¥{{record.payload.reserve_price}}</template></a-table-column>
          <a-table-column title="状态" key="status"><template #default="{record}"><a-tag>{{phase(record.status)}}</a-tag><div>{{record.nativeEnabled?'原生交易':''}} {{record.payload.miniapp_enabled?'小程序交易':''}}</div></template></a-table-column>
          <a-table-column title="操作" key="actions" :width="300"><template #default="{record}"><a-space wrap><a-tooltip title="编辑"><a-button size="small" :disabled="record.status==='UNKNOWN'" @click="open('listing',record)"><edit-outlined /></a-button></a-tooltip><a-button v-if="record.status==='UNKNOWN' && !record.linked" size="small" :disabled="!available(record,'ITEM_QUERY')" @click="open('resolve',record)">核查关联</a-button><a-button size="small" :disabled="!status?.salesEnabled || busy.has(record.key) || record.status==='UNKNOWN' || !available(record,record.linked?'ITEM_EDIT':'ITEM_PUBLISH')" @click="previewSync([record])">{{record.linked?'同步修改':'发布'}}</a-button><a-button size="small" :disabled="!record.linked || !available(record,'ITEM_QUERY')" @click="command(record.key,'ITEM_QUERY')">核对</a-button><a-popconfirm title="确认下架？" @confirm="command(record.key,'ITEM_DOWNSHELF')"><a-button size="small" :disabled="!record.linked || !available(record,'ITEM_DOWNSHELF')">下架</a-button></a-popconfirm></a-space></template></a-table-column>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="orders" tab="订单与售后">
        <a-table :data-source="records.orders" row-key="key" :scroll="{x:1100}" :pagination="{pageSize:10}">
          <a-table-column title="订单" key="no"><template #default="{record}">{{record.localOrderNo || '待买家关联'}}<div class="secondary">{{record.channel==='XIANYU_NATIVE'?'原生交易':'小程序交易'}} · {{record.seller}}</div></template></a-table-column>
          <a-table-column title="金额" key="amount"><template #default="{record}">{{cash(record.amountCents)}}<div class="secondary">退款 {{cash(record.refundCents)}}</div></template></a-table-column>
          <a-table-column title="交易" key="state"><template #default="{record}">{{platform(record.platformState)}}<div class="secondary">{{phase(record.creationState)}}</div></template></a-table-column>
          <a-table-column title="交付与发货" key="delivery"><template #default="{record}">{{phase(record.fulfillmentState)}}<div class="secondary">{{phase(record.shipmentState)}}</div></template></a-table-column>
          <a-table-column title="退款" key="refund"><template #default="{record}">{{phase(record.refundState)}}</template></a-table-column>
          <a-table-column title="操作" key="actions" :width="280"><template #default="{record}"><a-space wrap><a-button size="small" :disabled="!available(record,'ORDER_QUERY')" @click="orderCommand(record.key,'QUERY')">核对</a-button><a-button v-if="record.creationState==='PREPARING'" size="small" @click="orderCommand(record.key,'PREPARE')">恢复准备</a-button><a-button size="small" :disabled="!record.localOrderNo" @click="orderCommand(record.key,'DELIVERY_RETRY')">重试交付</a-button><a-button size="small" :disabled="!record.localOrderNo" @click="open('delivery',record)">交付核查</a-button><a-button size="small" @click="orderCommand(record.key,'SHIP_RETRY')">同步发货</a-button><a-button size="small" :disabled="!available(record,'REFUND_QUERY')" @click="orderCommand(record.key,'REFUND_QUERY')">退款查询</a-button><a-button size="small" :disabled="record.platformState<2" @click="open('refund',record)">售后审核</a-button></a-space></template></a-table-column>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="capabilities" tab="商家与权限">
        <a-space wrap><a-button :disabled="!configured" @click="open('seller')"><plus-outlined />商家授权</a-button><a-button :disabled="!configured" @click="open('capability')"><plus-outlined />权限配置</a-button></a-space>
        <a-list :data-source="records.sellers"><template #renderItem="{item}"><a-list-item><a-list-item-meta :title="`${item.key} · ${item.application}`" :description="`授权有效期：${new Date(item.expiresAt*1000).toLocaleString()}`" /><a-tag>{{item.enabled?'有效':'停用'}}</a-tag></a-list-item></template></a-list>
        <a-table :data-source="records.capabilities" :row-key="r=>`${r.application}:${r.seller}:${r.operation}`" :scroll="{x:850}"><a-table-column title="应用" data-index="application" /><a-table-column title="商家" data-index="seller" /><a-table-column title="能力" data-index="operation" /><a-table-column title="授权" key="grant"><template #default="{record}">{{record.granted?'已获授权':'未授权'}}</template></a-table-column><a-table-column title="接口验证" key="verified"><template #default="{record}">{{record.verified?'已核验':'未核验'}}</template></a-table-column><a-table-column title="操作" key="edit"><template #default="{record}"><a-tooltip title="编辑"><a-button size="small" @click="open('capability',record)"><edit-outlined /></a-button></a-tooltip></template></a-table-column></a-table>
      </a-tab-pane>
      <a-tab-pane key="jobs" tab="任务队列"><a-table :data-source="records.jobs" row-key="key" :scroll="{x:850}"><a-table-column title="任务" data-index="kind" /><a-table-column title="商家" data-index="seller" /><a-table-column title="状态" key="status"><template #default="{record}">{{phase(record.status)}}</template></a-table-column><a-table-column title="尝试次数" data-index="attempts" /><a-table-column title="异常" data-index="lastError" /><a-table-column title="操作" key="retry"><template #default="{record}"><a-button size="small" :disabled="record.status!=='FAILED'" @click="execute('retry-job',{key:record.key}).catch(()=>{})">重试</a-button></template></a-table-column></a-table></a-tab-pane>
      <a-tab-pane key="messages" tab="消息接收"><a-table :data-source="records.messages" :row-key="(r,i)=>`${r.seller}:${r.createdAt}:${i}`" :scroll="{x:750}"><a-table-column title="商家" data-index="seller" /><a-table-column title="主题" data-index="topic" /><a-table-column title="处理状态" data-index="status" /><a-table-column title="异常" data-index="lastError" /><a-table-column title="接收时间" key="time"><template #default="{record}">{{new Date(record.createdAt*1000).toLocaleString()}}</template></a-table-column></a-table></a-tab-pane>
    </a-tabs>
    <a-modal :open="!!diffTargets.length" title="商品同步差异" :confirm-loading="loading" width="min(920px,96vw)" @cancel="diffTargets=[]" @ok="confirmSync">
      <a-table :data-source="syncDiff" row-key="id" :scroll="{x:650}" :pagination="{pageSize:8}"><a-table-column title="商品" data-index="title" /><a-table-column title="字段" data-index="field" /><a-table-column title="平台快照" key="previous"><template #default="{record}"><div class="diff-value">{{record.previous}}</div></template></a-table-column><a-table-column title="本次提交" key="next"><template #default="{record}"><div class="diff-value">{{record.next}}</div></template></a-table-column></a-table>
    </a-modal>
    <a-drawer :open="!!dialog" :title="{listing:'商品渠道配置',seller:'商家授权',capability:'权限配置',refund:'退款审核',delivery:'交付核查',resolve:'发布结果核查'}[dialog]" width="min(640px,100vw)" @close="dialog=''">
      <a-form layout="vertical" @finish="submit">
        <template v-if="dialog==='listing'">
          <a-form-item label="商家" required><a-select v-model:value="draft.seller" :options="sellerOptions" :disabled="!!draft.key" /></a-form-item><a-form-item label="本地商品" required><a-select v-model:value="draft.localItemId" show-search :options="localItems.map(i=>({value:String(i.id),label:i.name}))" :disabled="!!draft.key" @change="chooseItem" /></a-form-item>
          <a-form-item label="标题" required><a-input v-model:value="draft.title" :maxlength="30" /></a-form-item><a-form-item label="描述" required><a-textarea v-model:value="draft.description" :rows="6" :maxlength="5000" /></a-form-item>
          <a-form-item label="关联已有闲鱼商品编号"><a-input v-model:value="draft.remoteItemId" :disabled="!!draft.key" /></a-form-item>
          <div class="form-grid"><a-form-item label="渠道类目编号" required><a-input v-model:value="draft.category" /></a-form-item><a-form-item label="售价（元）" required><a-input-number v-model:value="draft.price" :min="0.01" :precision="2" /></a-form-item><a-form-item label="库存" required><a-input-number v-model:value="draft.quantity" :min="1" :precision="0" /></a-form-item></div>
          <a-form-item label="商品图片编号" required><a-input v-model:value="draft.images" /><a-upload :show-upload-list="false" :custom-request="upload" accept="image/*" :disabled="!draft.seller"><a-button><upload-outlined />上传图片</a-button></a-upload></a-form-item>
          <div v-for="sku in draft.skuList" :key="sku.outer_id" class="sku-line"><a-checkbox v-model:checked="sku.selected">{{sku.property_list.map(p=>p.value_text).join(' / ')}}</a-checkbox><a-input-number v-model:value="sku.price" :min="1" :precision="0" addon-after="分" /><a-input-number v-model:value="sku.quantity" :min="1" :precision="0" addon-after="库存" /></div>
          <a-form-item label="小程序交易"><a-switch v-model:checked="draft.miniappEnabled" /></a-form-item>
          <a-form-item label="原生交易"><a-switch v-model:checked="draft.nativeEnabled" /></a-form-item><a-form-item v-if="draft.nativeEnabled" label="领取入口" required><a-input v-model:value="draft.claimEntryUrl" /><a-checkbox v-model:checked="draft.claimApproved">领取入口已获平台批准</a-checkbox></a-form-item>
        </template>
        <template v-else-if="dialog==='capability'">
          <a-form-item label="应用" required><a-select v-model:value="draft.application" :options="[{value:'commerce',label:'闲鱼电商'},{value:'buyer',label:'小程序C端'},{value:'seller',label:'小程序B端'}]" /></a-form-item>
          <a-form-item label="商家" required><a-select v-model:value="draft.seller" :options="sellerOptions" /></a-form-item>
          <a-form-item label="接口" required><a-select v-model:value="draft.operation" :options="operationOptions" show-search /></a-form-item>
          <a-form-item label="有效期" required><a-input v-model:value="draft.expires" type="datetime-local" /></a-form-item>
          <a-form-item label="请求对象参数名"><a-input v-model:value="draft.requestKey" /></a-form-item>
          <a-form-item label="获准类目编号"><a-input v-model:value="draft.categories" /></a-form-item>
          <a-form-item label="获准消息主题"><a-input v-model:value="draft.topics" /></a-form-item>
          <a-space><a-checkbox v-model:checked="draft.granted">已获授权</a-checkbox><a-checkbox v-model:checked="draft.verified">接口已核验</a-checkbox></a-space>
          <a-form-item v-if="draft.verified" label="实际调用及权限核验依据" required><a-textarea v-model:value="draft.verificationEvidence" :rows="4" :maxlength="1000" /></a-form-item>
        </template>
        <template v-else-if="dialog==='seller'"><a-form-item label="商家标识" required><a-input v-model:value="draft.key" /></a-form-item><a-form-item label="应用" required><a-select v-model:value="draft.application" :options="[{value:'commerce',label:'闲鱼电商'},{value:'seller',label:'小程序B端'}]" /></a-form-item><a-form-item label="一次性授权码" required><a-input-password v-model:value="draft.code" autocomplete="off" /></a-form-item></template>
        <template v-else-if="dialog==='resolve'"><a-form-item label="远端商品编号" required><a-input v-model:value="draft.remoteItemId" /></a-form-item><a-form-item label="平台调用与商品核查依据" required><a-textarea v-model:value="draft.evidence" :rows="5" :maxlength="1000" /></a-form-item></template>
        <template v-else-if="dialog==='delivery'"><a-list :data-source="deliveryTasks"><template #renderItem="{item}"><a-list-item><a-list-item-meta :title="item.taskKey" :description="`${phase(item.status)} · ${item.message}`" /></a-list-item></template></a-list><a-form-item label="待核查任务"><a-select v-model:value="draft.taskKey" :options="deliveryTasks.filter(t=>t.status==='UNKNOWN').map(t=>({value:t.taskKey,label:t.taskKey}))" /></a-form-item><a-form-item label="核查结论"><a-select v-model:value="draft.resolution" :options="[{value:'SUCCESS',label:'已确认远端完成'},{value:'READY',label:'已确认远端未执行，允许重试'}]" /></a-form-item><a-form-item label="远端核查依据" required><a-textarea v-model:value="draft.evidence" :maxlength="1000" :rows="4" /></a-form-item></template>
        <template v-else><a-form-item label="退款金额（元）" required><a-input-number v-model:value="draft.amount" :min="0.01" :max="draft.maximum" :precision="2" /></a-form-item><a-form-item label="退款方式"><a-select v-model:value="draft.operation" :options="[{value:'PART_REFUND',label:'部分退款'},{value:'AFTER_SEND_REFUND',label:'发货后全额退款'},{value:'DEAL_REFUND',label:'处理买家退款申请'}]" /></a-form-item><a-form-item v-if="draft.operation==='DEAL_REFUND'" label="处理意见"><a-select v-model:value="draft.decision" :options="[{value:'AGREE_REFUND',label:'同意退款'},{value:'REFUSE_REFUND',label:'拒绝退款'}]" /></a-form-item><a-form-item label="售后审核依据" required><a-textarea v-model:value="draft.reason" :rows="3" :maxlength="500" /></a-form-item></template>
        <div class="drawer-actions"><a-button @click="dialog=''">取消</a-button><a-button type="primary" html-type="submit" :loading="saving">{{dialog==='refund'?'确认退款':'保存'}}</a-button></div>
      </a-form>
    </a-drawer>
  </section>
</template>
<style scoped>
.exception-line{display:flex;flex-wrap:wrap;gap:12px 24px;padding:10px 0;border-top:1px solid #eee;border-bottom:1px solid #eee;font-size:13px}.exception-line b{margin-left:8px;font-variant-numeric:tabular-nums}
.diff-value{white-space:pre-wrap;overflow-wrap:anywhere;max-height:160px;overflow:auto;max-width:270px}
.channel-management{padding:20px;text-align:left;min-width:0}.toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.toolbar h2{font-size:20px;margin:0}.notice{margin-bottom:12px}.status-line{margin-bottom:8px}.secondary{font-size:12px;color:#666;margin-top:4px}.form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.sku-line{display:grid;grid-template-columns:minmax(100px,1fr) 140px 140px;gap:8px;align-items:center;padding:8px 0;border-bottom:1px solid #eee}.drawer-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:24px}.channel-management :deep(.ant-table){margin-top:12px}@media(max-width:600px){.channel-management{padding:12px}.toolbar{padding-right:48px}.form-grid{grid-template-columns:1fr}.sku-line{grid-template-columns:1fr 100px 100px}}
</style>
