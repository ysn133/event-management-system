import api from '../api';
import type { Salle, SalleForm } from '../types/salle';

const salleService = {
  getAll: async (): Promise<Salle[]> => {
    const response = await api.get<Salle[]>('/salles');
    return response.data;
  },

  getById: async (id: number): Promise<Salle> => {
    const response = await api.get<Salle>(`/salles/${id}`);
    return response.data;
  },

  create: async (data: SalleForm): Promise<Salle> => {
    const response = await api.post<Salle>('/salles', data);
    return response.data;
  },

  update: async (id: number, data: SalleForm): Promise<void> => {
    await api.put(`/salles/${id}`, data);
  },

  delete: async (id: number): Promise<void> => {
    await api.delete(`/salles/${id}`);
  },
};

export default salleService;