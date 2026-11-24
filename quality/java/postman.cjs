// Builds a Postman v2.1 collection and runs it with Newman; secrets stay private.
const fs=require('fs'),path=require('path'),newman=require('newman');
const root=__dirname,dir=path.join(root,'results'),priv=path.join(root,'.runtime');
const c=JSON.parse(fs.readFileSync(path.join(priv,'settings.json')));
const items=[];
function request(name,method,url,status,body,checks='',auth='token'){
  const header=[{key:'Content-Type',value:'application/json'}];
  if(auth)header.push({key:'Authorization',value:auth==='token'?'Bearer {{token}}':'Bearer not.a.valid.jwt'});
  const r={method,header,url:'{{baseUrl}}'+url};
  if(body!==undefined)r.body={mode:'raw',raw:JSON.stringify(body).replace(/"(\{\{(?:eventId|budgetId|expenseId)\}\})"/g,'$1'),options:{raw:{language:'json'}}};
  items.push({name,request:r,event:[{listen:'test',script:{type:'text/javascript',exec:[
    `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`,
    status===401 ? `pm.test('Accès refusé sans contenu applicatif sensible',()=>pm.expect(pm.response.text()).not.to.include('passwordHash'));` : `pm.test('Réponse JSON', () => pm.response.to.be.json);`,checks
  ]}}]});
}
request('Santé de la passerelle','GET','/health',200,undefined,'',null);
for(const p of ['/auth/me','/evenements','/budgets/1'])request('JWT requis '+p,'GET','/api'+p,401,undefined,'',null);
request('JWT falsifié refusé','GET','/api/evenements',401,undefined,'','invalid');
request('Identifiants incorrects','POST','/api/auth/login',401,{email:'{{email}}',password:'wrong-password-only-for-quality'},'',null);
request('Connexion valide','POST','/api/auth/login',200,{email:'{{email}}',password:'{{password}}'},
  `const b=pm.response.json();pm.test('Jeton émis',()=>pm.expect(b.accessToken).to.be.a('string'));pm.environment.set('token',b.accessToken);pm.test('Pas de hash dans la réponse',()=>pm.expect(JSON.stringify(b)).not.to.include('passwordHash'));`,null);
request('Profil authentifié','GET','/api/auth/me',200,undefined,`pm.test('Identité attendue',()=>pm.expect(pm.response.json().email).to.equal(pm.environment.get('email')));`);
const event={organisateurId:1,titre:'Campagne Postman {{runId}}',description:'Données isolées de qualité Java',dateDebut:'2027-02-10T09:00:00Z',dateFin:'2027-02-10T18:00:00Z',lieu:'Agadir',statut:'PLANIFIE'};
request('Événement invalide','POST','/api/evenements',400,{...event,titre:''});
request('Période événement invalide','POST','/api/evenements',400,{...event,dateFin:'2027-02-09T18:00:00Z'});
request('Créer événement','POST','/api/evenements',201,event,`pm.environment.set('eventId',pm.response.json().id);pm.test('Location fournie',()=>pm.expect(pm.response.headers.get('Location')).to.include('/api/evenements/'));`);
request('Lire événement','GET','/api/evenements/{{eventId}}',200,undefined,`pm.test('État initial',()=>pm.expect(pm.response.json().statut).to.equal('PLANIFIE'));`);
request('Pagination événements','GET','/api/evenements?page=0&size=2&sort=id,asc',200,undefined,`const b=pm.response.json();pm.test('Page bornée',()=>pm.expect(b.content.length).to.be.at.most(2));pm.test('Total présent',()=>pm.expect(b.totalElements).to.be.at.least(1));`);
request('Événement absent','GET','/api/evenements/999999999',404);
request('Budget invalide','POST','/api/budgets',400,{evenementId:'{{eventId}}',montantTotal:-1});
request('Référence événement vérifiée par HTTP','POST','/api/budgets',404,{evenementId:999999999,montantTotal:50000,depassementAutorise:false});
request('Créer budget','POST','/api/budgets',201,{evenementId:'{{eventId}}',montantTotal:50000,depassementAutorise:false},`pm.environment.set('budgetId',pm.response.json().id);`);
request('Refuser second budget','POST','/api/budgets',409,{evenementId:'{{eventId}}',montantTotal:50000});
request('Retrouver budget par événement','GET','/api/budgets/evenement/{{eventId}}',200);
const expense={description:'Supports techniques Postman',montant:1250,categorie:'Technique',dateDepense:'2026-11-23',statut:'EN_ATTENTE',commentaire:'Scénario contrôlé'};
request('Dépense invalide','POST','/api/budgets/{{budgetId}}/depenses',400,{...expense,montant:0});
request('Créer engagement manuel','POST','/api/budgets/{{budgetId}}/depenses',201,expense,`pm.environment.set('expenseId',pm.response.json().id);`);
request('Refuser dépassement','POST','/api/budgets/{{budgetId}}/depenses',409,{...expense,montant:60000});
request('Créer prévision','POST','/api/budgets/{{budgetId}}/depenses',201,{...expense,description:'Prévision non engagée',montant:3000,statut:'PREVUE'});
request('Bilan : prévision distincte','GET','/api/budgets/{{budgetId}}/bilan',200,undefined,`const b=pm.response.json();pm.test('Engagé exact',()=>pm.expect(b.totalEngage).to.equal(1250));pm.test('Prévu exact',()=>pm.expect(b.totalPrevu).to.equal(3000));pm.test('Solde exact',()=>pm.expect(b.soldeRestant).to.equal(48750));`);
request('Marquer payée','PATCH','/api/depenses/{{expenseId}}/statut',200,{statut:'PAYEE'},`pm.test('Paiement enregistré',()=>pm.expect(pm.response.json().statut).to.equal('PAYEE'));`);
request('Préserver paiement à la suppression','DELETE','/api/depenses/{{expenseId}}',409);
request('Bilan : paiement non doublé','GET','/api/budgets/{{budgetId}}/bilan',200,undefined,`const b=pm.response.json();pm.test('Engagé inchangé',()=>pm.expect(b.totalEngage).to.equal(1250));pm.test('Payé exact',()=>pm.expect(b.totalPaye).to.equal(1250));`);
request('Refuser réduction sous engagé','PUT','/api/budgets/{{budgetId}}',409,{montantTotal:500,depassementAutorise:false});
request('Filtrer dépenses','GET','/api/budgets/{{budgetId}}/depenses?categorie=Technique',200,undefined,`pm.test('Collection',()=>pm.expect(pm.response.json()).to.be.an('array'));`);
request('Clôturer budget','POST','/api/budgets/{{budgetId}}/cloturer',200);
request('Refuser dépense sur budget clôturé','POST','/api/budgets/{{budgetId}}/depenses',409,expense);
request('Rouvrir budget','POST','/api/budgets/{{budgetId}}/rouvrir',200);
request('Paiement toujours présent','GET','/api/depenses/{{expenseId}}',200,undefined,`pm.test('PAYEE conservée',()=>pm.expect(pm.response.json().statut).to.equal('PAYEE'));`);
const collection={info:{name:'EventFlow — Qualité Java : Auth, Événements, Budget',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},item:items};
fs.writeFileSync(path.join(root,'eventflow-java.postman_collection.json'),JSON.stringify(collection,null,2));
const environment={values:[{key:'baseUrl',value:c.origin},{key:'email',value:c.email},{key:'password',value:c.password},{key:'runId',value:Date.now().toString()}]};
newman.run({collection,environment,reporters:['cli','htmlextra'],reporter:{htmlextra:{export:path.join(dir,'postman.html'),skipSensitiveData:true,browserTitle:'EventFlow — API Java',title:'Campagne API Java — résultats Newman'}}},(err,summary)=>{
  if(err){console.error(err);process.exitCode=1;return;}
  fs.writeFileSync(path.join(priv,'newman-raw.json'),JSON.stringify(summary));
  const redacted=JSON.stringify(summary).replaceAll(c.password,'[REDACTED]').replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_JWT]');
  fs.writeFileSync(path.join(dir,'postman.json'),redacted);
  const result={date:new Date().toISOString(),stats:summary.run.stats,failures:summary.run.failures.map(f=>({source:f.source?.name,error:f.error?.message}))};
  fs.writeFileSync(path.join(dir,'postman-summary.json'),JSON.stringify(result,null,2));
  process.exitCode=summary.run.failures.length?1:0;
});
