import LoginPage from './LoginPage'
import { getSession, clearSession, type AuthUser } from './services/authService'
import { createPortal } from 'react-dom'
import { FormEvent, Fragment, ReactNode, useEffect, useState } from 'react'
import {
  AlertTriangle, BarChart3, Bell, Building2, CalendarDays, Check, ChevronDown, ChevronRight,
  CircleDollarSign, Clock3, ContactRound, CreditCard, FileText, LayoutDashboard,
  Download, Eye, Filter, GripVertical, Info, Layers3, MapPin, Menu, MessageSquare, MoreHorizontal,
  PanelLeftClose, PanelLeftOpen, Pencil, Plus, Search, Settings, ShieldCheck, Trash2,
  TrendingUp, Users, UtensilsCrossed, WalletCards, X,
} from 'lucide-react'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import budgetService, { getBudgetErrorMessage, isBudgetNotFound } from './services/budgetService'
import evenementService, { getEvenementErrorMessage } from './services/evenementService'
import prestataireService, { getPrestataireErrorMessage } from './services/prestataireService'
import salleService from './services/salleService'
import type { BilanFinancier, Budget, BudgetAlertLevel, BudgetStatistiques, Depense as ApiDepense, DepenseFilters, DepenseForm, DepenseStatut } from './types/budget'
import type { Evenement, EvenementStatut } from './types/evenement'
import type { Prestataire, PrestataireCreateForm, UtilisationPrestataire } from './types/prestataire'
import type { Salle, SalleForm } from './types/salle'
type EventItem = { id: number; organizerId: number; title: string; description: string; type: string; date: string; end: string; location: string; status: EvenementStatut; budget: number }
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
const nextEventStatuses = (status: EvenementStatut): EvenementStatut[] => ({
  PLANIFIE: ['CONFIRME', 'ANNULE'],
  CONFIRME: ['EN_COURS', 'ANNULE'],
  EN_COURS: ['TERMINE', 'ANNULE'],
  TERMINE: [],
  ANNULE: [],
}[status])
const expenseStatuses: DepenseStatut[] = ['PREVUE', 'EN_ATTENTE', 'PAYEE', 'ANNULEE']
const expenseStatusLabel = (status: DepenseStatut) => ({ PREVUE: 'Prévue', EN_ATTENTE: 'En attente', PAYEE: 'Payée', ANNULEE: 'Annulée' }[status])
const expenseStatusTone = (status: DepenseStatut) => ({ PREVUE: 'blue', EN_ATTENTE: 'orange', PAYEE: 'green', ANNULEE: 'gray' }[status])
const budgetAlertLabel = (level: BudgetAlertLevel) => ({ NORMAL: 'Situation normale', ATTENTION: 'Seuil de vigilance', LIMITE_ATTEINTE: 'Limite atteinte', DEPASSE: 'Budget dépassé' }[level])
const budgetAlertTone = (level: BudgetAlertLevel) => level === 'NORMAL' ? 'green' : level === 'ATTENTION' ? 'orange' : 'rose'
const expensePayload = (form: FormData): DepenseForm => ({
  description: String(form.get('description')),
  categorie: String(form.get('category')),
  montant: Number(form.get('amount')),
  dateDepense: String(form.get('date')),
  statut: String(form.get('status')) as DepenseStatut,
  commentaire: String(form.get('comment') || '') || undefined,
})
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
function Modal({ title, children, onClose, wide = false }: { title: string; children: ReactNode; onClose: () => void; wide?: boolean }) {
  return <div className="modal-backdrop" onMouseDown={onClose}><div className={`modal ${wide ? 'modal-wide' : ''}`} onMouseDown={e => e.stopPropagation()}>
    <div className="modal-head"><div><p className="eyebrow">EventFlow</p><h2>{title}</h2></div><button className="icon-btn" onClick={onClose} aria-label="Fermer"><X size={19} /></button></div>{children}
  </div></div>
}
function EmptyAction({ icon: Icon, title, text, action, onClick }: any) {
  return <div className="empty"><div className="empty-icon"><Icon size={22} /></div><h3>{title}</h3><p>{text}</p><Button onClick={onClick}><Plus size={16} />{action}</Button></div>
}

function AuthenticatedApp({ user }: { user: AuthUser }) {
  const [path, setPath] = useState(location.pathname === '/' ? '/' : location.pathname)
  const [collapsed, setCollapsed] = useState(false)
  const [mobile, setMobile] = useState(false)
  const [profile, setProfile] = useState(false)
  const [notice, setNotice] = useState('')
  const [events, setEvents] = useState<EventItem[]>([])
  const [eventsLoading, setEventsLoading] = useState(true)
  const [eventsError, setEventsError] = useState('')
  const [prestataires, setPrestataires] = useState<Prestataire[]>([])
  const [prestatairesLoading, setPrestatairesLoading] = useState(true)
  const [prestatairesError, setPrestatairesError] = useState('')
  const [expenses] = useState<Expense[]>(() => JSON.parse(localStorage.getItem('ef-expenses') || 'null') || initialExpenses)

  useEffect(() => {
    const syncPath = () => setPath(location.pathname)
    window.addEventListener('popstate', syncPath)
    return () => window.removeEventListener('popstate', syncPath)
  }, [])

  useEffect(() => {
    let active = true
    evenementService.list()
      .then(result => { if (active) { setEvents(result.content.map(mapEvenement)); setEventsError('') } })
      .catch(error => { if (active) setEventsError(getEvenementErrorMessage(error)) })
      .finally(() => { if (active) setEventsLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    prestataireService.list()
      .then(result => { if (active) { setPrestataires(result); setPrestatairesError('') } })
      .catch(error => { if (active) setPrestatairesError(getPrestataireErrorMessage(error)) })
      .finally(() => { if (active) setPrestatairesLoading(false) })
    return () => { active = false }
  }, [])

  const go = (to: string) => { history.pushState({}, '', to); setPath(new URL(to, location.origin).pathname); setMobile(false) }
  const flash = (msg: string) => { setNotice(msg); setTimeout(() => setNotice(''), 2600) }
  const title = path === '/budgets/depenses'
    ? 'Gestion des dépenses'
    : path === '/prestataires'
      ? 'Liste des prestataires'
      : path === '/prestataires/categories'
        ? 'Catégories'
        : path === '/prestataires/utilisations'
          ? 'Utilisation par événement'
          : nav.find(n => n[1] === path)?.[0] || (path.startsWith('/evenements/') ? 'Détail de l’événement' : 'EventFlow')

  return <div className="app-shell">
    <aside className={`sidebar ${collapsed ? 'collapsed' : ''} ${mobile ? 'mobile-open' : ''}`}>
      <div className="brand"><div className="brand-mark"><CalendarDays size={20} /></div><div className="brand-copy"><strong>EventFlow</strong><span>Gestion événementielle</span></div><button className="mobile-close" onClick={() => setMobile(false)}><X size={20} /></button></div>
      <nav>{nav.slice(0, -1).map(([label, href, Icon]) => <Fragment key={href}>
        <button onClick={() => go(href)} className={path === href || (href !== '/' && path.startsWith(href)) ? 'active' : ''}><Icon size={19} /><span>{label}</span>{label === 'Invités' && <em>24</em>}</button>
        {href === '/prestataires' && <div className="sidebar-subnav">
          <button onClick={() => go('/prestataires')} className={path === '/prestataires' ? 'active' : ''}><ContactRound size={16} /><span>Liste</span></button>
          <button onClick={() => go('/prestataires/categories')} className={path === '/prestataires/categories' ? 'active' : ''}><Layers3 size={16} /><span>Catégories</span></button>
          <button onClick={() => go('/prestataires/utilisations')} className={path === '/prestataires/utilisations' ? 'active' : ''}><CalendarDays size={16} /><span>Utilisation par événement</span></button>
        </div>}
      </Fragment>)}</nav>
      <div className="side-bottom"><button onClick={() => go('/parametres')} className={path === '/parametres' ? 'active' : ''}><Settings size={19} /><span>Paramètres</span></button><button onClick={() => setCollapsed(!collapsed)} className="collapse">{collapsed ? <PanelLeftOpen size={19} /> : <PanelLeftClose size={19} />}<span>Réduire le menu</span></button></div>
    </aside>
    {mobile && <div className="scrim" onClick={() => setMobile(false)} />}
    <main className={collapsed ? 'main-wide' : ''}>
      <header>
        <div className="header-title"><button className="menu-btn" onClick={() => setMobile(true)}><Menu size={21} /></button><div><p>{path === '/' ? 'Vue d’ensemble' : 'EventFlow  /'}</p><h1>{title}</h1></div></div>
        <div className="header-actions"><div className="global-search"><Search size={17} /><input aria-label="Recherche globale" placeholder="Rechercher..." /><kbd>⌘ K</kbd></div><button className="icon-btn notification" onClick={() => flash('Vous avez 3 nouvelles notifications')}><Bell size={19} /><i /></button><div className="profile-wrap"><button className="profile-btn" onClick={() => setProfile(!profile)}><span>{user.nom.charAt(0).toUpperCase()}</span><div><strong>{user.nom}</strong><small>Organisateur</small></div><ChevronDown size={15} /></button>{profile && <div className="profile-menu"><button onClick={() => { go('/parametres'); setProfile(false) }}>Modifier mon profil</button><button onClick={clearSession}>Se déconnecter</button></div>}</div></div>
      </header>
      <div className="content">
        {path === '/' && <Dashboard events={events} expenses={expenses} go={go} />}
        {path === '/evenements' && <EventsPage events={events} setEvents={setEvents} loading={eventsLoading} loadError={eventsError} go={go} flash={flash} />}
        {path.startsWith('/evenements/') && <EventDetail event={events.find(e => e.id === Number(path.split('/')[2]))} loading={eventsLoading} setEvents={setEvents} go={go} flash={flash} />}
        {path === '/budgets' && <BudgetPage events={events} flash={flash} go={go} />}
        {path === '/budgets/depenses' && <ExpenseManagementPage events={events} flash={flash} />}
        {path === '/salles' && <SallesPage flash={flash} />}
        {path === '/prestataires' && <PrestatairesPage prestataires={prestataires} loading={prestatairesLoading} loadError={prestatairesError} onCreated={provider => setPrestataires(current => [...current, provider])} flash={flash} />}
        {path === '/prestataires/categories' && <PrestataireCategoriesPage prestataires={prestataires} loading={prestatairesLoading} loadError={prestatairesError} />}
        {path === '/prestataires/utilisations' && <PrestataireUtilisationsPage />}
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
  const [search, setSearch] = useState(''), [statusFilter, setStatusFilter] = useState<EvenementStatut | ''>(''), [page, setPage] = useState(0), [result, setResult] = useState<EventItem[] | null>(null), [totalPages, setTotalPages] = useState(1), [totalElements, setTotalElements] = useState(0), [searching, setSearching] = useState(false), [modal, setModal] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearching(true)
      evenementService.list({ q: search || undefined, statut: statusFilter || undefined, page, size: 10, sort: 'dateDebut,asc' })
        .then(response => { setResult(response.content.map(mapEvenement)); setTotalPages(response.totalPages); setTotalElements(response.totalElements); setError('') })
        .catch(requestError => setError(getEvenementErrorMessage(requestError)))
        .finally(() => setSearching(false))
    }, 250)
    return () => clearTimeout(timer)
  }, [search, statusFilter, page])
  const filtered = result ?? events
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
    <section className="card table-card"><div className="toolbar"><label className="search-box"><Search size={17} /><input value={search} onChange={e => { setSearch(e.target.value); setPage(0) }} placeholder="Rechercher un événement..." /></label><div className="filter-buttons"><select value={statusFilter} onChange={e => { setStatusFilter(e.target.value as EvenementStatut | ''); setPage(0) }}><option value="">Tous les statuts</option>{(['PLANIFIE', 'CONFIRME', 'EN_COURS', 'TERMINE', 'ANNULE'] as EvenementStatut[]).map(status => <option key={status} value={status}>{eventStatusLabel(status)}</option>)}</select></div></div>
      <div className="table-scroll"><table><thead><tr><th>Événement</th><th>Date</th><th>Lieu</th><th>Statut</th><th /></tr></thead><tbody>{!loading && filtered.map((e: EventItem) => <tr key={e.id} onClick={() => go(`/evenements/${e.id}`)}><td><strong>{e.title}</strong><small>EVT-{String(e.id).padStart(3, '0')}</small></td><td>{prettyDate(e.date)}</td><td><span className="with-icon"><MapPin size={14} />{e.location}</span></td><td><Badge tone={eventStatusTone(e.status)}>{eventStatusLabel(e.status)}</Badge></td><td><button className="icon-btn"><MoreHorizontal size={18} /></button></td></tr>)}</tbody></table>{(loading || searching) && <p className="description">Chargement des événements...</p>}{!loading && !searching && !filtered.length && <p className="description">Aucun événement trouvé.</p>}</div><div className="pagination"><span>{totalElements || filtered.length} événement(s)</span><div><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Précédent</button><button className="current">{page + 1}</button><button disabled={page + 1 >= totalPages} onClick={() => setPage(value => value + 1)}>Suivant</button></div></div></section>
    {modal && <Modal title="Créer un événement" onClose={() => setModal(false)}><form onSubmit={submit} className="form-grid"><label className="full">Titre<input name="title" maxLength={200} required placeholder="Ex. Forum des entrepreneurs" /></label><label className="full">Lieu<input name="location" maxLength={255} placeholder="Ville ou adresse" /></label><label>Date et heure de début<input name="date" type="datetime-local" required /></label><label>Date et heure de fin<input name="end" type="datetime-local" required /></label><label className="full">Description<textarea name="description" placeholder="Décrivez l’événement..." /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setModal(false)}>Annuler</Button><Button type="submit">{saving ? 'Création...' : 'Créer l’événement'}</Button></div></form></Modal>}</>
}

function EventDetail({ event, loading, setEvents, go, flash }: any) {
  const [tab, setTab] = useState('Vue d’ensemble'), [editing, setEditing] = useState(false), [saving, setSaving] = useState(false), [changingStatus, setChangingStatus] = useState(false), [error, setError] = useState('')

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
        statut: event.status,
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

  const changeStatus = async (statut: EvenementStatut) => {
    setChangingStatus(true)
    setError('')
    try {
      const updated = await evenementService.updateStatus(event.id, statut)
      setEvents((current: EventItem[]) => current.map(item => item.id === event.id ? mapEvenement(updated) : item))
      flash(`Statut modifié : ${eventStatusLabel(statut)}`)
    } catch (requestError) {
      setError(getEvenementErrorMessage(requestError))
    } finally {
      setChangingStatus(false)
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

  const allowedStatuses = nextEventStatuses(event.status)
  return <><button className="back-link" onClick={() => go('/evenements')}>← Retour aux événements</button>{error && <div className="warning">{error}</div>}<section className="event-hero"><div><div className="hero-badges"><Badge>{event.type}</Badge><Badge tone={eventStatusTone(event.status)}>{eventStatusLabel(event.status)}</Badge></div><h2>{event.title}</h2><p><CalendarDays size={16} />{prettyDate(event.date)} au {prettyDate(event.end)} <span /> <MapPin size={16} />{event.location}</p></div>{allowedStatuses.length > 0 && <div className="hero-badges"><select aria-label="Modifier le statut" value="" disabled={changingStatus} onChange={e => e.target.value && void changeStatus(e.target.value as EvenementStatut)}><option value="">Changer le statut…</option>{allowedStatuses.map(status => <option value={status} key={status}>{eventStatusLabel(status)}</option>)}</select><Button kind="secondary" onClick={() => setEditing(true)}>Modifier l’événement</Button></div>}</section>
    <div className="tabs">{['Vue d’ensemble', 'Salles', 'Prestataires', 'Invités', 'Budget'].map(t => <button className={tab === t ? 'active' : ''} onClick={() => setTab(t)} key={t}>{t}</button>)}</div>
    {tab === 'Vue d’ensemble' && <div className="detail-grid"><section className="card"><CardHead title="À propos" subtitle="Informations générales" /><p className="description">{event.description || 'Aucune description fournie.'}</p><div className="info-grid"><div><span>Statut</span><strong>{eventStatusLabel(event.status)}</strong></div><div><span>Lieu</span><strong>{event.location}</strong></div><div><span>Date de début</span><strong>{prettyDate(event.date)}</strong></div><div><span>Date de fin</span><strong>{prettyDate(event.end)}</strong></div></div></section><section className="card"><CardHead title="Progression" subtitle="Préparation de l’événement" /><div className="progress-big"><div><strong>68 %</strong><span>Progression globale</span></div><div className="progress"><i style={{ width: '68%' }} /></div></div>{[['Salle réservée', true], ['Prestataires confirmés', true], ['Invitations envoyées', true], ['Budget finalisé', false]].map(([l, c]: any) => <div className="check-row" key={l}><span className={c ? 'done' : ''}>{c && <Check size={13} />}</span>{l}</div>)}</section></div>}
    {tab === 'Budget' && <BudgetDetail event={event} flash={flash} />}
    {!['Vue d’ensemble', 'Budget'].includes(tab) && <section className="card"><EmptyAction icon={tab === 'Salles' ? Building2 : tab === 'Invités' ? Users : ContactRound} title={`${tab} de l’événement`} text={`Les éléments associés à « ${event.title} » apparaîtront ici.`} action={`Ajouter ${tab === 'Invités' ? 'un invité' : 'un élément'}`} onClick={() => flash('Formulaire ouvert en mode démonstration')} /></section>}
    {editing && <Modal title="Modifier l’événement" onClose={() => setEditing(false)}><form onSubmit={update} className="form-grid"><label className="full">Titre<input name="title" maxLength={200} defaultValue={event.title} required /></label><label className="full">Lieu<input name="location" maxLength={255} defaultValue={event.location === 'Non défini' ? '' : event.location} /></label><label>Date et heure de début<input name="date" type="datetime-local" defaultValue={toLocalDateTime(event.date)} required /></label><label>Date et heure de fin<input name="end" type="datetime-local" defaultValue={toLocalDateTime(event.end)} required /></label><label className="full">Description<textarea name="description" defaultValue={event.description} /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="danger" onClick={() => void remove()}>Supprimer</Button><Button kind="secondary" onClick={() => setEditing(false)}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement...' : 'Enregistrer'}</Button></div></form></Modal>}</>
}

function BudgetPage({ events, flash, go }: { events: EventItem[]; flash: (message: string) => void; go: (path: string) => void }) {
  const [selected, setSelected] = useState(events[0]?.id ?? 0)
  useEffect(() => {
    if (events.length && !events.some(event => event.id === selected)) setSelected(events[0].id)
  }, [events, selected])
  const event = events.find((e: EventItem) => e.id === Number(selected))
  return <><PageIntro eyebrow="Finances" title="Budgets" text="Contrôlez les budgets et les dépenses de chaque événement." />
    {event ? <><label className="event-select">Événement sélectionné<select value={selected} onChange={e => setSelected(Number(e.target.value))}>{events.map((item: EventItem) => <option value={item.id} key={item.id}>{item.title}</option>)}</select></label><BudgetDetail event={event} flash={flash} go={go} /></> : <section className="card"><EmptyAction icon={CalendarDays} title="Aucun événement" text="Créez un événement avant de lui attribuer un budget." action="Créer un événement" onClick={() => go('/evenements')} /></section>}
  </>
}

function BudgetCreateModal({ event, saving, error, onClose, onSubmit }: { event: EventItem; saving: boolean; error: string; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  const [amount, setAmount] = useState(50000)
  return <Modal title="Créer un budget" onClose={onClose}><form className="form-grid" onSubmit={onSubmit}>
    <label className="full">Événement<input value={event.title} disabled /></label>
    <label className="full">Montant total (MAD)<input name="budget" type="number" min="0.01" step="0.01" value={amount} onChange={value => setAmount(Number(value.target.value))} required /></label>
    <label className="full checkbox-line"><input name="allowOverspending" type="checkbox" defaultChecked />Autoriser le dépassement du budget</label>
    <p className="full description">Les alertes sont calculées automatiquement à partir de 80 % du montant engagé.</p>
    {error && <p className="full warning">{error}</p>}
    <div className="form-actions full"><Button kind="secondary" onClick={onClose}>Annuler</Button><Button type="submit"><Plus size={16} />{saving ? 'Création...' : 'Créer le budget'}</Button></div>
  </form></Modal>
}

function ExpenseCreateModal({ budget, event, engaged, saving, error, onClose, onSubmit }: { budget: Budget; event: EventItem; engaged: number; saving: boolean; error: string; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return <Modal title="Ajouter une dépense" onClose={onClose}><form className="form-grid" onSubmit={onSubmit}>
    <label className="full">Événement<input value={event.title} disabled /></label>
    <label className="full">Description<input name="description" maxLength={255} required placeholder="Ex. Location de matériel" /></label>
    <label>Catégorie<input name="category" maxLength={100} required placeholder="Ex. Salle, Traiteur, Transport" /></label>
    <label>Montant (MAD)<input name="amount" type="number" min="0.01" step="0.01" required /></label>
    <label>Date<input name="date" type="date" defaultValue={new Date().toISOString().slice(0, 10)} required /></label>
    <label>Statut<select name="status" defaultValue="EN_ATTENTE">{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select></label>
    <label className="full">Commentaire (facultatif)<textarea name="comment" maxLength={2000} placeholder="Note utile pour votre équipe" /></label>
    <p className="full description">Déjà engagé : {money(engaged)} · Budget total : {money(budget.montantTotal)}. Le solde et les alertes se recalculent automatiquement.</p>
    {error && <p className="full warning">{error}</p>}
    <div className="form-actions full"><Button kind="secondary" onClick={onClose}>Annuler</Button><Button type="submit"><Plus size={16} />{saving ? 'Ajout...' : 'Ajouter la dépense'}</Button></div>
  </form></Modal>
}

function ExpenseDetailModal({ expense, event, budget, onClose, onEdit }: { expense: ApiDepense; event: EventItem; budget: Budget; onClose: () => void; onEdit: () => void }) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [onClose])

  const share = budget.montantTotal > 0 ? expense.montant / budget.montantTotal * 100 : 0
  const engaged = expense.statut === 'EN_ATTENTE' || expense.statut === 'PAYEE'
  return createPortal(<div className="modal-backdrop expense-details-backdrop" onMouseDown={onClose}>
    <div className="modal expense-details-modal" role="dialog" aria-modal="true" aria-labelledby="expense-details-title" onMouseDown={event => event.stopPropagation()}>
      <header className="expense-dialog-header">
        <span className="expense-dialog-icon"><FileText size={22} /></span>
        <div><span className="expense-dialog-reference">DEP-{String(expense.id).padStart(3, '0')}</span><h2 id="expense-details-title">Détails de la dépense</h2></div>
        <button className="icon-btn" onClick={onClose} aria-label="Fermer" autoFocus><X size={20} /></button>
      </header>
      <div className="expense-dialog-body">
        <section className="expense-dialog-hero">
          <div className="expense-dialog-title"><h3>{expense.description}</h3><Badge tone={expenseStatusTone(expense.statut)}>{expenseStatusLabel(expense.statut)}</Badge></div>
          <span className="expense-dialog-label">Montant de la dépense</span>
          <strong className="expense-dialog-amount">{new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(expense.montant)} <small>MAD</small></strong>
        </section>
        <dl className="expense-dialog-facts">
          <div><dt><CalendarDays size={16} />Date de dépense</dt><dd>{prettyDate(expense.dateDepense)}</dd></div>
          <div><dt><Layers3 size={16} />Catégorie</dt><dd>{expense.categorie}</dd></div>
          <div className="expense-dialog-event"><dt><CalendarDays size={16} />Événement</dt><dd>{event.title}</dd></div>
          {expense.reservationSalleId && <div className="expense-dialog-event"><dt><Building2 size={16} />Réservation de salle</dt><dd>RES-{expense.reservationSalleId}<span className="expense-dialog-source">Synchronisée automatiquement</span></dd></div>}
        </dl>
        <section className="expense-dialog-budget"><div className="expense-dialog-section-title"><span><WalletCards size={17} />Part du budget</span><strong>{new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(share)} %</strong></div><div className="progress"><i style={{ width: `${Math.min(share, 100)}%` }} /></div><div className="expense-dialog-budget-caption"><span>Budget alloué : {money(budget.montantTotal)}</span><span>{engaged ? 'Montant engagé' : expense.statut === 'ANNULEE' ? 'Dépense exclue du solde' : 'Dépense prévisionnelle'}</span></div></section>
        <section className="expense-dialog-comment"><h4><MessageSquare size={16} />Commentaire</h4><p className={expense.commentaire ? '' : 'expense-dialog-empty'}>{expense.commentaire || 'Aucun commentaire pour cette dépense.'}</p></section>
      </div>
      <footer className="expense-details-actions"><Button kind="secondary" onClick={onClose}>Fermer</Button>{budget.statut === 'OUVERT' && !expense.reservationSalleId && <Button onClick={onEdit}><Pencil size={15} />Modifier la dépense</Button>}</footer>
    </div>
  </div>, document.body)
}

function ExpenseDeleteModal({ expense, deleting, onClose, onConfirm }: { expense: ApiDepense; deleting: boolean; onClose: () => void; onConfirm: () => void }) {
  const [confirmed, setConfirmed] = useState(false)
  return <div className="modal-backdrop delete-backdrop" onMouseDown={deleting ? undefined : onClose}><div className="delete-modal" onMouseDown={mouseEvent => mouseEvent.stopPropagation()}>
    <div className="delete-head"><div className="delete-icon"><AlertTriangle size={26} /></div><h2>Supprimer la dépense</h2><button className="icon-btn" onClick={onClose} disabled={deleting} aria-label="Fermer"><X size={18} /></button></div>
    <div className="delete-body"><div className="delete-expense"><FileText size={23} /><div><strong>{expense.description}</strong><span><Badge tone="orange">{expense.categorie}</Badge><i />{money(expense.montant)}<i />{prettyDate(expense.dateDepense)}</span></div></div><div className="delete-warning"><AlertTriangle size={20} /><p>Cette action est irréversible. La dépense sera supprimée du budget et les indicateurs financiers seront recalculés.</p></div><label className="delete-confirm"><input type="checkbox" checked={confirmed} onChange={value => setConfirmed(value.target.checked)} /><span>Je confirme la suppression de cette dépense</span></label></div>
    <div className="delete-actions"><Button kind="secondary" onClick={onClose}>Annuler</Button><button className="delete-button" disabled={!confirmed || deleting} onClick={onConfirm}><Trash2 size={17} />{deleting ? 'Suppression…' : 'Supprimer définitivement'}</button></div>
  </div></div>
}

function BudgetDetail({ event, flash, go }: { event: EventItem; flash: (message: string) => void; go: (path: string) => void }) {
  const [budget, setBudget] = useState<Budget | null>(null)
  const [list, setList] = useState<ApiDepense[]>([])
  const [report, setReport] = useState<BilanFinancier | null>(null)
  const [statistics, setStatistics] = useState<BudgetStatistiques | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [modal, setModal] = useState(false)
  const [editBudget, setEditBudget] = useState(false)
  const [createBudget, setCreateBudget] = useState(false)
  const [updatingExpenseId, setUpdatingExpenseId] = useState<number | null>(null)
  const [expenseFilters, setExpenseFilters] = useState<DepenseFilters>({})
  const [showFilters, setShowFilters] = useState(false)
  const [editingExpense, setEditingExpense] = useState<ApiDepense | null>(null)
  const [viewingExpense, setViewingExpense] = useState<ApiDepense | null>(null)
  const [deletingExpense, setDeletingExpense] = useState<ApiDepense | null>(null)

  const loadDetails = async (currentBudget: Budget) => {
    const expensesResult = await budgetService.getExpenses(currentBudget.id, expenseFilters)
    const [reportResult, statisticsResult] = await Promise.all([
      budgetService.getReport(currentBudget.id),
      budgetService.getStatistics(currentBudget.id),
    ])
    setList(expensesResult)
    setReport(reportResult)
    setStatistics(statisticsResult)
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
        setStatistics(null)
      } else {
        setLoadError(getBudgetErrorMessage(requestError))
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [event.id])

  useEffect(() => {
    if (!budget) return
    const timer = window.setInterval(() => {
      void loadDetails(budget).catch(requestError => setError(getBudgetErrorMessage(requestError)))
    }, 30_000)
    return () => window.clearInterval(timer)
  }, [budget?.id, expenseFilters])

  const create = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    setSaving(true)
    setError('')
    try {
      const form = new FormData(ev.currentTarget)
      const created = await budgetService.create({
        evenementId: event.id,
        montantTotal: Number(form.get('budget')),
        depassementAutorise: form.has('allowOverspending'),
      })
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
      await budgetService.createExpense(budget.id, expensePayload(form))
      await loadDetails(budget)
      setModal(false)
      flash('Dépense ajoutée et bilan mis à jour')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const updateExpense = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    if (!budget || !editingExpense) return
    const form = new FormData(ev.currentTarget)
    setSaving(true)
    setError('')
    try {
      await budgetService.updateExpense(editingExpense.id, {
        description: String(form.get('description')),
        categorie: String(form.get('category')),
        montant: Number(form.get('amount')),
        dateDepense: String(form.get('date')),
        statut: String(form.get('status')) as DepenseStatut,
        commentaire: String(form.get('comment') || '') || undefined,
      })
      await loadDetails(budget)
      setEditingExpense(null)
      flash('Dépense mise à jour')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const exportExpenses = () => {
    const escape = (value: string | number) => `"${String(value).replace(/"/g, '""')}"`
    const rows = [
      ['Description', 'Catégorie', 'Montant (MAD)', 'Date', 'Statut'],
      ...list.map(item => [item.description, item.categorie, item.montant, item.dateDepense, expenseStatusLabel(item.statut)]),
    ]
    const csv = rows.map(row => row.map(escape).join(';')).join('\n')
    const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `depenses-${event.title.toLowerCase().replace(/[^a-z0-9]+/gi, '-')}.csv`
    link.click()
    URL.revokeObjectURL(url)
    flash('Export CSV généré')
  }

  const update = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    if (!budget) return
    setSaving(true)
    setError('')
    try {
      const form = new FormData(ev.currentTarget)
      const updated = await budgetService.update(budget.id, {
        montantTotal: Number(form.get('budget')),
        depassementAutorise: form.has('allowOverspending'),
      })
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
    if (!budget) return
    if (deletingExpense?.id !== id) {
      const expense = list.find(item => item.id === id)
      if (expense) setDeletingExpense(expense)
      return
    }
    setSaving(true)
    setError('')
    try {
      await budgetService.deleteExpense(id)
      await loadDetails(budget)
      setDeletingExpense(null)
      flash('Dépense supprimée')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const changeExpenseStatus = async (id: number, statut: DepenseStatut) => {
    if (!budget) return
    setUpdatingExpenseId(id)
    setError('')
    try {
      const updated = await budgetService.updateExpenseStatus(id, statut)
      setList(current => current.map(item => item.id === id ? updated : item))
      const [updatedReport, updatedStatistics] = await Promise.all([
        budgetService.getReport(budget.id),
        budgetService.getStatistics(budget.id),
      ])
      setReport(updatedReport)
      setStatistics(updatedStatistics)
      flash(`Statut modifié : ${expenseStatusLabel(statut)}`)
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setUpdatingExpenseId(null)
    }
  }

  const filterExpenses = async (ev: FormEvent<HTMLFormElement>) => {
    ev.preventDefault()
    if (!budget) return
    const form = new FormData(ev.currentTarget)
    const value = (name: string) => String(form.get(name) || '').trim()
    const filters: DepenseFilters = {
      categorie: value('categorie') || undefined,
      statut: (value('statut') || undefined) as DepenseStatut | undefined,
      dateDebut: value('dateDebut') || undefined,
      dateFin: value('dateFin') || undefined,
      montantMin: value('montantMin') ? Number(value('montantMin')) : undefined,
      montantMax: value('montantMax') ? Number(value('montantMax')) : undefined,
    }
    setExpenseFilters(filters)
    setError('')
    try {
      setList(await budgetService.getExpenses(budget.id, filters))
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    }
  }

  const clearExpenseFilters = async () => {
    if (!budget) return
    setExpenseFilters({})
    setError('')
    try {
      setList(await budgetService.getExpenses(budget.id))
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    }
  }

  const changeBudgetAvailability = async () => {
    if (!budget) return
    setSaving(true)
    setError('')
    try {
      const updated = budget.statut === 'CLOTURE'
        ? await budgetService.reopen(budget.id)
        : await budgetService.close(budget.id)
      setBudget(updated)
      flash(updated.statut === 'CLOTURE' ? 'Budget clôturé' : 'Budget rouvert')
    } catch (requestError) {
      setError(getBudgetErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <section className="card"><p className="description">Chargement du budget...</p></section>
  if (loadError) return <section className="card"><div className="empty"><h3>Impossible de charger le budget</h3><p>{loadError}</p><Button onClick={() => void load()}>Réessayer</Button></div></section>
  if (!budget) return <section className="card"><EmptyAction icon={WalletCards} title="Aucun budget" text={`Aucun budget n’est encore défini pour « ${event.title} ».`} action="Créer le budget" onClick={() => setCreateBudget(true)} />{createBudget && <BudgetCreateModal event={event} saving={saving} error={error} onClose={() => setCreateBudget(false)} onSubmit={create} />}</section>

  const totalPrevu = report?.totalPrevu ?? list.filter(item => item.statut === 'PREVUE').reduce((sum, item) => sum + item.montant, 0)
  const totalEngage = report?.totalEngage ?? list.filter(item => item.statut === 'EN_ATTENTE' || item.statut === 'PAYEE').reduce((sum, item) => sum + item.montant, 0)
  const totalPaye = report?.totalPaye ?? list.filter(item => item.statut === 'PAYEE').reduce((sum, item) => sum + item.montant, 0)
  const activeTotal = statistics?.totalDepenses ?? report?.totalDepenses ?? totalPrevu + totalEngage
  const balance = report?.soldeRestant ?? budget.montantTotal - totalEngage
  const pct = report?.pourcentageConsommation ?? (budget.montantTotal ? Math.round(totalEngage / budget.montantTotal * 10000) / 100 : 0)
  const alertLevel: BudgetAlertLevel = report?.niveauAlerte ?? (pct > 100 ? 'DEPASSE' : pct === 100 ? 'LIMITE_ATTEINTE' : pct >= 80 ? 'ATTENTION' : 'NORMAL')
  const formattedPct = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(pct)
  const cats = statistics
    ? statistics.repartitionParCategorie.map(category => ({ name: category.categorie, value: category.montantTotal, percentage: category.pourcentage }))
    : Object.entries(list.filter(item => item.statut !== 'ANNULEE').reduce<Record<string, number>>((totals, item) => ({ ...totals, [item.categorie]: (totals[item.categorie] || 0) + item.montant }), {})).map(([name, value]) => ({ name, value, percentage: activeTotal ? value / activeTotal * 100 : 0 }))
  const closed = budget.statut === 'CLOTURE'
  const pendingExpenses = list.filter(item => item.statut === 'EN_ATTENTE')
  const pendingAmount = pendingExpenses.reduce((sum, item) => sum + item.montant, 0)
  const alerts = [
    ...(pendingExpenses.length ? [{ tone: 'orange', title: 'Dépenses en attente', text: `${pendingExpenses.length} dépense${pendingExpenses.length > 1 ? 's' : ''}, soit ${money(pendingAmount)}, attend${pendingExpenses.length > 1 ? 'ent' : ''} une validation.` }] : []),
    ...(alertLevel !== 'NORMAL' ? [{ tone: 'rose', title: budgetAlertLabel(alertLevel), text: `Le budget est consommé à ${formattedPct} %.` }] : []),
  ]
  const monthFormatter = new Intl.DateTimeFormat('fr-FR', { month: 'short', year: '2-digit' })
  const monthlyExpenses = Object.values(list.filter(item => item.statut !== 'ANNULEE').reduce<Record<string, { key: string; name: string; montant: number }>>((months, item) => {
    const date = new Date(`${item.dateDepense}T00:00:00`)
    const key = item.dateDepense.slice(0, 7)
    const current = months[key] || { key, name: monthFormatter.format(date).replace('.', ''), montant: 0 }
    return { ...months, [key]: { ...current, montant: current.montant + item.montant } }
  }, {})).sort((a, b) => a.key.localeCompare(b.key)).slice(-6)

  return <div className="budget-stack">{error && <div className="warning">{error}</div>}{closed && <div className="budget-alert">Ce budget est clôturé. Les consultations restent disponibles, mais aucune modification n’est autorisée.</div>}<div className="finance-kpis"><Kpi label="Budget total" value={money(budget.montantTotal)} note={closed ? 'Budget clôturé' : 'Montant alloué'} icon={WalletCards} tone="indigo" /><Kpi label="Montant consommé" value={money(activeTotal)} note={`${statistics?.nombreDepenses ?? list.length} dépenses actives`} icon={BarChart3} tone="purple" /><Kpi label="Solde restant" value={money(balance)} note={balance < 0 ? 'Dépassement à surveiller' : 'Disponible'} icon={CircleDollarSign} tone={balance < 0 ? 'rose' : 'green'} /><Kpi label="Taux de consommation" value={`${formattedPct} %`} note={budgetAlertLabel(alertLevel)} icon={TrendingUp} tone={budgetAlertTone(alertLevel)} /><Kpi label="Dépenses en attente" value={money(pendingAmount)} note={`${pendingExpenses.length} à valider`} icon={Clock3} tone="orange" /><Kpi label="Alertes" value={String(alerts.length)} note={alerts.length ? 'Votre attention est requise' : 'Aucune alerte active'} icon={AlertTriangle} tone={alerts.length ? 'rose' : 'green'} /></div>
    <section className="card consumption"><div className="card-head"><div><h3>Engagement du budget</h3><p>{money(totalEngage)} engagés sur {money(budget.montantTotal)}</p></div><div className="hero-badges">{!closed && <Button kind="secondary" onClick={() => setEditBudget(true)}>Modifier le budget</Button>}<Button kind={closed ? 'primary' : 'secondary'} onClick={() => void changeBudgetAvailability()}>{saving ? 'Enregistrement…' : closed ? 'Rouvrir le budget' : 'Clôturer le budget'}</Button></div></div><div className="progress"><i className={alertLevel === 'DEPASSE' ? 'over' : ''} style={{ width: `${Math.min(pct, 100)}%` }} /></div><div className="progress-labels"><span>0 MAD</span><strong>{formattedPct} % engagé</strong><span>{money(budget.montantTotal)}</span></div>{alertLevel === 'ATTENTION' && <div className="budget-alert">Attention : plus de 80 % du budget est maintenant engagé.</div>}{alertLevel === 'LIMITE_ATTEINTE' && <div className="budget-alert">La totalité du budget est engagée. Toute nouvelle dépense provoquera un dépassement.</div>}{alertLevel === 'DEPASSE' && <div className="warning">Le budget est dépassé de {money(Math.abs(balance))}. Vérifiez les dépenses engagées.</div>}</section>
    <div className={`budget-tools ${showFilters ? 'filters-open' : ''}`}>
      <Button kind="secondary" onClick={() => go(`/budgets/depenses?event=${event.id}`)}><BarChart3 size={16} />Explorer les dépenses</Button>
      <Button kind="secondary" onClick={() => setShowFilters(value => !value)}><Filter size={16} />Filtrer</Button>
      <Button kind="secondary" onClick={exportExpenses}><Download size={16} />Exporter</Button>
      {!closed && <Button onClick={() => setModal(true)}><Plus size={16} />Ajouter une dépense</Button>}
    </div>
    <div className="budget-grid"><section className="card expense-card"><div className="card-head"><div><h3>Dépenses ({list.length})</h3><p>Les réservations de salle sont synchronisées automatiquement.</p></div></div><form className="expense-filters" onSubmit={filterExpenses}><input name="categorie" defaultValue={expenseFilters.categorie} placeholder="Catégorie" /><select name="statut" defaultValue={expenseFilters.statut || ''}><option value="">Tous les statuts</option>{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select><input name="dateDebut" type="date" defaultValue={expenseFilters.dateDebut} aria-label="Date minimale" /><input name="dateFin" type="date" defaultValue={expenseFilters.dateFin} aria-label="Date maximale" /><input name="montantMin" type="number" min="0" step="0.01" defaultValue={expenseFilters.montantMin} placeholder="Montant min." /><input name="montantMax" type="number" min="0" step="0.01" defaultValue={expenseFilters.montantMax} placeholder="Montant max." /><Button type="submit">Appliquer</Button><Button kind="secondary" onClick={() => void clearExpenseFilters()}>Effacer</Button></form>{list.length ? <div className="table-scroll"><table><thead><tr><th>Description</th><th>Catégorie</th><th>Montant</th><th>Date</th><th>Statut</th><th>Actions</th></tr></thead><tbody>{list.map((item: ApiDepense) => <tr key={item.id} className="expense-row" onClick={click => { if (!(click.target as HTMLElement).closest('button, select, input, a')) setViewingExpense(item) }}><td><button className="expense-link" onClick={() => setViewingExpense(item)}>{item.description}</button></td><td><Badge tone="gray">{item.categorie}</Badge></td><td><strong>{money(item.montant)}</strong></td><td>{prettyDate(item.dateDepense)}</td><td><select className={`expense-status expense-status-${expenseStatusTone(item.statut)}`} value={item.statut} disabled={closed || updatingExpenseId === item.id || Boolean(item.reservationSalleId)} aria-label={`Statut de ${item.description}`} onChange={event => void changeExpenseStatus(item.id, event.target.value as DepenseStatut)}>{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select></td><td><div className="row-actions"><button className="icon-btn" aria-label={`Voir ${item.description}`} onClick={() => setViewingExpense(item)}><Eye size={15} /></button>{!closed && !item.reservationSalleId && <button className="icon-btn" aria-label={`Modifier ${item.description}`} onClick={() => setEditingExpense(item)}><Pencil size={15} /></button>}{!closed && !item.reservationSalleId && <button className="icon-btn danger" aria-label={`Supprimer ${item.description}`} onClick={() => void remove(item.id)}><Trash2 size={15} /></button>}</div></td></tr>)}</tbody></table></div> : closed ? <p className="description">Aucune dépense enregistrée.</p> : <EmptyAction icon={CreditCard} title="Aucune dépense trouvée" text="Modifiez les filtres ou ajoutez une nouvelle dépense." action="Ajouter" onClick={() => setModal(true)} />}</section>
      <section className="card donut"><CardHead title="Dépenses par catégorie" subtitle="Répartition des dépenses actives" /><div className="donut-wrap"><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={cats} dataKey="value" innerRadius={56} outerRadius={78} paddingAngle={3}>{cats.map((_, i) => <Cell key={i} fill={['#5266df', '#8b5cf6', '#f59e0b', '#10b981', '#ef5a67'][i % 5]} />)}</Pie><Tooltip formatter={(value) => money(Number(value))} /></PieChart></ResponsiveContainer><div><strong>{money(activeTotal)}</strong><span>Total actif</span></div></div><div className="cat-list">{cats.map((category, i) => <div key={category.name}><span><i style={{ background: ['#5266df', '#8b5cf6', '#f59e0b', '#10b981', '#ef5a67'][i % 5]} } />{category.name} <small>{new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(category.percentage)} %</small></span><strong>{money(category.value)}</strong></div>)}</div></section>
      <section className="card monthly-card"><CardHead title="Évolution mensuelle" subtitle="Dépenses sur les six derniers mois actifs" /><div className="monthly-chart"><ResponsiveContainer width="100%" height="100%"><BarChart data={monthlyExpenses}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#edf0f5" /><XAxis dataKey="name" axisLine={false} tickLine={false} fontSize={10} /><YAxis axisLine={false} tickLine={false} fontSize={9} width={45} /><Tooltip formatter={(value) => money(Number(value))} /><Bar dataKey="montant" fill="#5967df" radius={[7, 7, 0, 0]} /></BarChart></ResponsiveContainer></div></section>
      <section className="card alerts-card"><CardHead title="Alertes et notifications" subtitle={`${alerts.length} alerte${alerts.length > 1 ? 's' : ''} active${alerts.length > 1 ? 's' : ''}`} />{alerts.length ? <div className="alerts-list">{alerts.map(alert => <div className={`alert-item alert-${alert.tone}`} key={alert.title}><span><AlertTriangle size={17} /></span><div><strong>{alert.title}</strong><p>{alert.text}</p></div><ChevronRight size={17} /></div>)}</div> : <div className="alerts-empty"><Check size={20} /><div><strong>Tout est sous contrôle</strong><p>Aucune alerte financière pour cet événement.</p></div></div>}</section>
    </div>
    {viewingExpense && <ExpenseDetailModal expense={viewingExpense} event={event} budget={budget} onClose={() => setViewingExpense(null)} onEdit={() => { setEditingExpense(viewingExpense); setViewingExpense(null) }} />}
    {deletingExpense && <ExpenseDeleteModal expense={deletingExpense} deleting={saving} onClose={() => setDeletingExpense(null)} onConfirm={() => void remove(deletingExpense.id)} />}
    {modal && <ExpenseCreateModal budget={budget} event={event} engaged={totalEngage} saving={saving} error={error} onClose={() => setModal(false)} onSubmit={add} />}
    {editingExpense && <Modal title="Modifier la dépense" onClose={() => setEditingExpense(null)}><form onSubmit={updateExpense} className="form-grid"><label className="full">Description<input name="description" maxLength={255} defaultValue={editingExpense.description} required /></label><label>Catégorie<input name="category" maxLength={100} defaultValue={editingExpense.categorie} required /></label><label>Montant (MAD)<input name="amount" type="number" min="0.01" step="0.01" defaultValue={editingExpense.montant} required /></label><label>Date de dépense<input name="date" type="date" defaultValue={editingExpense.dateDepense} required /></label><label>Statut<select name="status" defaultValue={editingExpense.statut}>{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select></label><label className="full">Commentaire (facultatif)<textarea name="comment" maxLength={2000} defaultValue={editingExpense.commentaire || ''} /></label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setEditingExpense(null)}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement...' : 'Enregistrer'}</Button></div></form></Modal>}
    {editBudget && <Modal title="Modifier le budget" onClose={() => setEditBudget(false)}><form onSubmit={update} className="form-grid"><label className="full">Montant total (MAD)<input name="budget" type="number" min="0.01" step="0.01" defaultValue={budget.montantTotal} required /></label><label className="full checkbox-line"><input name="allowOverspending" type="checkbox" defaultChecked={budget.depassementAutorise} />Autoriser le dépassement du budget</label>{error && <p className="full warning">{error}</p>}<div className="form-actions full"><Button kind="secondary" onClick={() => setEditBudget(false)}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement...' : 'Enregistrer'}</Button></div></form></Modal>}</div>
}

function ExpenseManagementPage({ events, flash }: { events: EventItem[]; flash: (message: string) => void }) {
  const requestedEvent = Number(new URLSearchParams(location.search).get('event'))
  const [selected, setSelected] = useState(requestedEvent || events[0]?.id || 0)
  const [budget, setBudget] = useState<Budget | null>(null)
  const [expenses, setExpenses] = useState<ApiDepense[]>([])
  const [report, setReport] = useState<BilanFinancier | null>(null)
  const [statistics, setStatistics] = useState<BudgetStatistiques | null>(null)
  const [filters, setFilters] = useState<DepenseFilters>({})
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [createModal, setCreateModal] = useState(false)
  const [editing, setEditing] = useState<ApiDepense | null>(null)
  const [viewing, setViewing] = useState<ApiDepense | null>(null)
  const [deleting, setDeleting] = useState<ApiDepense | null>(null)
  const [selectedRows, setSelectedRows] = useState<Set<number>>(new Set())
  const event = events.find(item => item.id === selected)

  useEffect(() => {
    if (events.length && !events.some(item => item.id === selected)) setSelected(events[0].id)
  }, [events, selected])

  const load = async (currentFilters: DepenseFilters = filters) => {
    if (!event) return
    setLoading(true)
    setError('')
    try {
      const currentBudget = await budgetService.getByEventId(event.id)
      const expenseResult = await budgetService.getExpenses(currentBudget.id, currentFilters)
      const [reportResult, statisticsResult] = await Promise.all([
        budgetService.getReport(currentBudget.id),
        budgetService.getStatistics(currentBudget.id),
      ])
      setBudget(currentBudget)
      setExpenses(expenseResult)
      setReport(reportResult)
      setStatistics(statisticsResult)
      setSelectedRows(new Set())
    } catch (requestError) {
      if (isBudgetNotFound(requestError)) {
        setBudget(null)
        setExpenses([])
        setReport(null)
        setStatistics(null)
      } else setError(getBudgetErrorMessage(requestError))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load({}) }, [event?.id])

  const applyFilters = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault()
    const form = new FormData(submitEvent.currentTarget)
    const value = (name: string) => String(form.get(name) || '').trim()
    const next: DepenseFilters = {
      categorie: value('categorie') || undefined,
      statut: (value('statut') || undefined) as DepenseStatut | undefined,
      dateDebut: value('dateDebut') || undefined,
      dateFin: value('dateFin') || undefined,
      montantMin: value('montantMin') ? Number(value('montantMin')) : undefined,
      montantMax: value('montantMax') ? Number(value('montantMax')) : undefined,
    }
    setFilters(next)
    await load(next)
  }

  const resetFilters = async () => {
    setFilters({})
    setSearch('')
    await load({})
  }

  const addExpense = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault()
    if (!budget) return
    const form = new FormData(submitEvent.currentTarget)
    setSaving(true)
    try {
      await budgetService.createExpense(budget.id, expensePayload(form))
      setCreateModal(false)
      await load(filters)
      flash('Dépense ajoutée')
    } catch (requestError) { setError(getBudgetErrorMessage(requestError)) } finally { setSaving(false) }
  }

  const updateExpense = async (submitEvent: FormEvent<HTMLFormElement>) => {
    submitEvent.preventDefault()
    if (!editing) return
    const form = new FormData(submitEvent.currentTarget)
    setSaving(true)
    try {
      await budgetService.updateExpense(editing.id, { description: String(form.get('description')), categorie: String(form.get('category')), montant: Number(form.get('amount')), dateDepense: String(form.get('date')), statut: String(form.get('status')) as DepenseStatut, commentaire: String(form.get('comment') || '') || undefined })
      setEditing(null)
      await load(filters)
      flash('Dépense mise à jour')
    } catch (requestError) { setError(getBudgetErrorMessage(requestError)) } finally { setSaving(false) }
  }

  const removeExpense = async (expense: ApiDepense) => {
    if (deleting?.id !== expense.id) { setDeleting(expense); return }
    setSaving(true)
    try { await budgetService.deleteExpense(expense.id); setDeleting(null); await load(filters); flash('Dépense supprimée') } catch (requestError) { setError(getBudgetErrorMessage(requestError)) } finally { setSaving(false) }
  }

  const updateStatus = async (expense: ApiDepense, statut: DepenseStatut) => {
    try { await budgetService.updateExpenseStatus(expense.id, statut); await load(filters); flash(`Statut modifié : ${expenseStatusLabel(statut)}`) } catch (requestError) { setError(getBudgetErrorMessage(requestError)) }
  }

  const visibleExpenses = expenses.filter(expense => expense.description.toLowerCase().includes(search.toLowerCase()))
  const paid = report?.totalPaye ?? expenses.filter(expense => expense.statut === 'PAYEE').reduce((sum, expense) => sum + expense.montant, 0)
  const pending = expenses.filter(expense => expense.statut === 'EN_ATTENTE').reduce((sum, expense) => sum + expense.montant, 0)
  const planned = expenses.filter(expense => expense.statut === 'PREVUE').reduce((sum, expense) => sum + expense.montant, 0)
  const activeTotal = statistics?.totalDepenses ?? report?.totalDepenses ?? expenses.filter(expense => expense.statut !== 'ANNULEE').reduce((sum, expense) => sum + expense.montant, 0)
  const cats = statistics?.repartitionParCategorie.map(category => ({ name: category.categorie, value: category.montantTotal, percentage: category.pourcentage })) ?? []
  const monthFormatter = new Intl.DateTimeFormat('fr-FR', { month: 'short', year: '2-digit' })
  const monthly = Object.values(expenses.filter(expense => expense.statut !== 'ANNULEE').reduce<Record<string, { key: string; name: string; montant: number }>>((months, expense) => {
    const key = expense.dateDepense.slice(0, 7)
    const current = months[key] || { key, name: monthFormatter.format(new Date(`${expense.dateDepense}T00:00:00`)).replace('.', ''), montant: 0 }
    return { ...months, [key]: { ...current, montant: current.montant + expense.montant } }
  }, {})).sort((a, b) => a.key.localeCompare(b.key)).slice(-6)
  const exportCsv = () => {
    const escape = (value: string | number) => `"${String(value).replace(/"/g, '""')}"`
    const csv = [['Description', 'Catégorie', 'Montant', 'Date', 'Statut'], ...visibleExpenses.map(expense => [expense.description, expense.categorie, expense.montant, expense.dateDepense, expenseStatusLabel(expense.statut)])].map(row => row.map(escape).join(';')).join('\n')
    const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a'); link.href = url; link.download = 'depenses.csv'; link.click(); URL.revokeObjectURL(url)
  }
  const toggleRow = (id: number) => setSelectedRows(current => { const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next })
  const toggleAll = () => setSelectedRows(selectedRows.size === visibleExpenses.length ? new Set() : new Set(visibleExpenses.map(expense => expense.id)))

  return <><PageIntro eyebrow="Finances" title="Gestion des dépenses" text="Suivez, analysez et pilotez l’ensemble des dépenses de vos événements." action={budget ? 'Créer une dépense' : undefined} onClick={() => setCreateModal(true)} />
    <label className="event-select">Événement sélectionné<select value={selected} onChange={value => setSelected(Number(value.target.value))}>{events.map(item => <option value={item.id} key={item.id}>{item.title}</option>)}</select></label>
    {error && <div className="warning expense-page-warning">{error}</div>}
    {loading ? <section className="card"><p className="description">Chargement des dépenses...</p></section> : !budget || !event ? <section className="card"><EmptyAction icon={WalletCards} title="Aucun budget" text="Cet événement ne possède pas encore de budget." action="Retour aux budgets" onClick={() => history.back()} /></section> : <div className="expense-management">
      <div className="expense-page-kpis"><Kpi label="Dépenses totales" value={money(activeTotal)} note="Hors dépenses annulées" icon={WalletCards} tone="indigo" /><Kpi label="Nombre de dépenses" value={String(statistics?.nombreDepenses ?? expenses.length)} note="Écritures actives" icon={BarChart3} tone="purple" /><Kpi label="Dépenses payées" value={money(paid)} note={`${activeTotal ? Math.round(paid / activeTotal * 100) : 0} % du total`} icon={Check} tone="green" /><Kpi label="En attente" value={money(pending)} note="À valider ou payer" icon={Clock3} tone="orange" /><Kpi label="Dépenses prévues" value={money(planned)} note="Planifiées à venir" icon={AlertTriangle} tone="rose" /></div>
      <div className="expense-analysis"><section className="card"><CardHead title="Dépenses par catégorie" subtitle="Répartition des dépenses actives" /><div className="expense-analysis-donut"><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={cats} dataKey="value" innerRadius={48} outerRadius={68} paddingAngle={3}>{cats.map((_, index) => <Cell key={index} fill={['#5266df', '#8b5cf6', '#f59e0b', '#10b981', '#ef5a67'][index % 5]} />)}</Pie><Tooltip formatter={value => money(Number(value))} /></PieChart></ResponsiveContainer><div><strong>{money(activeTotal)}</strong><span>Total</span></div></div><div className="analysis-category-list">{cats.slice(0, 4).map((category, index) => <span key={category.name}><i style={{ background: ['#5266df', '#8b5cf6', '#f59e0b', '#10b981'][index % 4] }} />{category.name}<strong>{money(category.value)}</strong></span>)}</div></section>
        <section className="card"><CardHead title="Évolution mensuelle des dépenses" subtitle="Historique des montants enregistrés" /><div className="expense-analysis-chart"><ResponsiveContainer width="100%" height="100%"><BarChart data={monthly}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#edf0f5" /><XAxis dataKey="name" axisLine={false} tickLine={false} fontSize={10} /><YAxis axisLine={false} tickLine={false} fontSize={9} width={45} /><Tooltip formatter={value => money(Number(value))} /><Bar dataKey="montant" fill="#5967df" radius={[7, 7, 0, 0]} /></BarChart></ResponsiveContainer></div></section>
        <section className="card quick-analysis"><CardHead title="Analyse rapide" subtitle="Indicateurs du budget" /><div><p><span>Montant moyen / dépense</span><strong>{money(statistics?.montantMoyen ?? 0)}</strong></p><p><span>Consommation du budget</span><strong>{new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(report?.pourcentageConsommation ?? 0)} %</strong></p><p><span>Reste du budget</span><strong>{money(report?.soldeRestant ?? 0)}</strong></p></div></section></div>
      <section className="card expense-register"><div className="card-head"><div><h3>Liste des dépenses ({visibleExpenses.length})</h3><p>Consultez, filtrez et gérez toutes les dépenses de l’événement.</p></div></div><form className="expense-page-filters" onSubmit={applyFilters}><label className="search-box"><Search size={16} /><input value={search} onChange={value => setSearch(value.target.value)} placeholder="Rechercher une dépense…" /></label><input name="dateDebut" type="date" defaultValue={filters.dateDebut} aria-label="Date de début" /><input name="dateFin" type="date" defaultValue={filters.dateFin} aria-label="Date de fin" /><input name="categorie" defaultValue={filters.categorie} placeholder="Catégorie" /><select name="statut" defaultValue={filters.statut || ''}><option value="">Tous les statuts</option>{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select><input name="montantMin" type="number" min="0" defaultValue={filters.montantMin} placeholder="Montant min." /><input name="montantMax" type="number" min="0" defaultValue={filters.montantMax} placeholder="Montant max." /><Button kind="secondary" onClick={() => void resetFilters()}>Réinitialiser</Button><Button type="submit"><Filter size={15} />Appliquer</Button></form><div className="expense-table-tools"><span>{selectedRows.size} sélectionnée(s)</span><div><Button kind="secondary" onClick={exportCsv}><Download size={15} />CSV</Button><Button kind="secondary" onClick={() => window.print()}><FileText size={15} />PDF</Button></div></div><div className="table-scroll"><table><thead><tr><th><input type="checkbox" checked={visibleExpenses.length > 0 && selectedRows.size === visibleExpenses.length} onChange={toggleAll} /></th><th>Description</th><th>Catégorie</th><th>Montant</th><th>Date</th><th>Statut</th><th>Actions</th></tr></thead><tbody>{visibleExpenses.map(expense => <tr key={expense.id} className="expense-row" onClick={click => { if (!(click.target as HTMLElement).closest('button, select, input, a')) setViewing(expense) }}><td><input type="checkbox" checked={selectedRows.has(expense.id)} onChange={() => toggleRow(expense.id)} /></td><td><button className="expense-link" onClick={() => setViewing(expense)}>{expense.description}</button></td><td><Badge tone="gray">{expense.categorie}</Badge></td><td><strong>{money(expense.montant)}</strong></td><td>{prettyDate(expense.dateDepense)}</td><td><select className={`expense-status expense-status-${expenseStatusTone(expense.statut)}`} value={expense.statut} onChange={value => void updateStatus(expense, value.target.value as DepenseStatut)}>{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select></td><td><div className="row-actions"><button className="icon-btn" onClick={() => setViewing(expense)} aria-label="Voir"><Eye size={14} /></button><button className="icon-btn" onClick={() => setEditing(expense)} aria-label="Modifier"><Pencil size={14} /></button><button className="icon-btn danger" onClick={() => void removeExpense(expense)} aria-label="Supprimer"><Trash2 size={14} /></button></div></td></tr>)}</tbody></table></div><div className="expense-table-footer"><span>1 – {visibleExpenses.length} sur {visibleExpenses.length} dépenses</span><strong>Page 1</strong></div></section>
      {viewing && <ExpenseDetailModal expense={viewing} event={event} budget={budget} onClose={() => setViewing(null)} onEdit={() => { setEditing(viewing); setViewing(null) }} />}
      {deleting && <ExpenseDeleteModal expense={deleting} deleting={saving} onClose={() => setDeleting(null)} onConfirm={() => void removeExpense(deleting)} />}
      {createModal && <ExpenseCreateModal budget={budget} event={event} engaged={report?.totalEngage ?? 0} saving={saving} error={error} onClose={() => setCreateModal(false)} onSubmit={addExpense} />}
      {editing && <Modal title="Modifier la dépense" onClose={() => setEditing(null)}><form onSubmit={updateExpense} className="form-grid"><label className="full">Description<input name="description" defaultValue={editing.description} required /></label><label>Catégorie<input name="category" defaultValue={editing.categorie} required /></label><label>Montant<input name="amount" type="number" min="0.01" step="0.01" defaultValue={editing.montant} required /></label><label>Date<input name="date" type="date" defaultValue={editing.dateDepense} required /></label><label>Statut<select name="status" defaultValue={editing.statut}>{expenseStatuses.map(status => <option value={status} key={status}>{expenseStatusLabel(status)}</option>)}</select></label><label className="full">Commentaire (facultatif)<textarea name="comment" maxLength={2000} defaultValue={editing.commentaire || ''} /></label><div className="form-actions full"><Button kind="secondary" onClick={() => setEditing(null)}>Annuler</Button><Button type="submit">{saving ? 'Enregistrement…' : 'Enregistrer'}</Button></div></form></Modal>}
    </div>}
  </>
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

function PrestatairesPage({
  prestataires,
  loading,
  loadError,
  onCreated,
  flash,
}: {
  prestataires: Prestataire[]
  loading: boolean
  loadError: string
  onCreated: (prestataire: Prestataire) => void
  flash: (message: string) => void
}) {
  const [search, setSearch] = useState('')
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const filtered = prestataires.filter(prestataire =>
    [prestataire.nom, prestataire.contact, prestataire.typeService]
      .join(' ')
      .toLowerCase()
      .includes(search.toLowerCase())
  )

  const createPrestataire = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    const form = new FormData(event.currentTarget)
    const data: PrestataireCreateForm = {
      nom: String(form.get('nom')),
      contact: String(form.get('contact')),
      typeService: String(form.get('typeService')),
      tarif: Number(form.get('tarif')),
    }

    try {
      const prestataire = await prestataireService.create(data)
      onCreated(prestataire)
      setOpen(false)
      flash('Prestataire ajouté avec succès.')
    } catch (error) {
      setFormError(getPrestataireErrorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  return <>
    <PageIntro eyebrow="Répertoire" title="Prestataires" text="Gérez vos prestataires et consultez leurs contrats." action="Ajouter un prestataire" onClick={() => { setFormError(''); setOpen(true) }} />
    {loadError && <div className="warning" role="alert">{loadError}</div>}
    <section className="card table-card">
      <div className="toolbar">
        <label className="search-box"><Search size={17} /><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Rechercher dans les prestataires..." /></label>
      </div>
      {loading
        ? <div className="empty compact"><p>Chargement des prestataires...</p></div>
        : loadError
          ? <div className="empty compact"><p>Les prestataires n’ont pas pu être chargés.</p></div>
        : filtered.length === 0
          ? <div className="empty compact">
            <div className="empty-icon"><ContactRound size={22} /></div>
            <h3>{prestataires.length === 0 ? 'Aucun prestataire enregistré' : 'Aucun résultat'}</h3>
            <p>{prestataires.length === 0 ? 'Les prestataires créés dans le microservice apparaîtront ici.' : 'Modifiez votre recherche pour afficher des prestataires.'}</p>
          </div>
          : <div className="table-scroll"><table>
            <thead><tr><th>Prestataire</th><th>Catégorie</th><th>Contact</th><th>Tarif</th></tr></thead>
            <tbody>{filtered.map(prestataire => <tr key={prestataire.id}>
              <td><strong>{prestataire.nom}</strong></td>
              <td>{prestataire.typeService}</td>
              <td>{prestataire.contact}</td>
              <td>{money(prestataire.tarif)}</td>
            </tr>)}</tbody>
          </table></div>}
      <div className="pagination"><span>{filtered.length} résultat{filtered.length > 1 ? 's' : ''}</span></div>
    </section>
    {open && <Modal title="Ajouter un prestataire" onClose={() => setOpen(false)}>
      <form className="form-grid" onSubmit={createPrestataire}>
        <label className="full">Nom du prestataire<input name="nom" maxLength={150} required /></label>
        <label className="full">Contact (e-mail ou téléphone)<input name="contact" maxLength={255} required /></label>
        <label className="full">Catégorie<input name="typeService" maxLength={100} required /></label>
        <label className="full">Tarif (MAD)<input name="tarif" type="number" min="0" max="9999999999.99" step="0.01" required /></label>
        {formError && <p className="full warning" role="alert">{formError}</p>}
        <div className="form-actions full">
          <Button kind="secondary" onClick={() => setOpen(false)}>Annuler</Button>
          <Button type="submit">{saving ? 'Enregistrement...' : 'Enregistrer'}</Button>
        </div>
      </form>
    </Modal>}
  </>
}

function PrestataireCategoriesPage({
  prestataires,
  loading,
  loadError,
}: {
  prestataires: Prestataire[]
  loading: boolean
  loadError: string
}) {
  const counts = new Map<string, number>()
  for (const prestataire of prestataires) {
    counts.set(prestataire.typeService, (counts.get(prestataire.typeService) || 0) + 1)
  }
  const categories = [...counts.entries()].sort(([left], [right]) => left.localeCompare(right))

  return <>
    <PageIntro eyebrow="Répertoire" title="Catégories" text="Catégories calculées à partir des types de service des prestataires." />
    {loadError && <div className="warning" role="alert">{loadError}</div>}
    <section className="card table-card">
      {loading
        ? <div className="empty compact"><p>Chargement des catégories...</p></div>
        : loadError
          ? <div className="empty compact"><p>Les catégories n’ont pas pu être chargées.</p></div>
        : categories.length === 0
          ? <div className="empty compact">
            <div className="empty-icon"><Layers3 size={22} /></div>
            <h3>Aucune catégorie</h3>
            <p>Les catégories apparaîtront dès qu’un prestataire sera enregistré.</p>
          </div>
          : <div className="table-scroll"><table>
            <thead><tr><th>Catégorie</th><th>Prestataires</th></tr></thead>
            <tbody>{categories.map(([typeService, count]) => <tr key={typeService}>
              <td><strong>{typeService}</strong></td>
              <td>{count}</td>
            </tr>)}</tbody>
          </table></div>}
    </section>
  </>
}

function PrestataireUtilisationsPage() {
  const [utilisations, setUtilisations] = useState<UtilisationPrestataire[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let active = true
    prestataireService.listUtilisations()
      .then(result => { if (active) { setUtilisations(result); setLoadError('') } })
      .catch(error => { if (active) setLoadError(getPrestataireErrorMessage(error)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const eventGroups = new Map<number, UtilisationPrestataire[]>()
  for (const utilisation of utilisations) {
    const group = eventGroups.get(utilisation.evenementId) || []
    group.push(utilisation)
    eventGroups.set(utilisation.evenementId, group)
  }

  return <>
    <PageIntro eyebrow="Prestataires" title="Utilisation par événement" text="Contrats réels reliant les prestataires aux événements." />
    {loadError && <div className="warning" role="alert">{loadError}</div>}
    {loading
      ? <section className="card empty compact"><p>Chargement des contrats...</p></section>
      : loadError
        ? <section className="card empty compact"><p>Les contrats n’ont pas pu être chargés.</p></section>
      : eventGroups.size === 0
        ? <section className="card empty">
          <div className="empty-icon"><CalendarDays size={22} /></div>
          <h3>Aucun contrat prestataire</h3>
          <p>Les contrats associés aux événements apparaîtront ici lorsqu’ils seront enregistrés.</p>
        </section>
        : [...eventGroups.entries()].map(([evenementId, contrats]) => <section className="card table-card" key={evenementId}>
          <CardHead title={contrats[0].evenementTitre} subtitle={`${contrats.length} contrat${contrats.length > 1 ? 's' : ''}`} />
          <div className="table-scroll"><table>
            <thead><tr><th>Prestataire</th><th>Prestation</th><th>Date</th><th>Statut</th><th>Montant</th></tr></thead>
            <tbody>{contrats.map(contrat => <tr key={contrat.id}>
              <td><strong>{contrat.prestataireNom}</strong></td>
              <td>{contrat.prestation}</td>
              <td>{prettyDate(contrat.datePrestation)}</td>
              <td><Badge>{contrat.statut}</Badge></td>
              <td>{money(contrat.montant)}</td>
            </tr>)}</tbody>
          </table></div>
        </section>)}
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

function App() {
  const [session, setSession] = useState(getSession)
  useEffect(() => {
    const update = () => setSession(getSession())
    window.addEventListener('eventflow-session', update)
    return () => window.removeEventListener('eventflow-session', update)
  }, [])
  useEffect(() => {
    if (!session) return
    const timeout = window.setTimeout(clearSession, Math.max(0, session.expiresAt - Date.now()))
    return () => window.clearTimeout(timeout)
  }, [session])
  return session ? <AuthenticatedApp user={session.user} /> : <LoginPage />
}
