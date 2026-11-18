import { secureApi } from './authService'
import { apiBaseUrl, gatewayUnavailableMessage } from './apiConfig'
import axios from 'axios'
import type {
  Evenement,
  EvenementApiError,
  EvenementCreateForm,
  EvenementUpdateForm,
  EvenementStatut,
  EvenementPage,
  EvenementSearchParams,
} from '../types/evenement'

const evenementApi = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

secureApi(evenementApi)

const evenementService = {
  list: async (params: EvenementSearchParams = {}): Promise<EvenementPage> => {
    const response = await evenementApi.get<EvenementPage>('/evenements', { params })
    return response.data
  },

  getById: async (id: number): Promise<Evenement> => {
    const response = await evenementApi.get<Evenement>(`/evenements/${id}`)
    return response.data
  },

  create: async (data: EvenementCreateForm): Promise<Evenement> => {
    const response = await evenementApi.post<Evenement>('/evenements', data)
    return response.data
  },

  update: async (id: number, data: EvenementUpdateForm): Promise<Evenement> => {
    const response = await evenementApi.put<Evenement>(`/evenements/${id}`, data)
    return response.data
  },

  updateStatus: async (id: number, statut: EvenementStatut): Promise<Evenement> => {
    const response = await evenementApi.patch<Evenement>(`/evenements/${id}/statut`, { statut })
    return response.data
  },

  delete: async (id: number): Promise<void> => {
    await evenementApi.delete(`/evenements/${id}`)
  },
}

export const getEvenementErrorMessage = (error: unknown): string => {
  if (!axios.isAxiosError<EvenementApiError>(error)) {
    return 'Une erreur inattendue est survenue.'
  }

  if (!error.response) {
    return gatewayUnavailableMessage
  }

  const fieldMessage = error.response.data?.fieldErrors?.[0]?.message
  return fieldMessage || error.response.data?.message || 'La requête Événement a échoué.'
}

export default evenementService
