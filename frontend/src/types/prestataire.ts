export interface Prestataire {
  id: number
  nom: string
  contact: string
  typeService: string
  tarif: number
}

export interface PrestataireCreateForm {
  nom: string
  contact: string
  typeService: string
  tarif: number
}

export interface UtilisationPrestataire {
  id: number
  prestataireId: number
  prestataireNom: string
  evenementId: number
  evenementTitre: string
  prestation: string
  montant: number
  datePrestation: string
  statut: string
}

export interface PrestataireApiError {
  success: false
  message: string
}
