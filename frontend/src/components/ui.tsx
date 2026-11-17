// Éléments d'interface du module Invités. Ils reprennent À L'IDENTIQUE (balisage et classes CSS) ceux
// d'App.tsx pour garder le même rendu ; App.tsx ne les exporte pas, d'où cette copie.
// Quand l'équipe les extraira dans un fichier commun, ce fichier pourra les réutiliser.
import { useState } from 'react'
import type { ComponentType, ReactNode } from 'react'
import { AlertTriangle, ChevronRight, Plus, Trash2, X } from 'lucide-react'

export function Button({ children, onClick, kind = 'primary', type = 'button', disabled = false, title }: { children: ReactNode; onClick?: () => void; kind?: 'primary' | 'secondary' | 'ghost' | 'danger'; type?: 'button' | 'submit'; disabled?: boolean; title?: string }) {
  return <button type={type} onClick={onClick} disabled={disabled} title={title} className={`btn btn-${kind}`}>{children}</button>
}

export function Badge({ children, tone = 'blue' }: { children: ReactNode; tone?: string }) {
  return <span className={`badge badge-${tone}`}><span className="badge-dot" />{children}</span>
}

export function Modal({ title, children, onClose, wide = false }: { title: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  return <div className="modal-backdrop" onMouseDown={onClose}><div className={`modal ${wide ? 'modal-wide' : ''}`} onMouseDown={e => e.stopPropagation()}>
    <div className="modal-head"><div><p className="eyebrow">EventFlow</p><h2>{title}</h2></div><button className="icon-btn" onClick={onClose} aria-label="Fermer"><X size={19} /></button></div>{children}
  </div></div>
}

export function PageIntro({ eyebrow, title, text, action, onClick }: { eyebrow: string; title: string; text: string; action?: string; onClick?: () => void }) {
  return <section className="page-intro"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2><p>{text}</p></div>{action && <Button onClick={onClick}><Plus size={17} />{action}</Button>}</section>
}

export function CardHead({ title, subtitle, action, onClick }: { title: string; subtitle: string; action?: string; onClick?: () => void }) {
  return <div className="card-head"><div><h3>{title}</h3><p>{subtitle}</p></div>{action && <Button kind="ghost" onClick={onClick}>{action}<ChevronRight size={16} /></Button>}</div>
}

export function EmptyAction({ icon: Icon, title, text, action, onClick }: { icon: ComponentType<{ size?: number }>; title: string; text: string; action: string; onClick: () => void }) {
  return <div className="empty"><div className="empty-icon"><Icon size={22} /></div><h3>{title}</h3><p>{text}</p><Button onClick={onClick}><Plus size={16} />{action}</Button></div>
}

// Même fenêtre de confirmation que la suppression d'une dépense (classes « delete-* »).
export function DeleteModal({ title, icon, subject, detail, warning, confirmLabel, actionLabel, busyLabel = 'Suppression…', deleting, error, onClose, onConfirm }: {
  title: string; icon: ReactNode; subject: string; detail: string; warning: string; confirmLabel: string; actionLabel: string; busyLabel?: string
  deleting: boolean; error: string; onClose: () => void; onConfirm: () => void
}) {
  const [confirmed, setConfirmed] = useState(false)
  return <div className="modal-backdrop delete-backdrop" onMouseDown={deleting ? undefined : onClose}><div className="delete-modal" onMouseDown={e => e.stopPropagation()}>
    <div className="delete-head"><div className="delete-icon"><AlertTriangle size={26} /></div><h2>{title}</h2><button className="icon-btn" onClick={onClose} disabled={deleting} aria-label="Fermer"><X size={18} /></button></div>
    <div className="delete-body">
      <div className="delete-expense">{icon}<div><strong>{subject}</strong><span>{detail}</span></div></div>
      <div className="delete-warning"><AlertTriangle size={20} /><p>{warning}</p></div>
      {error && <p className="warning guest-inline-error">{error}</p>}
      <label className="delete-confirm"><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} /><span>{confirmLabel}</span></label>
    </div>
    <div className="delete-actions"><Button kind="secondary" onClick={onClose}>Annuler</Button><button className="delete-button" disabled={!confirmed || deleting} onClick={onConfirm}><Trash2 size={17} />{deleting ? busyLabel : actionLabel}</button></div>
  </div></div>
}
