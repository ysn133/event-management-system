// Optional: Node 20+ on your workstation or VPS. Does not change business data.
// EVENTFLOW_URL=http://127.0.0.1:43871 node deploy/smoke-test.mjs
// Set EVENTFLOW_EMAIL and EVENTFLOW_PASSWORD to also test authenticated reads.
import assert from 'node:assert/strict'
const base = (process.env.EVENTFLOW_URL || 'http://127.0.0.1:43871').replace(/\/$/, '')
async function request(path, options) {
  return fetch(base + path, { ...options, signal: AbortSignal.timeout(15000), redirect: 'manual' })
}
assert.equal((await request('/health')).status, 200)
assert.match(await (await request('/')).text(), /<html/i)
assert.match(await (await request('/budgets')).text(), /<html/i) // SPA fallback
assert.equal((await request('/api/not-a-route')).status, 404)
const paths = ['/api/auth/me', '/api/evenements', '/api/budgets', '/api/salles', '/api/prestataires', '/api/v1/invites']
for (const path of paths) {
  assert.equal((await request(path)).status, 401, `Unauthenticated: ${path}`)
}
if (process.env.EVENTFLOW_EMAIL && process.env.EVENTFLOW_PASSWORD) {
  const response = await request('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: process.env.EVENTFLOW_EMAIL, password: process.env.EVENTFLOW_PASSWORD }),
  })
  assert.equal(response.status, 200, 'Login')
  const body = await response.json()
  const token = body.accessToken
  assert.ok(token, 'Login must return accessToken')
  for (const path of paths) {
    assert.equal((await request(path, { headers: { Authorization: `Bearer ${token}` } })).status, 200, `Authenticated: ${path}`)
  }
  console.log('PASS: login and authenticated reads across all six services')
} else {
  console.log('Authenticated reads skipped: provide EVENTFLOW_EMAIL and EVENTFLOW_PASSWORD')
}
console.log('PASS: frontend, SPA, health, API routing and anonymous request rejection')
