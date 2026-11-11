import { FormEvent, ReactNode, useEffect, useState } from 'react'
import {
  BarChart3, Bell, Building2, CalendarDays, Check, ChevronDown, ChevronRight,
  CircleDollarSign, Clock3, ContactRound, CreditCard, FileText, LayoutDashboard,
  MapPin, Menu, MoreHorizontal, PanelLeftClose, PanelLeftOpen, Plus, Search,
  Settings, Trash2, TrendingUp, Users, UtensilsCrossed, WalletCards, X,
} from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import budgetService, { getBudgetErrorMessage, isBudgetNotFound } from './services/budgetService'
import evenementService, { getEvenementErrorMessage } from './services/evenementService'
import salleService from './services/salleService'
import type { BilanFinancier, Budget, Depense as ApiDepense } from './types/budget'
import type { Evenement } from './types/evenement'
import type { Salle, SalleForm } from './types/salle'
type EventItem = { id: number; organizerId: number; title: string; description: string; type: string; date: string; end: string; location: string; status: string; budget: number }
type Expense = { id: number; eventId: number; description: string; category: string; amount: number; date: string }

const initialExpenses: Expense[] = [
  { id: 1, eventId: 1, description: 'Location Palais des Congrès', category: 'Salle', amount: 8000, date: '2026-11-04' },
  { id: 2, eventId: 1, description: 'Déjeuner et pauses café', category: 'Traiteur', amount: 12000, date: '2026-11-12' },
  { id: 3, eventId: 1, description: 'Équipement audiovisuel', category: 'Matériel', amount: 5000, date: '2026-11-18' },
  { id: 4, eventId: 2, description: 'Hôtel et salle de réunion', category: 'Salle', amount: 9500, date: '2026-08-14' },
  { id: 5, eventId: 3, description: 'Campagne digitale', category: 'Communication', amount: 18000, date: '2026-04-09' },
  { id: 6, eventId: 3, description: 'Scénographie du salon', category: 'Matériel', amount: 22000, date: '2026-05-02' },
]

const nav = [
  ['Tableau de bord', '/', LayoutDashboard], ['Événements', '/evenements', CalendarDays],
  ['Salles', '/salles', Building2], ['Prestataires', '/prestataires', ContactRound],
  ['Invités', '/invites', Users], ['Budgets', '/budgets', WalletCards], ['Paramètres', '/parametres', Settings],
] as const
// const rooms = [
//   ['Palais des Congrès', 'Agadir', '1 200', '18 000 MAD', 'Disponible'],
//   ['Atlas Ballroom', 'Casablanca', '450', '12 500 MAD', 'Réservée'],
//   ['Riad Al Bahja', 'Marrakech', '280', '9 800 MAD', 'Disponible'],
//   ['Espace Bouregreg', 'Rabat', '650', '14 000 MAD', 'Disponible'],
// ]
const providers = [
  ['Saveurs du Maroc', 'Traiteur', 'Nadia El Idrissi', 'contact@saveurs.ma', 'Actif'],
  ['Atlas Events Tech', 'Technique', 'Youssef Amrani', 'hello@atlastech.ma', 'Actif'],
  ['Studio Jasmin', 'Décoration', 'Salma Bennis', 'salma@jasmin.ma', 'Actif'],
  ['SecurePro', 'Sécurité', 'Karim Lahlou', 'contact@securepro.ma', 'En attente'],
]
const guests = [
  ['Alaoui', 'Meryem', 'meryem.alaoui@um6p.ma', 'UM6P', 'VIP'],
  ['Benjelloun', 'Omar', 'omar@innovate.ma', 'Innovate Maroc', 'Professionnel'],
  ['El Fassi', 'Sara', 'sara@leconomiste.ma', "L'Économiste", 'Presse'],
  ['Tazi', 'Amine', 'amine.tazi@gmail.com', 'Indépendant', 'Particulier'],
]

const money = (n: number) => new Intl.NumberFormat('fr-FR').format(n) + ' MAD'
const prettyDate = (s: string) => new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(s))
const eventStatusLabel = (status: string) => ({ PLANIFIE: 'En préparation', CONFIRME: 'Confirmé', EN_COURS: 'En cours', TERMINE: 'Terminé', ANNULE: 'Annulé' }[status] || status)
const eventStatusTone = (status: string) => status === 'TERMINE' || status === 'ANNULE' ? 'gray' : status === 'EN_COURS' || status === 'CONFIRME' ? 'green' : 'blue'
const mapEvenement = (event: Evenement): EventItem => ({
  id: event.id,
  organizerId: event.organisateurId,
  title: event.titre,
  description: event.description || '',
  type: 'Événement',
  date: event.dateDebut,
  end: event.dateFin,
  location: event.lieu || 'Non défini',
  status: event.statut,
  budget: 0,
})
const toIsoDateTime = (value: FormDataEntryValue | null) => new Date(String(value)).toISOString()
const toLocalDateTime = (value: string) => {
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}

function Button({ children, onClick, kind = 'primary', type = 'button' }: { children: ReactNode; onClick?: () => void; kind?: 'primary' | 'secondary' | 'ghost' | 'danger'; type?: 'button' | 'submit' }) {
  return <button type={type} onClick={onClick} className={`btn btn-${kind}`}>{children}</button>
}
function Badge({ children, tone = 'blue' }: { children: ReactNode; tone?: string }) {
  return <span className={`badge badge-${tone}`}><span className="badge-dot" />{children}</span>
}
function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return <div className="modal-backdrop" onMouseDown={onClose}><div className="modal" onMouseDown={e => e.stopPropagation()}>
    <div className="modal-head"><div><p className="eyebrow">EventFlow</p><h2>{title}</h2></div><button className="icon-btn" onClick={onClose} aria-label="Fermer"><X size={19} /></button></div>{children}
  </div></div>
}
function EmptyAction({ icon: Icon, title, text, action, onClick }: any) {
  return <div className="empty"><div className="empty-icon"><Icon size={22} /></div><h3>{title}</h3><p>{text}</p><Button onClick={onClick}><Plus size={16} />{action}</Button></div>
}

function App() {
  const [path, setPath] = useState(location.pathname === '/' ? '/' : location.pathname)
  const [collapsed, setCollapsed] = useState(false)
  const [mobile, setMobile] = useState(false)
  const [profile, setProfile] = useState(false)
  const [notice, setNotice] = useState('')
  const [events, setEvents] = useState<EventItem[]>([])
  const [eventsLoading, setEventsLoading] = useState(true)
  const [eventsError, setEventsError] = useState('')
  const [expenses] = useState<Expense[]>(() => JSON.parse(localStorage.getItem('ef-expenses') || 'null') || initialExpenses)

  useEffect(() => {
    const syncPath = () => setPath(location.pathname)
    window.addEventListener('popstate', syncPath)
    return () => window.removeEventListener('popstate', syncPath)
  }, [])

  useEffect(() => {
    let active = true
    evenementService.list()
      .then(items => { if (active) { setEvents(items.map(mapEvenement)); setEventsError('') } })
      .catch(error => { if (active) setEventsError(getEvenementErrorMessage(error)) })
      .finally(() => { if (active) setEventsLoading(false) })
    return () => { active = false }
  }, [])

  const go = (to: string) => { history.pushState({}, '', to); setPath(to); setMobile(false) }
  const flash = (msg: string) => { setNotice(msg); setTimeout(() => setNotice(''), 2600) }
  const title = nav.find(n => n[1] === path)?.[0] || (path.startsWith('/evenements/') ? 'Détail de l’événement' : 'EventFlow')

  return <div className="app-shell">
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''} ${mobile ? 'mobile-open' : ''}`}>
      <div className="brand"><div className="brand-mark"><CalendarDays size={20} /></div><div className="brand-copy"><strong>EventFlow</strong><span>Gestion événementielle</span></div><button className="mobile-close" onClick={() => setMobile(false)}><X size={20} /></button></div>
      <nav>{nav.slice(0, -1).map(([label, href, Icon]) => <button key={href} onClick={() => go(href)} className={path === href || (href !== '/' && path.startsWith(href)) ? 'active' : ''}><Icon size={19} /><span>{label}</span>{label === 'Invités' && <em>24</em>}</button>)}</nav>
      <div className="side-bottom"><button onClick={() => go('/parametres')} className={path === '/parametres' ? 'active' : ''}><Settings size={19} /><span>Paramètres</span></button><button onClick={() => setCollapsed(!collapsed)} className="collapse">{collapsed ? <PanelLeftOpen size={19} /> : <PanelLeftClose size={19} />}<span>Réduire le menu</span></button></div>
    </aside>
    {mobile && <div className="scrim" onClick={() => setMobile(false)} />}
    <main className={collapsed ? 'main-wide' : ''}>
      <header>
        <div className="header-title"><button className="menu-btn" onClick={() => setMobile(true)}><Menu size={21} /></button><div><p>{path === '/' ? 'Vue d’ensemble' : 'EventFlow  /'}</p><h1>{title}</h1></div></div>
        <div className="header-actions"><div className="global-search"><Search size={17} /><input aria-label="Recherche globale" placeholder="Rechercher..." /><kbd>⌘ K</kbd></div><button className="icon-btn notification" onClick={() => flash('Vous avez 3 nouvelles notifications')}><Bell size={19} /><i /></button><div className="profile-wrap"><button className="profile-btn" onClick={() => setProfile(!profile)}><span>U</span><div><strong>User</strong><small>Organisateur</small></div><ChevronDown size={15} /></button>{profile && <div className="profile-menu"><button onClick={() => { go('/parametres'); setProfile(false) }}>Modifier mon profil</button><button onClick={() => flash('Session de démonstration active')}>Se déconnecter</button></div>}</div></div>
      </header>
      <div className="content">
        {path === '/' && <Dashboard events={events} expenses={expenses} go={go} />}
        {path === '/evenements' && <EventsPage events={events} setEvents={setEvents} loading={eventsLoading} loadError={eventsError} go={go} flash={flash} />}
        {path.startsWith('/evenements/') && <EventDetail event={events.find(e => e.id === Number(path.split('/')[2]))} loading={eventsLoading} setEvents={setEvents} go={go} flash={flash} />}
        {path === '/budgets' && <BudgetPage events={events} flash={flash} />}
        {path === '/salles' && <SallesPage flash={flash} />}
        {path === '/prestataires' && <DirectoryPage type="prestataires" rows={providers} flash={flash} />}
        {path === '/invites' && <DirectoryPage type="invites" rows={guests} flash={flash} />}
        {path === '/parametres' && <SettingsPage flash={flash} />}
      </div>
    </main>
    {notice && <div className="toast"><Check size={18} />{notice}</div>}
  </div>
}

function Dashboard({ events, expenses, go }: { events: EventItem[]; expenses: Expense[]; go: (p: string) => void }) {
  const totalBudget = events.reduce((s, e) => s + e.budget, 0), totalSpent = expenses.reduce((s, e) => s + e.amount, 0)
  const consumedPercentage = totalBudget ? Math.round(totalSpent / totalBudget * 100) : 0
  const chart = events.slice(0, 3).map(e => ({ name: e.title.split(' ').slice(0, 2).join(' '), budget: e.budget, dépenses: expenses.filter(x => x.eventId === e.id).reduce((s, x) => s + x.amount, 0) }))
  return <>
    <section className="welcome"><div><p className="eyebrow">Mardi 24 mars 2026</p><h2>Bonjour User,</h2><p>Voici l’essentiel de votre activité événementielle aujourd’hui.</p></div><Button onClick={() => go('/evenements')}><Plus size={17} />Créer un événement</Button></section>
    <div className="kpi-grid">
      <Kpi label="Total événements" value={String(events.length)} note="+2 ce trimestre" icon={CalendarDays} tone="indigo" />
      <Kpi label="Événements à venir" value={String(events.filter(e => e.status === 'PLANIFIE' || e.status === 'CONFIRME').length)} note="Planifiés et confirmés" icon={Clock3} tone="blue" />
      <Kpi label="Réservations confirmées" value="8" note="Sur 10 réservations" icon={Building2} tone="green" />
      <Kpi label="Invitations en attente" value="12" note="24 invitations envoyées" icon={Users} tone="orange" />
      <Kpi label="Budget total alloué" value={money(totalBudget)} note="Tous les événements" icon={WalletCards} tone="purple" />
      <Kpi label="Dépenses totales" value={money(totalSpent)} note={`${consumedPercentage} % du budget consommé`} icon={TrendingUp} tone="rose" />
    </div>
    <div className="dashboard-grid">
      <section className="card span-2"><CardHead title="Événements à venir" subtitle="Vos prochains rendez-vous" action="Voir tous" onClick={() => go('/evenements')} /><div className="event-list">{events.slice(0, 3).map(e => <button key={e.id} onClick={() => go(`/evenements/${e.id}`)} className="event-row"><div className="date-tile"><strong>{new Date(e.date).getDate()}</strong><span>{new Date(e.date).toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '')}</span></div><div className="grow"><strong>{e.title}</strong><span><MapPin size={14} />{e.location}</span></div><Badge tone={eventStatusTone(e.status)}>{eventStatusLabel(e.status)}</Badge><ChevronRight size={18} /></button>)}</div></section>
      <section className="card activity"><CardHead title="Activité récente" subtitle="Dernières mises à jour" /><Timeline /></section>
      <section className="card span-2 chart-card"><CardHead title="Répartition des budgets" subtitle="Budget alloué et dépenses par événement" /><div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><BarChart data={chart} barGap={6}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e8eaf0" /><XAxis dataKey="name" axisLine={false} tickLine={false} fontSize={12} /><YAxis axisLine={false} tickLine={false} fontSize={11} /><Tooltip formatter={(v) => money(Number(v))} /><Bar dataKey="budget" fill="#dfe4ff" radius={[5, 5, 0, 0]} /><Bar dataKey="dépenses" fill="#5266df" radius={[5, 5, 0, 0]} /></BarChart></ResponsiveContainer></div><div className="legend"><span><i className="budget-dot" />Budget alloué</span><span><i className="spent-dot" />Dépenses</span></div></section>
      <section className="card quick"><CardHead title="Actions rapides" subtitle="Accès directs" /><div className="quick-grid">{[[CalendarDays, 'Créer un événement', '/evenements'], [Building2, 'Réserver une salle', '/salles'], [ContactRound, 'Ajouter un prestataire', '/prestataires'], [Users, 'Ajouter un invité', '/invites'], [WalletCards, 'Créer un budget', '/budgets']].map(([Icon, label, url]: any) => <button key={label} onClick={() => go(url)}><Icon size={18} /><span>{label}</span><Plus size={15} /></button>)}</div></section>
    </div>
  </>
}
function Kpi({ label, value, note, icon: Icon, tone }: any) { return <div className="kpi"><div className={`kpi-icon ${tone}`}><Icon size={20} /></div><p>{label}</p><strong>{value}</strong><span>{note}</span></div> }
function CardHead({ title, subtitle, action, onClick }: any) { return <div className="card-head"><div><h3>{title}</h3><p>{subtitle}</p></div>{action && <Button kind="ghost" onClick={onClick}>{action}<ChevronRight size={16} /></Button>}</div> }
function Timeline() { return <div className="timeline">{[['Événement créé', 'Conférence IA 2026', 'Il y a 2 h', CalendarDays], ['Salle réservée', 'Palais des Congrès', 'Hier', Building2], ['Invitation confirmée', 'Meryem Alaoui', 'Hier', Check], ['Dépense enregistrée', 'Équipement audiovisuel', '22 mars', CreditCard]].map(([a, b, c, Icon]: any) => <div key={a}><span><Icon size={15} /></span><p><strong>{a}</strong><small>{b}</small></p><time>{c}</time></div>)}</div> }

function EventsPage({ events, setEvents, loading, loadError, go, flash }: any) {
  const [search, setSearch] = useState(''), [modal, setModal] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('')
  const filtered = events.filter((e: EventItem) => e.title.toLowerCase().includes(search.toLowerCase()))
  const submit = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    const form = new FormData(ev.currentTarget)
    setSaving(true)
    setError('')
    try {
      const created = await evenementService.create({
        organisateurId: 1,
        titre: String(form.get('title')),
        description: String(form.get('description') || ''),
        dateDebut: toIsoDateTime(form.get('date')),
        dateFin: toIsoDateTime(form.get('end')),
        lieu: String(form.get('location') || ''),
        statut: 'PLANIFIE',
      })
      setEvents((current: EventItem[]) => [...current, mapEvenement(created)])
      setModal(false)
      flash('Événement créé avec succès')
    } catch (requestError) {
      setError(getEvenementErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }
  return <><PageIntro eyebrow="Gestion" title="Événements" text="Planifiez et suivez tous vos événements depuis un seul espace." action="Créer un événement" onClick={() => setModal(true)} />
    {loadError && <div className="warning">{loadError}</div>}
    <section className="card table-card"><div className="toolbar"><label className="search-box"><Search size={17} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Rechercher un événement..." /></label><div className="filter-buttons"><select><option>Tous les types</option><option>Conférence</option><option>Séminaire</option></select><select><option>Tous les statuts</option><option>En préparation</option><option>Terminé</option></select></div></div>
      <div className="table-scroll"><table><thead><tr><th>Événement</th><th>Date</th><th>Lieu</th><th>Statut</th><th /></tr></thead><tbody>{!loading && filtered.map((e: EventItem) => <tr key={e.id} onClick={() => go(`/evenements/${e.id}`)}><td><strong>{e.title}</strong><small>EVT-{String(e.id).padStart(3, '0')}</small></td><td>{prettyDate(e.date)}</td><td><span className="with-icon"><MapPin size={14} />{e.location}</span></td><td><Badge tone={eventStatusTone(e.status)}>{eventStatusLabel(e.status)}</Badge></td><td><button className="icon-btn"><MoreHorizontal size={18} /></button></td></tr>)}</tbody></table>{loading && <p className="description">Chargement des événements...</p>}{!loading && !filtered.length && <p className="description">Aucun événement trouvé.</p>}</div><div className="pagination"><span>{filtered.length} événement(s)</span><div><button disabled>Précédent</button><button className="current">1</button><button disabled>Suivant</button></div></div></section>
    {modal && <Modal title="Créer un événement" onClose={() => setModal(false)}><form onSubmit={submit} className="form-grid"><label className="full">Titre<input name="title" maxLength={200} required placeholder="Ex. Forum des entrepreneurs" /></label><label className="full">Lieu<input name="location" maxLength={255} placeholder="Ville ou adresse" /></label><label>Date et heure de début<input name="date" type="datetime-local" required /></label><label>Date et heure de fin<input name="end" type="datetime-local" required /></label><label className="full">Description<textarea name="description" placeholder="Décrivez l’événement..." /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setModal(false)}>Annuler</Button><Button type="submit">{saving ? 'Création...' : 'Créer l’événement'}</Button></div></form></Modal>}</>
}

function EventDetail({ event, loading, setEvents, go, flash }: any) {
  const [tab, setTab] = useState('Vue d’ensemble'), [editing, setEditing] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('')

  if (loading) return <section className="card"><p className="description">Chargement de l’événement...</p></section>
  if (!event) return <section className="card"><div className="empty"><h3>Événement introuvable</h3><p>L’événement demandé n’existe pas ou a été supprimé.</p><Button onClick={() => go('/evenements')}>Retour aux événements</Button></div></section>

  const update = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    const form = new FormData(ev.currentTarget)
    setSaving(true)
    setError('')
    try {
      const updated = await evenementService.update(event.id, {
        titre: String(form.get('title')),
        description: String(form.get('description') || ''),
        dateDebut: toIsoDateTime(form.get('date')),
        dateFin: toIsoDateTime(form.get('end')),
        lieu: String(form.get('location') || ''),
        statut: String(form.get('status')),
      })
      setEvents((current: EventItem[]) => current.map(item => item.id === event.id ? mapEvenement(updated) : item))
      setEditing(false)
      flash('Événement modifié avec succès')
    } catch (requestError) {
      setError(getEvenementErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    if (!confirm(`Supprimer définitivement « ${event.title} » ?`)) return
    setError('')
    try {
      await evenementService.delete(event.id)
      setEvents((current: EventItem[]) => current.filter(item => item.id !== event.id))
      flash('Événement supprimé')
      go('/evenements')
    } catch (requestError) {
      setError(getEvenementErrorMessage(requestError))
    }
  }

  return <><button className="back-link" onClick={() => go('/evenements')}>← Retour aux événements</button>{error && <div className="warning">{error}</div>}<section className="event-hero"><div><div className="hero-badges"><Badge>{event.type}</Badge><Badge tone={eventStatusTone(event.status)}>{eventStatusLabel(event.status)}</Badge></div><h2>{event.title}</h2><p><CalendarDays size={16} />{prettyDate(event.date)} au {prettyDate(event.end)} <span /> <MapPin size={16} />{event.location}</p></div><Button kind="secondary" onClick={() => setEditing(true)}>Modifier l’événement</Button></section>
    <div className="tabs">{['Vue d’ensemble', 'Salles', 'Prestataires', 'Invités', 'Budget'].map(t => <button className={tab === t ? 'active' : ''} onClick={() => setTab(t)} key={t}>{t}</button>)}</div>
    {tab === 'Vue d’ensemble' && <div className="detail-grid"><section className="card"><CardHead title="À propos" subtitle="Informations générales" /><p className="description">{event.description || 'Aucune description fournie.'}</p><div className="info-grid"><div><span>Statut</span><strong>{eventStatusLabel(event.status)}</strong></div><div><span>Lieu</span><strong>{event.location}</strong></div><div><span>Date de début</span><strong>{prettyDate(event.date)}</strong></div><div><span>Date de fin</span><strong>{prettyDate(event.end)}</strong></div></div></section><section className="card"><CardHead title="Progression" subtitle="Préparation de l’événement" /><div className="progress-big"><div><strong>68 %</strong><span>Progression globale</span></div><div className="progress"><i style={{ width: '68%' }} /></div></div>{[['Salle réservée', true], ['Prestataires confirmés', true], ['Invitations envoyées', true], ['Budget finalisé', false]].map(([l, c]: any) => <div className="check-row" key={l}><span className={c ? 'done' : ''}>{c && <Check size={13} />}</span>{l}</div>)}</section></div>}
    {tab === 'Budget' && <BudgetDetail event={event} flash={flash} />}
    {!['Vue d’ensemble', 'Budget'].includes(tab) && <section className="card"><EmptyAction icon={tab === 'Salles' ? Building2 : tab === 'Invités' ? Users : ContactRound} title={`${tab} de l’événement`} text={`Les éléments associés à « ${event.title} » apparaîtront ici.`} action={`Ajouter ${tab === 'Invités' ? 'un invité' : 'un élément'}`} onClick={() => flash('Formulaire ouvert en mode démonstration')} /></section>}
    {editing && <Modal title="Modifier l’événement" onClose={() => setEditing(false)}><form onSubmit={update} className="form-grid"><label className="full">Titre<input name="title" maxLength={200} defaultValue={event.title} required /></label><label className="full">Lieu<input name="location" maxLength={255} defaultValue={event.location === 'Non défini' ? '' : event.location} /></label><label>Date et heure de début<input name="date" type="datetime-local" defaultValue={toLocalDateTime(event.date)} required /></label><label>Date et heure de fin<input name="end" type="datetime-local" defaultValue={toLocalDateTime(event.end)} required /></label><label>Statut<select name="status" defaultValue={event.status}>{['PLANIFIE', 'CONFIRME', 'EN_COURS', 'TERMINE', 'ANNULE'].map(status => <option value={status} key={status}>{eventStatusLabel(status)}</option>)}</select></label><label className="full">Description<textarea name="description" defaultValue={event.description} /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="danger" onClick={() => void remove()}>Supprimer</Button><Button kind="secondary" onClick={() => setEditing(false)}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement...' : 'Enregistrer'}</Button></div></form></Modal>}</>
}

function BudgetPage({ events, flash }: { events: EventItem[]; flash: (message: string) => void }) {
  const [selected, setSelected] = useState(events[0]?.id ?? 0)
  useEffect(() => {
    if (events.length && !events.some(event => event.id === selected)) setSelected(events[0].id)
  }, [events, selected])
  const event = events.find((e: EventItem) => e.id === Number(selected))
  return <><PageIntro eyebrow="Finances" title="Budgets" text="Contrôlez les budgets et les dépenses de chaque événement." />
    {event ? <><label className="event-select">Événement sélectionné<select value={selected} onChange={e => setSelected(Number(e.target.value))}>{events.map((item: EventItem) => <option value={item.id} key={item.id}>{item.title}</option>)}</select></label><BudgetDetail event={event} flash={flash} /></> : <section className="card"><EmptyAction icon={CalendarDays} title="Aucun événement" text="Créez un événement avant de lui attribuer un budget." action="Créer un événement" onClick={() => history.pushState({}, '', '/evenements')} /></section>}
  </>
}
function BudgetDetail({ event, flash }: { event: EventItem; flash: (message: string) => void }) {
  const [budget, setBudget] = useState<Budget | null>(null)
  const [list, setList] = useState<ApiDepense[]>([])
  const [report, setReport] = useState<BilanFinancier | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [modal, setModal] = useState(false)
  const [editBudget, setEditBudget] = useState(false)
  const [createBudget, setCreateBudget] = useState(false)

  const loadDetails = async (currentBudget: Budget) => {
    const [expensesResult, reportResult] = await Promise.all([
      budgetService.getExpenses(currentBudget.id),
      budgetService.getReport(currentBudget.id),
    ])
    setList(expensesResult)
    setReport(reportResult)
  }

  const load = async () => {
    setLoading(true)
    setError('')
    setLoadError('')
    try {
      const currentBudget = await budgetService.getByEventId(event.id)
      setBudget(currentBudget)
      await loadDetails(currentBudget)
    } catch (requestError) {
      if (isBudgetNotFound(requestError)) {
        setBudget(null)
        setList([])
        setReport(null)
      } else {
        setLoadError(getBudgetErrorMessage(requestError))
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [event.id])

  const create = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    setSaving(true)
    setError('')
    try {
      const amount = Number(new FormData(ev.currentTarget).get('budget'))
      const created = await budgetService.create({ evenementId: event.id, montantTotal: amount })
      setBudget(created)
      await loadDetails(created)
      setCreateBudget(false)
      flash('Budget créé avec succès')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const add = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    if (!budget) return
    const form = new FormData(ev.currentTarget)
    setSaving(true)
    setError('')
    try {
      await budgetService.createExpense(budget.id, {
        description: String(form.get('description')),
        categorie: String(form.get('category')),
        montant: Number(form.get('amount')),
        dateDepense: String(form.get('date')),
        statut: 'PAYEE',
      })
      await loadDetails(budget)
      setModal(false)
      flash('Dépense ajoutée et bilan mis à jour')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const update = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    if (!budget) return
    setSaving(true)
    setError('')
    try {
      const amount = Number(new FormData(ev.currentTarget).get('budget'))
      const updated = await budgetService.update(budget.id, { montantTotal: amount })
      setBudget(updated)
      await loadDetails(updated)
      setEditBudget(false)
      flash('Budget mis à jour')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const remove = async (id: number) => {
    if (!budget || !confirm('Supprimer définitivement cette dépense ?')) return
    setError('')
    try {
      await budgetService.deleteExpense(id)
      await loadDetails(budget)
      flash('Dépense supprimée')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    }
  }

  if (loading) return <section className="card"><p className="description">Chargement du budget...</p></section>
  if (loadError) return <section className="card"><div className="empty"><h3>Impossible de charger le budget</h3><p>{loadError}</p><Button onClick={() => void load()}>Réessayer</Button></div></section>
  if (!budget) return <section className="card"><EmptyAction icon={WalletCards} title="Aucun budget" text={`Aucun budget n’est encore défini pour « ${event.title} ».`} action="Créer le budget" onClick={() => setCreateBudget(true)} />{createBudget && <Modal title="Créer le budget" onClose={() => setCreateBudget(false)}><form onSubmit={create} className="form-grid"><label className="full">Montant total (MAD)<input name="budget" type="number" min="0.01" step="0.01" required /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setCreateBudget(false)}>Annuler</Button><Button type="submit">{saving ? 'Création...' : 'Créer'}</Button></div></form></Modal>}</section>

  const spent = report?.totalDepenses ?? list.reduce((sum, item) => sum + item.montant, 0)
  const balance = report?.soldeRestant ?? budget.montantTotal - spent
  const pct = budget.montantTotal ? Math.round(spent / budget.montantTotal * 100) : 0
  const cats = Object.entries(list.reduce<Record<string, number>>((totals, item) => ({ ...totals, [item.categorie]: (totals[item.categorie] || 0) + item.montant }), {})).map(([name, value]) => ({ name, value }))

  return <div className="budget-stack">{error && <div className="warning">{error}</div>}<div className="finance-kpis"><Kpi label="Budget total" value={money(budget.montantTotal)} note="Montant alloué" icon={WalletCards} tone="indigo" /><Kpi label="Total des dépenses" value={money(spent)} note={`${list.length} dépenses enregistrées`} icon={CreditCard} tone="rose" /><Kpi label="Solde restant" value={money(balance)} note={balance < 0 ? 'Dépassement à surveiller' : 'Disponible'} icon={CircleDollarSign} tone={balance < 0 ? 'rose' : 'green'} /><Kpi label="Budget consommé" value={`${pct} %`} note={pct > 100 ? 'Budget dépassé' : 'Consommation actuelle'} icon={BarChart3} tone="orange" /></div>
    <section className="card consumption"><div className="card-head"><div><h3>Consommation du budget</h3><p>{money(spent)} dépensés sur {money(budget.montantTotal)}</p></div><Button kind="secondary" onClick={() => setEditBudget(true)}>Modifier le budget</Button></div><div className="progress"><i className={pct > 100 ? 'over' : ''} style={{ width: `${Math.min(pct, 100)}%` }} /></div><div className="progress-labels"><span>0 MAD</span><strong>{pct} % consommé</strong><span>{money(budget.montantTotal)}</span></div>{balance < 0 && <div className="warning">Le budget est dépassé de {money(Math.abs(balance))}. Vérifiez les dernières dépenses.</div>}</section>
    <div className="budget-grid"><section className="card expense-card"><div className="card-head"><div><h3>Dépenses</h3><p>Données enregistrées dans le service Budget</p></div><Button onClick={() => setModal(true)}><Plus size={16} />Ajouter une dépense</Button></div>{list.length ? <div className="table-scroll"><table><thead><tr><th>Description</th><th>Catégorie</th><th>Montant</th><th>Date</th><th /></tr></thead><tbody>{list.map((item: ApiDepense) => <tr key={item.id}><td><strong>{item.description}</strong></td><td><Badge tone="gray">{item.categorie}</Badge></td><td><strong>{money(item.montant)}</strong></td><td>{prettyDate(item.dateDepense)}</td><td><button className="icon-btn danger" onClick={() => void remove(item.id)}><Trash2 size={16} /></button></td></tr>)}</tbody></table></div> : <EmptyAction icon={CreditCard} title="Aucune dépense" text="Ajoutez une première dépense à ce budget." action="Ajouter" onClick={() => setModal(true)} />}</section>
      <section className="card donut"><CardHead title="Par catégorie" subtitle="Répartition des dépenses" /><div className="donut-wrap"><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={cats} dataKey="value" innerRadius={56} outerRadius={78} paddingAngle={3}>{cats.map((_, i) => <Cell key={i} fill={['#5266df', '#8b5cf6', '#f59e0b', '#10b981', '#ef5a67'][i % 5]} />)}</Pie><Tooltip formatter={(value) => money(Number(value))} /></PieChart></ResponsiveContainer><div><strong>{money(spent)}</strong><span>Total</span></div></div><div className="cat-list">{cats.map((category, i) => <div key={category.name}><span><i style={{ background: ['#5266df', '#8b5cf6', '#f59e0b', '#10b981', '#ef5a67'][i % 5] }} />{category.name}</span><strong>{money(category.value)}</strong></div>)}</div></section></div>
    {modal && <Modal title="Ajouter une dépense" onClose={() => setModal(false)}><form onSubmit={add} className="form-grid"><label className="full">Description<input name="description" maxLength={255} required placeholder="Ex. Location de matériel" /></label><label>Catégorie<select name="category">{['Salle', 'Traiteur', 'Matériel', 'Transport', 'Communication', 'Hébergement', 'Autres'].map(category => <option key={category}>{category}</option>)}</select></label><label>Montant (MAD)<input name="amount" type="number" min="0.01" step="0.01" required /></label><label className="full">Date de dépense<input name="date" type="date" required /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setModal(false)}>Annuler</Button><Button type="submit">{saving ? 'Ajout...' : 'Ajouter la dépense'}</Button></div></form></Modal>}
    {editBudget && <Modal title="Modifier le budget" onClose={() => setEditBudget(false)}><form onSubmit={update} className="form-grid"><label className="full">Montant total (MAD)<input name="budget" type="number" min="0.01" step="0.01" defaultValue={budget.montantTotal} required /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setEditBudget(false)}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement...' : 'Enregistrer'}</Button></div></form></Modal>}</div>
}

function SallesPage({ flash }: { flash: (message: string) => void }) {
  const [salles, setSalles] = useState<Salle[]>([])
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState(false)
  const [editing, setEditing] = useState<Salle | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const loadSalles = async () => {
    try {
      setError('')
      const data = await salleService.getAll()
      setSalles(data)
    } catch (err: any) {
      setError(err.response?.data?.message || 'Impossible de charger les salles.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadSalles()
  }, [])

  const filtered = salles.filter(s =>
    `${s.nom} ${s.adresse}`.toLowerCase().includes(search.toLowerCase())
  )

  const openCreate = () => {
    setEditing(null)
    setError('')
    setModal(true)
  }

  const openEdit = (salle: Salle) => {
    setEditing(salle)
    setError('')
    setModal(true)
  }

  const submit = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    const form = new FormData(ev.currentTarget)

    const data: SalleForm = {
      nom: String(form.get('nom')).trim(),
      capacite: Number(form.get('capacite')),
      adresse: String(form.get('adresse')).trim(),
      prix: Number(form.get('prix')),
    }

    try {
      setSaving(true)
      setError('')

      if (editing) {
        await salleService.update(editing.id, data)
        flash('Salle modifiée avec succès')
      } else {
        await salleService.create(data)
        flash('Salle ajoutée avec succès')
      }

      setModal(false)
      setEditing(null)
      await loadSalles()
    } catch (err: any) {
      setError(
        err.response?.data?.message ||
        'Une erreur est survenue lors de l’enregistrement.'
      )
    } finally {
      setSaving(false)
    }
  }

  const remove = async (salle: Salle) => {
    if (!confirm(`Voulez-vous supprimer la salle « ${salle.nom} » ?`)) return

    try {
      await salleService.delete(salle.id)
      flash('Salle supprimée avec succès')
      await loadSalles()
    } catch (err: any) {
      const message = err.response?.data?.message ||
        'Impossible de supprimer cette salle.'
      flash(message)
    }
  }

  return <>
    <PageIntro
      eyebrow="Répertoire"
      title="Salles"
      text="Gérez les lieux et leurs disponibilités."
      action="Ajouter une salle"
      onClick={openCreate}
    />

    <section className="card table-card">
      <div className="toolbar">
        <label className="search-box">
          <Search size={17} />
          <input
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Rechercher une salle..."
          />
        </label>
      </div>

      {loading ? (
        <p className="description">Chargement des salles...</p>
      ) : error && salles.length === 0 ? (
        <div className="empty">
          <h3>Erreur de chargement</h3>
          <p>{error}</p>
          <Button onClick={loadSalles}>Réessayer</Button>
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty">
          <div className="empty-icon"><Building2 size={22} /></div>
          <h3>Aucune salle trouvée</h3>
          <p>Ajoutez une salle ou modifiez votre recherche.</p>
          <Button onClick={openCreate}>
            <Plus size={16} />Ajouter une salle
          </Button>
        </div>
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Nom de la salle</th>
                <th>Adresse</th>
                <th>Capacité</th>
                <th>Prix</th>
                <th>Statut</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(salle => (
                <tr key={salle.id}>
                  <td>
                    <strong>{salle.nom}</strong>
                    <small>SAL-{String(salle.id).padStart(3, '0')}</small>
                  </td>
                  <td>
                    <span className="with-icon">
                      <MapPin size={14} />
                      {salle.adresse}
                    </span>
                  </td>
                  <td>{salle.capacite} personnes</td>
                  <td><strong>{money(salle.prix)}</strong></td>
                  <td>
                    <Badge
                      tone={salle.disponibilite === 'Disponible' ? 'green' : 'orange'}
                    >
                      {salle.disponibilite === 'Reservee' ? 'Réservée' : 'Disponible'}
                    </Badge>
                  </td>
                  <td>
                    <div className="filter-buttons">
                      <button
                        className="icon-btn"
                        title="Modifier"
                        onClick={() => openEdit(salle)}
                      >
                        <Settings size={17} />
                      </button>
                      <button
                        className="icon-btn danger"
                        title="Supprimer"
                        onClick={() => remove(salle)}
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="pagination">
        <span>{filtered.length} résultat(s)</span>
      </div>
    </section>

    {modal && (
      <Modal
        title={editing ? 'Modifier une salle' : 'Ajouter une salle'}
        onClose={() => {
          if (!saving) {
            setModal(false)
            setError('')
          }
        }}
      >
        <form onSubmit={submit} className="form-grid">
          <label className="full">
            Nom de la salle
            <input
              name="nom"
              required
              maxLength={150}
              defaultValue={editing?.nom || ''}
              placeholder="Ex. Salle de conférence"
            />
          </label>

          <label>
            Capacité (personnes)
            <input
              name="capacite"
              type="number"
              min="1"
              required
              defaultValue={editing?.capacite ?? ''}
              placeholder="Ex. 100"
            />
          </label>

          <label>
            Prix (MAD)
            <input
              name="prix"
              type="number"
              min="0.01"
              step="0.01"
              required
              defaultValue={editing?.prix ?? ''}
              placeholder="Ex. 2000"
            />
          </label>

          <label className="full">
            Adresse
            <input
              name="adresse"
              required
              defaultValue={editing?.adresse || ''}
              placeholder="Ex. Agadir, Maroc"
            />
          </label>

          {error && (
            <p className="full warning">{error}</p>
          )}

          <div className="form-actions full">
            <Button
              kind="secondary"
              onClick={() => setModal(false)}
            >
              Annuler
            </Button>
            <Button type="submit">
              {saving ? 'Enregistrement...' : editing ? 'Enregistrer' : 'Ajouter la salle'}
            </Button>
          </div>
        </form>
      </Modal>
    )}
  </>
}

function DirectoryPage({ type, rows, flash }: any) {
  const copy: any = { salles: ['Salles', 'Gérez les lieux et leurs disponibilités.', ['Nom de la salle', 'Ville', 'Capacité', 'Prix', 'Disponibilité'], 'Ajouter une salle', Building2], prestataires: ['Prestataires', 'Centralisez vos partenaires et contrats.', ['Prestataire', 'Catégorie', 'Contact', 'E-mail', 'Statut'], 'Ajouter un prestataire', ContactRound], invites: ['Invités', 'Gérez les participants et leurs invitations.', ['Nom', 'Prénom', 'E-mail', 'Organisation', 'Type'], 'Ajouter un invité', Users] }
  const [title, text, heads, action, Icon] = copy[type], [search, setSearch] = useState(''), [open, setOpen] = useState(false)
  const filtered = rows.filter((r: string[]) => r.join(' ').toLowerCase().includes(search.toLowerCase()))
  return <><PageIntro eyebrow="Répertoire" title={title} text={text} action={action} onClick={() => setOpen(true)} />{type === 'invites' && <div className="mini-stats"><div><strong>24</strong><span>Invitations envoyées</span></div><div><strong>17</strong><span>Confirmations</span></div><div><strong>3</strong><span>Refus</span></div><div><strong>4</strong><span>En attente</span></div></div>}<section className="card table-card"><div className="toolbar"><label className="search-box"><Search size={17} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder={`Rechercher dans ${title.toLowerCase()}...`} /></label><select><option>Tous les statuts</option><option>Disponible</option><option>Actif</option></select></div><div className="table-scroll"><table><thead><tr>{heads.map((h: string) => <th key={h}>{h}</th>)}<th /></tr></thead><tbody>{filtered.map((r: string[], i: number) => <tr key={i}>{r.map((c, j) => <td key={j}>{j === 0 ? <strong>{c}</strong> : j === r.length - 1 ? <Badge tone={c === 'Réservée' ? 'orange' : 'green'}>{c}</Badge> : c}</td>)}<td><button className="icon-btn" onClick={() => flash('Fiche détaillée ouverte')}><MoreHorizontal size={18} /></button></td></tr>)}</tbody></table></div><div className="pagination"><span>{filtered.length} résultats</span><div><button className="current">1</button></div></div></section>{open && <Modal title={action} onClose={() => setOpen(false)}><div className="empty compact"><div className="empty-icon"><Icon size={22} /></div><h3>Formulaire de démonstration</h3><p>Les données seront enregistrées localement dans la version finale de ce module.</p><Button onClick={() => { setOpen(false); flash(`${title.slice(0, -1)} ajouté avec succès`) }}>Enregistrer</Button></div></Modal>}</>
}
function SettingsPage({ flash }: any) { return <><PageIntro eyebrow="Compte" title="Paramètres" text="Mettez à jour vos informations d’organisateur." /><section className="card settings-card"><div className="avatar-large">U</div><form className="form-grid" onSubmit={e => { e.preventDefault(); flash('Profil mis à jour avec succès') }}><label>Prénom<input defaultValue="User" /></label><label>Nom<input defaultValue="" /></label><label>Adresse e-mail<input type="email" defaultValue="user@eventflow.ma" /></label><label>Téléphone<input defaultValue="+212 6 12 34 56 78" /></label><label className="full">Organisation<input defaultValue="EventFlow Maroc" /></label><div className="form-actions full"><Button type="submit">Enregistrer les modifications</Button></div></form></section></> }
function PageIntro({ eyebrow, title, text, action, onClick }: any) { return <section className="page-intro"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2><p>{text}</p></div>{action && <Button onClick={onClick}><Plus size={17} />{action}</Button>}</section> }

export default App
