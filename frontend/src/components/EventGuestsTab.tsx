// Onglet « Invités » du détail d'un événement : statistiques, invités associés, réponses, envoi.
// Relié au service Invités (port 5003). Les événements eux-mêmes restent gérés par le service Événements.
import { FormEvent, useEffect, useState } from 'react'
import { Mail, Phone, Plus, Search, Send, Trash2, UserPlus, Users } from 'lucide-react'
import inviteService, { getInviteErrorMessage } from '../services/inviteService'
import type { CanalEnvoi, Invite, InviteAssocie, StatistiquesInvitations, StatutInvitation } from '../types/invite'
import { Badge, Button, DeleteModal, EmptyAction, Modal } from './ui'
import { buildInviteSearch, channelLabel, formatPercent, inviteRef, invitationStatusLabel, invitationStatusTone, invitationStatuses, prettyDateTime } from '../utils/invites'
import '../styles/invites.css'

const PAGE_SIZE = 10

type Props = { event: { id: number; title: string }; flash: (message: string) => void; go: (path: string) => void }

export default function EventGuestsTab({ event, flash, go }: Props) {
  const [stats, setStats] = useState<StatistiquesInvitations | null>(null)
  const [guests, setGuests] = useState<InviteAssocie[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<StatutInvitation | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [associating, setAssociating] = useState(false)
  const [sending, setSending] = useState<InviteAssocie | null>(null)
  const [removing, setRemoving] = useState<InviteAssocie | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    Promise.all([
      inviteService.getEventGuests(event.id, { statut: filter || undefined, limit: PAGE_SIZE, offset: page * PAGE_SIZE }),
      inviteService.getStatistics(event.id),
    ])
      .then(([list, statistics]) => {
        if (!active) return
        // Dernier élément de la dernière page retiré : on revient à la page précédente.
        if (list.items.length === 0 && page > 0 && list.total > 0) { setPage(page - 1); return }
        setGuests(list.items)
        setTotal(list.total)
        setStats(statistics)
        setError('')
      })
      .catch(requestError => { if (active) setError(getInviteErrorMessage(requestError)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [event.id, filter, page, reload])

  const refresh = () => setReload(value => value + 1)
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const changeStatus = async (guest: InviteAssocie, statut: StatutInvitation) => {
    if (statut === guest.statut_invitation) return
    setBusyId(guest.invitation_id)
    try {
      await inviteService.updateStatus(guest.invitation_id, statut)
      flash(`Réponse enregistrée : ${invitationStatusLabel(statut)}`)
      refresh()
    } catch (requestError) {
      setError(getInviteErrorMessage(requestError))
    } finally {
      setBusyId(null)
    }
  }

  const tile = (label: string, value: number | undefined, note: string) => <div><strong>{value ?? '—'}</strong><span>{label}{note ? ` · ${note}` : ''}</span></div>

  return <div className="guests-scope">
    <div className="mini-stats">
      {stats && stats.capacite !== null
        ? <div><strong>{stats.places_occupees} / {stats.capacite}</strong><span>Places occupées · {stats.places_restantes} restante(s)</span></div>
        : tile('Invités associés', stats?.total, 'capacité inconnue')}
      {tile('En attente', stats?.en_attente, 'sans réponse')}
      {tile('Acceptées', stats?.acceptees, stats ? formatPercent(stats.pourcentage_acceptation) : '')}
      {tile('Refusées', stats?.refusees, stats ? formatPercent(stats.pourcentage_refus) : '')}
    </div>
    {stats?.capacite_depassee && <div className="warning guest-capacity-alert">La capacité de la salle ({stats.capacite}) est dépassée : {stats.places_occupees} invités non refusés.</div>}
    {stats && stats.places_restantes === 0 && !stats.capacite_depassee && <div className="warning guest-capacity-alert">La salle est complète : refusez ou retirez une invitation pour libérer une place.</div>}
    <section className="card table-card">
      <div className="toolbar">
        <div className="filter-buttons">
          <select aria-label="Filtrer par réponse" value={filter} onChange={e => { setFilter(e.target.value as StatutInvitation | ''); setPage(0) }}>
            <option value="">Toutes les réponses</option>
            {invitationStatuses.map(status => <option key={status} value={status}>{invitationStatusLabel(status)}</option>)}
          </select>
        </div>
        <Button disabled={stats?.places_restantes === 0} onClick={() => setAssociating(true)}><UserPlus size={16} />Associer un invité</Button>
      </div>
      {loading && guests.length === 0 ? <p className="description">Chargement des invités...</p>
        : error && guests.length === 0 ? <div className="empty"><h3>Erreur de chargement</h3><p>{error}</p><Button onClick={refresh}>Réessayer</Button></div>
        : guests.length === 0 ? (filter
          ? <div className="empty"><div className="empty-icon"><Search size={22} /></div><h3>Aucune invitation</h3><p>Aucun invité n’a la réponse « {invitationStatusLabel(filter)} » pour le moment.</p><Button kind="secondary" onClick={() => setFilter('')}>Voir tous les invités</Button></div>
          : <EmptyAction icon={Users} title="Aucun invité associé" text={`Associez des invités à « ${event.title} » pour leur envoyer une invitation.`} action="Associer un invité" onClick={() => setAssociating(true)} />)
        : <>
          {error && <div className="warning">{error}</div>}
          <div className="table-scroll"><table>
            <thead><tr><th>Invité</th><th>Contact</th><th>Réponse</th><th>Envoi</th><th>Actions</th></tr></thead>
            <tbody>{guests.map(guest => {
              const locked = guest.etat_envoi === 'ENVOYEE' || guest.statut_invitation !== 'EN_ATTENTE'
              return <tr key={guest.invitation_id} className="guest-row">
                <td><strong>{guest.nom}</strong><small>{inviteRef(guest.id)}</small></td>
                <td><div className="guest-contact">
                  {guest.email && <span className="with-icon"><Mail size={14} />{guest.email}</span>}
                  {guest.telephone && <span className="with-icon"><Phone size={14} />{guest.telephone}</span>}
                </div></td>
                <td><select className={`guest-status-select tone-${invitationStatusTone(guest.statut_invitation)}`} aria-label={`Réponse de ${guest.nom}`} value={guest.statut_invitation} disabled={busyId === guest.invitation_id} onChange={e => void changeStatus(guest, e.target.value as StatutInvitation)}>
                  {invitationStatuses.map(status => <option key={status} value={status}>{invitationStatusLabel(status)}</option>)}
                </select></td>
                <td><div className="guest-send-state">
                  {guest.etat_envoi === 'ENVOYEE' && guest.date_envoi
                    ? <><Badge tone="green">Envoyée</Badge><small>{prettyDateTime(guest.date_envoi)}</small></>
                    : <Badge tone="gray">Non envoyée</Badge>}
                </div></td>
                <td><div className="guest-actions">
                  {guest.etat_envoi !== 'ENVOYEE' && <Button kind="secondary" onClick={() => setSending(guest)}><Send size={14} />Envoyer</Button>}
                  <button className="icon-btn danger" disabled={locked} aria-label={`Retirer ${guest.nom} de l’événement`}
                    title={locked ? 'Une invitation envoyée ou ayant reçu une réponse est conservée dans l’historique.' : 'Retirer de l’événement'}
                    onClick={() => setRemoving(guest)}><Trash2 size={16} /></button>
                </div></td>
              </tr>
            })}</tbody>
          </table></div>
          <div className="pagination"><span>{total} invité(s)</span><div><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Précédent</button><button className="current">{page + 1}</button><button disabled={page + 1 >= totalPages} onClick={() => setPage(value => value + 1)}>Suivant</button></div></div>
        </>}
    </section>
    {associating && <AssociateModal event={event} onClose={() => setAssociating(false)} onAssociated={() => refresh()} flash={flash} go={path => { setAssociating(false); go(path) }} />}
    {sending && <SendModal event={event} guest={sending} onClose={() => setSending(null)} onSent={message => { setSending(null); flash(message); refresh() }} />}
    {removing && <RemoveModal event={event} guest={removing} onClose={() => setRemoving(null)} onRemoved={() => { setRemoving(null); flash('Invité retiré de l’événement'); refresh() }} />}
  </div>
}

function AssociateModal({ event, onClose, onAssociated, flash, go }: { event: { id: number; title: string }; onClose: () => void; onAssociated: () => void; flash: (message: string) => void; go: (path: string) => void }) {
  const LIMIT = 8
  const [search, setSearch] = useState('')
  const [results, setResults] = useState<Invite[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [associated, setAssociated] = useState<number[]>([])
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    const timer = setTimeout(() => {
      setLoading(true)
      const filters = buildInviteSearch(search)
      const request = filters ? inviteService.search(filters, { limit: LIMIT, offset: 0 }) : inviteService.list({ limit: LIMIT, offset: 0 })
      request
        .then(result => { if (active) { setResults(result.items); setTotal(result.total); setError('') } })
        .catch(requestError => { if (active) setError(getInviteErrorMessage(requestError)) })
        .finally(() => { if (active) setLoading(false) })
    }, 250)
    return () => { active = false; clearTimeout(timer) }
  }, [search])

  const associate = async (invite: Invite) => {
    setBusyId(invite.id)
    setError('')
    try {
      await inviteService.associate(event.id, invite.id)
      setAssociated(current => [...current, invite.id])
      flash(`${invite.nom} associé à l’événement`)
      onAssociated()
    } catch (requestError) {
      // 409 : déjà associé à cet événement (aucun doublon n'est possible).
      setError(getInviteErrorMessage(requestError))
    } finally {
      setBusyId(null)
    }
  }

  return <Modal title="Associer un invité" onClose={onClose}>
    <div className="guest-modal-body">
      <label>Rechercher dans le répertoire<span className="search-box" style={{ width: '100%' }}><Search size={17} /><input autoFocus value={search} onChange={e => setSearch(e.target.value)} placeholder="Nom, e-mail ou téléphone..." /></span></label>
      {error && <p className="warning" style={{ margin: 0 }}>{error}</p>}
      <div className="guest-search-results">
        {loading && results.length === 0 ? <p className="guest-empty-line">Recherche en cours...</p>
          : results.length === 0 ? <p className="guest-empty-line">{search.trim() ? 'Aucun invité ne correspond à cette recherche.' : 'Le répertoire est vide.'}</p>
          : results.map(invite => <div className="guest-result" key={invite.id}>
            <div className="grow"><strong>{invite.nom}</strong><span>{[invite.email, invite.telephone].filter(Boolean).join(' · ')}</span></div>
            {associated.includes(invite.id) ? <Badge tone="green">Associé</Badge>
              : <Button kind="secondary" disabled={busyId === invite.id} onClick={() => void associate(invite)}><Plus size={14} />{busyId === invite.id ? 'Association...' : 'Associer'}</Button>}
          </div>)}
      </div>
      {total > LIMIT && <p className="description" style={{ padding: 0 }}>{LIMIT} premiers résultats sur {total} : affinez la recherche pour trouver un invité précis.</p>}
      <div className="form-actions"><Button kind="secondary" onClick={() => go('/invites')}><UserPlus size={15} />Créer un invité</Button><Button onClick={onClose}>Terminer</Button></div>
    </div>
  </Modal>
}

function SendModal({ event, guest, onClose, onSent }: { event: { id: number; title: string }; guest: InviteAssocie; onClose: () => void; onSent: (message: string) => void }) {
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const channels: CanalEnvoi[] = [...(guest.email ? ['EMAIL' as const] : []), ...(guest.telephone ? ['SMS' as const] : [])]

  const submit = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    const canal = String(new FormData(ev.currentTarget).get('canal')) as CanalEnvoi
    setSending(true)
    setError('')
    try {
      const result = await inviteService.send(event.id, guest.id, canal)
      // Honnêteté : en mode simulé, le service l'indique et aucun message réel n'est parti.
      onSent(result.envoi.simulation ? 'Envoi simulé : aucun message réel n’a été envoyé' : `Invitation envoyée par ${channelLabel(result.envoi.canal)}`)
    } catch (requestError) {
      setError(getInviteErrorMessage(requestError))
      setSending(false)
    }
  }

  return <Modal title="Envoyer l’invitation" onClose={onClose}>
    <form onSubmit={submit} className="form-grid">
      <label className="full">Invité<input value={guest.nom} disabled /></label>
      <label className="full">Événement<input value={event.title} disabled /></label>
      <label className="full">Canal d’envoi<select name="canal" defaultValue={channels[0]} required>{channels.map(channel => <option key={channel} value={channel}>{channelLabel(channel)}</option>)}</select></label>
      <p className="full guest-note">La date d’envoi n’est enregistrée que si le service d’envoi confirme l’envoi. Si ce service est une simulation, aucun message réel n’est envoyé et l’application vous le précise.</p>
      {error && <p className="full warning" style={{ margin: 0 }}>{error}</p>}
      <div className="form-actions full"><Button kind="secondary" onClick={onClose}>Annuler</Button><Button type="submit" disabled={sending || channels.length === 0}><Send size={15} />{sending ? 'Envoi...' : 'Envoyer l’invitation'}</Button></div>
    </form>
  </Modal>
}

function RemoveModal({ event, guest, onClose, onRemoved }: { event: { id: number; title: string }; guest: InviteAssocie; onClose: () => void; onRemoved: () => void }) {
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')

  const confirm = async () => {
    setDeleting(true)
    setError('')
    try {
      await inviteService.dissociate(event.id, guest.id)
      onRemoved()
    } catch (requestError) {
      // 409 : invitation déjà envoyée ou ayant reçu une réponse.
      setError(getInviteErrorMessage(requestError))
      setDeleting(false)
    }
  }

  return <DeleteModal title="Retirer l’invité" icon={<Users size={23} />} subject={guest.nom} detail={event.title}
    warning="L’invité sera retiré de cet événement. Il reste dans le répertoire des invités et pourra être associé de nouveau."
    confirmLabel="Je confirme le retrait de cet invité" actionLabel="Retirer l’invité" busyLabel="Retrait…" deleting={deleting} error={error} onClose={onClose} onConfirm={() => void confirm()} />
}
