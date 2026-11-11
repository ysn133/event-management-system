export interface Evenement {
  id: number
  organisateurId: number
  titre: string
  description: string | null
  dateDebut: string
  dateFin: string
  lieu: string | null
  statut: string
}

export interface EvenementCreateForm {
  organisateurId: number
  titre: string
  description?: string
  dateDebut: string
  dateFin: string
  lieu?: string
  statut?: string
}

export interface EvenementUpdateForm {
  titre: string
  description?: string
  dateDebut: string
  dateFin: string
  lieu?: string
  statut: string
}

export interface EvenementApiError {
  success: false
  code: string
  message: string
  fieldErrors?: Array<{ field: string; message: string }>
}
