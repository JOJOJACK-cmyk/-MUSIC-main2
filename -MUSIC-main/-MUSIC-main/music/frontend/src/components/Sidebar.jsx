import React from 'react';
import { NavLink } from 'react-router-dom';

export default function Sidebar({ onOpenAddModal }) {
  return (
    <aside className="sidebar">
      <NavLink to="/" className="logo">
        <i className="fa-solid fa-compact-disc spin-logo"></i>
        <span>Music</span>
      </NavLink>
      <nav className="nav-menu">
        <NavLink to="/" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <i className="fa-solid fa-house"></i> 홈
        </NavLink>
        <NavLink to="/charts" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <i className="fa-solid fa-chart-line"></i> TOP 100 차트
        </NavLink>
        <NavLink to="/live" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <i className="fa-solid fa-tv"></i> 실시간 라이브
        </NavLink>
        <NavLink to="/library" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <i className="fa-solid fa-lines-leaning"></i> 내 보관함
        </NavLink>

        <NavLink to="/payment" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <i className="fa-solid fa-credit-card"></i> 이용권 결제
        </NavLink>
      </nav>

      <button className="add-music-nav-btn" onClick={onOpenAddModal}>
        <i className="fa-solid fa-plus"></i> 음원 등록
      </button>
    </aside>
  );
}