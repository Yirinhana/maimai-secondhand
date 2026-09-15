// Real browser regression for the manual black-box feedback. Creates only one unpublished local draft.
const { chromium, expect } = require('@playwright/test');
const fs = require('node:fs'), path = require('node:path');
const ROOT=path.resolve(__dirname,'../..'), BASE=process.env.MAIMAI_046_BASE||'http://127.0.0.1:5173';
if(!['http://127.0.0.1:5173','https://market.example.com'].includes(BASE))throw Error('Unapproved host');
const local=BASE.startsWith('http://127.'), run=Date.now();
const accounts=JSON.parse(fs.readFileSync(path.join(ROOT,'.local/private/demo-accounts-040.json'),'utf8').replace(/^\uFEFF/,''));
const report={base:BASE,checks:[],errors:[],screenshots:[],draftId:null};
async function shot(p,name){const file=path.join(ROOT,`.local/screenshots/046-${run}-blackbox-${name}.png`);await expect.poll(()=>p.evaluate(()=>document.documentElement.scrollWidth-innerWidth)).toBeLessThanOrEqual(1);await p.screenshot({path:file,fullPage:true});report.screenshots.push(file);}
async function read(ctx,url){const r=await ctx.request.get(BASE+'/api/v1'+url);expect(r.ok()).toBe(true);return r.json();}
function observe(p){p.on('pageerror',e=>report.errors.push(e.message));}
async function login(p,email,switching=false){await p.goto(BASE+'/login'+(switching?'?switch=1':'?redirect='+encodeURIComponent('/\n/example.invalid')));await p.getByLabel('邮箱',{exact:true}).fill(email);await p.getByLabel('密码',{exact:true}).fill(accounts.password);await p.getByRole('button',{name:switching?'切换并登录':'登录',exact:true}).click();await expect(p).not.toHaveURL(/\/login/, {timeout:15000});}
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const ctx=await browser.newContext({viewport:{width:1365,height:920}});
  let p=await ctx.newPage();observe(p);await login(p,accounts.sellers[0].email);await expect(p).toHaveURL(BASE+'/');
  const first=await read(ctx,'/auth/me');
  await p.goto(BASE+'/publish');await expect(p.getByRole('heading',{name:'发布闲置',exact:true})).toBeVisible();
  await p.getByLabel('标题',{exact:true}).fill('表单恢复校验 '+run);
  await p.getByLabel(/^价格（元）/).fill('12.345');await p.getByLabel(/^库存/).fill('-1');
  await p.getByLabel(/^商品描述/).fill('中途离开后保留这段描述。');
  await p.getByRole('button',{name:'保存草稿',exact:true}).click();
  await expect(p.getByLabel(/^分类/)).toBeFocused();
  await expect(p.locator('#product-priceYuan-error')).toContainText('最多保留两位小数');
  await expect(p.locator('#product-stock-error')).toContainText('不能填写小数或负数');
  await shot(p,'draft-errors-desktop');
  await p.close();p=await ctx.newPage();observe(p);await p.goto(BASE+'/publish');
  await expect(p.getByLabel('标题',{exact:true})).toHaveValue('表单恢复校验 '+run);
  await expect(p.getByLabel(/^价格（元）/)).toHaveValue('12.345');await expect(p.getByLabel(/^库存/)).toHaveValue('-1');
  await expect(p.getByText('已恢复上次未完成的内容，请核对后继续填写')).toBeVisible();
  report.checks.push('Incomplete values survive closing and reopening the page; save focuses the first missing field with explicit price and stock errors');
  // Same browser: successfully sign into another account, then return through the remembered-account menu.
  await login(p,accounts.sellers[1].email,true);
  await p.goto(BASE+'/publish');await expect(p.getByLabel('标题',{exact:true})).toHaveValue('');
  const anotherTab=await ctx.newPage();observe(anotherTab);await anotherTab.goto(BASE+'/me');
  await p.getByRole('button',{name:'打开个人菜单'}).click();await p.getByRole('menuitem',{name:'切换账号',exact:true}).click();
  await expect(p.getByRole('heading',{name:'切换账号',exact:true})).toBeVisible();
  await p.getByRole('button',{name:'选择账号 '+accounts.sellers[0].email,exact:true}).click();
  await expect(p.getByLabel('密码',{exact:true})).toBeFocused();await expect(p.getByLabel('密码',{exact:true})).toHaveValue('');
  await p.setViewportSize({width:390,height:844});await shot(p,'account-switch-mobile');
  await p.getByLabel('密码',{exact:true}).fill(accounts.password);await p.getByRole('button',{name:'切换并登录',exact:true}).click();await expect(p).not.toHaveURL(/\/login/);
  await expect(anotherTab).toHaveURL(BASE+'/');await anotherTab.close();
  expect((await read(ctx,'/auth/me')).id).toBe(first.id);
  await p.goto(BASE+'/publish');await expect(p.getByLabel('标题',{exact:true})).toHaveValue('表单恢复校验 '+run);
  const stored=await p.evaluate(()=>JSON.parse(localStorage.getItem('maimai:device-accounts:v1')));
  expect(stored).toHaveLength(2);for(const a of stored)expect(Object.keys(a).sort()).toEqual(['avatarUrl','email','id','nickname']);
  report.checks.push('Remembered accounts require a password, retain only profile metadata, reload other tabs, and keep drafts isolated by account');
  await p.getByRole('button',{name:'一键清空',exact:true}).click();await p.getByRole('button',{name:'确认操作',exact:true}).click();
  await expect(p.getByLabel('标题',{exact:true})).toHaveValue('');await p.reload();await expect(p.getByLabel('标题',{exact:true})).toHaveValue('');
  if(local){
   await p.getByLabel('标题',{exact:true}).fill('未发布的表单验收记录 '+run);
   const last=await p.getByLabel(/^分类/).locator('option').last().getAttribute('value');await p.getByLabel(/^分类/).selectOption(last);
   await p.getByLabel(/^价格（元）/).fill('25.01');await p.getByLabel(/^库存/).fill('3');await p.getByLabel(/^所在地区/).fill('上海市');
   await p.getByRole('button',{name:'保存草稿',exact:true}).click();await expect(p).toHaveURL(/\/publish\/\d+$/, {timeout:15000});
   await expect(p.locator('input[type="file"]')).toBeFocused();
   const id=Number(new URL(p.url()).pathname.split('/').pop());report.draftId=id;
   const saved=await read(ctx,`/seller/products/${id}`);expect(saved.priceCents).toBe(2501);expect(saved.stockAvailable).toBe(3);expect(saved.status).toBe('DRAFT');
   await p.getByLabel('标题',{exact:true}).fill('尚未保存的修改 '+run);await p.reload();await expect(p.getByLabel('标题',{exact:true})).toHaveValue('尚未保存的修改 '+run);
   await p.getByLabel(/^调整量/).fill('1.5');await p.getByRole('button',{name:'调整库存',exact:true}).click();await expect(p.getByText('请输入非零整数调整量',{exact:true})).toBeVisible();
   expect((await read(ctx,`/seller/products/${id}`)).stockAvailable).toBe(3);
   report.checks.push('Valid money/stock creates a persistent server draft; unfinished edits restore; fractional stock adjustment is rejected');
  }
  report.checks.push('Clear form removes the stored unfinished draft and survives reload');
  await p.goto(BASE+'/community/demands');await p.getByRole('button',{name:'发布求购',exact:true}).click();
  await p.getByLabel(/^想要什么/).fill('预算验证');await p.getByLabel(/^具体需求/).fill('只校验表单，不发布。');
  await p.getByLabel(/^最低预算/).fill('100');await p.getByLabel(/^最高预算/).fill('10');
  await p.getByRole('button',{name:'提交审核',exact:true}).click();await expect(p.getByLabel(/^最高预算/)).toBeFocused();
  await expect(p.locator('#demand-budgetMax-error')).toHaveText('最高预算不能低于最低预算');
  await p.getByLabel(/^最高预算/).fill('1000001');await p.getByRole('button',{name:'提交审核',exact:true}).click();await expect(p.locator('#demand-budgetMax-error')).toHaveText('最高预算不能超过 100 万元');
  await p.getByLabel(/^最低预算/).fill('-1');await p.getByRole('button',{name:'提交审核',exact:true}).click();await expect(p.locator('#demand-budgetMin-error')).toBeVisible();
  await shot(p,'budget-errors-mobile');report.checks.push('Budget minimum, reversed range and 1M cap errors stay directly under their corresponding fields');
  const products=await read(ctx,'/seller/products?size=50');const existing=products.content.find(r=>r.id!==report.draftId);
  await p.goto(BASE+'/publish/'+existing.id);await p.getByRole('button',{name:'查看修改历史',exact:true}).click();
  const history=await read(ctx,`/seller/products/${existing.id}/revisions`);
  expect(history.content.length).toBeGreaterThan(0);
  for(const entry of history.content){expect(Object.keys(entry).sort()).toEqual(['actionLabel','content','createdAt','version']);expect(entry.content.categoryName).toBeTruthy();const json=JSON.stringify(entry);for(const key of ['actorId','categoryId','experienceSource','latitude','longitude','"id"','BASELINE'])expect(json).not.toContain(key);}
  await p.locator('.revision summary').first().click();await shot(p,'revision-mobile');report.checks.push('Existing revision history renders business labels and the API omits database identifiers and internal source markers');
  // Remove one local remembered record without changing the actual account.
  await p.goto(BASE+'/login?switch=1');await p.getByRole('button',{name:'移除本机记录 '+accounts.sellers[1].email,exact:true}).click();await expect(p.getByRole('button',{name:'选择账号 '+accounts.sellers[1].email,exact:true})).toHaveCount(0);expect((await read(ctx,'/auth/me')).id).toBe(first.id);
  await p.goto(BASE+'/');expect(report.errors).toEqual([]);
 }finally{fs.writeFileSync(path.join(ROOT,`.local/046-blackbox-${local?'local':'public'}.json`),JSON.stringify(report,null,2));await browser.close();}
 console.log(JSON.stringify({checks:report.checks,errors:report.errors,draftId:report.draftId}));
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
