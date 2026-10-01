import React, { useEffect, useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import MobileTopBar from './MobileTopBar';
import { MobileFullPlayer, MobileMiniPlayer } from './MobilePlayer';

const TABS = [
  { to: '/', label: '홈', icon: 'fa-house', end: true },
  { to: '/charts', label: '차트', icon: 'fa-chart-line' },
  { to: '/live', label: '라이브', icon: 'fa-tower-broadcast' },
  { to: '/shop', label: '스토어', icon: 'fa-bag-shopping' },
  { to: '/library', label: '보관함', icon: 'fa-layer-group' },
];

/**
 * 모바일 전용 화면 틀: 상단 앱 바 · 본문 · 미니 플레이어 · 하단 탭바 · 전체화면 플레이어.
 * 라이브 시청 화면에서는 영상·채팅에 집중하도록 미니 플레이어를 숨긴다.
 */
export default function MobileShell({ children }) {
  const { pathname } = useLocation();
  const [fullOpen, setFullOpen] = useState(false);
  const watchingLive = /^\/live\/[^/]+$/.test(pathname);

  // 화면을 옮기면 전체화면 플레이어는 닫고, 본문은 맨 위로
  useEffect(() => {
    setFullOpen(false);
    document.querySelector('.m-main')?.scrollTo(0, 0);
  }, [pathname]);

  return (
    <div className={`m-app ${watchingLive ? 'watching-live' : ''}`}>
      <MobileTopBar />
      <main className="m-main">{children}</main>
      {!watchingLive && <MobileMiniPlayer onOpen={() => setFullOpen(true)} />}
      <nav className="m-tabbar" aria-label="주요 메뉴">
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end} className={({ isActive }) => `m-tab ${isActive ? 'active' : ''}`}>
            <i className={`fa-solid ${t.icon}`} />
            <span>{t.label}</span>
          </NavLink>
        ))}
      </nav>
      <MobileFullPlayer open={fullOpen} onClose={() => setFullOpen(false)} />
    </div>
  );
}
