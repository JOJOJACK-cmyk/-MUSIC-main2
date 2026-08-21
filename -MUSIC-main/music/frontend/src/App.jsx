import React, { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import PlayerBar from './components/PlayerBar';
import MainPage from './pages/MainPage';
import LoginPage from './pages/LoginPage';
import ChartPage from './pages/ChartPage';
import LivePage from './pages/LivePage';
import LibraryPage from './pages/LibraryPage';
import { PlayerProvider } from './context/PlayerContext';
import './styles/style.css';

export default function App() {
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  return (
    <PlayerProvider>
      <BrowserRouter>
        <Routes>
          {/* Auth Page (Full Screen) */}
          <Route path="/login" element={<LoginPage />} />

          {/* Main Layout with Persistent Sidebar & Player */}
          <Route
            path="*"
            element={
              <div className="app-container">
                <Sidebar onOpenAddModal={() => setIsAddModalOpen(true)} />
                <Routes>
                  <Route
                    path="/"
                    element={
                      <MainPage
                        isModalOpen={isAddModalOpen}
                        setIsModalOpen={setIsAddModalOpen}
                      />
                    }
                  />
                  <Route path="/charts" element={<ChartPage />} />
                  <Route path="/live" element={<LivePage />} />
                  <Route path="/library" element={<LibraryPage />} />
                </Routes>
                <PlayerBar />
              </div>
            }
          />
        </Routes>
      </BrowserRouter>
    </PlayerProvider>
  );
}
