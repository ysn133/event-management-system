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

export interface Depense {
  id: number
  budgetId: number
  description: string
  montant: number
  categorie: string
  dateDepense: string
  statut: string | null
}

export interface DepenseForm {
  description: string
  montant: number
  categorie: string
  dateDepense: string
  statut?: string
}

export interface BilanFinancier {
  budgetId: number
  evenementId: number
  montantTotal: number
  totalDepenses: number
  soldeRestant: number
  depassement: boolean
}

export interface BudgetApiError {
  success: false
  code: string
  message: string
  fieldErrors?: Array<{ field: string; message: string }>
}
