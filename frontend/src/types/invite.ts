// Types du service Invités (module_invites, port 5003). Noms de champs identiques à l'API (snake_case).

export type StatutInvitation = 'EN_ATTENTE' | 'ACCEPTEE' | 'REFUSEE'
export type EtatEnvoi = 'NON_ENVOYEE' | 'ENVOYEE'
export type CanalEnvoi = 'EMAIL' | 'SMS'

export interface Page<T> {
  items: T[]
  total: number
  limit: number
  offset: number
}

export interface Invite {
  id: number
  nom: string
  email: string | null
  telephone: string | null
}

export interface InviteForm {
  nom: string
  email: string | null
  telephone: string | null
}

export interface PageParams {
  limit?: number
  offset?: number
}

export interface InviteSearchFilters {
  nom?: string
  email?: string
  telephone?: string
}

// Suivi de l'invitation liée à une association invité ↔ événement.
export interface SuiviInvitation {
  invitation_id: number
  statut_invitation: StatutInvitation
  date_envoi: string | null
  etat_envoi: EtatEnvoi
}

export interface InviteAssocie extends Invite, SuiviInvitation {}

export interface EvenementAssocie extends SuiviInvitation {
  id: number
  titre: string
  date_debut: string
  date_fin: string
  lieu: string | null
  statut: string
}

export interface Invitation {
  id: number
  invite_id: number
  evenement_id: number
  statut: StatutInvitation
  date_envoi: string | null
  etat_envoi: EtatEnvoi
}

export interface EnvoiResultat {
  confirme: boolean
  canal: CanalEnvoi
  fournisseur: string
  simulation: boolean
}

export interface InvitationEnvoi {
  invitation: Invitation
  envoi: EnvoiResultat
}

export interface StatistiquesInvitations {
  evenement_id: number
  total: number
  en_attente: number
  acceptees: number
  refusees: number
  pourcentage_acceptation: number
  pourcentage_refus: number
  /** Invitations non refusées (en attente + acceptées) : elles occupent une place. */
  places_occupees: number
  /** Capacité totale des salles réservées ; null si aucune salle ou service Salles indisponible. */
  capacite: number | null
  places_restantes: number | null
  capacite_depassee: boolean
}

export interface InviteApiErrorDetail {
  source: string
  champ: string
  message: string
  type: string
}

// Format d'erreur du service : { "error": { "code", "message", "details"? } }
export interface InviteApiError {
  error?: {
    code: string
    message: string
    details?: InviteApiErrorDetail[]
  }
}
