// Starts disposable servers; never points at the user's saved database.
const {chromium}=require('playwright');
const {spawn}=require('node:child_process');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const ROOT=path.resolve(__dirname,'../..');
const OUT=path.join(ROOT,'frontend/target/email-ui-checks');
const BASE='http://127.0.0.1:5177';
fs.mkdirSync(OUT,{recursive:true});
const processes=[];
function launch(command,args,cwd,env,log){const file=fs.openSync(path.join(OUT,log),'w');const p=spawn(command,args,{cwd,env:{...process.env,...env},stdio:['ignore',file,file]});p.on('error',e=>console.error(e));processes.push(p);return p;}
async function wait(url){const until=Date.now()+90000;while(Date.now()<until){try{if((await fetch(url)).ok)return;}catch{}await new Promise(r=>setTimeout(r,500));}throw Error('Server did not start: '+url);}
(async()=>{let browser;try{
  launch('java',['-jar','target/careerbridge-0.0.1-SNAPSHOT.jar','--server.port=8087','--spring.datasource.url=jdbc:h2:mem:emailui;MODE=PostgreSQL;DB_CLOSE_DELAY=-1','--spring.jpa.hibernate.ddl-auto=create-drop','--app.upload-dir=target/email-ui-resumes','--app.local-email-testing=true','--app.local-inbox-port=8027','--app.frontend-url='+BASE,'--app.mail-enabled=false','--app.ai-url=','--app.redis-enabled=false'],path.join(ROOT,'backend'),{},'backend.log');
  launch(process.execPath,['node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','5177'],path.join(ROOT,'frontend'),{API_PROXY_TARGET:'http://127.0.0.1:8087'},'frontend.log');
  await Promise.all([wait('http://127.0.0.1:8087/actuator/health'),wait(BASE)]);
  browser=await chromium.launch({headless:true});const context=await browser.newContext();const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{if(r.status()>=500)errors.push(r.url()+': '+r.status());});
  await page.goto(BASE+'/register');await page.getByLabel('Full name',{exact:true}).fill('Inbox Flow Tester');await page.getByLabel('Email address',{exact:true}).fill('inbox-flow@example.com');await page.getByLabel('Password',{exact:true}).fill('Strong@12345');await page.getByLabel('University',{exact:true}).selectOption({index:1});await page.getByRole('button',{name:'Create account'}).click();await page.waitForURL('**/verify-email');
  await page.getByRole('link',{name:'Open test inbox'}).waitFor();assert.equal(await page.getByRole('link',{name:'Open test inbox'}).getAttribute('target'),'_self');
  for(const width of [1280,390]){await page.setViewportSize({width,height:900});await page.screenshot({path:path.join(OUT,'verification-'+width+'.png'),fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'Verification overflow '+width);}
  await page.getByRole('link',{name:'Open test inbox'}).click();await page.waitForURL('http://127.0.0.1:8027/**');assert.equal(context.pages().length,1);assert.equal(await page.locator('article.message').count(),1);assert(await page.getByText('inbox-flow@example.com',{exact:true}).count()>0);
  for(const width of [1280,390]){await page.setViewportSize({width,height:900});await page.screenshot({path:path.join(OUT,'inbox-'+width+'.png'),fullPage:true});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),'Inbox overflow '+width);if(width===390){const button=await page.getByRole('link',{name:'Verify email'}).boundingBox();const card=await page.locator('article.message').boundingBox();assert(button.x>=card.x&&button.x+button.width<=card.x+card.width,'Button fits card');}}
  await page.getByRole('link',{name:'Refresh inbox',exact:true}).click();await page.getByRole('link',{name:'Back to verification',exact:true}).click();await page.waitForURL(BASE+'/verify-email?email=**');await page.getByRole('link',{name:'Open test inbox'}).click();await page.getByRole('link',{name:'Verify email'}).click();await page.waitForURL(BASE+'/verify-email#token=**');assert.equal(context.pages().length,1);await page.getByRole('button',{name:'Confirm email address'}).click();await page.getByRole('heading',{name:'You’re all set.'}).waitFor();assert.equal(await page.getByRole('link',{name:'Continue to sign in'}).count(),1);assert.equal(await page.getByRole('button',{name:'Resend verification email'}).count(),0);await page.screenshot({path:path.join(OUT,'verified-mobile.png'),fullPage:true});
  await page.getByRole('link',{name:'Continue to sign in'}).click();await page.getByLabel('Email address',{exact:true}).fill('inbox-flow@example.com');await page.getByLabel('Password',{exact:true}).fill('Strong@12345');await page.getByRole('button',{name:'Sign in',exact:true}).click();await page.waitForURL(BASE+'/dashboard');assert.equal(context.pages().length,1);assert.deepEqual(errors,[]);fs.writeFileSync(path.join(OUT,'results.json'),JSON.stringify({passed:true,checks:['registration','in-app filtered inbox','same-tab verification and sign-in','refresh and return navigation','1280px and 390px overflow','mobile button fit','success state'],errors},null,2));
  console.log('Email UI checks passed: single-tab registration → inbox → verification → sign-in, desktop and mobile.');
}catch(e){console.error(e);process.exitCode=1;}finally{if(browser)await browser.close();for(const p of processes)p.kill('SIGTERM');}})();
