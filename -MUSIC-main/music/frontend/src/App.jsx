import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

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
import PaymentPage from './pages/PaymentPage'; // 👈 1. 결제 페이지 임포트 추가

import { PlayerProvider } from './context/PlayerContext';
import { AuthProvider, useAuth } from './context/AuthContext';
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

export default function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // 음원 등록 핸들러
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
          <div className="app-container">
            <Sidebar onOpenAddModal={() => setIsModalOpen(true)} />

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

                {/* 💳 2. 결제 페이지 라우트 추가 완료 */}
                <Route
                  path="/payment"
                  element={
                    <ProtectedRoute>
                      <PaymentPage />
                    </ProtectedRoute>
                  }
                />
              </Routes>
            </main>

            {/* 음원 등록 모달 */}
            {isModalOpen && (
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
        </BrowserRouter>
      </PlayerProvider>
    </AuthProvider>
  );
}