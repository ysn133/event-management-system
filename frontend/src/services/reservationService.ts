
import api from '../api';
import type {
  Reservation,
  ReservationCreate,
  ReservationUpdate,
} from '../types/reservation';

const reservationService = {
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
};

export default reservationService;
