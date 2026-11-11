import axios from 'axios'
import type {
  Evenement,
  EvenementApiError,
  EvenementCreateForm,
  EvenementUpdateForm,
} from '../types/evenement'

const evenementApi = axios.create({
  baseURL: import.meta.env.VITE_EVENT_API_URL || 'http://localhost:5001/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

const evenementService = {
  list: async (): Promise<Evenement[]> => {
    const response = await evenementApi.get<Evenement[]>('/evenements')
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

  delete: async (id: number): Promise<void> => {
    await evenementApi.delete(`/evenements/${id}`)
  },
}

export const getEvenementErrorMessage = (error: unknown): string => {
  if (!axios.isAxiosError<EvenementApiError>(error)) {
    return 'Une erreur inattendue est survenue.'
  }

  if (!error.response) {
    return 'Le service Événement est indisponible. Vérifiez qu’il est démarré sur le port 5001.'
  }

  const fieldMessage = error.response.data?.fieldErrors?.[0]?.message
  return fieldMessage || error.response.data?.message || 'La requête Événement a échoué.'
}

export default evenementService
