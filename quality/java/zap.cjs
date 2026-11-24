// Passive, authenticated HTTP analysis of an explicit local allow-list only.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const root=__dirname,c=JSON.parse(fs.readFileSync(path.join(root,'.runtime/settings.json'))),f=JSON.parse(fs.readFileSync(path.join(root,'.runtime/fixture.json')));
const key=crypto.randomBytes(24).toString('hex'),port=46391,image=process.env.QUALITY_ZAP_IMAGE||'ghcr.io/zaproxy/zaproxy:bare';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let owned=false;
const docker=(args)=>{const r=cp.spawnSync('docker',args,{encoding:'utf8',timeout:180000});if(r.status!==0)throw Error(r.stderr?.trim()||r.error?.message||'Docker command failed');return r.stdout.trim();};
(async()=>{
 docker(['image','inspect',image]); // Never initiate an unbounded download from this script.
 fs.writeFileSync(path.join(root,'.runtime/zap.properties'),`api.key=${key}\napi.addrs.addr.name=127.0.0.1\napi.addrs.addr.regex=false\n`,{mode:0o600});
 const existing=cp.spawnSync('docker',['inspect','eventflow-quality-zap'],{encoding:'utf8'});
 if(existing.status===0){
  const container=JSON.parse(existing.stdout)[0];
  if(container.Config.Labels?.['eventflow.purpose']!=='quality-campaign')throw Error('Existing ZAP container is outside this campaign');
  owned=true;
  if(!container.State.Running)docker(['start','eventflow-quality-zap']);
 }else {owned=true;docker(['run','-d','--name','eventflow-quality-zap','--network','host','--label','eventflow.purpose=quality-campaign','-v',`${path.join(root,'.runtime/zap.properties')}:/zap/quality.properties:ro`,image,'zap.sh','-daemon','-host','127.0.0.1','-port',String(port),'-configfile','/zap/quality.properties']);}
 const api=async(endpoint,params={})=>{const r=await fetch(`http://127.0.0.1:${port}/${endpoint}?`+new URLSearchParams({apikey:key,...params}),{signal:AbortSignal.timeout(30000)});if(!r.ok)throw Error('ZAP API HTTP '+r.status);return r.json();};
 let ready=false;for(let n=0;n<90;n++){try{await api('JSON/core/view/version/');ready=true;break;}catch{}await sleep(1000);}if(!ready)throw Error('ZAP not ready');
 const version=await api('JSON/core/view/version/'),scanners=await api('JSON/pscan/view/scanners/');
 if(!scanners.scanners?.length)throw Error('No passive rules installed; install pscanrules before scanning');
 const auth=await fetch(c.origin+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:c.email,password:c.password})});if(!auth.ok)throw Error('Quality login failed');
 const {accessToken}=await auth.json();
 const paths=['/','/api/auth/me','/api/evenements?page=0&size=10',`/api/evenements/${f.eventId}`,`/api/budgets/${f.budgetId}`,`/api/budgets/${f.budgetId}/bilan`,`/api/budgets/${f.budgetId}/depenses`];
 const requests=[];
 for(const url of paths){
  const request=`GET ${c.origin}${url} HTTP/1.1\r\nHost: 127.0.0.1:46380\r\n${url.startsWith('/api/')?'Authorization: Bearer '+accessToken+'\r\n':''}Connection: close\r\n\r\n`;
  const response=await api('JSON/core/action/sendRequest/',{request,followRedirects:'false'});
  const message=response.sendRequest?.[0];
  if(!message?.responseHeader?.includes('200'))throw Error('Expected 200 for '+url);
  requests.push({method:'GET',url:c.origin+url,status:200});
 }
 let drained=false;for(let n=0;n<90;n++){if(Number((await api('JSON/pscan/view/recordsToScan/')).recordsToScan)===0){drained=true;break;}await sleep(1000);}if(!drained)throw Error('Passive queue not drained');
 const alerts=await api('JSON/core/view/alerts/',{baseurl:c.origin,start:'0',count:'1000'});
 const redact=s=>s.replaceAll(accessToken,'[JWT MASQUÉ]').replaceAll(key,'[CLÉ ZAP MASQUÉE]');
 fs.writeFileSync(path.join(root,'results/zap-alerts.json'),redact(JSON.stringify(alerts,null,2)));
 const risks={};for(const a of alerts.alerts||[])risks[a.risk]=(risks[a.risk]||0)+1;
 const summary={date:new Date().toISOString(),version:version.version,mode:'Passive authenticated allow-list, no spider and no active attack',scanners:scanners.scanners.length,requests,risks,alerts:(alerts.alerts||[]).length};
 fs.writeFileSync(path.join(root,'results/zap-summary.json'),JSON.stringify(summary,null,2));
 const html=await fetch(`http://127.0.0.1:${port}/OTHER/core/other/htmlreport/?`+new URLSearchParams({apikey:key}));if(!html.ok)throw Error('Native HTML report unavailable');
 fs.writeFileSync(path.join(root,'results/zap.html'),redact(await html.text()));
 console.log(summary);
})().catch(e=>{console.error(e.message);process.exitCode=1;}).finally(()=>{if(!owned)return;try{
 const logs=cp.spawnSync('docker',['logs','eventflow-quality-zap'],{encoding:'utf8',timeout:30000});
 fs.writeFileSync(path.join(root,'.runtime/zap-daemon.log'),(logs.stdout||'')+(logs.stderr||''),{mode:0o600});
 docker(['stop','eventflow-quality-zap']);docker(['rm','eventflow-quality-zap']);
}catch{}});
