import axios from 'axios';

const BACKEND_URL = 'http://localhost:8080';

export const authApi = {
  // 일반 회원가입
  signup: async (signupData) => {
    return await axios.post('/api/auth/signup', signupData);
  },

  // 일반 로그인
  login: async (loginData) => {
    return await axios.post('/api/auth/login', loginData);
  },

  // 소셜 로그인 (백엔드 포트 직접 지정)
  oauthLogin: (provider) => {
    window.location.href = `${BACKEND_URL}/oauth2/authorization/${provider}`;
  }
};