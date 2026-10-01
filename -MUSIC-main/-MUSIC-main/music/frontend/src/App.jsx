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
import BroadcasterNextSongListener from './components/BroadcasterNextSongListener';
import LiveMiniPlayer from './components/LiveMiniPlayer';

import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import ChartPage from './pages/ChartPage';
import LivePage from './pages/LivePage';
import ChatOverlay from './pages/ChatOverlay';
import ChatDock from './pages/ChatDock';
import NoteResultPage from './pages/NoteResultPage';
import ShopPage from './pages/ShopPage';
import ProductPage from './pages/ProductPage';
import ShopResultPage from './pages/ShopResultPage';
import LibraryPage from './pages/LibraryPage';
import PaymentPage from './pages/PaymentPage';
import PaymentSuccessPage from './pages/PaymentSuccessPage';
import PaymentFailPage from './pages/PaymentFailPage';

import { PlayerProvider } from './context/PlayerContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { NotificationProvider } from './context/NotificationContext';
import { LiveViewProvider } from './context/LiveViewContext';
import useIsMobile from './hooks/useIsMobile';
import MobileShell from './components/mobile/MobileShell';
import MobileHome from './pages/mobile/MobileHome';
import MobileChart from './pages/mobile/MobileChart';
import MobileLive from './pages/mobile/MobileLive';
import MobileLibrary from './pages/mobile/MobileLibrary';
import MobileShop from './pages/mobile/MobileShop';
import MobileSearch from './pages/mobile/MobileSearch';
import { musicApi } from './api/musicApi';
import './styles/style.css';
import './styles/mobile.css';
import ProtectedRoute from './components/ProtectedRoute';

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
        role: 'ROLE_USER', // URL 의 role 은 신뢰하지 않음 — checkAuthStatus 의 /me 응답으로 갱신
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
        <LiveViewProvider>
          <BrowserRouter>
            <AppRoot />
          </BrowserRouter>
        </LiveViewProvider>
      </PlayerProvider>
    </AuthProvider>
  );
}

// 사이드바·플레이어 없이 전체 화면으로 띄우는 경로
const CHROMELESS_ROUTES = ['/login'];
// OBS 브라우저 소스용 채팅 오버레이 (동적 경로) — 앱 크롬 없이 렌더
const CHROMELESS_PATTERNS = [/^\/live\/[^/]+\/chat\/?$/, /^\/live\/[^/]+\/dock\/?$/];

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
        <Route path="/live/:broadcastId/dock" element={<ChatDock />} />
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
  // 📱 모바일(768px 이하)은 하단 탭바·미니 플레이어로 된 전용 화면을 쓴다
  const isMobile = useIsMobile();

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
      const status = error?.response?.status;
      const serverMsg = error?.response?.data?.message;
      let msg = serverMsg || '음원 등록에 실패했습니다.';
      if (status === 401) msg = '세션이 만료되었습니다. 다시 로그인해 주세요.';
      else if (status === 403) msg = '음원 등록 권한이 없습니다. (관리자 계정으로 로그인해 주세요)';
      console.error('음원 등록 실패:', status, error?.response?.data || error);
      alert(msg);
    }
  };

  // (크롬리스 경로 /login, /live/:id/chat 는 AppRoot 에서 이미 처리됨)

  if (isMobile) return <MobileApp />;

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
                <Route path="/shop" element={<ShopPage />} />
                <Route
                  path="/shop/result"
                  element={
                    <ProtectedRoute>
                      <ShopResultPage />
                    </ProtectedRoute>
                  }
                />
                <Route path="/shop/:id" element={<ProductPage />} />
                <Route
                  path="/notes/result"
                  element={
                    <ProtectedRoute>
                      <NoteResultPage />
                    </ProtectedRoute>
                  }
                />
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
            <BroadcasterNextSongListener />
            <LiveMiniPlayer />

          </div>
  );
}

// 📱 모바일 전용 화면: 페이지 구성이 다른 곳(홈·차트·라이브·스토어·보관함·검색)은 모바일 페이지,
//    상품 상세·결제처럼 그대로 써도 되는 곳은 데스크톱 페이지를 모바일 틀 안에서 재사용한다.
function MobileApp() {
  const guard = (el) => <ProtectedRoute>{el}</ProtectedRoute>;
  return (
    <>
      <MobileShell>
        <Routes>
          <Route path="/" element={<MobileHome />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/charts" element={<MobileChart />} />
          <Route path="/search" element={<MobileSearch />} />
          <Route path="/library" element={guard(<MobileLibrary />)} />
          <Route path="/live" element={guard(<MobileLive />)} />
          <Route path="/live/:broadcastId" element={<LiveDetailPage />} />
          <Route path="/shop" element={<MobileShop />} />
          <Route path="/shop/result" element={guard(<ShopResultPage />)} />
          <Route path="/shop/:id" element={<ProductPage />} />
          <Route path="/notes/result" element={guard(<NoteResultPage />)} />
          <Route path="/payment" element={guard(<PaymentPage />)} />
          <Route path="/payment/success" element={guard(<PaymentSuccessPage />)} />
          <Route path="/payment/fail" element={guard(<PaymentFailPage />)} />
        </Routes>
      </MobileShell>
      <YouTubePlayer />
      <HlsAudioPlayer />
      <PreviewLockModal />
      <BroadcasterNextSongListener />
      <LiveMiniPlayer />
    </>
  );
}