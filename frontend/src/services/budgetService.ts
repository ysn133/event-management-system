import { secureApi } from './authService'
import { apiBaseUrl, gatewayUnavailableMessage } from './apiConfig'
import axios from 'axios'
import type {
  BilanFinancier,
  Budget,
  BudgetApiError,
  BudgetCreateForm,
  BudgetStatistiques,
  BudgetUpdateForm,
  Depense,
  DepenseFilters,
  DepenseForm,
  DepenseStatut,
} from '../types/budget'

const budgetApi = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
})

secureApi(budgetApi)

const budgetService = {
  getReconciliation: async (id: number): Promise<ReconciliationPreview> =>
    (await budgetApi.get<ReconciliationPreview>(`/budgets/${id}/rapprochement`)).data,
  reconcile: async (id: number, decision: ReconciliationDecision): Promise<Depense> =>
    (await budgetApi.post<Depense>(`/budgets/${id}/rapprochement`, decision)).data,
  getByEventId: async (eventId: number): Promise<Budget> => {
    const response = await budgetApi.get<Budget>(`/budgets/evenement/${eventId}`)
    return response.data
  },

  create: async (data: BudgetCreateForm): Promise<Budget> => {
    const response = await budgetApi.post<Budget>('/budgets', data)
    return response.data
  },

  update: async (id: number, data: BudgetUpdateForm): Promise<Budget> => {
    const response = await budgetApi.put<Budget>(`/budgets/${id}`, data)
    return response.data
  },

  close: async (id: number): Promise<Budget> => {
    const response = await budgetApi.post<Budget>(`/budgets/${id}/cloturer`)
    return response.data
  },

  reopen: async (id: number): Promise<Budget> => {
    const response = await budgetApi.post<Budget>(`/budgets/${id}/rouvrir`)
    return response.data
  },

  getExpenses: async (budgetId: number, filters: DepenseFilters = {}): Promise<Depense[]> => {
    const response = await budgetApi.get<Depense[]>(`/budgets/${budgetId}/depenses`, {
      params: filters,
    })
    return response.data
  },

  createExpense: async (budgetId: number, data: DepenseForm): Promise<Depense> => {
    const response = await budgetApi.post<Depense>(`/budgets/${budgetId}/depenses`, data)
    return response.data
  },

  updateExpense: async (id: number, data: DepenseForm): Promise<Depense> => {
    const response = await budgetApi.put<Depense>(`/depenses/${id}`, data)
    return response.data
  },

  updateExpenseStatus: async (id: number, statut: DepenseStatut): Promise<Depense> => {
    const response = await budgetApi.patch<Depense>(`/depenses/${id}/statut`, { statut })
    return response.data
  },

  deleteExpense: async (id: number): Promise<void> => {
    await budgetApi.delete(`/depenses/${id}`)
  },

  getReport: async (budgetId: number): Promise<BilanFinancier> => {
    const response = await budgetApi.get<BilanFinancier>(`/budgets/${budgetId}/bilan`)
    return response.data
  },

  getStatistics: async (budgetId: number): Promise<BudgetStatistiques> => {
    const response = await budgetApi.get<BudgetStatistiques>(`/budgets/${budgetId}/statistiques`)
    return response.data
  },
}

export const isBudgetNotFound = (error: unknown): boolean =>
  axios.isAxiosError<BudgetApiError>(error) && error.response?.status === 404

export const getBudgetErrorMessage = (error: unknown): string => {
  if (!axios.isAxiosError<BudgetApiError>(error)) {
    return 'Une erreur inattendue est survenue.'
  }

  if (!error.response) {
    return gatewayUnavailableMessage
  }

  const fieldMessage = error.response.data?.fieldErrors?.[0]?.message
  return fieldMessage || error.response.data?.message || 'La requête Budget a échoué.'
}

export default budgetService

export interface ReconciliationIssue {
  type: 'ROOM' | 'PROVIDER'
  sourceId: number
  reason: 'SOURCE_MISSING' | 'UNLINKED_SOURCE' | 'STALE_EXPENSE' | 'PAID_AMOUNT_DIFFERENCE'
  description: string
  amount: number
  cancelled: boolean
  expenseId: number | null
  token: string
}
export interface ReconciliationPreview { issues: ReconciliationIssue[]; manualExpenses: Depense[] }
export interface ReconciliationDecision {
  type: ReconciliationIssue['type']; sourceId: number; token: string
  action: 'LINK' | 'CREATE' | 'SYNC' | 'RESOLVE_MISSING'
  expenseId?: number; confirmNoExistingExpense?: boolean
}
