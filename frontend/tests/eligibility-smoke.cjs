// Use an isolated demo database; this check creates an opening and changes demo academics.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const BASE=process.env.TEST_BASE_URL||'http://127.0.0.1:5173';
const OUT=path.resolve(process.env.TEST_OUTPUT_DIR||'target/eligibility-checks');
fs.mkdirSync(OUT,{recursive:true});

(async()=>{
  const browser=await chromium.launch({headless:true});
  const page=await browser.newPage({viewport:{width:1365,height:900}});
  const errors=[],failures=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('response',r=>{if(r.url().includes('/api/')&&r.status()>=500)failures.push(r.status()+' '+r.url());});
  const title='Eligibility QA Internship '+Date.now();
  let job,recruiterToken;
  async function login(role){await page.goto(BASE+'/login');await page.locator('.demo-grid').getByRole('button',{name:role,exact:true}).click();await page.waitForURL('**/dashboard');await page.locator('.workspace-main h1').waitFor();}
  async function logout(){await page.locator('.sidebar').getByRole('button',{name:'Sign out',exact:true}).click();await page.waitForURL('**/login');}
  async function success(message){await page.getByRole('status').filter({hasText:message}).waitFor();}
  async function request(method,route,body,token){return page.evaluate(async d=>{const response=await fetch('/api'+d.route,{method:d.method,headers:{Authorization:'Bearer '+(d.token||localStorage.getItem('careerbridge.token')),...(d.body===undefined?{}:{'Content-Type':'application/json'})},body:d.body===undefined?undefined:JSON.stringify(d.body)});return {status:response.status,data:await response.json()};},{method,route,body,token});}
  async function openJob(){await page.goto(BASE+'/jobs');await page.getByLabel('Search opportunities').fill(title);await page.getByRole('button',{name:title,exact:true}).click();await page.locator('dialog .eligibility-report').waitFor();}
  async function academicScore(score,backlogs=0){
    await page.goto(BASE+'/profile');const form=page.locator('.academic-panel');await form.locator('[name=tenthScoreType]').waitFor();
    await form.locator('[name=tenthScoreType]').selectOption('PERCENTAGE');await form.locator('[name=secondaryQualification]').selectOption('TWELFTH');await form.locator('[name=secondaryScoreType]').selectOption('PERCENTAGE');
    for(const [name,value] of Object.entries({tenthScore:String(score),secondaryScore:'75',cgpa:'8',department:'Computer Science',graduationYear:'2028',activeBacklogs:String(backlogs)}))await form.locator(`[name=${name}]`).fill(value);
    await form.getByRole('button',{name:'Save academic details'}).click();await success('Academic details saved');
  }
  try {
    await login('Recruiter');recruiterToken=await page.evaluate(()=>localStorage.getItem('careerbridge.token'));
    await page.goto(BASE+'/openings');await page.getByRole('button',{name:'New opening'}).click();
    const form=page.locator('dialog form');
    for(const [name,value] of Object.entries({title,description:'Java internship with school marks, branch, year and backlog requirements.',location:'Remote',requiredSkills:'Java, SQL',minTenthPercentage:'80',minSecondaryPercentage:'75',minCgpa:'8',maxActiveBacklogs:'0',allowedBranches:'Computer Science',allowedGraduationYears:'2028, nope'}))await form.locator(`[name=${name}]`).fill(value);
    await form.locator('[name=workMode]').selectOption('REMOTE');
    await form.getByRole('button',{name:'Publish opening',exact:true}).click();await success('Enter graduation years');
    assert(await page.locator('dialog').isVisible());
    await form.locator('[name=allowedGraduationYears]').fill('2028');
    await page.screenshot({path:path.join(OUT,'recruiter-eligibility-form.png'),fullPage:true});
    const created=page.waitForResponse(r=>r.url().endsWith('/api/recruiter/jobs')&&r.request().method()==='POST');
    await form.getByRole('button',{name:'Publish opening',exact:true}).click();job=await (await created).json();await success('Opening published');
    assert.equal(job.maxActiveBacklogs,0);assert.deepEqual(job.allowedGraduationYears,[2028]);
    const opening=page.locator('.opening-list .panel').filter({has:page.getByRole('heading',{name:title,exact:true})});
    await opening.getByRole('button',{name:'Edit',exact:true}).click();
    assert.equal(await page.locator('dialog [name=minTenthPercentage]').inputValue(),'80');assert.equal(await page.locator('dialog [name=maxActiveBacklogs]').inputValue(),'0');
    await page.getByRole('button',{name:'Close dialog'}).click();await logout();

    await login('Student');await openJob();
    await page.locator('dialog .eligibility-badge').getByText('Details needed',{exact:true}).waitFor();
    assert(await page.getByRole('button',{name:'Add eligibility details',exact:true}).isDisabled());
    const bypass=await request('POST','/student/applications',{jobId:job.id,eligible:true});assert.equal(bypass.status,400);
    await page.getByRole('link',{name:'Update academic details'}).click();await page.waitForURL('**/profile');
    await academicScore(79.99);await openJob();
    await page.locator('dialog .eligibility-report').getByText('10th percentage must be at least 80%; yours: 79.99%',{exact:true}).waitFor();
    assert(await page.getByRole('button',{name:'Eligibility requirements not met',exact:true}).isDisabled());
    await page.screenshot({path:path.join(OUT,'student-ineligible-desktop.png'),fullPage:true});
    await page.getByRole('button',{name:'Close dialog'}).click();
    await page.getByLabel('Eligibility',{exact:true}).selectOption('NOT_ELIGIBLE');await page.getByRole('button',{name:title,exact:true}).waitFor();
    await page.getByLabel('Eligibility',{exact:true}).selectOption('DETAILS_REQUIRED');await page.getByRole('heading',{name:'No matching openings'}).waitFor();
    await academicScore(80,1);await openJob();await page.locator('dialog .eligibility-report').getByText('Active backlogs must be at most 0; yours: 1',{exact:true}).waitFor();
    await academicScore(80,0);await openJob();
    assert(await page.getByRole('button',{name:'Submit application',exact:true}).isEnabled());
    await page.setViewportSize({width:390,height:844});
    assert(await page.locator('dialog').evaluate(d=>d.scrollWidth<=d.clientWidth+1),'Mobile eligibility dialog overflows');
    await page.screenshot({path:path.join(OUT,'student-eligible-mobile.png'),fullPage:true});
    await page.setViewportSize({width:1365,height:900});
    // The recruiter changes the cutoff after the student opened an eligible form.
    const recruiterSession=await request('POST','/auth/login',{email:'recruiter@careerbridge.dev',password:'Demo@12345'});assert.equal(recruiterSession.status,200);recruiterToken=recruiterSession.data.token;
    assert.equal((await request('PUT','/recruiter/jobs/'+job.id,{...job,minTenthPercentage:90},recruiterToken)).status,200);
    await page.getByRole('button',{name:'Submit application',exact:true}).click();await success('10th percentage must be at least 90%');
    await page.locator('dialog .eligibility-report').getByText('10th percentage must be at least 90%; yours: 80%',{exact:true}).waitFor();
    assert(await page.getByRole('button',{name:'Eligibility requirements not met',exact:true}).isDisabled());
    assert.equal((await request('PUT','/recruiter/jobs/'+job.id,job,recruiterToken)).status,200);
    await openJob();await page.getByRole('button',{name:'Submit application',exact:true}).click();await success('Application submitted');
    await page.goto(BASE+'/applications');await page.getByRole('heading',{name:title,exact:true}).waitFor();await logout();

    await login('Recruiter');await page.goto(BASE+'/candidates');await page.getByLabel('Compare with opening').selectOption(String(job.id));
    const studentCard=page.locator('.candidate-card').filter({has:page.getByRole('heading',{name:'Parv Nigam',exact:true})});await studentCard.getByText('Eligible to apply',{exact:true}).waitFor();assert(await studentCard.getByRole('button',{name:'Invite to apply'}).isEnabled());
    const missingCard=page.locator('.candidate-card').filter({has:page.getByRole('heading',{name:'Aarav Sharma',exact:true})});await missingCard.getByText('Details needed',{exact:true}).waitFor();assert(await missingCard.getByRole('button',{name:'Invite to apply'}).isDisabled());
    assert.deepEqual(errors,[]);assert.deepEqual(failures,[]);
    fs.writeFileSync(path.join(OUT,'results.json'),JSON.stringify({workflows:['recruiter create/edit persistence and year validation','missing details and direct API bypass','below-cutoff and backlog explanations','eligibility filters','exact thresholds and mobile dialog','changed rules enforced at submission','eligible application and recruiter comparison'],javascriptErrors:errors,serverErrors:failures},null,2));
    console.log('PASS: eligibility settings, explanations, filters, missing data, cutoffs, backlogs, stale forms, application enforcement, recruiter comparison and mobile layout.');
  }catch(e){await page.screenshot({path:path.join(OUT,'failure.png'),fullPage:true});console.error('Failed at:',page.url());console.error((await page.locator('body').innerText()).slice(-6000));console.error(e);process.exitCode=1;}
  finally{await browser.close();}
})();
