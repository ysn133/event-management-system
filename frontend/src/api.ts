import axios from 'axios';
import { secureApi } from './services/authService';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:5242/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

secureApi(api);

export default api;
