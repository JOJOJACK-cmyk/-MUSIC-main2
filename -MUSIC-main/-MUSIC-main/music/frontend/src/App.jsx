import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import LiveDetailPage from './pages/LiveDetailPage';
import Sidebar from './components/Sidebar';
import PlayerBar from './components/PlayerBar';
import YouTubePlayer from './components/YouTubePlayer';
import HlsAudioPlayer from './components/HlsAudioPlayer';
import MusicModal from './components/MusicModal';

import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import ChartPage from './pages/ChartPage';
import LivePage from './pages/LivePage';
import LibraryPage from './pages/LibraryPage';
import PaymentPage from './pages/PaymentPage';

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

// 💡 소셜 로그인 직후 URL 파라미터를 감지하여 localStorage에 저장하는 컴포넌트
function AuthHandler() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const email = params.get('email');

    if (email) {
      const userData = {
        nickname: params.get('nickname') || '사용자',
        email: email,
        profileImageUrl: params.get('profileImageUrl') || '',
        role: params.get('role') || 'ROLE_USER', // 💡 백엔드가 넘겨준 role을 여기서 받아서 저장합니다!
      };

      localStorage.setItem('user', JSON.stringify(userData));

      // URL을 깔끔하게 정리 (파라미터 제거)
      navigate(location.pathname, { replace: true });

      // 상태 반영을 위해 새로고침
      window.location.reload();
    }
  }, [location, navigate]);

  return null;
}

export default function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // 💡 로컬스토리지에서 관리자(ROLE_ADMIN) 여부 확인
  let isAdmin = false;
  try {
    const rawUser = localStorage.getItem('user');
    if (rawUser) {
      const userObj = JSON.parse(rawUser);
      // ROLE_ADMIN 또는 ADMIN 모두 허용
      const role = userObj.role ? userObj.role.toUpperCase() : '';
      isAdmin = role === 'ROLE_ADMIN' || role === 'ADMIN';
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

  return (
    <AuthProvider>
      <PlayerProvider>
        <BrowserRouter>
          <NotificationProvider>
          <AuthHandler /> {/* 💡 로그인 직후 파라미터 캐치 핸들러 실행 */}
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
          </div>
          </NotificationProvider>
        </BrowserRouter>
      </PlayerProvider>
    </AuthProvider>
  );
}