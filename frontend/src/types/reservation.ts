
export type StatutReservation =
  | 'EN_ATTENTE'
  | 'CONFIRMEE'
  | 'ANNULEE';

export interface ReservationCreate {
  salleId: number;
  evenementId: number;
  dateDebut: string;
  dateFin: string;
  prix: number;
}

export interface ReservationUpdate {
  dateDebut: string;
  dateFin: string;
  prix: number;
}

export interface Reservation {
  id: number;
  salleId: number;
  evenementId: number;
  nomSalle: string;
  dateDebut: string;
  dateFin: string;
  prix: number;
  statut: StatutReservation;
}
