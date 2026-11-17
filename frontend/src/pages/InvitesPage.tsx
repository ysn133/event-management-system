// Page « Invités » (/invites) : répertoire des invités, relié au service Invités (port 5003).
// Les invités d'UN événement (association, envoi, réponses, statistiques) sont gérés dans l'onglet
// « Invités » du détail d'un événement (components/EventGuestsTab.tsx).
import { FormEvent, useEffect, useState } from 'react'
import { CalendarDays, ChevronRight, Eye, Mail, MapPin, Pencil, Phone, Plus, Search, Trash2, Users } from 'lucide-react'
import inviteService, { getInviteErrorMessage } from '../services/inviteService'
import type { EvenementAssocie, Invite, InviteForm } from '../types/invite'
import { Badge, Button, DeleteModal, EmptyAction, Modal, PageIntro } from '../components/ui'
import { buildInviteSearch, inviteRef, invitationStatusLabel, invitationStatusTone, normalizeOptional, prettyDate } from '../utils/invites'
import '../styles/invites.css'

const PAGE_SIZE = 10

type Props = { flash: (message: string) => void; go: (path: string) => void }

export default function InvitesPage({ flash, go }: Props) {
  const [items, setItems] = useState<Invite[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [formFor, setFormFor] = useState<Invite | 'new' | null>(null)
  const [detail, setDetail] = useState<Invite | null>(null)
  const [toDelete, setToDelete] = useState<Invite | null>(null)

  useEffect(() => {
    let active = true
    const timer = setTimeout(() => {
      setLoading(true)
      const params = { limit: PAGE_SIZE, offset: page * PAGE_SIZE }
      const filters = buildInviteSearch(search)
      const request = filters ? inviteService.search(filters, params) : inviteService.list(params)
      request
        .then(result => {
          if (!active) return
          // Dernier élément de la dernière page supprimé : on revient à la page précédente.
          if (result.items.length === 0 && page > 0 && result.total > 0) { setPage(page - 1); return }
          setItems(result.items)
          setTotal(result.total)
          setError('')
        })
        .catch(requestError => { if (active) setError(getInviteErrorMessage(requestError)) })
        .finally(() => { if (active) setLoading(false) })
    }, 250)
    return () => { active = false; clearTimeout(timer) }
  }, [search, page, reload])

  const refresh = () => setReload(value => value + 1)
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return <div className="guests-scope">
    <PageIntro eyebrow="Répertoire" title="Invités" text="Gérez les participants et leurs invitations." action="Ajouter un invité" onClick={() => setFormFor('new')} />
    <section className="card table-card">
      <div className="toolbar">
        <label className="search-box"><Search size={17} /><input value={search} onChange={e => { setSearch(e.target.value); setPage(0) }} placeholder="Rechercher par nom, e-mail ou téléphone..." aria-label="Rechercher un invité" /></label>
      </div>
      {loading && items.length === 0 ? <p className="description">Chargement des invités...</p>
        : error && items.length === 0 ? <div className="empty"><h3>Erreur de chargement</h3><p>{error}</p><Button onClick={refresh}>Réessayer</Button></div>
        : items.length === 0 ? (search.trim()
          ? <div className="empty"><div className="empty-icon"><Search size={22} /></div><h3>Aucun résultat</h3><p>Aucun invité ne correspond à « {search.trim()} ».</p><Button kind="secondary" onClick={() => setSearch('')}>Effacer la recherche</Button></div>
          : <EmptyAction icon={Users} title="Aucun invité" text="Ajoutez votre premier invité pour pouvoir l’associer à un événement." action="Ajouter un invité" onClick={() => setFormFor('new')} />)
        : <>
          {error && <div className="warning">{error}</div>}
          <div className="table-scroll"><table>
            <thead><tr><th>Invité</th><th>E-mail</th><th>Téléphone</th><th>Actions</th></tr></thead>
            <tbody>{items.map(invite => <tr key={invite.id} className="guest-row">
              <td><strong>{invite.nom}</strong><small>{inviteRef(invite.id)}</small></td>
              <td>{invite.email ? <span className="with-icon"><Mail size={14} />{invite.email}</span> : <span className="guest-none">—</span>}</td>
              <td>{invite.telephone ? <span className="with-icon"><Phone size={14} />{invite.telephone}</span> : <span className="guest-none">—</span>}</td>
              <td><div className="guest-actions">
                <button className="icon-btn" title="Voir ses événements" aria-label={`Voir les événements de ${invite.nom}`} onClick={() => setDetail(invite)}><Eye size={16} /></button>
                <button className="icon-btn" title="Modifier" aria-label={`Modifier ${invite.nom}`} onClick={() => setFormFor(invite)}><Pencil size={16} /></button>
                <button className="icon-btn danger" title="Supprimer" aria-label={`Supprimer ${invite.nom}`} onClick={() => setToDelete(invite)}><Trash2 size={16} /></button>
              </div></td>
            </tr>)}</tbody>
          </table></div>
          <div className="pagination"><span>{total} invité(s)</span><div><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Précédent</button><button className="current">{page + 1}</button><button disabled={page + 1 >= totalPages} onClick={() => setPage(value => value + 1)}>Suivant</button></div></div>
        </>}
    </section>
    {formFor && <InviteFormModal invite={formFor === 'new' ? null : formFor} onClose={() => setFormFor(null)} onSaved={message => { setFormFor(null); flash(message); refresh() }} />}
    {detail && <InviteDetailModal invite={detail} onClose={() => setDetail(null)} onEdit={() => { setFormFor(detail); setDetail(null) }} go={path => { setDetail(null); go(path) }} />}
    {toDelete && <InviteDeleteModal invite={toDelete} onClose={() => setToDelete(null)} onDeleted={() => { setToDelete(null); flash('Invité supprimé avec succès'); refresh() }} />}
  </div>
}

function InviteFormModal({ invite, onClose, onSaved }: { invite: Invite | null; onClose: () => void; onSaved: (message: string) => void }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    const form = new FormData(ev.currentTarget)
    const data: InviteForm = { nom: String(form.get('nom') ?? '').trim(), email: normalizeOptional(form.get('email')), telephone: normalizeOptional(form.get('telephone')) }
    if (!data.nom) { setError('Le nom est obligatoire.'); return }
    if (!data.email && !data.telephone) { setError('Renseignez au moins un e-mail ou un téléphone.'); return }
    setSaving(true)
    setError('')
    try {
      if (invite) await inviteService.update(invite.id, data)
      else await inviteService.create(data)
      onSaved(invite ? 'Invité modifié avec succès' : 'Invité ajouté avec succès')
    } catch (requestError) {
      setError(getInviteErrorMessage(requestError))
      setSaving(false)
    }
  }

  return <Modal title={invite ? 'Modifier l’invité' : 'Ajouter un invité'} onClose={onClose}>
    <form onSubmit={submit} className="form-grid">
      <label className="full">Nom complet<input name="nom" maxLength={150} required defaultValue={invite?.nom ?? ''} placeholder="Ex. Meryem Alaoui" autoFocus /></label>
      <label>Adresse e-mail<input name="email" type="email" maxLength={254} defaultValue={invite?.email ?? ''} placeholder="nom@exemple.ma" /></label>
      <label>Téléphone<input name="telephone" type="tel" maxLength={20} defaultValue={invite?.telephone ?? ''} placeholder="+212 6 12 34 56 78" /></label>
      <p className="full description" style={{ padding: 0 }}>Au moins un moyen de contact est obligatoire : e-mail ou téléphone.</p>
      {error && <p className="full warning" style={{ margin: 0 }}>{error}</p>}
      <div className="form-actions full"><Button kind="secondary" onClick={onClose}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement...' : invite ? 'Enregistrer' : 'Ajouter l’invité'}</Button></div>
    </form>
  </Modal>
}

function InviteDetailModal({ invite, onClose, onEdit, go }: { invite: Invite; onClose: () => void; onEdit: () => void; go: (path: string) => void }) {
  const [events, setEvents] = useState<EvenementAssocie[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    inviteService.getEvents(invite.id, { limit: 100, offset: 0 })
      .then(result => { if (active) setEvents(result.items) })
      .catch(requestError => { if (active) setError(getInviteErrorMessage(requestError)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [invite.id])

  return <Modal title="Détails de l’invité" onClose={onClose}>
    <div className="guest-modal-body">
      <div className="guest-identity"><div className="avatar-large">{invite.nom.trim().charAt(0).toUpperCase()}</div><div><strong>{invite.nom}</strong><span>{inviteRef(invite.id)}</span></div></div>
      <div className="info-grid" style={{ padding: 0 }}>
        <div><span>Adresse e-mail</span><strong>{invite.email || 'Non renseignée'}</strong></div>
        <div><span>Téléphone</span><strong>{invite.telephone || 'Non renseigné'}</strong></div>
      </div>
      <h4 className="guest-section-title">Événements associés</h4>
      {loading ? <p className="guest-empty-line">Chargement...</p>
        : error ? <p className="warning" style={{ margin: 0 }}>{error}</p>
        : events.length === 0 ? <div className="empty compact"><div className="empty-icon"><CalendarDays size={22} /></div><h3>Aucun événement</h3><p>Cet invité n’est associé à aucun événement pour le moment.</p></div>
        : <div className="guest-event-list"><div className="event-list">{events.map(event => <button key={event.invitation_id} className="event-row" onClick={() => go(`/evenements/${event.id}`)}>
          <div className="date-tile"><strong>{new Date(event.date_debut).getDate()}</strong><span>{new Date(event.date_debut).toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '')}</span></div>
          <div className="grow"><strong>{event.titre}</strong><span><MapPin size={14} />{event.lieu || 'Lieu non défini'} · {prettyDate(event.date_debut)}</span></div>
          <Badge tone={invitationStatusTone(event.statut_invitation)}>{invitationStatusLabel(event.statut_invitation)}</Badge><ChevronRight size={18} />
        </button>)}</div></div>}
      <div className="form-actions"><Button kind="secondary" onClick={onClose}>Fermer</Button><Button onClick={onEdit}><Pencil size={15} />Modifier</Button></div>
    </div>
  </Modal>
}

function InviteDeleteModal({ invite, onClose, onDeleted }: { invite: Invite; onClose: () => void; onDeleted: () => void }) {
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')

  const confirm = async () => {
    setDeleting(true)
    setError('')
    try {
      await inviteService.delete(invite.id)
      onDeleted()
    } catch (requestError) {
      // 409 : l'invité a encore des invitations ; le message du service l'explique.
      setError(getInviteErrorMessage(requestError))
      setDeleting(false)
    }
  }

  return <DeleteModal title="Supprimer l’invité" icon={<Users size={23} />} subject={invite.nom} detail={invite.email || invite.telephone || inviteRef(invite.id)}
    warning="Cette action est irréversible. Un invité qui a encore des invitations ne peut pas être supprimé : retirez-le d’abord des événements concernés."
    confirmLabel="Je confirme la suppression de cet invité" actionLabel="Supprimer définitivement" deleting={deleting} error={error} onClose={onClose} onConfirm={() => void confirm()} />
}
