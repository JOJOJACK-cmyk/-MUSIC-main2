import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      // 💡 1. URL SearchParams(?token=...) 및 Hash(#token=...) 모두 확인
      const urlParams = new URLSearchParams(window.location.search);
      const hashParams = new URLSearchParams(
        window.location.hash.startsWith('#')
          ? window.location.hash.substring(1)
          : window.location.hash
      );

      const token =
        urlParams.get('token') ||
        urlParams.get('accessToken') ||
        urlParams.get('jwt') ||
        hashParams.get('token') ||
        hashParams.get('accessToken');

      const nickname =
        urlParams.get('nickname') ||
        urlParams.get('name') ||
        hashParams.get('nickname') ||
        hashParams.get('name');

      const email =
        urlParams.get('email') ||
        hashParams.get('email');

      const profileImageUrl =
        urlParams.get('profileImageUrl') ||
        urlParams.get('image') ||
        urlParams.get('picture') ||
        hashParams.get('profileImageUrl');

      // 💡 소셜 로그인 리다이렉트로 토큰이나 닉네임이 전달된 경우
      if (token || nickname || email) {
        if (token) localStorage.setItem('accessToken', token);

        const socialUser = {
          nickname: nickname
            ? decodeURIComponent(nickname)
            : email
            ? email.split('@')[0]
            : '소셜 사용자',
          email: email ? decodeURIComponent(email) : '',
          profileImageUrl: profileImageUrl ? decodeURIComponent(profileImageUrl) : '',
        };

        localStorage.setItem('user', JSON.stringify(socialUser));
        setUser(socialUser);

        // URL 주소창에서 파라미터 깔끔하게 제거 (?token=... 제거)
        window.history.replaceState({}, document.title, window.location.pathname);
        setLoading(false);
        return;
      }

      // 💡 2. 로컬 스토리지에 기존 저장된 사용자 정보 확인
      const savedUser = localStorage.getItem('user');
      const savedToken = localStorage.getItem('accessToken');

      if (savedUser) {
        try {
          setUser(JSON.parse(savedUser));
        } catch (e) {
          localStorage.removeItem('user');
        }
      }

      // 💡 3. 토큰이 있는 경우에만 백엔드 /api/auth/me 확인
      if (savedToken) {
        try {
          const response = await fetch('http://localhost:8080/api/auth/me', {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${savedToken}`,
            },
            credentials: 'include',
          });

          if (response.ok) {
            const data = await response.json();
            setUser(data);
            localStorage.setItem('user', JSON.stringify(data));
          }
        } catch (e) {
          // 백엔드 me 엔드포인트 미구현 시 savedUser 유지
        }
      }
    } catch (err) {
      console.warn('인증 초기화 에러:', err);
    } finally {
      setLoading(false);
    }
  };

  // 일반 로그인 시 호출
  const login = (userData, token) => {
    if (token) localStorage.setItem('accessToken', token);
    if (userData) {
      localStorage.setItem('user', JSON.stringify(userData));
      setUser(userData);
    }
  };

  // 로그아웃
  const logout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
    setUser(null);
    window.location.href = '/';
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, checkAuthStatus }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);