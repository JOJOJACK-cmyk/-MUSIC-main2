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

// 401 이 떠도 자동 로그아웃 처리를 하면 안 되는 경로 (로그인/인증 확인 등)
const AUTH_EXEMPT = ['/api/auth/login', '/api/auth/signup', '/api/auth/me', '/api/auth/logout'];

// 세션/토큰이 만료돼 401 이 오면 앱 전체에 알림 → AuthContext 가 로컬 상태를 정리한다.
const handleAuthError = (error) => {
  const status = error?.response?.status;
  const url = error?.config?.url || '';
  if (status === 401 && !AUTH_EXEMPT.some((p) => url.includes(p))) {
    try {
      window.dispatchEvent(new Event('auth:expired'));
    } catch (_) {}
  }
  return Promise.reject(error);
};

// 전역 axios (authApi, 일부 페이지가 직접 사용) 에도 동일 적용
axios.defaults.withCredentials = true;
axios.interceptors.request.use(attachToken);
axios.interceptors.response.use((r) => r, handleAuthError);

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
    return handleAuthError(error);
  }
);

export default api;
