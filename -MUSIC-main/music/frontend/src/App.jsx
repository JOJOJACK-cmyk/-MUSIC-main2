import React, { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

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

import { PlayerProvider } from './context/PlayerContext';
import { AuthProvider } from './context/AuthContext';
import { musicApi } from './api/musicApi';
import './styles/style.css';

export default function App() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  // 음원 등록 핸들러
  const handleAddMusicSubmit = async (data) => {
    try {
      if (data.youtubeVideoId) {
        // YouTube 등록: videoId 문자열을 전달
        await musicApi.createMusicFromYouTube(data.youtubeVideoId);
      } else {
        // 일반 등록
        await musicApi.createMusic(data);
      }

      alert('음원이 성공적으로 등록되었습니다!');
      setIsModalOpen(false);
      window.location.reload(); // 등록 후 목록 갱신
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
                <Route path="/" element={<MainPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/chart" element={<ChartPage />} />
                <Route path="/live" element={<LivePage />} />
                <Route path="/library" element={<LibraryPage />} />
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