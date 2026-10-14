import {useState} from 'react';
import {Upload,Download,Trash2,FileCheck2,ArrowUpRight,GitCompareArrows,Eye} from 'lucide-react';
import {api,download,date,datetime} from './api';
import {PageTitle,Button,Field,Badge,Modal,Empty,LoadState,useLoad,useToast} from './ui';

function Score({title,value,detail}) {
  return <div className="resume-score"><span>{title}</span><strong>{value==null?'—':value}<small>{value==null?'Not scored':'/100'}</small></strong><p>{detail}</p></div>;
}
function Report({item}) {
  const {readiness,match,target,ai,limitations}=item.report;
  return <div className="resume-report">
    <p className="muted">{item.filename} · {datetime(item.createdAt)} · {target.title}</p>
    <div className="resume-score-grid"><Score title="Estimated ATS readiness" value={readiness.score} detail="Contact, sections and content evidence"/><Score title="Job skill match" value={match.score} detail={target.type==='GENERAL'?'Choose a job target to check skill coverage.':target.title}/></div>
    <p className="resume-report-note">A CareerBridge estimate, not an employer ATS score or a prediction of shortlisting.</p>
    <h3>Why you received this score</h3>
    <div className="resume-checks">{readiness.checks.map(check=><section key={check.key}><div><h4>{check.title}</h4><strong>{check.earned}/{check.possible}</strong></div><p>{check.detail}</p>{check.earned<check.possible&&<p className="resume-suggestion">{check.suggestion}</p>}</section>)}</div>
    <h3>Target skill coverage</h3><p className="muted">{match.explanation}</p>
    {match.requiredSkills.length>0&&<div className="resume-skill-grid"><section><h4>Matched skills</h4><div className="tags">{match.matchedSkills.map(s=><span key={s}>{s}</span>)}</div>{!match.matchedSkills.length&&<p>No target skills detected in this version.</p>}</section><section><h4>Missing mentions</h4><div className="tags missing">{match.missingSkills.map(s=><span key={s}>{s}</span>)}</div><p className="muted small">{match.missingSkills.length?'Add a skill only if you can support it with real experience. Otherwise, use it as a learning goal.':'All identified target skills are mentioned. This does not verify proficiency.'}</p></section></div>}
    <h3>AI writing suggestions</h3>
    {ai.status==='OPENAI'?<><p className="muted small">AI advice does not affect the scores. Review wording and preserve your actual achievements.</p>{ai.suggestions.map((s,i)=><section className="resume-ai-item" key={i}><h4>{s.issue}</h4><p>{s.recommendation}</p></section>)}</>:<p className="muted">{ai.status==='NOT_REQUESTED'?'Not requested for this report. The local suggestions above are available without AI.':'AI suggestions were unavailable. Your local scores and suggestions were saved successfully.'}</p>}
    <details className="resume-method"><summary>What this report can and cannot check</summary><ul>{limitations.map(text=><li key={text}>{text}</li>)}</ul><p>Scoring method: {item.report.scorerVersion}. Section detection uses separate heading lines. Job match counts unique skill mentions equally; repeating a keyword adds no credit.</p></details>
  </div>;
}
function Comparison({items}) {
  const [a,b]=items, sameMethod=a.report.scorerVersion===b.report.scorerVersion,
    sameTarget=a.report.target.fingerprint===b.report.target.fingerprint;
  return <><p className="muted">Compare the report dates and targets below. Scores describe these saved versions.</p>
    {(!sameMethod||!sameTarget)&&<p className="resume-report-note">{!sameMethod?'These reports use different scoring methods; readiness scores are not directly comparable.':'These reports use different job targets; job-match scores are not directly comparable.'}</p>}
    <div className="resume-comparison-scroll"><table className="resume-comparison"><thead><tr><th>Check</th>{items.map(item=><th key={item.id}>{item.filename}<small>{datetime(item.createdAt)}</small></th>)}</tr></thead><tbody>
      <tr><th>Target</th>{items.map(x=><td key={x.id}>{x.report.target.title}</td>)}</tr>
      <tr><th>Estimated readiness</th>{items.map(x=><td key={x.id}>{x.report.readiness.score}/100</td>)}</tr>
      <tr><th>Job skill match</th>{items.map(x=><td key={x.id}>{x.report.match.score==null?'Not scored':`${x.report.match.score}/100`}</td>)}</tr>
      {a.report.readiness.checks.map(check=><tr key={check.key}><th>{check.title}</th>{items.map(x=>{const c=x.report.readiness.checks.find(c=>c.key===check.key);return <td key={x.id}>{c?`${c.earned}/${c.possible}`:'Not checked'}</td>;})}</tr>)}
      <tr><th>Missing skill mentions</th>{items.map(x=><td key={x.id}>{x.report.match.requiredSkills.length?(x.report.match.missingSkills.join(', ')||'None'):'No target skills identified'}</td>)}</tr>
    </tbody></table></div></>;
}

export function ResumesPage() {
  const state=useLoad('/student/resumes'),profile=useLoad('/student/profile'),jobs=useLoad('/jobs');
  const [page,setPage]=useState(0),history=useLoad(`/student/resume-reports?page=${page}`),toast=useToast();
  const [busy,setBusy]=useState(false),[generating,setGenerating]=useState(false),[resumeId,setResumeId]=useState(''),
    [mode,setMode]=useState('GENERAL'),[selected,setSelected]=useState(null),[compare,setCompare]=useState([]),
    [comparing,setComparing]=useState(false),[removing,setRemoving]=useState(null),[preview,setPreview]=useState(null);
  const activeJobs=(jobs.data||[]).filter(j=>j.status==='ACTIVE');
  async function upload(e) {
    e.preventDefault();const form=e.currentTarget;setBusy(true);
    try {const result=await api('/student/resumes',{method:'POST',form:new FormData(form)});setResumeId(String(result.id));form.reset();toast('Resume uploaded as a new version');state.reload();}
    catch(e){toast(e.message,'error');}finally{setBusy(false);}
  }
  async function addSkills(skills) {
    const p=profile.data?.profile;if(!p)return;
    try {await api('/student/profile',{method:'PUT',body:{...p,skills:[...new Set([...p.skills,...skills])]}});toast('Detected skills added. Review them in your profile.');profile.reload();}
    catch(e){toast(e.message,'error');}
  }
  async function analyse(e) {
    e.preventDefault();setGenerating(true);const data=new FormData(e.currentTarget);
    const chosen=resumeId||String(state.data?.[0]?.id||'');
    const body={aiSuggestions:data.get('aiSuggestions')==='on'};
    if(mode==='OPENING')body.jobId=Number(data.get('jobId'));
    if(mode==='PASTED'){body.targetTitle=data.get('targetTitle');body.jobDescription=data.get('jobDescription');}
    try {const result=await api(`/student/resumes/${chosen}/reports`,{method:'POST',body});setSelected(result);setPage(0);history.reload();toast('Resume report saved');}
    catch(e){toast(e.message,'error');}finally{setGenerating(false);}
  }
  async function remove() {
    setBusy(true);
    try {
      await api(removing.kind==='resume'?`/student/resumes/${removing.id}`:`/student/resume-reports/${removing.id}`,{method:'DELETE'});
      if(removing.kind==='resume'){state.reload();if(String(removing.id)===resumeId)setResumeId('');}
      else {history.reload();setCompare(items=>items.filter(x=>x.id!==removing.id));}
      toast(removing.kind==='resume'?'Resume version deleted':'Report deleted');setRemoving(null);
    }catch(e){toast(e.message,'error');}finally{setBusy(false);}
  }
  async function showText() {
    setBusy(true);
    try {setPreview(await api(`/student/resumes/${resumeId||state.data[0].id}/text`));}
    catch(e){toast(e.message,'error');}finally{setBusy(false);}
  }
  return <><PageTitle title="Your story, version by version." eyebrow="RESUME STUDIO">Upload, review and improve your resume for the opportunity ahead.</PageTitle>
    <section className="panel upload-panel"><Upload size={28}/><div><h2>Upload a resume</h2><p>Text-based PDF, DOCX or TXT · up to 5 MB. Detected skills need your review.</p><form onSubmit={upload}><input type="file" name="file" accept=".pdf,.docx,.txt" required aria-label="Choose resume file"/><Button busy={busy} type="submit">Upload new version</Button></form></div></section>
    <LoadState state={state}>{items=>items.length?<>
      <section className="panel resume-analyser"><div className="panel-head"><div><span className="eyebrow">A CLEARER NEXT DRAFT</span><h2>ATS resume review</h2></div><FileCheck2 size={26}/></div><p>Check resume content and job skill coverage. Every score comes with an explanation.</p>
        <form onSubmit={analyse}><div className="form-grid"><Field label="Resume version"><select aria-label="Resume version" value={resumeId||String(items[0].id)} onChange={e=>setResumeId(e.target.value)} disabled={generating} required>{items.map(v=><option value={v.id} key={v.id}>{v.filename} · {datetime(v.createdAt)}</option>)}</select></Field>
          <Field label="Review target"><select aria-label="Review target" value={mode} onChange={e=>setMode(e.target.value)} disabled={generating}><option value="GENERAL">General resume readiness</option><option value="OPENING">Choose an opening</option><option value="PASTED">Paste a job description</option></select></Field>
          {mode==='OPENING'&&<Field label="Target opening" wide>{jobs.error?<p role="alert">{jobs.error} <button type="button" className="text-button" onClick={jobs.reload}>Try again</button></p>:<select aria-label="Target opening" name="jobId" required disabled={generating||jobs.loading}><option value="">{jobs.loading?'Loading openings…':activeJobs.length?'Select an opening':'No active openings — paste a description instead'}</option>{activeJobs.map(j=><option key={j.id} value={j.id}>{j.title} · {j.companyName}</option>)}</select>}</Field>}
          {mode==='PASTED'&&<><Field label="Target role (optional)" name="targetTitle" maxLength={150} disabled={generating} wide/><Field label="Job description" wide><textarea name="jobDescription" required minLength={30} maxLength={12000} rows={6} disabled={generating} placeholder="Paste the role description and required skills. Matching identifies skills from a limited dictionary."/></Field></>}
        </div><label className="check-field resume-ai-choice"><input name="aiSuggestions" type="checkbox" disabled={generating}/><span>Include optional AI writing suggestions. I agree to send project/experience text and target details to the configured OpenAI service. Common email, phone and URL patterns are masked, but other personal details may remain. This requires provider configuration and may incur API charges.</span></label>
        <div className="row-actions"><Button type="submit" busy={generating} disabled={busy||(mode==='OPENING'&&(jobs.loading||!!jobs.error||!activeJobs.length))}>Analyse resume <ArrowUpRight size={16}/></Button><Button type="button" variant="secondary" busy={busy} disabled={generating} onClick={showText}><Eye size={16}/>Preview extracted text</Button></div>
        <p className="muted small">Local scoring works without AI. Scores estimate content readiness, not employer decisions. Layout checks and OCR are not included.</p></form>
      </section>
      <div className="section-heading"><h2>Your resume versions</h2></div><div className="resume-list">{items.map((v,index)=><section className="panel" key={v.id}><div className="panel-head"><div><Badge tone={index===0?'green':''}>{index===0?'Latest version':'Previous version'}</Badge><h3>{v.filename}</h3><p className="muted small">{datetime(v.createdAt)} · {Math.round(v.sizeBytes/1024)} KB</p></div><div className="row-actions"><Button variant="secondary small" onClick={()=>download(`/resumes/${v.id}/download`,v.filename).catch(e=>toast(e.message,'error'))}><Download size={15}/>Download</Button><Button variant="ghost small" aria-label={`Delete ${v.filename}`} onClick={()=>setRemoving({...v,kind:'resume'})}><Trash2 size={15}/></Button></div></div><div className="tags">{v.detectedSkills.map(s=><span key={s}>{s}</span>)}</div>{v.detectedSkills.length>0&&<Button variant="secondary small" disabled={!profile.data} onClick={()=>addSkills(v.detectedSkills)}>Add detected skills to profile</Button>}</section>)}</div>
    </>:<Empty title="Give your profile a resume.">Upload a version to review its readiness and make it available with future applications.</Empty>}</LoadState>
    <section className="panel resume-history"><div className="panel-head"><div><span className="eyebrow">SEE YOUR PROGRESS</span><h2>Saved resume reports</h2></div><Button variant="secondary small" disabled={compare.length!==2} onClick={()=>setComparing(true)}><GitCompareArrows size={16}/>Compare selected reports</Button></div>
      <p className="muted small">Select two reports to compare revisions. Saved reports remain available if a resume version is deleted.</p>
      <LoadState state={history}>{items=>items.length?<div className="resume-report-list">{items.map(item=><article key={item.id}><label className="check-field"><input type="checkbox" aria-label={`Compare report ${item.id} for ${item.filename}`} checked={compare.some(x=>x.id===item.id)} disabled={compare.length===2&&!compare.some(x=>x.id===item.id)} onChange={e=>setCompare(current=>e.target.checked?[...current,item]:current.filter(x=>x.id!==item.id))}/><span><strong>{item.filename}</strong><small>{datetime(item.createdAt)} · {item.report.target.title}</small></span></label><div className="row-actions"><Badge>{item.report.readiness.score}/100 readiness</Badge><Button variant="secondary small" onClick={()=>setSelected(item)}>View report</Button><Button variant="ghost small" aria-label={`Delete report ${item.id}`} onClick={()=>setRemoving({...item,kind:'report'})}><Trash2 size={15}/></Button></div></article>)}</div>:<Empty title={page?'No more reports':'Your first review starts here.'}>{page?'Go back to earlier reports.':'Analyse a resume to save an explained report.'}</Empty>}</LoadState>
      <div className="row-actions resume-pagination"><Button variant="ghost small" disabled={page===0||history.loading} onClick={()=>setPage(p=>p-1)}>Previous reports</Button><span className="muted small">Page {page+1}</span><Button variant="ghost small" disabled={history.loading||!history.data||history.data.length<20} onClick={()=>setPage(p=>p+1)}>Next reports</Button></div>
    </section>
    {selected&&<Modal title="Your resume report" wide onClose={()=>setSelected(null)}><Report item={selected}/></Modal>}
    {comparing&&compare.length===2&&<Modal title="Compare resume reports" wide onClose={()=>setComparing(false)}><Comparison items={compare}/></Modal>}
    {preview&&<Modal title="Extracted resume text" wide onClose={()=>setPreview(null)}><p>{preview.filename}</p><p className="muted">Check that important content is present and in the right order. This is what the local content reviewer reads.</p><pre className="resume-text-preview">{preview.text}</pre></Modal>}
    {removing&&<Modal title={removing.kind==='resume'?'Delete resume version?':'Delete saved report?'} onClose={()=>!busy&&setRemoving(null)}><p>{removing.kind==='resume'?`Delete ${removing.filename}? Saved reports will remain. Resumes attached to applications must be retained.`:`Delete the report for ${removing.filename}? This will not delete the resume.`}</p><div className="row-actions"><Button variant="secondary" disabled={busy} onClick={()=>setRemoving(null)}>Cancel</Button><Button busy={busy} onClick={remove}>{removing.kind==='resume'?'Delete resume':'Delete report'}</Button></div></Modal>}
  </>;
}
