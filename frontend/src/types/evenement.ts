export type EvenementStatut = 'PLANIFIE' | 'CONFIRME' | 'EN_COURS' | 'TERMINE' | 'ANNULE'

export interface Evenement {
  id: number
  organisateurId: number
  titre: string
  description: string | null
  dateDebut: string
  dateFin: string
  lieu: string | null
  statut: EvenementStatut
}

export interface EvenementCreateForm {
  organisateurId: number
  titre: string
  description?: string
  dateDebut: string
  dateFin: string
  lieu?: string
  statut?: EvenementStatut
}

export interface EvenementUpdateForm {
  titre: string
  description?: string
  dateDebut: string
  dateFin: string
  lieu?: string
  statut: EvenementStatut
}

export interface EvenementSearchParams {
  q?: string
  organisateurId?: number
  statut?: EvenementStatut
  dateDebut?: string
  dateFin?: string
  page?: number
  size?: number
  sort?: string
}

export interface EvenementPage {
  content: Evenement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface EvenementApiError {
  success: false
  code: string
  message: string
  fieldErrors?: Array<{ field: string; message: string }>
}
