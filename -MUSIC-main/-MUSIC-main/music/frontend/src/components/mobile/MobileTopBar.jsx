import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useNotifications } from '../../context/NotificationContext';
import ProfilePanel from '../ProfilePanel';
import MobileSheet from './MobileSheet';

// 경로별 상단 제목. 목록에 없는 (상세) 경로는 뒤로가기 버튼을 보여준다.
const TITLES = {
  '/': null, // 홈은 로고
  '/charts': 'TOP 100',
  '/live': '라이브',
  '/shop': '스토어',
  '/library': '보관함',
  '/search': '검색',
  '/payment': '이용권',
};

function timeAgo(ms) {
  const s = Math.floor((Date.now() - ms) / 1000);
  if (s < 60) return '방금';
  if (s < 3600) return `${Math.floor(s / 60)}분 전`;
  if (s < 86400) return `${Math.floor(s / 3600)}시간 전`;
  return `${Math.floor(s / 86400)}일 전`;
}

/** 모바일 상단 앱 바: [← | 로고/제목] ··· 🔍 🔔 👤 */
export default function MobileTopBar() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth() || {};
  const notif = useNotifications();
  const [bellOpen, setBellOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const isTop = Object.prototype.hasOwnProperty.call(TITLES, pathname);
  const title = isTop ? TITLES[pathname] : null;
  const unread = notif?.unreadCount || 0;

  const openBell = () => { setBellOpen(true); notif?.markAllRead?.(); };
  // 링크로 바로 들어온 상세 화면(공유된 라이브 주소 등)은 돌아갈 앱 내 기록이 없으니 홈으로
  const goBack = () => {
    if ((window.history.state?.idx ?? 0) > 0) navigate(-1);
    else navigate('/', { replace: true });
  };

  return (
    <header className="m-topbar">
      {isTop ? (
        title ? <h1 className="m-topbar-title">{title}</h1> : (
          <div className="m-logo"><span>♪</span>StreamWave</div>
        )
      ) : (
        <button className="m-icon-btn" aria-label="뒤로" onClick={goBack}>
          <i className="fa-solid fa-chevron-left" />
        </button>
      )}

      <div className="m-topbar-actions">
        {pathname !== '/search' && (
          <button className="m-icon-btn" aria-label="검색" onClick={() => navigate('/search')}>
            <i className="fa-solid fa-magnifying-glass" />
          </button>
        )}
        <button className="m-icon-btn" aria-label="알림" onClick={openBell}>
          <i className="fa-solid fa-bell" />
          {unread > 0 && <span className="m-badge">{unread > 9 ? '9+' : unread}</span>}
        </button>
        {user ? (
          <button className="m-avatar" aria-label="내 정보" onClick={() => setProfileOpen(true)}>
            {user.profileImageUrl ? <img src={user.profileImageUrl} alt="" /> : <i className="fa-solid fa-user" />}
          </button>
        ) : (
          <button className="m-login" onClick={() => navigate('/login')}>로그인</button>
        )}
      </div>

      <MobileSheet open={bellOpen} title="알림" onClose={() => setBellOpen(false)} tall>
        {(notif?.notifications || []).length === 0 ? (
          <div className="m-empty small">새 알림이 없어요</div>
        ) : (
          notif.notifications.map((n) => (
            <button key={n.id} className="m-noti" onClick={() => { setBellOpen(false); notif.openNotification?.(n); }}>
              <i className={`fa-solid ${n.type === 'LIVE_START' ? 'fa-tower-broadcast' : n.type === 'PASS_EXPIRY' ? 'fa-ticket' : n.type === 'NEW_HOT_SONG' ? 'fa-fire' : 'fa-bell'}`} />
              <span>
                <strong>{n.title}</strong>
                <small>{n.message}</small>
                <em>{n.createdAt ? timeAgo(n.createdAt) : ''}</em>
              </span>
            </button>
          ))
        )}
      </MobileSheet>

      {profileOpen && <ProfilePanel onClose={() => setProfileOpen(false)} />}
    </header>
  );
}
