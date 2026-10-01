import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { musicApi } from '../api/musicApi';

export default function Sidebar({ onOpenAddModal }) {
  const [cleaning, setCleaning] = useState(false);

  const handleRevalidate = async () => {
    if (cleaning) return;
    if (!window.confirm('DB의 모든 곡을 유튜브에서 다시 확인해 음악이 아닌 영상을 삭제합니다. 진행할까요?')) return;
    setCleaning(true);
    try {
      const res = await musicApi.revalidateCatalog();
      const d = res.data || res;
      let msg = `정리 완료: ${d.checked}곡 검사 / ${d.removed}곡 삭제`;
      if (d.failed) msg += ` / ${d.failed}곡 삭제실패`;
      if (d.apiError) msg += `\n(유튜브 API 오류 ${d.apiError}건 — 할당량 문제일 수 있음)`;
      alert(msg);
      window.location.reload();
    } catch (e) {
      alert('정리 실패: ' + (e?.response?.data?.message || e.message));
    } finally {
      setCleaning(false);
    }
  };
  // 💡 관리자 여부 판별 (디버깅 로그 포함)
  let isAdmin = false;
  try {
    const rawUser = localStorage.getItem('user');
    console.log('현재 로컬스토리지 user 데이터:', rawUser); // 콘솔에서 값 확인용

    if (rawUser) {
      const userObj = JSON.parse(rawUser);
      console.log('파싱된 유저 객체:', userObj);

      // 관리자 + 부 관리자 모두 콘텐츠 관리 UI 노출
      const role = userObj.role ? userObj.role.toUpperCase() : '';
      if (['ROLE_ADMIN', 'ADMIN', 'ROLE_SUB_ADMIN', 'SUB_ADMIN'].includes(role)) {
        isAdmin = true;
      }
    }
  } catch (e) {
    console.error('권한 확인 중 오류:', e);
  }

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

        <NavLink
          to="/shop"
          tabIndex={-1}
          onClick={(e) => e.currentTarget.blur()}
          className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
        >
          <i className="fa-solid fa-bag-shopping"></i> 스토어
        </NavLink>
      </nav>

      {/* 💡 관리자 계정일 때만 '음원 등록' 버튼 노출 */}
      {isAdmin && (
        <>
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
          <button
            className="add-music-nav-btn"
            tabIndex={-1}
            disabled={cleaning}
            onClick={(e) => { e.currentTarget.blur(); handleRevalidate(); }}
            style={{ marginTop: 8, background: 'transparent', border: '1px solid var(--primary-color)' }}
          >
            <i className="fa-solid fa-broom"></i> {cleaning ? '정리 중…' : '카탈로그 정리'}
          </button>
        </>
      )}
    </aside>
  );
}