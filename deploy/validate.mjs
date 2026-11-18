// Validate deployment wiring without a daemon, real secrets or running containers.
// node deploy/validate.mjs
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const cwd = new URL('../', import.meta.url)
let executable = 'docker', args = ['compose']
try { execFileSync(executable, [...args, 'version'], { stdio: 'ignore' }) }
catch { executable = 'docker-compose'; args = [] }
const env = {
  ...process.env, COMPOSE_PROJECT_NAME: 'eventflow-config-validation',
  PUBLIC_ORIGIN: 'https://events.example.invalid', GATEWAY_PORT: '43871',
  DB_NAME: 'eventflow', DB_USER: 'eventflow',
  DB_PASSWORD: 'a'.repeat(64), JWT_SECRET: 'b'.repeat(64),
  CONTRACT_SERVICE_KEY: 'c'.repeat(64), ROOM_BUDGET_SERVICE_KEY: 'd'.repeat(64),
  BOOTSTRAP_USER_EMAIL: 'test@example.invalid', BOOTSTRAP_USER_PASSWORD: 'e'.repeat(32),
}
const model = JSON.parse(execFileSync(executable, [
  ...args, '--env-file', 'deploy/production.env.example', '-f', 'compose.production.yaml',
  'config', '--format', 'json',
], { cwd, env, encoding: 'utf8' }))
const services = model.services
assert.equal(Object.keys(services).length, 9)
for (const [name, service] of Object.entries(services)) {
  assert.equal(service.container_name, undefined, `${name}: no global name collision`)
  assert.equal(service.network_mode, undefined, `${name}: no host network`)
  if (name !== 'gateway') assert.ok(!service.ports?.length, `${name}: no host ports`)
  for (const dependency of Object.keys(service.depends_on || {})) assert.ok(services[dependency])
}
assert.equal(services.gateway.ports[0].host_ip, '127.0.0.1')
assert.equal(String(services.gateway.ports[0].published), '43871')
assert.equal(services.postgres.volumes[0].type, 'volume')
assert.equal(services.postgres.volumes[0].target, '/var/lib/postgresql')
const backends = ['auth-service', 'evenement-service', 'budget-service', 'salles-service', 'prestataires-service', 'invites-service']
for (const name of backends) {
  assert.equal(services[name].depends_on.migrate.condition, 'service_completed_successfully')
  assert.equal(services.gateway.depends_on[name].condition, 'service_healthy')
  for (const [key, value] of Object.entries(services[name].environment)) {
    if (key.endsWith('SERVICE_URL')) assert.ok(services[new URL(value).hostname], `${name}.${key}: Docker hostname`)
  }
}
assert.equal(services.budget_service, undefined)
assert.equal(services['invites-service'].environment.APP_ENV, 'production')
assert.equal(services['invites-service'].environment.NOTIFICATION_BACKEND, 'none')
assert.equal(services['invites-service'].environment.AUTH_ENABLED, 'true')
assert.equal(services['budget-service'].environment.CONTRACT_SERVICE_KEY, services['prestataires-service'].environment.CONTRACT_SERVICE_KEY)
assert.equal(services['budget-service'].environment.ROOM_BUDGET_SERVICE_KEY, services['salles-service'].environment.ROOM_BUDGET_SERVICE_KEY)
// Verify the actual shared client configuration in development and production.
const source = readFileSync(new URL('../frontend/src/services/apiConfig.ts', import.meta.url), 'utf8')
  .replaceAll('import.meta.env', 'environment').replaceAll('export const ', 'const ')
for (const [environment, expected] of [
  [{ PROD: true }, '/api'],
  [{ PROD: true, VITE_GATEWAY_URL: '' }, '/api'],
  [{ PROD: false }, 'http://localhost:8081/api'],
  [{ PROD: false, VITE_GATEWAY_URL: 'http://localhost:12345/' }, 'http://localhost:12345/api'],
]) {
  assert.equal(vm.runInNewContext(source + '\napiBaseUrl', { environment }), expected)
}
const gateway = readFileSync(new URL('./gateway.conf', import.meta.url), 'utf8')
assert.match(gateway, /proxy_set_header X-Service-Key ""/)
assert.match(gateway, /try_files \$uri \$uri\/ \/index.html/)
assert.doesNotMatch(gateway, /host\.docker\.internal/)
console.log('PASS: nine services, private ports, persistent DB, migration ordering, service URLs, keys, frontend URLs and gateway wiring')
