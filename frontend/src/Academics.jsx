import {useState} from 'react';
import {GraduationCap} from 'lucide-react';
import {api} from './api';
import {Badge,Button,Field,useToast} from './ui';

export function schoolResult(academics, prefix) {
  const a=academics||{}, type=a[`${prefix}ScoreType`], score=a[`${prefix}Score`];
  if(type==='GRADE')return a[`${prefix}Grade`]||'Not provided';
  if(score==null)return 'Not provided';
  return type==='PERCENTAGE'?`${score}%`:`${score} / ${a[`${prefix}Scale`]}`;
}

function SchoolFields({prefix,title,values,onChange}) {
  const type=values[`${prefix}ScoreType`]||'';
  return <fieldset className="academic-section"><legend>{title}</legend><div className="form-grid">
    <Field label={`${title} institution`} name={`${prefix}Institution`} value={values[`${prefix}Institution`]||''} onChange={onChange} maxLength={200} wide/>
    <Field label={`${title} board / awarding body`} name={`${prefix}Board`} value={values[`${prefix}Board`]||''} onChange={onChange} maxLength={100}/>
    <Field label={`${title} passing year`} name={`${prefix}Year`} type="number" min={1960} max={new Date().getFullYear()} value={values[`${prefix}Year`]??''} onChange={onChange}/>
    <Field label={`${title} result format`}><select name={`${prefix}ScoreType`} value={type} onChange={onChange}><option value="">Choose a format</option><option value="PERCENTAGE">Percentage</option><option value="CGPA">CGPA</option><option value="GRADE">Letter grade</option></select></Field>
    {type==='GRADE'?<Field label={`${title} letter grade`} name={`${prefix}Grade`} value={values[`${prefix}Grade`]||''} onChange={onChange} maxLength={30} placeholder="For example, A1"/>:type&&<Field label={`${title} ${type==='CGPA'?'CGPA':'percentage'}`} name={`${prefix}Score`} type="number" min={0} max={type==='CGPA'?(values[`${prefix}Scale`]||10):100} step="0.01" value={values[`${prefix}Score`]??''} onChange={onChange}/>}
    {type==='CGPA'&&<Field label={`${title} CGPA scale`} name={`${prefix}Scale`} type="number" min={0.01} max={10} step="0.01" value={values[`${prefix}Scale`]??''} onChange={onChange} required={values[`${prefix}Score`]!==''&&values[`${prefix}Score`]!=null} placeholder="For example, 10"/>}
  </div></fieldset>;
}

export function AcademicForm({data,onSaved}) {
  const [values,setValues]=useState({...data.profile.academics,department:data.user.department,graduationYear:data.user.graduationYear,cgpa:data.profile.cgpa});
  const [busy,setBusy]=useState(false),[error,setError]=useState('');const toast=useToast();
  function change(e) {
    const {name,value}=e.target;
    setValues(old=>{
      const next={...old,[name]:value};
      if(name.endsWith('ScoreType')){
        const prefix=name.replace('ScoreType','');
        next[`${prefix}Score`]='';next[`${prefix}Scale`]='';next[`${prefix}Grade`]='';
      }
      return next;
    });
    setError('');
  }
  async function save(e) {
    e.preventDefault();setBusy(true);setError('');
    const numeric=['tenthYear','tenthScore','tenthScale','secondaryYear','secondaryScore','secondaryScale','graduationYear','currentSemester','cgpa','activeBacklogs','clearedBacklogs','gapYears'];
    const body={...values};
    numeric.forEach(key=>{body[key]=values[key]==null||values[key]===''?null:Number(values[key]);});
    ['tenthScoreType','secondaryScoreType','secondaryQualification'].forEach(key=>{body[key]=values[key]||null;});
    try {await api('/student/academics',{method:'PUT',body});toast('Academic details saved');onSaved();}
    catch(e){setError(e.message);}finally{setBusy(false);}
  }
  return <section className="panel academic-panel"><div className="panel-head"><h2><GraduationCap size={21}/> Academic details</h2><Badge>Self-declared</Badge></div>
    <p className="muted small">Add your academic journey. You can save an incomplete profile and return later. Leave unknown results blank.</p>
    {data.university&&<p className="muted small">Registered university: {data.university.name}</p>}
    <form onSubmit={save}>
      <SchoolFields prefix="tenth" title="10th" values={values} onChange={change}/>
      <fieldset className="academic-section"><legend>12th / diploma qualification</legend><Field label="Qualification type"><select name="secondaryQualification" value={values.secondaryQualification||''} onChange={change}><option value="">Choose a qualification</option><option value="TWELFTH">12th</option><option value="DIPLOMA">Diploma</option></select></Field></fieldset>
      <SchoolFields prefix="secondary" title={values.secondaryQualification==='DIPLOMA'?'Diploma':'12th'} values={values} onChange={change}/>
      <fieldset className="academic-section"><legend>College</legend><div className="form-grid">
        <Field label="College institution" name="collegeInstitution" value={values.collegeInstitution||''} onChange={change} maxLength={200} wide/>
        <Field label="Degree" name="degree" value={values.degree||''} onChange={change} maxLength={140} placeholder="For example, B.Tech"/>
        <Field label="Branch / department" name="department" value={values.department||''} onChange={change} maxLength={100}/>
        <Field label="Graduation year" name="graduationYear" type="number" min={1960} max={2100} value={values.graduationYear??''} onChange={change}/>
        <Field label="Current semester" name="currentSemester" type="number" min={1} max={16} value={values.currentSemester??''} onChange={change}/>
        <Field label="College CGPA (out of 10)" name="cgpa" type="number" min={0} max={10} step="0.01" value={values.cgpa??''} onChange={change}/>
        <small className="wide muted">Use your institution’s reported CGPA on a 10-point scale. School results retain their own format.</small>
        <Field label="Active backlogs" name="activeBacklogs" type="number" min={0} max={50} value={values.activeBacklogs??''} onChange={change}/>
        <Field label="Cleared backlogs" name="clearedBacklogs" type="number" min={0} max={50} value={values.clearedBacklogs??''} onChange={change}/>
        <Field label="Education gap (years)" name="gapYears" type="number" min={0} max={20} value={values.gapYears??''} onChange={change}/>
      </div></fieldset>
      {error&&<p className="academic-error" role="alert">{error}</p>}
      <Button type="submit" busy={busy}>Save academic details</Button>
    </form>
  </section>;
}

export function AcademicSummary({profile,user,compact=false}) {
  const a=profile?.academics||{}, provided=value=>value==null||value===''?'Not provided':value;
  if(compact)return <div className="academic-compact"><span>10th <strong>{schoolResult(a,'tenth')}</strong></span><span>{a.secondaryQualification==='DIPLOMA'?'Diploma':'12th / diploma'} <strong>{schoolResult(a,'secondary')}</strong></span><span>CGPA <strong>{profile?.cgpa==null?'Not provided':`${profile.cgpa} / 10`}</strong></span></div>;
  const rows=[
    ['10th institution',a.tenthInstitution],['10th board',a.tenthBoard],['10th passing year',a.tenthYear],['10th result',schoolResult(a,'tenth')],
    ['Qualification',a.secondaryQualification==='DIPLOMA'?'Diploma':a.secondaryQualification==='TWELFTH'?'12th':null],['Secondary institution',a.secondaryInstitution],['Secondary board',a.secondaryBoard],['Secondary passing year',a.secondaryYear],['Secondary result',schoolResult(a,'secondary')],
    ['College',a.collegeInstitution],['Degree',a.degree],['Branch',user?.department],['Graduation year',user?.graduationYear],['Semester',a.currentSemester],['CGPA (out of 10)',profile?.cgpa],['Active backlogs',a.activeBacklogs],['Cleared backlogs',a.clearedBacklogs],['Gap years',a.gapYears]
  ];
  return <section className="academic-review"><div className="panel-head"><h3>Academic record</h3><Badge>Self-declared</Badge></div><dl className="academic-facts">{rows.map(([title,value])=><div key={title}><dt>{title}</dt><dd>{provided(value)}</dd></div>)}</dl></section>;
}
