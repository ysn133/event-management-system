import axios from 'axios'
import type {
  Prestataire,
  PrestataireApiError,
  PrestataireCreateForm,
  UtilisationPrestataire,
} from '../types/prestataire'

const prestataireApi = axios.create({
  baseURL: import.meta.env.VITE_PRESTATAIRES_API_URL || 'http://localhost:5002/api',
  headers: {
    'Content-Type': 'application/json',
  },
})

const prestataireService = {
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
