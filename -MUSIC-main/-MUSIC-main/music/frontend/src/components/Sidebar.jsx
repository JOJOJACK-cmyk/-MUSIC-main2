import React from 'react';
import { NavLink } from 'react-router-dom';

export default function Sidebar({ onOpenAddModal }) {
  return (
    <aside className="sidebar">
      {/* 🎵 왼쪽 상단 'Music' 로고 영역 */}
      <NavLink
        to="/"
        className="logo"
        tabIndex={-1}
        onClick={(e) => e.currentTarget.blur()}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          textDecoration: 'none',
          outline: 'none',
          transition: 'transform 0.2s ease, opacity 0.2s ease',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'scale(1.05)';
          e.currentTarget.style.opacity = '0.85';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'scale(1)';
          e.currentTarget.style.opacity = '1';
        }}
      >
        <i className="fa-solid fa-compact-disc spin-logo" style={{ color: '#ff2a85' }}></i>
        <span>Music</span>
      </NavLink>

      <nav className="nav-menu">
        <NavLink
          to="/"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <i className="fa-solid fa-house"></i> 홈
        </NavLink>
        <NavLink
          to="/charts"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <i className="fa-solid fa-chart-line"></i> TOP 100 차트
        </NavLink>
        <NavLink
          to="/live"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <i className="fa-solid fa-tv"></i> 실시간 라이브
        </NavLink>
        <NavLink
          to="/library"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <i className="fa-solid fa-lines-leaning"></i> 내 보관함
        </NavLink>

        <NavLink
          to="/payment"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <i className="fa-solid fa-credit-card"></i> 이용권 결제
        </NavLink>
      </nav>

      <button
        className="add-music-nav-btn"
        tabIndex={-1}
        onClick={(e) => {
          e.currentTarget.blur();
          onOpenAddModal && onOpenAddModal();
        }}
      >
        <i className="fa-solid fa-plus"></i> 음원 등록
      </button>
    </aside>
  );
}