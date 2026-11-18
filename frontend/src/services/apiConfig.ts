// Every browser API call goes through the same NGINX entry point.
// A relative URL keeps production on the same domain (including HTTPS).
// Local development still defaults to the separately running gateway.
const gatewayOrigin = (import.meta.env.VITE_GATEWAY_URL ?? (import.meta.env.PROD ? '' : 'http://localhost:8081')).replace(/\/+$/, '')
export const apiBaseUrl = `${gatewayOrigin}/api`
export const gatewayUnavailableMessage = 'La passerelle API est indisponible. Lancez ./scripts/run-local.sh.'
