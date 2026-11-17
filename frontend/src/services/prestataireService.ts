import axios from 'axios'
import { secureApi } from './authService'
import type {
  Prestataire,
  PrestataireApiError,
  PrestataireCreateForm,
  UtilisationPrestataire,
  ContractForm,
} from '../types/prestataire'

const prestataireApi = axios.create({
  baseURL: import.meta.env.VITE_PRESTATAIRES_API_URL || 'http://localhost:5002/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

secureApi(prestataireApi)

const prestataireService = {
  assign: async (data: ContractForm & { evenementId: number; prestataireId: number; requestId: string }) => {
    const response = await prestataireApi.post('/contrats-prestataire', data)
    return response.data
  },
  updateContract: async (id: number, data: ContractForm & { version: number }) => {
    const response = await prestataireApi.put(`/contrats-prestataire/${id}`, data)
    return response.data
  },
  synchronizeContract: async (id: number) => {
    const response = await prestataireApi.post(`/contrats-prestataire/${id}/synchroniser`)
    return response.data
  },
  list: async (): Promise<Prestataire[]> => {
    const response = await prestataireApi.get<Prestataire[]>('/prestataires')
    return response.data
  },

  create: async (data: PrestataireCreateForm): Promise<Prestataire> => {
    const response = await prestataireApi.post<Prestataire>('/prestataires', data)
    return response.data
  },

  listUtilisations: async (): Promise<UtilisationPrestataire[]> => {
    const response = await prestataireApi.get<UtilisationPrestataire[]>('/prestataires/utilisations')
    return response.data
  },
}

export const getPrestataireErrorMessage = (error: unknown): string => {
  if (!axios.isAxiosError<PrestataireApiError>(error)) {
    return 'Une erreur inattendue est survenue.'
  }

  if (!error.response) {
    return 'Le microservice Prestataires est indisponible. Vérifiez qu’il est démarré sur le port 5002.'
  }

  return error.response.data?.message || 'La requête Prestataires a échoué.'
}

export default prestataireService
