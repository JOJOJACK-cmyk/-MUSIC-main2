import axios from 'axios';

const api = axios.create({
  baseURL: '', // Vite proxy handles requests to Spring Boot
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { message, code, errorCode } = error.response.data || {};
      console.error(
        `[API ERROR ${error.response.status}] ${code || errorCode || ''}: ${message || ''}`
      );
    }
    return Promise.reject(error);
  }
);

export default api;
