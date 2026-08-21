import axios from 'axios';

export const authApi = {

  // 일반 회원가입
  signup: async (signupData) => {
    return await axios.post(
      '/api/auth/signup',
      signupData
    );
  },

  // 일반 로그인
  login: async (loginData) => {
    return await axios.post(
      '/api/auth/login',
      loginData
    );
  },

  // 소셜 로그인
  oauthLogin: (provider) => {
    window.location.href =
      `/oauth2/authorization/${provider}`;
  }

};