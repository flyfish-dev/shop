import test from 'node:test';
import assert from 'node:assert/strict';
import { payXianyu } from '../src/modules/xianyu/runtime.js';
import { isOrderRefundFrozen, refundStatusText } from '../src/modules/shop/utils/shopDelivery.js';
test('passes long platform identifiers unchanged and refreshes server state', async () => {
  const id='3318740388015865620';let observed;
  const result=await payXianyu({action:'XIANYU_TRADE_PAY',platformOrderId:id},{requestPayment:async payload=>{observed=payload;}});
  assert.deepEqual(observed,{orderInfo:id});assert.equal(result,'refresh');
});
test('rejects numeric order ids and unfinished creation', async()=>{
  await assert.rejects(()=>payXianyu({action:'XIANYU_TRADE_PAY',platformOrderId:123},{}));
  await assert.rejects(()=>payXianyu({action:'XIANYU_PENDING',platformOrderId:'123'},{}));
});
test('cancellation never becomes a paid result', async()=>{
  const result=await payXianyu({action:'XIANYU_TRADE_PAY',platformOrderId:'123'},{requestPayment:async()=>{throw {status:'6001'};}});
  assert.equal(result,'cancelled');
});
test('refund presentation preserves a previous partial refund after a later request closes',()=>{
  const order={salesChannel:'XIANYU_MINIAPP',refundState:'CLOSED',refundAmount:1};
  assert.equal(isOrderRefundFrozen(order),true);
  assert.equal(isOrderRefundFrozen({...order,refundAmount:0}),false);
  assert.equal(isOrderRefundFrozen({...order,refundState:'PROCESSING',refundAmount:0}),true);
  assert.equal(refundStatusText('PARTIAL',key=>key),'orders.refundStates.PARTIAL');
});
