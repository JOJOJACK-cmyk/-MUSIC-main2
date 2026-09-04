import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const [activeTab, setActiveTab] = useState('login');
  const navigate = useNavigate();
  const { login } = useAuth();

  const [loginData, setLoginData] = useState({ email: '', password: '' });
  const [signupData, setSignupData] = useState({ email: '', password: '', nickname: '' });

  // 💡 2단계 인증 모달 관련 상태
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalType, setModalType] = useState('email'); // 'email' (아이디 찾기) 또는 'password' (비밀번호 찾기)
  const [step, setStep] = useState(1); // 1단계: 정보 입력 및 코드 전송, 2단계: 코드 입력 및 확인

  const [inputVal, setInputVal] = useState(''); // 닉네임 또는 이메일
  const [codeVal, setCodeVal] = useState('');     // 인증 코드 6자리
  const [resultMessage, setResultMessage] = useState('');
  const [finalResult, setFinalResult] = useState(''); // 찾은 이메일 또는 임시 비밀번호

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await authApi.login(loginData);
      const token = response?.accessToken || response?.token || response?.data?.accessToken;
      const userData = {
        id: response?.id || response?.data?.id,
        nickname: response?.nickname || response?.name || response?.data?.nickname || loginData.email.split('@')[0],
        email: response?.email || response?.data?.email || loginData.email,
        profileImageUrl: response?.profileImageUrl || response?.data?.profileImageUrl || '',
        role: response?.role || response?.data?.role || 'ROLE_USER',
      };

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

  // 💡 1단계: 인증 코드 전송 요청
  const handleSendCode = async (e) => {
    e.preventDefault();
    setResultMessage('');

    try {
      if (modalType === 'email') {
        const res = await axios.post('/api/auth/find-email/send-code', { nickname: inputVal });
        setResultMessage(res.data.message);
        setStep(2); // 2단계로 이동
      } else {
        const res = await axios.post('/api/auth/send-code', { email: inputVal });
        setResultMessage(res.data.message);
        setStep(2); // 2단계로 이동
      }
    } catch (err) {
      setResultMessage(err.response?.data?.message || '요청 처리에 실패했습니다.');
    }
  };

  // 💡 2단계: 인증 코드 확인 및 결과(이메일/임시비번) 받기
  const handleVerifyAndGet = async (e) => {
    e.preventDefault();
    setResultMessage('');

    try {
      if (modalType === 'email') {
        const res = await axios.post('/api/auth/find-email/verify', {
          nickname: inputVal,
          code: codeVal
        });
        setFinalResult(`찾은 아이디(이메일): ${res.data.email}`);
      } else {
        const res = await axios.post('/api/auth/verify-and-reset', {
          email: inputVal,
          code: codeVal
        });
        setFinalResult(`임시 비밀번호: ${res.data.tempPassword} (로그인 후 변경해주세요)`);
      }
    } catch (err) {
      setResultMessage(err.response?.data?.message || '인증에 실패했습니다.');
    }
  };

  // 모달을 열거나 닫을 때 상태 초기화
  const openModal = (type) => {
    setModalType(type);
    setStep(1);
    setInputVal('');
    setCodeVal('');
    setResultMessage('');
    setFinalResult('');
    setIsModalOpen(true);
  };

  return (
    <div className="auth-body">
      <div className="auth-container">
        <div className="auth-header">
          <Link to="/" className="logo" style={{ justifyContent: 'center' }}>
            <i className="fa-solid fa-wave-square"></i>
            <span>MUSIC</span>
          </Link>
          <p className="auth-subtitle">
            {activeTab === 'login'
              ? '다시 오신 걸 환영해요'
              : '음악 스트리밍을 시작해보세요'}
          </p>
        </div>

        <div className="auth-tabs">
          <button
            className={`tab-btn ${activeTab === 'login' ? 'active' : ''}`}
            onClick={() => setActiveTab('login')}
          >
            로그인
          </button>
          <button
            className={`tab-btn ${activeTab === 'signup' ? 'active' : ''}`}
            onClick={() => setActiveTab('signup')}
          >
            회원가입
          </button>
        </div>

        {activeTab === 'login' ? (
          <form className="auth-form" onSubmit={handleLoginSubmit}>
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

            {/* 아이디 / 비밀번호 찾기 링크 */}
            <div className="find-links" style={{ marginTop: '15px', textAlign: 'center', fontSize: '13px', color: 'var(--text-sub)' }}>
              <span
                style={{ cursor: 'pointer', color: 'var(--text-sub)', marginRight: '15px' }}
                onClick={() => openModal('email')}
              >
                아이디 찾기
              </span>
              <span style={{ opacity: 0.4 }}>|</span>
              <span
                style={{ cursor: 'pointer', color: 'var(--text-sub)', marginLeft: '15px' }}
                onClick={() => openModal('password')}
              >
                비밀번호 찾기
              </span>
            </div>

            <div className="social-login-container">
              <p className="social-title">간편 로그인</p>
              <div className="social-buttons">
                <button
                  type="button"
                  className="social-icon-btn kakao"
                  onClick={() => authApi.oauthLogin('kakao')}
                  aria-label="카카오로 로그인"
                >
                  <i className="fa-solid fa-comment"></i>
                </button>
                <button
                  type="button"
                  className="social-icon-btn google"
                  onClick={() => authApi.oauthLogin('google')}
                  aria-label="구글로 로그인"
                >
                  <i className="fa-brands fa-google"></i>
                </button>
                <button
                  type="button"
                  className="social-icon-btn naver"
                  onClick={() => authApi.oauthLogin('naver')}
                  aria-label="네이버로 로그인"
                >
                  <span>N</span>
                </button>
              </div>
            </div>
          </form>
        ) : (
          <form className="auth-form" onSubmit={handleSignupSubmit}>
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
          </form>
        )}
      </div>

      {/* 💡 2단계 인증 모달 팝업 */}
      {isModalOpen && (
        <div
          className="auth-modal-backdrop"
          onMouseDown={(e) => e.target === e.currentTarget && setIsModalOpen(false)}
        >
          <div className="auth-modal">
            <h3>{modalType === 'email' ? '아이디 찾기' : '비밀번호 찾기'}</h3>

            {/* 1단계: 정보 입력 및 인증 코드 전송 */}
            {step === 1 && (
              <form onSubmit={handleSendCode} style={{ marginTop: 16 }}>
                <p className="auth-modal-hint">
                  {modalType === 'email' ? '가입하신 닉네임을 입력해주세요.' : '가입하신 이메일(아이디)을 입력해주세요.'}
                </p>
                <input
                  className="auth-modal-input"
                  type={modalType === 'email' ? 'text' : 'email'}
                  placeholder={modalType === 'email' ? '닉네임 입력' : '이메일 입력'}
                  value={inputVal}
                  onChange={(e) => setInputVal(e.target.value)}
                  required
                />
                <button type="submit" className="auth-modal-btn">인증번호 전송</button>
              </form>
            )}

            {/* 2단계: 인증 코드 입력 및 확인 */}
            {step === 2 && !finalResult && (
              <form onSubmit={handleVerifyAndGet} style={{ marginTop: 16 }}>
                <p className="auth-modal-ok">
                  인증 코드가 이메일로 전송되었습니다. 메일함을 확인해주세요!
                </p>
                <input
                  className="auth-modal-input"
                  type="text"
                  placeholder="6자리 인증 코드 입력"
                  value={codeVal}
                  onChange={(e) => setCodeVal(e.target.value)}
                  required
                />
                <button type="submit" className="auth-modal-btn">인증 확인</button>
              </form>
            )}

            {resultMessage && !finalResult && (
              <p className="auth-modal-err">{resultMessage}</p>
            )}

            {finalResult && <p className="auth-modal-result">{finalResult}</p>}

            <button className="auth-modal-close" onClick={() => setIsModalOpen(false)}>
              닫기
            </button>
          </div>
        </div>
      )}
    </div>
  );
}