import axios from 'axios';

const BACKEND_URL = ''; // Vite 프록시 경유 (세션 쿠키가 동일 출처로 유지됨)

// 쿠키 및 세션 전달 활성화
axios.defaults.withCredentials = true;

export const authApi = {
  // 일반 회원가입
  signup: async (signupData) => {
    const response = await axios.post(`${BACKEND_URL}/api/auth/signup`, signupData);
    return response.data;
  },

  // 일반 로그인
  login: async (loginData) => {
    const response = await axios.post(`${BACKEND_URL}/api/auth/login`, loginData);
    return response.data;
  },

  // 내 정보 조회 (소셜 로그인 / 일반 로그인 후 사용자 프로필 동기화)
  getMe: async () => {
    const token = localStorage.getItem('accessToken');
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await axios.get(`${BACKEND_URL}/api/auth/me`, { headers });
    return response.data;
  },

  // 소셜 로그인 (백엔드 포트 직접 지정)
  oauthLogin: (provider) => {
    window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`;
  },

  // 로그아웃
  logout: async () => {
    return await axios.post(`${BACKEND_URL}/api/auth/logout`);
  }
};