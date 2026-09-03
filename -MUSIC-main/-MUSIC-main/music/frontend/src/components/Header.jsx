import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Header({ searchTerm, setSearchTerm }) {
  const { user, logout } = useAuth();

  const handleLogoutClick = async (e) => {
    e.currentTarget.blur();
    try {
      if (logout) {
        await logout();
      }
    } catch (err) {
      console.error('로그아웃 처리 중 오류:', err);
    } finally {
      // 💡 로그아웃 후 로컬스토리지 잔여 정보 확실히 지우고 페이지 새로고침
      localStorage.removeItem('user');
      window.location.href = '/';
    }
  };

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
        {/* 🔔 알림 버튼 */}
        <button
          type="button"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--text-sub)',
            cursor: 'pointer',
            fontSize: '16px',
            padding: '4px',
            outline: 'none',
          }}
        >
          <i className="fa-solid fa-bell"></i>
        </button>

        {user ? (
          /* 🟢 로그인 완료 상태: 닉네임 + 프로필 아바타 + 로그아웃 버튼 */
          <div className="user-info" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {/* 👤 프로필 아바타 버튼 (클릭 시 포커스 깜빡임 방지) */}
            <button
              type="button"
              tabIndex={-1}
              onClick={(e) => e.currentTarget.blur()}
              style={{
                background: 'transparent',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                outline: 'none',
              }}
            >
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
            </button>

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

            {/* 🚪 로그아웃 버튼 */}
            <button
              type="button"
              onClick={handleLogoutClick}
              tabIndex={-1}
              className="auth-nav-link"
              style={{
                background: 'transparent',
                border: '1px solid rgba(255, 255, 255, 0.2)',
                borderRadius: '16px',
                padding: '4px 10px',
                color: '#aaa',
                cursor: 'pointer',
                fontSize: '12px',
                outline: 'none',
              }}
            >
              로그아웃
            </button>
          </div>
        ) : (
          /* 🔴 비로그인 상태: 로그인 링크 + 기본 아바타 */
          <>
            <Link to="/login" className="auth-nav-link" style={{ outline: 'none' }}>
              로그인
            </Link>
            <button
              type="button"
              tabIndex={-1}
              onClick={(e) => e.currentTarget.blur()}
              style={{
                background: 'transparent',
                border: 'none',
                padding: 0,
                cursor: 'pointer',
                outline: 'none',
              }}
            >
              <div className="avatar">
                <i className="fa-solid fa-user"></i>
              </div>
            </button>
          </>
        )}
      </div>
    </header>
  );
}