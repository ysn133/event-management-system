
export type StatutReservation =
  | 'EN_ATTENTE'
  | 'CONFIRMEE'
  | 'ANNULEE';

export interface ReservationCreate {
  requestId?: string;
  salleId: number;
  evenementId: number;
  dateDebut: string;
  dateFin: string;
  prix: number;
}

export interface ReservationUpdate {
  version?: number;
  dateDebut: string;
  dateFin: string;
  prix: number;
}

export interface Reservation {
  budgetVersion?: number;
  budgetSyncState?: 'SYNCED' | 'PENDING' | 'BLOCKED';
  budgetApproved?: boolean;
  id: number;
  salleId: number;
  evenementId: number;
  nomSalle: string;
  dateDebut: string;
  dateFin: string;
  prix: number;
  statut: StatutReservation;
}
