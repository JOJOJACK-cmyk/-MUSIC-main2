import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';

const AuthContext = createContext(null);

const API = ''; // Vite 프록시로 동일 출처 요청 (세션 쿠키 전달)

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // 💡 이용권(구독) 상태 - 플레이어 미리듣기 제한 해제 판정의 단일 소스
  const [subscription, setSubscription] = useState(null); // { active, passName, startDate, expireDate }

  // 마지막으로 서버 인증 상태를 확인한 시각 (탭 복귀 시 과도한 재검증 방지)
  const lastCheckRef = useRef(0);

  // 화면상 로그인인데 서버 세션/토큰이 만료된 경우 로컬 상태를 깨끗이 정리
  const clearAuthState = useCallback(() => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
    setSubscription(null);
  }, []);

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

  // 세션 만료(전역 401) 이벤트 → 로컬 인증 상태 정리
  useEffect(() => {
    const onExpired = () => clearAuthState();
    window.addEventListener('auth:expired', onExpired);
    return () => window.removeEventListener('auth:expired', onExpired);
  }, [clearAuthState]);

  // 탭 복귀 시 마지막 확인 후 60초 지났으면 서버 인증 상태 재검증
  useEffect(() => {
    const revalidate = () => {
      if (document.visibilityState !== 'visible') return;
      if (Date.now() - lastCheckRef.current < 60_000) return;
      checkAuthStatus();
    };
    document.addEventListener('visibilitychange', revalidate);
    window.addEventListener('focus', revalidate);
    return () => {
      document.removeEventListener('visibilitychange', revalidate);
      window.removeEventListener('focus', revalidate);
    };
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
          // URL 의 role 은 누구나 조작할 수 있으므로 쓰지 않는다. 실제 권한은 아래 /me 응답으로 덮어쓴다.
          role: 'ROLE_USER',
        };

        localStorage.setItem('user', JSON.stringify(socialUser));
        setUser(socialUser);

        window.history.replaceState({}, document.title, window.location.pathname);
        // return 하지 않고 아래 /api/auth/me 로 서버 기준 사용자 정보(권한 포함)를 확인한다.
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

        lastCheckRef.current = Date.now();

        if (response.ok) {
          const data = await response.json();
          setUser(data);
          localStorage.setItem('user', JSON.stringify(data));
        } else if (response.status === 401 || response.status === 403) {
          // 서버 세션/토큰이 만료됨 → 화면상 로그인 상태를 정리 (스테일 user 유지 금지)
          clearAuthState();
        }
      } catch (e) {
        // 네트워크 오류(응답 없음)면 서버 판단 불가 → savedUser 유지
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

  const logout = async () => {
    // 💡 서버 세션(JSESSIONID)을 무효화하지 않으면 새로고침 시 /api/auth/me 가
    //    세션 쿠키로 다시 인증되어 로그아웃이 되지 않는다.
    try {
      await fetch(`${API}/api/auth/logout`, {
        method: 'POST',
        headers: { ...authHeaders() },
        credentials: 'include',
      });
    } catch (_) {
      // 네트워크 오류가 나도 로컬 상태는 정리하고 진행
    }

    localStorage.removeItem('accessToken');
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
    setSubscription(null);

    // 남아있을 수 있는 쿠키를 클라이언트에서도 제거 시도
    document.cookie = 'JSESSIONID=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT';

    window.location.replace('/');
  };

  // 💡 프리미엄(유료 이용권) 판정: 오직 서버의 유효한 이용권(tb_pass) 상태만 신뢰한다.
  //    role/premium 힌트로 판정하면 "구매 안 했는데 구매됨" 버그가 생긴다.
  const isPremium = Boolean(subscription?.active);

  // 권한 판정
  const roleUpper = String(user?.role || '').toUpperCase();
  // 최고 관리자: 권한 부여 등 민감한 관리 기능 (관리자만)
  const isSuperAdmin = ['ROLE_ADMIN', 'ADMIN'].includes(roleUpper);
  // 관리자(부 관리자 포함): 음원 등록/수정/삭제 등 콘텐츠 관리
  const isAdmin = isSuperAdmin || ['ROLE_SUB_ADMIN', 'SUB_ADMIN'].includes(roleUpper);

  // 미리듣기 제한 해제 판정의 단일 소스: 유료 이용권 보유자 또는 관리자
  const hasFullAccess = Boolean(isPremium || isAdmin);

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
        isAdmin,
        isSuperAdmin,
        hasFullAccess,
        refreshSubscription,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
