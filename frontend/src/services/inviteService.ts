import axios from 'axios'
import { secureApi } from './authService'
import { apiBaseUrl, gatewayUnavailableMessage } from './apiConfig'
import type {
  CanalEnvoi,
  EvenementAssocie,
  Invitation,
  InvitationEnvoi,
  Invite,
  InviteApiError,
  InviteAssocie,
  InviteForm,
  InviteSearchFilters,
  Page,
  PageParams,
  StatistiquesInvitations,
  StatutInvitation,
} from '../types/invite'

const inviteApi = axios.create({
  baseURL: `${apiBaseUrl}/v1`,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Ajoute le jeton JWT de la session (le module le retransmet au service Événements).
secureApi(inviteApi)

const inviteService = {
  // --- Invités (répertoire) ---
  list: async (params: PageParams = {}): Promise<Page<Invite>> => {
    const response = await inviteApi.get<Page<Invite>>('/invites', { params })
    return response.data
  },

  // Au moins un critère est obligatoire (nom, e-mail ou téléphone). Les critères se combinent (ET).
  search: async (filters: InviteSearchFilters, params: PageParams = {}): Promise<Page<Invite>> => {
    const response = await inviteApi.get<Page<Invite>>('/invites/search', { params: { ...filters, ...params } })
    return response.data
  },

  create: async (data: InviteForm): Promise<Invite> => {
    const response = await inviteApi.post<Invite>('/invites', data)
    return response.data
  },

  // PUT = remplacement complet : tous les champs sont renvoyés.
  update: async (id: number, data: InviteForm): Promise<Invite> => {
    const response = await inviteApi.put<Invite>(`/invites/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<void> => {
    await inviteApi.delete(`/invites/${id}`)
  },

  getEvents: async (inviteId: number, params: PageParams & { statut?: StatutInvitation } = {}): Promise<Page<EvenementAssocie>> => {
    const response = await inviteApi.get<Page<EvenementAssocie>>(`/invites/${inviteId}/evenements`, { params })
    return response.data
  },

  // --- Invités d'un événement ---
  getEventGuests: async (eventId: number, params: PageParams & { statut?: StatutInvitation } = {}): Promise<Page<InviteAssocie>> => {
    const response = await inviteApi.get<Page<InviteAssocie>>(`/evenements/${eventId}/invites`, { params })
    return response.data
  },

  associate: async (eventId: number, inviteId: number): Promise<Invitation> => {
    const response = await inviteApi.post<Invitation>(`/evenements/${eventId}/invites/${inviteId}`)
    return response.data
  },

  dissociate: async (eventId: number, inviteId: number): Promise<void> => {
    await inviteApi.delete(`/evenements/${eventId}/invites/${inviteId}`)
  },

  // --- Invitations ---
  send: async (eventId: number, inviteId: number, canal: CanalEnvoi): Promise<InvitationEnvoi> => {
    const response = await inviteApi.post<InvitationEnvoi>(`/evenements/${eventId}/invitations`, { invite_id: inviteId, canal })
    return response.data
  },

  updateStatus: async (invitationId: number, statut: StatutInvitation): Promise<Invitation> => {
    const response = await inviteApi.patch<Invitation>(`/invitations/${invitationId}/statut`, { statut })
    return response.data
  },

  getStatistics: async (eventId: number): Promise<StatistiquesInvitations> => {
    const response = await inviteApi.get<StatistiquesInvitations>(`/evenements/${eventId}/statistiques-invitations`)
    return response.data
  },
}

export const getInviteErrorMessage = (error: unknown): string => {
  if (!axios.isAxiosError<InviteApiError>(error)) {
    return 'Une erreur inattendue est survenue.'
  }

  if (!error.response) {
    return gatewayUnavailableMessage
  }

  const apiError = error.response.data?.error
  if (error.response.status === 401) {
    return 'Votre session a expiré. Reconnectez-vous pour continuer.'
  }
  if (apiError?.code === 'CAPACITE_SALLE_DEPASSEE') {
    return apiError.message || 'La capacité de la salle est atteinte : impossible d’ajouter un invité.'
  }
  if (apiError?.code === 'SERVICE_SALLES_INDISPONIBLE') {
    return 'Le service Salles est indisponible : la capacité ne peut pas être vérifiée. Vérifiez qu’il est démarré sur le port 5242.'
  }
  if (apiError?.code === 'SERVICE_EVENEMENTS_INDISPONIBLE') {
    return 'Le service Événements est indisponible. Vérifiez qu’il est démarré sur le port 5001.'
  }

  // 422 : message du premier champ fautif (les messages du service sont déjà en français).
  const fieldMessage = apiError?.details?.[0]?.message
  return fieldMessage || apiError?.message || 'La requête Invités a échoué.'
}

export default inviteService
