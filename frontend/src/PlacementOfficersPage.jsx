import {useState} from 'react';
import {Link} from 'react-router-dom';
import {api} from './api';
import {PageTitle,Button,Field,Badge,Empty,LoadState,useLoad,useToast} from './ui';

export default function PlacementOfficersPage(){
  const accounts=useLoad('/admin/users'), universities=useLoad('/admin/universities'), toast=useToast();
  const [busy,setBusy]=useState(false);

  async function createOfficer(event){
    event.preventDefault();
    const form=event.currentTarget, fields=Object.fromEntries(new FormData(form));
    setBusy(true);
    try{
      await api('/admin/users',{method:'POST',body:{
        name:fields.name,email:fields.email,password:fields.password,
        role:'PLACEMENT_OFFICER',universityId:Number(fields.universityId),department:fields.department
      }});
      form.reset();
      accounts.reload();
      toast('Placement officer account created');
    }catch(error){toast(error.message,'error');}
    finally{setBusy(false);}
  }

  async function changeAccess(officer){
    try{
      await api(`/admin/users/${officer.id}`,{method:'PATCH',body:{enabled:!officer.enabled}});
      accounts.reload();
      toast('Placement officer access updated');
    }catch(error){toast(error.message,'error');}
  }

  return <>
    <PageTitle title="Placement officers" eyebrow="ADMINISTRATION">
      Create university staff accounts and manage their access. Each officer is assigned to one university.
    </PageTitle>
    <section className="panel narrow">
      <h2>Create Placement Officer</h2>
      <p className="muted">The officer will manage students, academic reviews, campus drives and placement reports for the selected university.</p>
      <LoadState state={universities}>{schools=>schools.length?<form onSubmit={createOfficer}>
        <Field label="Full name" name="name" required maxLength={80} autoComplete="name"/>
        <Field label="Official email" name="email" type="email" required maxLength={254} autoComplete="email"/>
        <Field label="University"><select aria-label="University" name="universityId" required defaultValue="">
          <option value="" disabled>Select a university</option>
          {schools.map(school=><option key={school.id} value={school.id}>{school.name}</option>)}
        </select></Field>
        <Field label="Department (optional)" name="department" maxLength={100}/>
        <Field label="Temporary password" name="password" type="password" required minLength={10} maxLength={72} autoComplete="new-password"/>
        <p className="muted small">Use 10–72 characters including uppercase, lowercase and a number. The officer can change the password in Settings.</p>
        <Button type="submit" busy={busy}>Create Placement Officer</Button>
      </form>:<Empty title="Add a university first"><Link to="/universities">Open Universities</Link> to add the university this officer will manage.</Empty>}</LoadState>
    </section>
    <section className="panel">
      <h2>Existing placement officers</h2>
      <LoadState state={accounts}>{items=>{
        const officers=items.filter(account=>account.role==='PLACEMENT_OFFICER');
        return officers.length?<div className="table-wrap"><table>
          <thead><tr><th>Officer</th><th>University</th><th>Department</th><th>Access</th><th>Action</th></tr></thead>
          <tbody>{officers.map(officer=><tr key={officer.id}>
            <td><strong>{officer.name}</strong><small>{officer.email}</small></td>
            <td>{universities.data?.find(school=>school.id===officer.universityId)?.name||`University #${officer.universityId}`}</td>
            <td>{officer.department||'—'}</td>
            <td><Badge tone={officer.enabled?'green':'red'}>{officer.enabled?'Enabled':'Disabled'}</Badge></td>
            <td><Button variant="secondary small" onClick={()=>changeAccess(officer)}>{officer.enabled?'Disable':'Enable'}</Button></td>
          </tr>)}</tbody>
        </table></div>:<Empty title="No placement officers yet">Create the first officer using the form above.</Empty>;
      }}</LoadState>
    </section>
  </>;
}
