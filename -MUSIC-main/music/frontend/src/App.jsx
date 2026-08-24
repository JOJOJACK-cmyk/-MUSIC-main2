import React, { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

import Sidebar from './components/Sidebar';
import PlayerBar from './components/PlayerBar';
import YouTubePlayer from './components/YouTubePlayer';

import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import ChartPage from './pages/ChartPage';
import LivePage from './pages/LivePage';
import LibraryPage from './pages/LibraryPage';

import {
  PlayerProvider,
  usePlayer
} from './context/PlayerContext';

import './styles/style.css';


/**
 * 현재 재생 중인 YouTube 영상
 */
function NowPlayingVideo() {

  const { currentTrack } = usePlayer();


  // 현재 선택된 곡이 없으면
  // YouTube 플레이어를 보여주지 않음
  if (!currentTrack?.youtubeVideoId) {
    return null;
  }


  return (

    <div className="youtube-player-floating">

      {/* 현재 재생 곡 제목 */}
      <div className="youtube-player-title">

        <span>
          현재 재생
        </span>

        <strong>
          {currentTrack.title}
        </strong>

      </div>


      {/* 실제 YouTube Player */}
    <YouTubePlayer
        key={currentTrack.youtubeVideoId}
         videoId={currentTrack.youtubeVideoId}
        />

    </div>
  );
}


/**
 * 메인 App
 */
export default function App() {

  // 음악 추가 Modal 상태
  const [
    isAddModalOpen,
    setIsAddModalOpen
  ] = useState(false);


  return (

    <PlayerProvider>

      <BrowserRouter>

        <Routes>


          {/* =========================
              로그인 페이지
             ========================= */}

          <Route
            path="/login"
            element={
              <LoginPage />
            }
          />


          {/* =========================
              로그인 이후 메인 화면
             ========================= */}

          <Route
            path="*"
            element={

              <div className="app-container">


                {/* 왼쪽 Sidebar */}
                <Sidebar

                  onOpenAddModal={() =>
                    setIsAddModalOpen(true)
                  }

                />


                {/* =========================
                    페이지 영역
                   ========================= */}

                <Routes>


                  {/* 메인 페이지 */}
                  <Route

                    path="/"

                    element={

                      <MainPage

                        isModalOpen={
                          isAddModalOpen
                        }

                        setIsModalOpen={
                          setIsAddModalOpen
                        }

                      />
                    }

                  />


                  {/* 차트 */}
                  <Route
                    path="/charts"
                    element={
                      <ChartPage />
                    }
                  />


                  {/* 라이브 */}
                  <Route
                    path="/live"
                    element={
                      <LivePage />
                    }
                  />


                  {/* 라이브러리 */}
                  <Route
                    path="/library"
                    element={
                      <LibraryPage />
                    }
                  />


                </Routes>


                {/* =========================
                    실제 YouTube 영상
                   ========================= */}

                <NowPlayingVideo />


                {/* =========================
                    하단 음악 PlayerBar
                   ========================= */}

                <PlayerBar />


              </div>
            }
          />


        </Routes>

      </BrowserRouter>

    </PlayerProvider>
  );
}