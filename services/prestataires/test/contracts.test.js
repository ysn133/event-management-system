const assert = require('node:assert/strict');
const { test } = require('node:test');
const createApp = require('../server');

const requestId = 'd45acece-0e7f-4d34-aef1-07a067fb13c2';
const input = { prestataireId: 12, evenementId: 20, prestation: 'Buffet', montant: 700,
  datePrestation: '2027-01-10T12:00:00.000Z', statut: 'CONFIRME', requestId };

async function fixture(t, budgetCall = async () => {}) {
  let contract;
  let budgetCalls = 0;
  const db = { release() {}, async query(sql, values = []) {
    if (['BEGIN','COMMIT','ROLLBACK'].includes(sql)) return { rows: [] };
    if (sql.startsWith('select id from prestataires')) return { rows: [{ id: 12 }] };
    if (sql.includes('where request_id=')) return { rows: contract?.request_id === values[0] ? [structuredClone(contract)] : [] };
    if (sql.startsWith('insert into contrats_prestataire')) {
      contract = { id: 41, prestataire_id: values[0], evenement_id: values[1], prestation: values[2], montant: values[3],
        date_prestation: values[4], statut: values[5], request_id: values[6], budget_sync_state: 'PENDING',
        budget_sync_version: 1, pending_payload: JSON.parse(values[7]) };
    } else if (sql.includes("set budget_sync_state='BLOCKED'")) {
      if (contract.budget_sync_version === values[1]) contract.budget_sync_state = 'BLOCKED';
    } else if (sql.includes("set budget_sync_state='PENDING'")) {
      contract.pending_payload = JSON.parse(values[1]); contract.budget_sync_state='PENDING'; contract.budget_sync_version++;
    } else if (sql.includes('set prestation=$2')) {
      Object.assign(contract, { prestation: values[1], montant: values[2], date_prestation: values[3], statut: values[4],
        budget_sync_state: 'SYNCED', pending_payload: null });
    }
    return { rows: contract ? [structuredClone(contract)] : [] };
  }};
  const clients = { authorize: async token => { assert.equal(token, 'Bearer test-user'); return { id: 1 }; }, event: async () => ({ id: 20 }),
    budget: async (row, payload) => { budgetCalls++; await budgetCall(row, payload, budgetCalls); } };
  const server = createApp({ connect: async () => db, query: db.query }, clients).listen(0);
  await new Promise(resolve => server.once('listening',resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const call = async (path, method='POST', body, authenticated=true) => {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/contrats-prestataire${path}`, {
      method, headers: { 'Content-Type':'application/json', ...(authenticated ? { Authorization:'Bearer test-user' } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, body: await response.json() };
  };
  return { call, contract: () => contract, budgetCalls: () => budgetCalls };
}

test('event contracts endpoint requires JWT and includes pending source records', async t => {
  const f = await fixture(t, async () => { const e = new Error('Timeout'); e.status = 503; throw e; });
  assert.equal((await f.call('/evenement/20', 'GET', undefined, false)).status, 401);
  await f.call('', 'POST', input);
  const result = await f.call('/evenement/20', 'GET');
  assert.equal(result.status, 200);
  assert.equal(result.body.length, 1);
  assert.equal(result.body[0].budgetSyncState, 'PENDING');
  assert.equal(result.body[0].evenementId, 20);
});

test('assignment and repeated creation charge Budget once', async t => {
  const f=await fixture(t);
  assert.equal((await f.call('', 'POST',input)).status,201);
  assert.equal((await f.call('', 'POST',input)).status,201);
  assert.equal(f.budgetCalls(),1);
  assert.equal(f.contract().budget_sync_state,'SYNCED');
});
test('an uncertain Budget response keeps a recoverable contract and reuses its version',async t => {
  const versions=[];
  const f=await fixture(t,async (row,payload,count) => {
    versions.push(row.budget_sync_version);
    if(count===1) { const error=new Error('Timeout'); error.status=503; throw error; }
  });
  const failed=await f.call('','POST',input);
  assert.equal(failed.status,503); assert.equal(failed.body.pending,true);
  assert.equal(f.contract().budget_sync_state,'PENDING');
  assert.equal((await f.call('/41/synchroniser')).status,200);
  assert.deepEqual(versions,[1,1]); assert.equal(f.contract().budget_sync_state,'SYNCED');
});
test('Budget rejection leaves an editable blocked proposal, not a confirmed assignment',async t => {
  const f=await fixture(t,async () => { const e=new Error('Solde insuffisant'); e.status=409; e.definitive=true; throw e; });
  assert.equal((await f.call('','POST',input)).status,409);
  assert.equal(f.contract().budget_sync_state,'BLOCKED');
});
test('updates and cancellation synchronize the same contract with increasing versions',async t => {
  const requests=[];
  const f=await fixture(t,async (row,payload) => requests.push([row.id,row.budget_sync_version,payload.statut]));
  await f.call('','POST',input);
  assert.equal((await f.call('/41','PUT',{...input,montant:800,version:1})).status,200);
  assert.equal((await f.call('/41','PUT',{...input,montant:800,statut:'ANNULE',version:2})).status,200);
  assert.deepEqual(requests,[[41,1,'CONFIRME'],[41,2,'CONFIRME'],[41,3,'ANNULE']]);
});
test('pending contracts cannot be edited and stale updates are rejected',async t => {
  let unavailable=true;
  const f=await fixture(t,async () => { if(unavailable) throw new Error('Unavailable'); });
  await f.call('','POST',input);
  assert.equal((await f.call('/41','PUT',{...input,montant:800,version:1})).status,409);
  unavailable=false; await f.call('/41/synchroniser');
  assert.equal((await f.call('/41','PUT',{...input,montant:800,version:0})).status,409);
});
test('rejects reuse of a creation key with a different amount',async t => {
  const f=await fixture(t); await f.call('','POST',input);
  assert.equal((await f.call('','POST',{...input,montant:800})).status,409);
  assert.equal(f.budgetCalls(),1);
});
test('requires authentication and validates positive monetary amounts',async t => {
  const f=await fixture(t);
  assert.equal((await f.call('','POST',input,false)).status,401);
  for(const montant of [-1,0,1.001]) assert.equal((await f.call('','POST',{...input,montant})).status,400);
  assert.equal(f.budgetCalls(),0);
});
