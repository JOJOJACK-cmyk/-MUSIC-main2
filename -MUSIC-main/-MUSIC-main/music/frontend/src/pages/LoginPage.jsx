import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext'; // 💡 useAuth 추가

export default function LoginPage() {
  const [activeTab, setActiveTab] = useState('login');
  const navigate = useNavigate();
  const { login } = useAuth(); // 💡 AuthContext의 login 함수 가져오기

  const [loginData, setLoginData] = useState({ email: '', password: '' });
  const [signupData, setSignupData] = useState({ email: '', password: '', nickname: '' });

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await authApi.login(loginData);

      // 💡 백엔드 응답 데이터에서 사용자 정보와 토큰 추출 (응답 구조에 유연하게 대응)
      const token = response?.accessToken || response?.token || response?.data?.accessToken;
    const userData = {
        id: response?.id || response?.data?.id,
        nickname: response?.nickname || response?.name || response?.data?.nickname || loginData.email.split('@')[0],
         email: response?.email || response?.data?.email || loginData.email,
        profileImageUrl: response?.profileImageUrl || response?.data?.profileImageUrl || '',
            };

      // 💡 AuthContext에 로그인 사용자 정보 등록 (전역 상태 및 로컬 스토리지 동기화)
      login(userData, token);

      alert('로그인되었습니다!');
      navigate('/');
    } catch (err) {
      console.error('로그인 에러:', err);
      alert('로그인 실패: 이메일 또는 비밀번호를 확인해주세요.');
    }
  };

  const handleSignupSubmit = async (e) => {
    e.preventDefault();
    try {
      await authApi.signup(signupData);
      alert('회원가입이 완료되었습니다! 로그인해 주세요.');
      setActiveTab('login');
    } catch (err) {
      alert('회원가입 실패: 이미 존재하는 이메일이거나 오류가 발생했습니다.');
    }
  };

  return (
    <div className="auth-body">
      <div className="auth-container">
        <div className="auth-header">
          <Link to="/" className="logo" style={{ justifyContent: 'center' }}>
            <i className="fa-solid fa-wave-square"></i>
            <span>MUSIC</span>
          </Link>
        </div>

        <div className="auth-tabs">
          <button
            id="tab-login"
            className={`tab-btn ${activeTab === 'login' ? 'active' : ''}`}
            onClick={() => setActiveTab('login')}
          >
            로그인
          </button>
          <button
            id="tab-signup"
            className={`tab-btn ${activeTab === 'signup' ? 'active' : ''}`}
            onClick={() => setActiveTab('signup')}
          >
            회원가입
          </button>
        </div>

        {activeTab === 'login' ? (
          <form id="login-form" className="auth-form" onSubmit={handleLoginSubmit}>
            <div className="input-group">
              <i className="fa-solid fa-envelope"></i>
              <input
                type="email"
                name="email"
                placeholder="이메일"
                value={loginData.email}
                onChange={(e) => setLoginData({ ...loginData, email: e.target.value })}
                required
              />
            </div>
            <div className="input-group">
              <i className="fa-solid fa-lock"></i>
              <input
                type="password"
                name="password"
                placeholder="비밀번호"
                value={loginData.password}
                onChange={(e) => setLoginData({ ...loginData, password: e.target.value })}
                required
              />
            </div>
            <button type="submit" className="auth-submit-btn">
              로그인
            </button>

            <div className="social-login-container">
              <p className="social-title">또는 소셜 계정으로 로그인</p>
              <div className="social-buttons">
                <button
                  type="button"
                  className="btn-social btn-kakao"
                  onClick={() => authApi.oauthLogin('kakao')}
                >
                  <i className="fa-solid fa-comment"></i> 카카오로 로그인
                </button>
                <button
                  type="button"
                  className="btn-social btn-google"
                  onClick={() => authApi.oauthLogin('google')}
                >
                  <i className="fa-brands fa-google"></i> 구글로 로그인
                </button>
                <button
                  type="button"
                  className="btn-social btn-naver"
                  onClick={() => authApi.oauthLogin('naver')}
                >
                  <i className="fa-solid fa-N"></i> 네이버로 로그인
                </button>
              </div>
            </div>
          </form>
        ) : (
          <form id="signup-form" className="auth-form" onSubmit={handleSignupSubmit}>
            <div className="input-group">
              <i className="fa-solid fa-envelope"></i>
              <input
                type="email"
                placeholder="이메일"
                value={signupData.email}
                onChange={(e) => setSignupData({ ...signupData, email: e.target.value })}
                required
              />
            </div>

            <div className="input-group">
              <i className="fa-solid fa-lock"></i>
              <input
                type="password"
                placeholder="비밀번호"
                value={signupData.password}
                onChange={(e) => setSignupData({ ...signupData, password: e.target.value })}
                required
              />
            </div>

            <div className="input-group">
              <i className="fa-solid fa-id-card"></i>
              <input
                type="text"
                placeholder="닉네임"
                value={signupData.nickname}
                onChange={(e) => setSignupData({ ...signupData, nickname: e.target.value })}
                required
              />
            </div>

            <button type="submit" className="auth-submit-btn">
              회원가입 완료
            </button>

            <div className="social-login-container">
              <p className="social-title">또는 소셜 계정으로 회원가입</p>
              <div className="social-buttons">
                <button
                  type="button"
                  className="btn-social btn-kakao"
                  onClick={() => authApi.oauthLogin('kakao')}
                >
                  <i className="fa-solid fa-comment"></i> 카카오로 시작하기
                </button>
                <button
                  type="button"
                  className="btn-social btn-google"
                  onClick={() => authApi.oauthLogin('google')}
                >
                  <i className="fa-brands fa-google"></i> 구글로 시작하기
                </button>
                <button
                  type="button"
                  className="btn-social btn-naver"
                  onClick={() => authApi.oauthLogin('naver')}
                >
                  <i className="fa-solid fa-N"></i> 네이버로 시작하기
                </button>
              </div>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}