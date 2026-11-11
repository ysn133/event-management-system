export interface Budget {
  id: number
  evenementId: number
  montantTotal: number
}

export interface BudgetCreateForm {
  evenementId: number
  montantTotal: number
}

export interface BudgetUpdateForm {
  montantTotal: number
}

export type DepenseStatut = 'PREVUE' | 'EN_ATTENTE' | 'PAYEE' | 'ANNULEE'
export type BudgetAlertLevel = 'NORMAL' | 'ATTENTION' | 'LIMITE_ATTEINTE' | 'DEPASSE'

export interface Depense {
  id: number
  budgetId: number
  description: string
  montant: number
  categorie: string
  dateDepense: string
  statut: DepenseStatut
}

export interface DepenseForm {
  description: string
  montant: number
  categorie: string
  dateDepense: string
  statut?: DepenseStatut
}

export interface BilanFinancier {
  budgetId: number
  evenementId: number
  montantTotal: number
  totalDepenses: number
  totalPrevu: number
  totalEngage: number
  totalPaye: number
  soldeRestant: number
  depassement: boolean
  pourcentageConsommation: number
  niveauAlerte: BudgetAlertLevel
}

export interface BudgetApiError {
  success: false
  code: string
  message: string
  fieldErrors?: Array<{ field: string; message: string }>
}
