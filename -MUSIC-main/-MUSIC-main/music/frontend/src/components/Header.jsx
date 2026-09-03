import React, { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';
import ProfilePanel from './ProfilePanel';

function timeAgo(ms) {
  const s = Math.floor((Date.now() - ms) / 1000);
  if (s < 60) return '방금';
  if (s < 3600) return `${Math.floor(s / 60)}분 전`;
  if (s < 86400) return `${Math.floor(s / 3600)}시간 전`;
  return `${Math.floor(s / 86400)}일 전`;
}

function iconFor(type) {
  if (type === 'LIVE_START') return 'fa-tower-broadcast';
  if (type === 'NEW_HOT_SONG') return 'fa-fire';
  return 'fa-bell';
}

export default function Header({ searchTerm, setSearchTerm }) {
  const { user } = useAuth();
  const notif = useNotifications();
  const notifications = notif?.notifications || [];
  const unreadCount = notif?.unreadCount || 0;
  const openNotification = notif?.openNotification;

  const [bellOpen, setBellOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const bellRef = useRef(null);

  useEffect(() => {
    const onDown = (e) => {
      if (bellRef.current && !bellRef.current.contains(e.target)) setBellOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, []);

  const toggleBell = () => {
    setBellOpen((v) => {
      const next = !v;
      if (next) notif?.markAllRead?.();
      return next;
    });
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
        {/* 🔔 알림 버튼 + 드롭다운 */}
        <div ref={bellRef} style={{ position: 'relative' }}>
          <button
            type="button"
            onClick={toggleBell}
            style={{
              background: 'transparent',
              border: 'none',
              color: bellOpen ? '#ec4899' : 'var(--text-sub)',
              cursor: 'pointer',
              fontSize: '16px',
              padding: '4px',
              outline: 'none',
              position: 'relative',
            }}
          >
            <i className="fa-solid fa-bell"></i>
            {unreadCount > 0 && (
              <span
                style={{
                  position: 'absolute',
                  top: -2,
                  right: -2,
                  minWidth: 16,
                  height: 16,
                  padding: '0 4px',
                  borderRadius: 999,
                  background: '#ec4899',
                  color: '#fff',
                  fontSize: 10,
                  fontWeight: 800,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  lineHeight: 1,
                }}
              >
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </button>

          {bellOpen && (
            <div
              style={{
                position: 'absolute',
                top: 40,
                right: 0,
                width: 320,
                maxHeight: '70vh',
                overflowY: 'auto',
                background: '#0c0c0e',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 14,
                boxShadow: '0 24px 60px rgba(0,0,0,0.6)',
                zIndex: 3000,
              }}
            >
              <div style={{ padding: '12px 14px', borderBottom: '1px solid rgba(255,255,255,0.08)', fontWeight: 700, fontSize: 13, color: '#fff' }}>
                알림
              </div>
              {notifications.length === 0 ? (
                <div style={{ padding: '28px 14px', textAlign: 'center', color: '#71717a', fontSize: 13 }}>
                  새로운 알림이 없습니다.
                </div>
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.id}
                    onClick={() => {
                      openNotification?.(n);
                      setBellOpen(false);
                    }}
                    style={{
                      display: 'flex',
                      gap: 10,
                      padding: '11px 14px',
                      borderBottom: '1px solid rgba(255,255,255,0.05)',
                      cursor: 'pointer',
                      background: n.followed ? 'rgba(0,255,163,0.06)' : 'transparent',
                    }}
                  >
                    <i className={`fa-solid ${iconFor(n.type)}`} style={{ color: n.followed ? '#00FFA3' : '#ec4899', fontSize: 14, marginTop: 3 }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, color: '#f4f4f5', fontWeight: 600 }}>{n.title}</div>
                      <div style={{ fontSize: 12, color: '#a1a1aa', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {n.message}
                      </div>
                      <div style={{ fontSize: 10, color: '#52525b', marginTop: 3 }}>{timeAgo(n.createdAt)}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>

        {user ? (
          /* 🟢 로그인 상태: 프로필 아바타(클릭 시 설정창) + 닉네임 */
          <div className="user-info" style={{ display: 'flex', alignItems: 'center', gap: '8px', position: 'relative' }}>
            <button
              type="button"
              onClick={() => setProfileOpen((v) => !v)}
              style={{ background: 'transparent', border: 'none', padding: 0, cursor: 'pointer', outline: 'none' }}
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
                  border: profileOpen ? '2px solid #ec4899' : '2px solid transparent',
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
              onClick={() => setProfileOpen((v) => !v)}
              style={{
                color: '#fff',
                fontSize: '14px',
                fontWeight: '500',
                maxWidth: '120px',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                cursor: 'pointer',
              }}
            >
              {user.nickname || user.name || '사용자'}
            </span>

            {profileOpen && <ProfilePanel onClose={() => setProfileOpen(false)} />}
          </div>
        ) : (
          /* 🔴 비로그인 상태 */
          <>
            <Link to="/login" className="auth-nav-link" style={{ outline: 'none' }}>
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
