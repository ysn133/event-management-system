import assert from 'node:assert/strict'

const base = (process.env.GATEWAY_URL || 'http://localhost:8081').replace(/\/+$/, '')
const origin = process.env.FRONTEND_ORIGIN || 'http://localhost:5173'
let token
async function request(path, { method = 'GET', body, authenticated = true, headers = {} } = {}) {
  const response = await fetch(`${base}${path}`, {
    method, headers: { 'Content-Type': 'application/json', Origin: origin,
      ...(authenticated && token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000),
  })
  const raw = await response.text()
  let data
  try { data = JSON.parse(raw) } catch { data = raw }
  assert.equal(response.headers.get('access-control-allow-origin'), origin, 'Exactly one allowed CORS origin')
  return { response, data }
}

try {
  const health = await request('/health', { authenticated: false })
  assert.equal(health.response.status, 200)
  assert.equal(health.data.service, 'gateway')
  const login = await request('/api/auth/login', { method: 'POST', authenticated: false,
    body: { email: process.env.TEST_AUTH_EMAIL || 'admin@eventflow.local',
      password: process.env.TEST_AUTH_PASSWORD || 'EventFlow-dev-2026!' } })
  assert.equal(login.response.status, 200, 'Login is routed to Auth with its JSON body')
  token = login.data.accessToken
  assert.ok(token)
  console.log('PASS: gateway health and login')

  for (const [path, status] of [
    ['/api/auth/me', 200], ['/api/evenements', 200], ['/api/salles', 200],
    ['/api/prestataires', 200], ['/api/v1/invites?limit=1', 200],
    ['/api/budgets/999999999', 404], ['/api/depenses/999999999', 404],
    ['/api/reservations/evenement/999999999', 200],
    ['/api/contrats-prestataire/evenement/999999999', 200],
  ]) {
    assert.equal((await request(path, { authenticated: false })).response.status, 401, `JWT required: ${path}`)
    assert.equal((await request(path, { headers: { Authorization: 'Bearer invalid.jwt.token' } })).response.status, 401)
    assert.equal((await request(path)).response.status, status, `Valid JWT routed: ${path}`)
  }
  console.log('PASS: all service routes and JWT forwarding')

  const events = await request('/api/evenements?page=0&size=1&sort=id,asc')
  assert.equal(events.response.status, 200)
  assert.ok(events.data.content.length <= 1, 'Pagination query preserved')
  const providers = await request('/api/prestataires/recherche?nom=__gateway_no_such_provider__')
  assert.equal(providers.response.status, 200)
  assert.deepEqual(providers.data, [], 'Search query preserved')
  const guests = await request('/api/v1/invites?limit=1')
  assert.equal(guests.data.limit, 1)
  console.log('PASS: query strings and pagination')

  const preflight = await request('/api/budgets', { method: 'OPTIONS', authenticated: false,
    headers: { 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'authorization,content-type' } })
  assert.equal(preflight.response.status, 204)
  assert.match(preflight.response.headers.get('access-control-allow-headers'), /Authorization/)
  const deniedOrigin = await fetch(`${base}/health`, { headers: { Origin: 'https://untrusted.example' } })
  assert.equal(deniedOrigin.headers.get('access-control-allow-origin'), null)
  assert.equal((await request('/api/not-a-service')).response.status, 404)
  console.log('PASS: CORS preflight, untrusted origins and unknown routes')
  console.log('Gateway checks passed. No database records were modified.')
} catch (error) {
  console.error(`Gateway check failed: ${error.message}`)
  process.exitCode = 1
}
