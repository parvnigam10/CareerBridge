import {useEffect,useState} from 'react';
import {Link,useLocation} from 'react-router-dom';
import {Mail,CheckCircle2,ArrowRight,Inbox} from 'lucide-react';
import {Brand} from './PublicPages';
import {api} from './api';
import {Button,Field} from './ui';

export function VerifyEmailPage(){
  const location=useLocation();
  const [token]=useState(()=>new URLSearchParams(window.location.hash.slice(1)).get('token')||'');
  const [email,setEmail]=useState(location.state?.email||new URLSearchParams(location.search).get('email')||'');
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState(''),[verified,setVerified]=useState(false),[cooldown,setCooldown]=useState(0),[remaining,setRemaining]=useState(0),[localInbox,setLocalInbox]=useState(null);
  useEffect(()=>{let active=true;api('/public/email-testing').then(result=>{if(active&&result.enabled)setLocalInbox(result.inboxUrl);}).catch(()=>{});return()=>{active=false;};},[]);
  useEffect(()=>{if(!cooldown){setRemaining(0);return;}const tick=()=>setRemaining(Math.max(0,Math.ceil((cooldown-Date.now())/1000)));tick();const id=setInterval(tick,1000);return()=>clearInterval(id);},[cooldown]);
  async function verify(){setBusy(true);setError('');setMessage('');try{const result=await api('/auth/verify-email',{method:'POST',body:{token}});setVerified(true);setMessage(result.message);window.history.replaceState(null,'','/verify-email');}catch(e){setError(e.message);}finally{setBusy(false);}}
  async function resend(e){e.preventDefault();setBusy(true);setError('');setMessage('');try{const result=await api('/auth/resend-verification',{method:'POST',body:{email}});setMessage(result.message);setCooldown(Date.now()+60000);}catch(e){setError(e.message);}finally{setBusy(false);}}
  const inboxUrl=localInbox?localInbox+(email?'?email='+encodeURIComponent(email):''):null;
  return <div className="verification-page">
    <header className="verification-header"><Brand/><Link className="verification-home" to="/">Back to home</Link></header>
    <main className="verification-card" aria-labelledby="verification-title">
      <div className={`verification-icon ${verified?'is-verified':''}`}>{verified?<CheckCircle2 size={30}/>:<Mail size={30}/>}</div>
      <span className="eyebrow">EMAIL VERIFICATION</span>
      <h1 id="verification-title">{verified?'You’re all set.':token?'Confirm your email':'Check your email'}</h1>
      <p className="verification-intro">{verified?'Your email is verified. Sign in to continue to your workspace.':token?'Confirm below to finish verifying the email address you registered with.':'We’ll send a confirmation link to your email address. It expires after 24 hours and works once.'}</p>
      {email&&!token&&!verified&&<p className="verification-address">{email}</p>}
      {error&&<div className="verification-feedback error" role="alert">{error}</div>}
      {message&&<div className="verification-feedback success" role="status">{message}</div>}
      {verified?<Link className="button full" to="/login">Continue to sign in <ArrowRight size={17}/></Link>:<>
        {token&&<div className="verification-primary"><Button variant="full" busy={busy} onClick={verify}>Confirm email address <ArrowRight size={17}/></Button></div>}
        {inboxUrl&&<section className="verification-inbox" aria-label="Local test inbox">
          <div className="verification-inbox-heading"><Inbox size={19}/><strong>Your local test inbox</strong><span>TEST MODE</span></div>
          <p>Test messages appear here instead of a real email account. Continue in this tab, then return to sign in.</p>
          <a href={inboxUrl} target="_self" className="button secondary full">Open test inbox <ArrowRight size={17}/></a>
        </section>}
        <details className="verification-resend" open={!token&&!inboxUrl}>
          <summary>Didn’t get the email?</summary>
          <form onSubmit={resend}><Field label="Email address" name="email" type="email" required maxLength={254} autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)}/>
            <Button variant="secondary full" type="submit" busy={busy} disabled={remaining>0}>{remaining>0?`Resend available in ${remaining}s`:'Resend verification email'}</Button>
            <p className="verification-help">Check spam too. A new email replaces your previous verification link.</p>
          </form>
        </details>
        <Link className="verification-signin" to="/login">Back to sign in</Link>
      </>}
    </main>
    <p className="verification-footer">CareerBridge · Prepare. Connect. Grow.</p>
  </div>;
}
