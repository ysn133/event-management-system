const assert = require('node:assert/strict');
const { test } = require('node:test');
const createApp = require('../server');

async function fixture(t, authorize = async () => ({ id: 1 })) {
  let queries = 0, checks = 0;
  const server = createApp({ query: async () => { queries++; return { rows: [] }; } }, {
    authorize: async token => { checks++; return authorize(token); },
  }).listen(0);
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  return { queries: () => queries, checks: () => checks,
    call: (path, method = 'GET', token) => fetch(`http://127.0.0.1:${server.address().port}${path}`, {
      method, headers: token ? { Authorization: token } : {},
    }) };
}

test('all provider directory, service and contract routes reject anonymous access before database reads', async t => {
  const f = await fixture(t);
  for (const [method, path] of [
    ['GET', '/api/prestataires'], ['POST', '/api/prestataires'],
    ['GET', '/api/prestataires/1'], ['PUT', '/api/prestataires/1'], ['DELETE', '/api/prestataires/1'],
    ['GET', '/api/prestataires/recherche'], ['GET', '/api/prestataires/utilisations'],
    ['GET', '/api/prestataires/1/services'], ['POST', '/api/prestataires/1/services'],
    ['PUT', '/api/prestataires/1/services/2'], ['DELETE', '/api/prestataires/1/services/2'],
    ['POST', '/api/contrats-prestataire'], ['PUT', '/api/contrats-prestataire/1'],
    ['GET', '/api/contrats-prestataire/evenement/1'], ['POST', '/api/contrats-prestataire/1/synchroniser'],
  ]) assert.equal((await f.call(path, method)).status, 401, `${method} ${path}`);
  assert.equal(f.queries(), 0); assert.equal(f.checks(), 0);
});

test('a bearer string is not sufficient: Auth must validate the session', async t => {
  const f = await fixture(t, async () => { const e = new Error('Invalid or expired JWT'); e.status = 401; throw e; });
  for (const token of ['Basic password', 'Bearer tampered', 'Bearer expired'])
    assert.equal((await f.call('/api/prestataires', 'GET', token)).status, 401);
  assert.equal(f.queries(), 0);
});

test('Auth outages and invalid identity responses fail closed without logging the user out', async t => {
  const f = await fixture(t, async () => { throw new Error('Network failure'); });
  assert.equal((await f.call('/api/prestataires', 'GET', 'Bearer valid')).status, 503);
  assert.equal(f.queries(), 0);
  const malformed = await fixture(t, async () => ({}));
  assert.equal((await malformed.call('/api/prestataires', 'GET', 'Bearer valid')).status, 503);
  assert.equal(malformed.queries(), 0);
});

test('health and preflight remain public; validated API requests succeed', async t => {
  const f = await fixture(t);
  assert.equal((await f.call('/health')).status, 200);
  assert.equal((await f.call('/api/prestataires', 'OPTIONS')).status, 204);
  assert.equal(f.checks(), 0);
  assert.equal((await f.call('/api/prestataires', 'GET', 'Bearer valid')).status, 200);
  assert.equal(f.checks(), 1); assert.equal(f.queries(), 1);
});
