import {useEffect,useState} from 'react';
import {Link,useLocation} from 'react-router-dom';
import {ArrowRight,CheckCircle2,Inbox,KeyRound,Mail} from 'lucide-react';
import {Brand} from './PublicPages';
import {api} from './api';
import {Button,Field} from './ui';

function RecoveryLayout({complete=false,children,title,intro}){
  return <div className="verification-page"><header className="verification-header"><Brand/><Link className="verification-home" to="/">Back to home</Link></header>
    <main className="verification-card" aria-labelledby="recovery-title"><div className={`verification-icon ${complete?'is-verified':''}`}>{complete?<CheckCircle2 size={30}/>:<KeyRound size={30}/>}</div>
      <span className="eyebrow">PASSWORD RECOVERY</span><h1 id="recovery-title">{title}</h1><p className="verification-intro">{intro}</p>{children}
    </main><p className="verification-footer">CareerBridge · Prepare. Connect. Grow.</p></div>;
}

export function ForgotPasswordPage(){
  const location=useLocation();
  const [email,setEmail]=useState(new URLSearchParams(location.search).get('email')||'');
  const [busy,setBusy]=useState(false),[sent,setSent]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState(''),[deadline,setDeadline]=useState(0),[remaining,setRemaining]=useState(0),[inbox,setInbox]=useState(null);
  useEffect(()=>{let active=true;api('/public/email-testing').then(data=>{if(active&&data.enabled)setInbox(data.inboxUrl);}).catch(()=>{});return()=>{active=false;};},[]);
  useEffect(()=>{if(!deadline)return;const tick=()=>setRemaining(Math.max(0,Math.ceil((deadline-Date.now())/1000)));tick();const id=setInterval(tick,1000);return()=>clearInterval(id);},[deadline]);
  async function submit(e){e.preventDefault();setBusy(true);setError('');setMessage('');try{const result=await api('/auth/forgot-password',{method:'POST',body:{email}});setSent(true);setMessage(result.message);setDeadline(Date.now()+60000);}catch(e){setError(e.message);}finally{setBusy(false);}}
  return <RecoveryLayout title={sent?'Check your inbox':'Forgot your password?'} intro="Enter your CareerBridge email. We’ll send a single-use reset link that expires after 30 minutes.">
    {error&&<div className="verification-feedback error" role="alert">{error}</div>}{message&&<div className="verification-feedback success" role="status">{message}</div>}
    <form className="recovery-form" onSubmit={submit}><Field label="Email address" name="email" type="email" required maxLength={254} autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)}/>
      <Button variant="full" busy={busy} type="submit" disabled={remaining>0}>{remaining>0?`Send again in ${remaining}s`:sent?'Send another reset link':'Send reset link'} <ArrowRight size={17}/></Button>
    </form>
    {sent&&inbox&&<section className="verification-inbox" aria-label="Local test inbox"><div className="verification-inbox-heading"><Inbox size={19}/><strong>Your local test inbox</strong><span>TEST MODE</span></div>
      <p>Open your reset message here and continue in the same tab. Refresh the inbox if the message is still arriving.</p><a className="button secondary full" target="_self" href={inbox+'?flow=reset&email='+encodeURIComponent(email)}>Open test inbox <ArrowRight size={17}/></a>
    </section>}
    <p className="verification-help">If you don’t see a message, check spam. Requesting another link replaces the previous one.</p><Link className="verification-signin" to="/login">Back to sign in</Link>
  </RecoveryLayout>;
}

export function ResetPasswordPage(){
  const [token]=useState(()=>new URLSearchParams(window.location.hash.slice(1)).get('token')||'');
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[complete,setComplete]=useState(false);
  async function submit(e){e.preventDefault();setError('');const fields=Object.fromEntries(new FormData(e.currentTarget));if(fields.newPassword!==fields.confirmPassword){setError('The passwords do not match.');return;}
    setBusy(true);try{await api('/auth/reset-password',{method:'POST',body:{token,...fields}});window.dispatchEvent(new Event('careerbridge:expired'));setComplete(true);window.history.replaceState(null,'','/reset-password');}catch(e){setError(e.message);}finally{setBusy(false);}}
  return <RecoveryLayout complete={complete} title={complete?'Password changed':token?'Choose a new password':'You need a reset link'} intro={complete?'Your previous login sessions have ended. Sign in with your new password.':token?'Use a password different from your current one. Your email, profile and application data will stay the same.':'Request a password-reset email, then open its link to continue.'}>
    {error&&<div className="verification-feedback error" role="alert">{error}</div>}
    {complete?<Link className="button full" to="/login">Continue to sign in <ArrowRight size={17}/></Link>:token?<>
      <form className="recovery-form" onSubmit={submit}><Field label="New password" name="newPassword" type="password" required minLength={10} maxLength={72} autoComplete="new-password"/>
        <Field label="Confirm new password" name="confirmPassword" type="password" required minLength={10} maxLength={72} autoComplete="new-password"/>
        <p className="verification-help recovery-password-help">Use 10–72 characters including uppercase, lowercase and a number.</p><Button variant="full" type="submit" busy={busy}>Save new password <ArrowRight size={17}/></Button>
      </form><Link className="verification-signin" to="/forgot-password">Request a new reset link</Link>
    </>:<Link className="button full" to="/forgot-password">Request a reset link <Mail size={17}/></Link>}
  </RecoveryLayout>;
}
