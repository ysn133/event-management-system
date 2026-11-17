import { useEffect, useState } from 'react'
import budgetService, { getBudgetErrorMessage, type ReconciliationIssue, type ReconciliationPreview, type ReconciliationDecision } from './services/budgetService'

export default function BudgetReconciliation({ budgetId, closed, refreshKey, onChanged }: {
  budgetId: number; closed: boolean; refreshKey: string; onChanged: () => Promise<void>
}) {
  const [preview, setPreview] = useState<ReconciliationPreview | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState<Record<string, string>>({})
  const load = async () => {
    try { setPreview(await budgetService.getReconciliation(budgetId)); setError('') }
    catch (e) { setError(getBudgetErrorMessage(e)) }
  }
  useEffect(() => { let active = true
    void budgetService.getReconciliation(budgetId).then(data => { if (active) { setPreview(data); setError('') } })
      .catch(e => { if (active) setError(getBudgetErrorMessage(e)) })
    return () => { active = false }
  }, [budgetId, refreshKey])
  const act = async (issue: ReconciliationIssue, action: ReconciliationDecision['action']) => {
    if (action === 'CREATE' && !window.confirm('Confirmez qu’aucune dépense existante ne correspond à cette réservation ou ce contrat. Une nouvelle dépense sera ajoutée au budget.')) return
    if (action === 'RESOLVE_MISSING' && !window.confirm('Source introuvable : confirmer sa disparition ? Un paiement enregistré sera conservé. Une dépense non payée sera annulée.')) return
    setBusy(true); setError('')
    try {
      await budgetService.reconcile(budgetId, { type: issue.type, sourceId: issue.sourceId, token: issue.token, action,
        expenseId: Number(selected[`${issue.type}:${issue.sourceId}`]) || undefined, confirmNoExistingExpense: action === 'CREATE' })
      await onChanged(); await load()
    } catch (e) { setError(getBudgetErrorMessage(e)) }
    finally { setBusy(false) }
  }
  if (!error && preview?.issues.length === 0) return null
  return <section className="card reconciliation-card" aria-label="Rapprochement des dépenses">
    <div className="card-head"><div><h3>Rapprochement des dépenses</h3><p>Vérifiez les anciennes réservations et prestations. Aucun rapprochement par libellé ou montant n’est automatique.</p></div></div>
    {error && <p role="alert" className="form-error">{error} <button className="btn btn-secondary" onClick={() => void load()}>Réessayer</button></p>}
    {!preview && !error && <p>Vérification des sources…</p>}
    {preview?.issues.map(issue => {
      const key = `${issue.type}:${issue.sourceId}`
      const reasons = { SOURCE_MISSING: 'Source introuvable — paiement conservé jusqu’à vérification', UNLINKED_SOURCE: 'Aucune dépense liée', STALE_EXPENSE: 'Informations à synchroniser', PAID_AMOUNT_DIFFERENCE: 'Montant source différent du paiement enregistré : vérifiez le service d’origine. Le paiement n’est pas modifié.' }
      return <div className="reconciliation-item" key={key}>
        <div><strong>{issue.description}</strong><p>{issue.type === 'ROOM' ? 'Réservation' : 'Contrat'} #{issue.sourceId} · {issue.amount.toLocaleString('fr-FR')} MAD{issue.cancelled ? ' · Source annulée' : ''}</p><p>{reasons[issue.reason]}</p></div>
        {!closed && issue.reason === 'UNLINKED_SOURCE' && <div className="reconciliation-actions">
          <select aria-label={`Dépense à lier à ${issue.description}`} value={selected[key] || ''} disabled={busy} onChange={e => setSelected({ ...selected, [key]: e.target.value })}>
            <option value="">Choisir une dépense existante…</option>
            {preview.manualExpenses.filter(e => e.statut !== 'ANNULEE').map(e => <option key={e.id} value={e.id}>#{e.id} — {e.description} — {e.montant} MAD ({e.statut})</option>)}
          </select>
          <button className="btn btn-secondary" disabled={busy || !selected[key]} onClick={() => void act(issue, 'LINK')}>Lier</button>
          {!issue.cancelled && <button className="btn btn-secondary" disabled={busy} onClick={() => void act(issue, 'CREATE')}>Créer la dépense manquante</button>}
        </div>}
        {!closed && issue.reason === 'STALE_EXPENSE' && <button className="btn btn-secondary" disabled={busy} onClick={() => void act(issue, 'SYNC')}>Synchroniser</button>}
        {!closed && issue.reason === 'SOURCE_MISSING' && <button className="btn btn-secondary" disabled={busy} onClick={() => void act(issue, 'RESOLVE_MISSING')}>Confirmer après vérification</button>}
      </div>
    })}
  </section>
}
