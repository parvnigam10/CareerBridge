export const token=()=>localStorage.getItem('careerbridge.token');
export async function api(path,{method='GET',body,form,raw=false}={}) {
  const headers={};if(token())headers.Authorization=`Bearer ${token()}`;
  if(body!==undefined)headers['Content-Type']='application/json';
  let response;try{response=await fetch(`/api${path}`,{method,headers,body:form|| (body!==undefined?JSON.stringify(body):undefined)});}catch{throw new Error('Cannot reach the backend. Check that it is running on port 8081.');}
  if(!response.ok){const data=await response.json().catch(()=>({}));if(response.status===401&&!path.startsWith('/auth/login'))window.dispatchEvent(new Event('careerbridge:expired'));throw new Error(data.message||data.detail|| (response.status===403?'Your account cannot perform this action.':`Request failed (${response.status})`));}
  return raw?response:response.status===204?null:response.json();
}
export async function download(path,name){const response=await api(path,{raw:true});const disposition=response.headers.get('Content-Disposition')||'';let filename=disposition.match(/filename="([^"]+)"/)?.[1];const encoded=disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];if(encoded)try{filename=decodeURIComponent(encoded);}catch{}const url=URL.createObjectURL(await response.blob());const a=document.createElement('a');a.href=url;a.download=name||filename||'download';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
export const label=s=>String(s||'').replaceAll('_',' ').toLowerCase().replace(/\b\w/g,c=>c.toUpperCase());
export const date=s=>s?new Date(s).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'}):'—';
export const datetime=s=>s?new Date(s).toLocaleString('en-IN',{day:'numeric',month:'short',hour:'numeric',minute:'2-digit'}):'—';
export const money=(n,type)=>n?`${new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(n)}${type==='INTERNSHIP'?' / month':' / year'}`:'Not disclosed';
