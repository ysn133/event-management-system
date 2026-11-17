
import api from '../api';
import type {
  Reservation,
  ReservationCreate,
  ReservationUpdate,
} from '../types/reservation';

const reservationService = {
  synchronizeBudget: async (id: number): Promise<void> => {
    await api.post(`/reservations/${id}/synchroniser-budget`);
  },
  getByEvenement: async (evenementId: number): Promise<Reservation[]> => {
    const response = await api.get<Reservation[]>(
      `/reservations/evenement/${evenementId}`
    );
    return response.data;
  },

  getById: async (id: number): Promise<Reservation> => {
    const response = await api.get<Reservation>(`/reservations/${id}`);
    return response.data;
  },

  create: async (data: ReservationCreate): Promise<Reservation> => {
    const response = await api.post<Reservation>('/reservations', data);
    return response.data;
  },

  update: async (
    id: number,
    data: ReservationUpdate
  ): Promise<void> => {
    await api.put(`/reservations/${id}`, data);
  },

  cancel: async (id: number): Promise<void> => {
    await api.patch(`/reservations/${id}/annuler`);
  },

  confirm: async (id: number): Promise<void> => {
    await api.patch(`/reservations/${id}/confirmer`);
  },
  
  remove: async (id: number): Promise<void> => {
    await api.delete(`/reservations/${id}`);
  },
};

export default reservationService;
