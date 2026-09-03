import axios from 'axios';

// 저장된 액세스 토큰을 Authorization 헤더로 주입 (SPA <-> API stateless 인증)
const attachToken = (config) => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
};

// 전역 axios (authApi, 일부 페이지가 직접 사용) 에도 동일 적용
axios.defaults.withCredentials = true;
axios.interceptors.request.use(attachToken);

const api = axios.create({
  baseURL: '', // Vite proxy 가 :8080 으로 전달
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(attachToken);

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
