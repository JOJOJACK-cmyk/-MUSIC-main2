import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

import Sidebar from './components/Sidebar';
import PlayerBar from './components/PlayerBar';
import YouTubePlayer from './components/YouTubePlayer';
import HlsAudioPlayer from './components/HlsAudioPlayer';

import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import ChartPage from './pages/ChartPage';
import LivePage from './pages/LivePage';
import LibraryPage from './pages/LibraryPage';

import { PlayerProvider } from './context/PlayerContext';
import { AuthProvider } from './context/AuthContext'; // 💡 인증 Context 추가
import './styles/style.css';

export default function App() {
  return (
    <AuthProvider>
      <PlayerProvider>
        <BrowserRouter>
          <div className="app-container">
            <Sidebar />

            <main className="main-content">
              <Routes>
                <Route path="/" element={<MainPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/chart" element={<ChartPage />} />
                <Route path="/live" element={<LivePage />} />
                <Route path="/library" element={<LibraryPage />} />
              </Routes>
            </main>

            {/* 전역 플레이어 및 컨트롤러 컴포넌트 */}
            <YouTubePlayer />
            <HlsAudioPlayer />
            <PlayerBar />
          </div>
        </BrowserRouter>
      </PlayerProvider>
    </AuthProvider>
  );
}