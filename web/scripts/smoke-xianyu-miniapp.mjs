import fs from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
const { chromium }=await import(process.env.FLYFISH_PLAYWRIGHT_MODULE || 'playwright');
const origin='http://127.0.0.1:10090';
try { await fetch(origin);throw new Error('Port 10090 is occupied; select an unused test port'); }
catch(error) {if(!error.message.includes('fetch failed')) throw error;}
const server=spawn('npm',['run','dev','--','--config','vite.xianyu.config.js','--port','10090','--strictPort'],{
  detached:true,stdio:'ignore',env:{...process.env,VITE_XIANYU_API_BASE:'https://xianyu.fixture.test'}
});
let browser;
let page;
try {
  for(let i=0;i<100;i++) {try{if((await fetch(`${origin}/xianyu.html`)).ok) break;}catch{}await new Promise(resolve=>setTimeout(resolve,100));}
  browser=await chromium.launch({headless:true,channel:'chrome'});
  const context=await browser.newContext({locale:'zh-CN'});
  const errors=[];let requests=0;
  const item={id:1,name:'示例数字服务',price:19.9,type:'SERVICE_PACKAGE',deliveryMode:'MANUAL',description:'自有数字服务测试夹具',enabled:true,groupId:1,tags:['数字服务'],params:'{}',skus:[]};
  const order={orderNo:'LOCAL-1',itemId:1,itemName:item.name,amount:19.9,currency:'CNY',status:'PAYING',deliveryStatus:'WAITING',deliveryMode:'MANUAL',paymentProvider:'xianyu',salesChannel:'XIANYU_MINIAPP'};
  await context.route('**/*',async route=>{
    const url=new URL(route.request().url()),path=url.pathname;
    if(url.hostname==='o.alicdn.com') return route.fulfill({contentType:'text/javascript',body: path.endsWith('checkEvn.js') ? ''
      : 'window.__fixturePayments=[];window.WindVane={call(){}};window.xy={authorize:async()=>({authCode:"fixture-one-use-code"}),requestPayment:async({orderInfo})=>{window.__fixturePayments.push(orderInfo);throw {status:"6001"};}};'});
    if(url.hostname!=='xianyu.fixture.test') return route.continue();
    const headers={'access-control-allow-origin':origin,'access-control-allow-credentials':'true','access-control-allow-headers':'Authorization,Content-Type,Accept-Language,Idempotency-Key','access-control-allow-methods':'GET,POST,OPTIONS'};
    if(route.request().method()==='OPTIONS') return route.fulfill({status:204,headers});
    const ok=result=>route.fulfill({contentType:'application/json',headers,body:JSON.stringify({success:true,code:200,result})});
    if(path==='/xianyu/auth/authorize') return ok({ticket:'fixture-ticket'});
    if(path==='/xianyu/auth/exchange') return ok({token:'xy.fixture-session',expire:Date.now()+3600000});
    if(path==='/portal/users/current') return ok({id:17,username:'买家测试账户',email:'fixture@example.test',phone:'123456',createTime:'2020-01-01 00:00:00',authorizations:{}});
    if(path==='/shops/payment-methods') return ok(['xianyu']);
    if(path==='/shops/items/1') return ok(item);
    if(path==='/shops/items/1/purchase-availability') return ok({purchasable:true});
    if(path==='/shops/items/1/contracts') return ok([]);
    if(path==='/shops/orders' && route.request().method()==='POST') { requests++;return ok({order,payment:{provider:'xianyu',tradeType:'miniapp',action:'XIANYU_PENDING'}}); }
    if(path==='/shops/orders/LOCAL-1') return ok(order);
    if(path==='/shops/orders/mine') return ok([{...order,status:'DELIVERED',deliveryStatus:'SUCCESS',extractable:true,refundState:'PARTIAL',refundAmount:1}]);
    if(path==='/xianyu/orders/LOCAL-1/payment') return ok({provider:'xianyu',tradeType:'miniapp',action:'XIANYU_TRADE_PAY',platformOrderId:'3318740388015865620'});
    if(path==='/shops/items') return ok([item]);
    return ok([]);
  });
  page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));
  page.on('console',event=>{if(event.type()==='error') console.error(event.text().slice(0,1000));});
  await fs.mkdir('../.local/xianyu-validation',{recursive:true});
  for(const size of [{width:1440,height:1000},{width:390,height:844}]) {
    await page.setViewportSize(size);await page.goto(`${origin}/xianyu.html#/shop/detail/1`);
    await page.getByText('示例数字服务',{exact:true}).first().waitFor();
    await page.waitForFunction(()=>!document.querySelector('.ant-spin-blur'),{},{timeout:8000});
    await page.locator('.checkout-btn').click();
    const action=page.locator('.pay-actions button');await action.waitFor({timeout:20000});
    await action.click();
    await page.waitForFunction(()=>window.__fixturePayments?.length===1);
    const id=await page.evaluate(()=>window.__fixturePayments[0]);
    if(id!=='3318740388015865620' || typeof id!=='string') throw new Error('Platform order id was altered');
    if(await page.getByText('支付成功',{exact:true}).count()) throw new Error('Cancelled payment was presented as paid');
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)) throw new Error('Miniapp overflows viewport');
    await page.screenshot({path:`../.local/xianyu-validation/mini-payment-${size.width}.png`,fullPage:true});
    await page.locator('.ant-modal-close').click();
    await page.goto(`${origin}/xianyu.html#/account/orders`);
    await page.getByText('部分退款',{exact:true}).waitFor();
    if(await page.getByRole('button',{name:/提\s*取/}).count()) throw new Error('A refund-frozen order exposes extraction');
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)) throw new Error('Refund order page overflows viewport');
    if(await page.evaluate(()=>document.querySelector('.orders-header h2').getBoundingClientRect().bottom>document.querySelector('.orders-summary').getBoundingClientRect().top)) throw new Error('Order heading is occluded by the summary');
    await page.screenshot({path:`../.local/xianyu-validation/mini-refund-${size.width}.png`,fullPage:true});
    await page.evaluate(()=>{sessionStorage.clear();window.__fixturePayments=[];});
  }
  if(requests!==2 || errors.length) throw new Error(`Unexpected flow: ${requests} orders; ${errors.join(';')}`);
  console.log('Miniapp desktop/mobile SDK fixture smoke passed; no real Xianyu transaction performed');
} catch(error) {
  if(page) {await page.screenshot({path:'../.local/xianyu-validation/mini-failed.png',fullPage:true});console.error((await page.locator('body').innerText()).slice(0,1500));}
  if(page) console.error(await page.evaluate(()=>{
    const output=[];let component=document.querySelector('.checkout-btn')?.__vueParentComponent;
    for(let i=0;component && i<8;i++,component=component.parent) output.push({name:component.type.__name || component.type.name,loading:component.setupState?.loading,spinning:component.props?.spinning,delay:component.props?.delay,attrs:Object.keys(component.attrs||{}),route:component.proxy?.$router?.route?.fullPath});
    return output;
  }));
  throw error;
} finally {
  await browser?.close();
  const exited=once(server,'exit').catch(()=>{});
  try {process.kill(-server.pid,'SIGTERM');} catch{}
  await Promise.race([exited,new Promise(resolve=>setTimeout(resolve,3000))]);
}
