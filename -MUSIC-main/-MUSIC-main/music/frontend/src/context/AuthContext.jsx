import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';

const AuthContext = createContext(null);

const API = ''; // Vite 프록시로 동일 출처 요청 (세션 쿠키 전달)

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // 💡 이용권(구독) 상태 - 플레이어 미리듣기 제한 해제 판정의 단일 소스
  const [subscription, setSubscription] = useState(null); // { active, passName, startDate, expireDate }

  const authHeaders = () => {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  // 서버 기준으로 이용권 상태를 다시 불러온다 (로그인 직후 / 결제 완료 후 호출)
  const refreshSubscription = useCallback(async () => {
    try {
      const res = await fetch(`${API}/api/v1/payments/subscription`, {
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        credentials: 'include',
      });
      if (res.ok) {
        const data = await res.json();
        setSubscription(data);
        return data;
      }
    } catch (_) {}
    setSubscription(null);
    return null;
  }, []);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  // user 가 채워지면 이용권 상태도 동기화
  useEffect(() => {
    if (user) refreshSubscription();
    else setSubscription(null);
  }, [user, refreshSubscription]);

  const checkAuthStatus = async () => {
    try {
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

      const email = urlParams.get('email') || hashParams.get('email');

      const profileImageUrl =
        urlParams.get('profileImageUrl') ||
        urlParams.get('image') ||
        urlParams.get('picture') ||
        hashParams.get('profileImageUrl');

      const role = urlParams.get('role') || hashParams.get('role');

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
          role: role ? decodeURIComponent(role) : 'ROLE_USER',
        };

        localStorage.setItem('user', JSON.stringify(socialUser));
        setUser(socialUser);

        window.history.replaceState({}, document.title, window.location.pathname);
        setLoading(false);
        return;
      }

      const savedUser = localStorage.getItem('user');
      const savedToken = localStorage.getItem('accessToken');

      if (savedUser) {
        try {
          setUser(JSON.parse(savedUser));
        } catch (e) {
          localStorage.removeItem('user');
        }
      }

      // 토큰 유무와 무관하게 세션 기반 로그인도 있으므로 /me 를 시도한다
      try {
        const response = await fetch(`${API}/api/auth/me`, {
          method: 'GET',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          credentials: 'include',
        });

        if (response.ok) {
          const data = await response.json();
          setUser(data);
          localStorage.setItem('user', JSON.stringify(data));
        }
      } catch (e) {
        // /me 실패 시 savedUser 유지
      }
    } catch (err) {
      console.warn('인증 초기화 에러:', err);
    } finally {
      setLoading(false);
    }
  };

  const login = (userData, token) => {
    if (token) localStorage.setItem('accessToken', token);
    if (userData) {
      localStorage.setItem('user', JSON.stringify(userData));
      setUser(userData);
    }
  };

  const logout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
    setUser(null);
    setSubscription(null);
    window.location.href = '/';
  };

  // 프리미엄 판정: 서버 구독 상태 우선, 없으면 user 객체의 힌트(role/premium) 사용
  const isPremium = Boolean(
    subscription?.active ||
      user?.premium === true ||
      ['PREMIUM', 'ROLE_ADMIN', 'ADMIN'].includes(String(user?.role || '').toUpperCase())
  );

  return (
    <AuthContext.Provider
      value={{
        user,
        setUser,
        loading,
        login,
        logout,
        checkAuthStatus,
        subscription,
        isPremium,
        refreshSubscription,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
