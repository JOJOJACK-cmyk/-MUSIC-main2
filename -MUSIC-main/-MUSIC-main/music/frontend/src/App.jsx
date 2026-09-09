import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import LiveDetailPage from './pages/LiveDetailPage';
import Sidebar from './components/Sidebar';
import PlayerBar from './components/PlayerBar';
import YouTubePlayer from './components/YouTubePlayer';
import HlsAudioPlayer from './components/HlsAudioPlayer';
import MusicModal from './components/MusicModal';
import PreviewLockModal from './components/PreviewLockModal';
import LiveNowButton from './components/LiveNowButton';

import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import ChartPage from './pages/ChartPage';
import LivePage from './pages/LivePage';
import ChatOverlay from './pages/ChatOverlay';
import LibraryPage from './pages/LibraryPage';
import PaymentPage from './pages/PaymentPage';
import PaymentSuccessPage from './pages/PaymentSuccessPage';
import PaymentFailPage from './pages/PaymentFailPage';

import { PlayerProvider } from './context/PlayerContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import { musicApi } from './api/musicApi';
import './styles/style.css';

// 🔒 비로그인 유저의 접근을 막는 라우트 가드 컴포넌트
function ProtectedRoute({ children }) {
  const user = localStorage.getItem('user');
  const token = localStorage.getItem('token') || localStorage.getItem('accessToken');

  if (!user && !token) {
    alert('로그인이 필요한 서비스입니다.');
    return <Navigate to="/login" replace />;
  }

  return children;
}

// 💡 소셜 로그인 직후 URL 파라미터를 감지하여 인증 상태를 동기화하는 컴포넌트
function AuthHandler() {
  const location = useLocation();
  const navigate = useNavigate();
  const { checkAuthStatus } = useAuth();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const token = params.get('token') || params.get('accessToken');
    const email = params.get('email');

    if (token || email) {
      const userData = {
        nickname: params.get('nickname') || '사용자',
        email: email || '',
        profileImageUrl: params.get('profileImageUrl') || '',
        role: params.get('role') || 'ROLE_USER',
      };

      if (token) localStorage.setItem('accessToken', token);
      localStorage.setItem('user', JSON.stringify(userData));

      // URL 파라미터 제거 후, 전체 새로고침 없이 컨텍스트만 서버 기준으로 갱신
      navigate(location.pathname, { replace: true });
      checkAuthStatus?.();
    }
  }, [location, navigate, checkAuthStatus]);

  return null;
}

export default function App() {
  return (
    <AuthProvider>
      <PlayerProvider>
        <BrowserRouter>
          <AppRoot />
        </BrowserRouter>
      </PlayerProvider>
    </AuthProvider>
  );
}

// 사이드바·플레이어 없이 전체 화면으로 띄우는 경로
const CHROMELESS_ROUTES = ['/login'];
// OBS 브라우저 소스용 채팅 오버레이 (동적 경로) — 앱 크롬 없이 렌더
const CHROMELESS_PATTERNS = [/^\/live\/[^/]+\/chat\/?$/];

// OBS 오버레이는 알림/토스트/플레이어까지 전부 배제하고 순수 렌더한다.
function AppRoot() {
  const location = useLocation();
  const isChromeless =
    CHROMELESS_ROUTES.includes(location.pathname) ||
    CHROMELESS_PATTERNS.some((re) => re.test(location.pathname));

  if (isChromeless) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/live/:broadcastId/chat" element={<ChatOverlay />} />
      </Routes>
    );
  }

  return (
    <NotificationProvider>
      <AuthHandler /> {/* 💡 로그인 직후 파라미터 캐치 핸들러 실행 */}
      <AppShell />
    </NotificationProvider>
  );
}

function AppShell() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // 💡 로컬스토리지에서 관리자(ROLE_ADMIN) 여부 확인
  let isAdmin = false;
  try {
    const rawUser = localStorage.getItem('user');
    if (rawUser) {
      const userObj = JSON.parse(rawUser);
      const role = userObj.role ? userObj.role.toUpperCase() : '';
      isAdmin = ['ROLE_ADMIN', 'ADMIN', 'ROLE_SUB_ADMIN', 'SUB_ADMIN'].includes(role);
    }
  } catch (e) {
    console.error(e);
  }

  // 음원 등록 핸들러 (관리자만 실행 가능하도록 방어)
  const handleAddMusicSubmit = async (data) => {
    try {
      if (data.youtubeVideoId) {
        await musicApi.createMusicFromYouTube(data.youtubeVideoId);
      } else {
        await musicApi.createMusic(data);
      }

      alert('음원이 성공적으로 등록되었습니다!');
      setIsModalOpen(false);
      window.location.reload();
    } catch (error) {
      console.error('음원 등록 실패:', error);
      alert('음원 등록에 실패했습니다. (백엔드 컨트롤러 또는 API Key를 확인해 주세요)');
    }
  };

  // (크롬리스 경로 /login, /live/:id/chat 는 AppRoot 에서 이미 처리됨)

  return (
          <div className="app-container">
            {/* 💡 관리자일 때만 모달 오픈 함수 전달 */}
            <Sidebar
              onOpenAddModal={isAdmin ? () => setIsModalOpen(true) : null}
              isAdmin={isAdmin}
            />

            <main className="main-content">
              <Routes>
                {/* 누구나 접근 가능한 공개 페이지 */}
                <Route path="/" element={<MainPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/charts" element={<ChartPage />} />

                {/* 🔒 라우트 가드가 적용된 보호된 페이지 */}
                <Route
                  path="/library"
                  element={
                    <ProtectedRoute>
                      <LibraryPage />
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/live"
                  element={
                    <ProtectedRoute>
                      <LivePage />
                    </ProtectedRoute>
                  }
                />

                <Route
                  path="/payment"
                  element={
                    <ProtectedRoute>
                      <PaymentPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/payment/success"
                  element={
                    <ProtectedRoute>
                      <PaymentSuccessPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/payment/fail"
                  element={
                    <ProtectedRoute>
                      <PaymentFailPage />
                    </ProtectedRoute>
                  }
                />
                <Route path="/live/:broadcastId" element={<LiveDetailPage />} />
              </Routes>
            </main>

            {/* 음원 등록 모달 */}
            {isModalOpen && isAdmin && (
              <MusicModal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                onSubmit={handleAddMusicSubmit}
              />
            )}

            <YouTubePlayer />
            <HlsAudioPlayer />
            <PlayerBar />
            <PreviewLockModal />
            <LiveNowButton />

          </div>
  );
}