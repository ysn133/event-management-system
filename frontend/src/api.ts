import axios from 'axios';
import { secureApi } from './services/authService';
import { apiBaseUrl } from './services/apiConfig';

const api = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json',
  },
});

secureApi(api);

export default api;
