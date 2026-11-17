import { useEffect, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { AlertTriangle, ArrowRight, ContactRound, WalletCards, X } from 'lucide-react'
import type { Budget, BilanFinancier } from './types/budget'
import type { Prestataire, ContractForm, UtilisationPrestataire } from './types/prestataire'

const money = (amount: number) => new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'MAD' }).format(amount)
export default function ProviderContractForm({ providers, eventTitle, eventDate, budget, report, editing, committedAmount, busy, error, onClose, onSave }: {
  providers: Prestataire[]; eventTitle: string; eventDate: string; budget: Budget; report: BilanFinancier;
  editing: UtilisationPrestataire | null; busy: boolean; error: string; onClose: () => void;
  committedAmount: number;
  onSave: (providerId: number, data: ContractForm) => void;
}) {
  const draft = editing?.pendingPayload || editing
  const [providerId, setProviderId] = useState(editing?.prestataireId || 0)
  const [amount, setAmount] = useState(draft?.montant ?? 0)
  const [status, setStatus] = useState<ContractForm['statut']>((draft?.statut as ContractForm['statut']) || 'CONFIRME')
  const provider = providers.find(item => item.id === providerId)
  const remaining = report.soldeRestant + committedAmount - (status === 'ANNULE' ? 0 : amount)
  useEffect(() => {
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape' && !busy) onClose() }
    window.addEventListener('keydown', close)
    return () => { document.body.style.overflow = previous; window.removeEventListener('keydown', close) }
  }, [busy, onClose])
  const forbidden = remaining < 0 && !budget.depassementAutorise && status !== 'ANNULE'
  const save = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    onSave(providerId, { prestation: String(form.get('prestation')).trim(), montant: amount, datePrestation: new Date(String(form.get('date'))).toISOString(), statut: status })
  }
  const localDate = (value: string) => { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
  return createPortal(<div className="modal-backdrop" onMouseDown={busy ? undefined : onClose}><div className="modal provider-contract-modal" role="dialog" aria-modal="true" aria-labelledby="provider-contract-title" onMouseDown={event => event.stopPropagation()}>
    <div className="modal-head"><div><p className="eyebrow">{eventTitle}</p><h2 id="provider-contract-title">{editing ? 'Modifier la prestation' : 'Ajouter un prestataire'}</h2></div><button className="icon-btn" aria-label="Fermer" onClick={onClose} disabled={busy}><X size={20} /></button></div>
    <form onSubmit={save}><fieldset disabled={busy} className="provider-contract-fields"><div className="provider-contract-layout"><div className="provider-contract-inputs">
      <label>Prestataire<select required value={providerId || ''} disabled={Boolean(editing)} onChange={event => { const selected = Number(event.target.value); setProviderId(selected); setAmount(providers.find(item => item.id === selected)?.tarif || 0) }}><option value="" disabled>Sélectionner un prestataire</option>{providers.map(item => <option key={item.id} value={item.id}>{item.nom} · {item.typeService}</option>)}</select></label>
      {provider && <div className="provider-contract-contact"><ContactRound size={18} /><div><strong>{provider.nom}</strong><small>{provider.contact}</small></div></div>}
      <label>Prestation<input name="prestation" required maxLength={255} defaultValue={draft?.prestation || ''} placeholder="Ex. Buffet et pauses café" /></label>
      <div className="provider-contract-pair"><label>Montant convenu (MAD)<input type="number" required min="0.01" max="9999999999.99" step="0.01" value={amount || ''} onChange={event => setAmount(Number(event.target.value))} /></label><label>Statut<select value={status} onChange={event => setStatus(event.target.value as ContractForm['statut'])}><option value="EN_ATTENTE">En attente</option><option value="CONFIRME">Confirmé</option>{editing && <><option value="TERMINE">Terminé</option><option value="ANNULE">Annulé</option></>}</select></label></div>
      <label>Date de prestation<input name="date" type="datetime-local" required defaultValue={localDate(draft?.datePrestation || eventDate)} /></label>
      {error && <p className="warning" role="alert">{error}</p>}
    </div><aside className="provider-contract-impact"><span className="provider-contract-impact-icon"><WalletCards size={22} /></span><h3>Impact sur le budget</h3><p>Le montant est engagé dès validation du contrat.</p><dl><div><dt>Budget alloué</dt><dd>{money(budget.montantTotal)}</dd></div><div><dt>Solde actuel</dt><dd>{money(report.soldeRestant)}</dd></div><div><dt>Cette prestation</dt><dd>{money(status === 'ANNULE' ? 0 : amount)}</dd></div></dl><div className={`provider-contract-balance ${remaining < 0 ? 'negative' : ''}`}><span>Solde après validation</span><strong>{money(remaining)}</strong></div>{remaining < 0 ? <div className="provider-contract-warning"><AlertTriangle size={18} /><p>{forbidden ? 'Solde insuffisant. Réduisez le montant ou ajustez le budget.' : 'Dépassement autorisé : cette prestation dépassera le budget alloué.'}</p></div> : <p className="provider-contract-footnote">Une dépense liée sera enregistrée automatiquement. Le service Budget confirme le solde à la validation.</p>}</aside></div></fieldset>
    <div className="provider-contract-actions"><button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>Annuler</button><button type="submit" className="btn btn-primary" disabled={busy || forbidden || !providerId}>{busy ? 'Validation du budget…' : editing ? 'Enregistrer les modifications' : 'Valider la prestation'}<ArrowRight size={16} /></button></div></form>
  </div></div>, document.body)
}
