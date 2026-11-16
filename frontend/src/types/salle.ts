export type DisponibiliteSalle = 'Disponible' | 'Reservee';

export interface Salle {
  id: number;
  nom: string;
  capacite: number;
  adresse: string;
  prix: number;
  disponibilite: DisponibiliteSalle;
}

export interface SalleForm {
  nom: string;
  capacite: number;
  adresse: string;
  prix: number;
}