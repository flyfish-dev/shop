const { chromium }=await import(process.env.FLYFISH_PLAYWRIGHT_MODULE || 'playwright');
import fs from 'node:fs/promises';
const browser=await chromium.launch({headless:true,channel:'chrome'});
const context=await browser.newContext({locale:'zh-CN'});
const page=await context.newPage();
const selectTab=async name=>{
  const target=page.getByRole('tab',{name,exact:true});
  await target.focus();
  await target.click();
  await page.waitForFunction(label=>[...document.querySelectorAll('[role=tab]')].some(e=>e.textContent===label && e.getAttribute('aria-selected')==='true'),name);
};
const errors=[];page.on('pageerror',error=>errors.push(error.message));
await context.addInitScript(()=>localStorage.setItem('access_token','fixture-token'));
await page.route('**/*',async route=>{
  const path=new URL(route.request().url()).pathname;
  const ok=result=>route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,result})});
  if(path==='/portal/users/current') return ok({id:17,username:'接口测试用户',email:'fixture@example.test',phone:'123456',createTime:'2020-01-01 00:00:00',authorizations:{gitea:{type:'GITEA',openid:'1',loginName:'fixture'}}});
  if(path==='/portal/capabilities') return ok([{code:'shop',name:'飞鱼小铺',path:'/shop'}]);
  if(path==='/shops/managements/xianyu/status') return ok({configured:true,salesEnabled:false,processingEnabled:false,blockers:['聚石塔联调尚未完成'],operations:[]});
  if(path.startsWith('/shops/managements/xianyu/')) {
    if(path.endsWith('listings')) return ok([{key:'fixture-listing',seller:'commerce',localItemId:'1',revision:1,status:'DRAFT',nativeEnabled:false,linked:false,payload:{title:'示例数字交付服务',reserve_price:'19.90',desc:'测试商品',images:['123'],channel_cat_id:'123'}}]);
    if(path.endsWith('sellers')) return ok([{key:'commerce',application:'commerce',expiresAt:1800000000,enabled:true}]);
    return ok([]);
  }
  if(path.startsWith('/portal/') || path.startsWith('/shops/')) return ok([]);
  await route.continue();
});
await fs.mkdir('../.local/xianyu-validation',{recursive:true});
try {
for(const size of [{width:1440,height:1000},{width:390,height:844}]) {
  await page.setViewportSize(size);
  await page.goto('http://127.0.0.1:10089/shop/manage/xianyu');
  await page.getByRole('heading',{name:'闲鱼渠道',exact:true}).waitFor();
  await page.getByText('示例数字交付服务',{exact:true}).waitFor();
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
  if(overflow) throw new Error('Management page overflows viewport');
  await page.screenshot({path:`../.local/xianyu-validation/manage-${size.width}.png`,fullPage:true});
  await page.getByRole('button',{name:/新增映射/}).click();
  await page.getByText('商品渠道配置',{exact:true}).waitFor();
  if(await page.getByText('退款金额（元）',{exact:true}).isVisible()) throw new Error('Refund fields leaked into listing configuration');
  await page.getByRole('button',{name:/^取\s*消$/}).click();
  await selectTab('商家与权限');
  await page.getByRole('button',{name:/权限配置/}).click();
  await page.getByText('请求对象参数名',{exact:true}).waitFor();
  if(await page.getByText('退款金额（元）',{exact:true}).isVisible()) throw new Error('Refund fields leaked into capability configuration');
  await page.getByRole('button',{name:/^取\s*消$/}).click();
  await selectTab('商品映射');
}
if(errors.length) throw new Error(errors.join('\n'));
console.log('Xianyu management desktop/mobile fixture smoke passed');
} catch(error) {
  await page.screenshot({path:'../.local/xianyu-validation/manage-failed.png',fullPage:true});
  console.error((await page.locator('body').innerText()).slice(0,3000));
  throw error;
} finally { await browser.close(); }
