import React from 'react';
import { Link } from 'react-router-dom';

export default function Header({ searchTerm, setSearchTerm }) {
  return (
    <header className="top-header">
      <div className="search-bar">
        <i className="fa-solid fa-magnifying-glass"></i>
        <input
          type="text"
          placeholder="듣고 싶은 곡, 아티스트를 검색하세요"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>
      <div className="user-profile">
        <i className="fa-solid fa-bell" style={{ cursor: 'pointer' }}></i>
        <Link to="/login" className="auth-nav-link">
          로그인
        </Link>
        <div className="avatar">
          <i className="fa-solid fa-user"></i>
        </div>
      </div>
    </header>
  );
}
