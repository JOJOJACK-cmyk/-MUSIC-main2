import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Header({ searchTerm, setSearchTerm }) {
  const { user, logout } = useAuth();

  return (
    <header className="top-header">
      {/* 1. 검색바 영역 */}
      <div className="search-bar">
        <i className="fa-solid fa-magnifying-glass"></i>
        <input
          type="text"
          placeholder="듣고 싶은 곡, 아티스트를 검색하세요"
          value={searchTerm || ''}
          onChange={(e) => setSearchTerm && setSearchTerm(e.target.value)}
        />
      </div>

      {/* 2. 우측 사용자 프로필 / 로그인 영역 */}
      <div className="user-profile" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <i className="fa-solid fa-bell" style={{ cursor: 'pointer' }}></i>

        {user ? (
          /* 🟢 로그인 완료 상태: 닉네임 + 프로필 아바타 + 로그아웃 버튼 */
          <div className="user-info" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div
              className="avatar"
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                overflow: 'hidden',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {user.profileImageUrl ? (
                <img
                  src={user.profileImageUrl}
                  alt={user.nickname || '프로필'}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  onError={(e) => {
                    e.currentTarget.style.display = 'none';
                  }}
                />
              ) : (
                <i className="fa-solid fa-user"></i>
              )}
            </div>

            <span
              className="user-nickname"
              style={{
                color: '#fff',
                fontSize: '14px',
                fontWeight: '500',
                maxWidth: '120px',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {user.nickname || user.name || '사용자'}
            </span>

            <button
              onClick={logout}
              className="auth-nav-link"
              style={{
                background: 'transparent',
                border: '1px solid rgba(255, 255, 255, 0.2)',
                borderRadius: '16px',
                padding: '4px 10px',
                color: '#aaa',
                cursor: 'pointer',
                fontSize: '12px',
              }}
            >
              로그아웃
            </button>
          </div>
        ) : (
          /* 🔴 비로그인 상태: 로그인 링크 + 기본 아바타 */
          <>
            <Link to="/login" className="auth-nav-link">
              로그인
            </Link>
            <div className="avatar">
              <i className="fa-solid fa-user"></i>
            </div>
          </>
        )}
      </div>
    </header>
  );
}