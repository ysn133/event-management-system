import type { CanalEnvoi, InviteSearchFilters, StatutInvitation } from '../types/invite'

export const invitationStatuses: StatutInvitation[] = ['EN_ATTENTE', 'ACCEPTEE', 'REFUSEE']
export const invitationStatusLabel = (status: StatutInvitation) => ({ EN_ATTENTE: 'En attente', ACCEPTEE: 'Acceptée', REFUSEE: 'Refusée' }[status])
export const invitationStatusTone = (status: StatutInvitation) => ({ EN_ATTENTE: 'orange', ACCEPTEE: 'green', REFUSEE: 'rose' }[status])
export const channelLabel = (channel: CanalEnvoi) => ({ EMAIL: 'E-mail', SMS: 'SMS' }[channel])

export const inviteRef = (id: number) => `INV-${String(id).padStart(3, '0')}`

export const prettyDate = (value: string) =>
  new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value))

export const prettyDateTime = (value: string) =>
  new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value))

export const formatPercent = (value: number) => `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(value)} %`

// Une seule zone de recherche : on devine le critère (e-mail s'il y a « @ », téléphone s'il n'y a que des
// chiffres et signes de numéro, sinon nom). Longueurs limitées comme côté service.
export const buildInviteSearch = (raw: string): InviteSearchFilters | null => {
  const query = raw.trim()
  if (!query) return null
  if (query.includes('@')) return { email: query.slice(0, 254) }
  if (/^[+\d][\d\s().-]*$/.test(query)) return { telephone: query.slice(0, 20) }
  return { nom: query.slice(0, 255) }
}

export const normalizeOptional = (value: FormDataEntryValue | null): string | null => {
  const text = String(value ?? '').trim()
  return text ? text : null
}
