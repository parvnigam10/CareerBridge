import {Link} from 'react-router-dom';
import {CheckCircle2,AlertCircle,Info} from 'lucide-react';

const titles={ELIGIBLE:'Eligible to apply',NOT_ELIGIBLE:'Not eligible',DETAILS_REQUIRED:'Details needed'};
export function EligibilityBadge({report}) {
  if(!report)return null;
  const Icon=report.eligible?CheckCircle2:report.status==='NOT_ELIGIBLE'?AlertCircle:Info;
  return <span className={`eligibility-badge ${report.status.toLowerCase()}`}><Icon size={13}/>{titles[report.status]}</span>;
}

export function EligibilityReport({report,title='Your eligibility',student=false,compact=false}) {
  if(!report)return null;
  const needsDetails=report.checks.some(c=>c.status==='MISSING'||c.status==='REVIEW_REQUIRED');
  const checks=<ul className="eligibility-checks">{report.checks.map(c=><li key={c.key} className={c.status.toLowerCase()}>
    <div><strong>{c.label}</strong><span className="eligibility-check-status">{c.status==='MET'?'Met':c.status==='NOT_MET'?'Not met':c.status==='MISSING'?'Missing details':'Check result format'}</span></div>
    <p>Required: {c.requirement} · Your record: {c.actual}</p><small>{c.message}</small>
  </li>)}</ul>;
  if(compact)return <div className="eligibility-compact"><EligibilityBadge report={report}/>{report.checks.length>0&&<details><summary>View eligibility checks</summary>{checks}</details>}</div>;
  return <section className="eligibility-report"><div className="panel-head"><h3>{title}</h3><EligibilityBadge report={report}/></div>
    {report.checks.length?checks:<p className="muted small">This opening has no additional eligibility restrictions.</p>}
    {student&&needsDetails&&<Link className="eligibility-profile-link" to="/profile">Update academic details →</Link>}
  </section>;
}

export function EligibilityRequirements({job,compact=false}) {
  const minimum=(value,unit)=>value>0?`At least ${value}${unit}`:'No minimum';
  const rows=[['10th',minimum(job.minTenthPercentage,'%')],['12th / diploma',minimum(job.minSecondaryPercentage,'%')],['College CGPA',minimum(job.minCgpa,' / 10')],
    ['Branches',job.allowedBranches?.length?job.allowedBranches.join(', '):'All branches'],['Graduation years',job.allowedGraduationYears?.length?job.allowedGraduationYears.join(', '):'All years'],
    ['Active backlogs',job.maxActiveBacklogs==null?'No limit':`At most ${job.maxActiveBacklogs}`],['Experience',minimum(job.minExperience,' years')]];
  if(job.universityId!=null)rows.push(['University','Campus-specific opening']);
  if(compact)return <div className="eligibility-requirements-compact">{rows.filter(([key,value])=>!['No minimum','No limit','All branches','All years'].includes(value)).map(([key,value])=><span key={key}><strong>{key}:</strong> {value}</span>)}</div>;
  return <section className="eligibility-requirements"><h3>Eligibility requirements</h3><dl>{rows.map(([key,value])=><div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>
    {(job.minTenthPercentage>0||job.minSecondaryPercentage>0)&&<p className="muted small">School cutoffs use institution-reported percentages. CGPA and letter grades are not automatically converted.</p>}
  </section>;
}
