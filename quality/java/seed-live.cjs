const fs=require('fs'),path=require('path');
const root=__dirname,c=JSON.parse(fs.readFileSync(path.join(root,'.runtime/settings.json')));
(async()=>{
 const login=await fetch(c.origin+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:c.email,password:c.password})});
 if(!login.ok)throw Error('Login failed '+login.status);
 const {accessToken}=await login.json();
 const call=async(url,body)=>{
   const r=await fetch(c.origin+url,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+accessToken},body:JSON.stringify(body)});
   if(!r.ok)throw Error(url+' '+r.status+' '+await r.text());return r.json();
 };
 const event=await call('/api/evenements',{organisateurId:1,titre:'Qualité Java — démonstration',description:'Environnement isolé : campagne qualité du 23 novembre 2026',dateDebut:'2027-03-15T09:00:00Z',dateFin:'2027-03-15T18:00:00Z',lieu:'Agadir',statut:'PLANIFIE'});
 const budget=await call('/api/budgets',{evenementId:event.id,montantTotal:50000,depassementAutorise:false});
 await call(`/api/budgets/${budget.id}/depenses`,{description:'Communication de démonstration',montant:2000,categorie:'Communication',dateDepense:'2026-11-23',statut:'EN_ATTENTE'});
 fs.writeFileSync(path.join(root,'.runtime/fixture.json'),JSON.stringify({eventId:event.id,budgetId:budget.id,eventTitle:event.titre,token:accessToken},null,2),{mode:0o600});
 fs.writeFileSync(path.join(root,'results/live-fixture.json'),JSON.stringify({eventId:event.id,budgetId:budget.id,eventTitle:event.titre,db:'eventflow_quality',nonJavaDependencies:'Explicit test doubles'},null,2));
 console.log('Isolated demonstration fixture ready:',event.id,budget.id);
})().catch(e=>{console.error(e);process.exitCode=1;});
