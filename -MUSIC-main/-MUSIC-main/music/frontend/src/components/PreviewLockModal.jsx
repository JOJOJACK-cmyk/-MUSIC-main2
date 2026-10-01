import React from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { usePlayer } from '../context/PlayerContext';
import { useAuth } from '../context/AuthContext';

// 무료(비로그인 또는 미결제) 회원이 세션 누적 1분 미리듣기를 모두 소진했을 때 뜨는 안내창.
export default function PreviewLockModal() {
  const { previewLocked, previewLimitSeconds, dismissPreviewLock } = usePlayer();
  const { user } = useAuth();
  const navigate = useNavigate();

  if (!previewLocked) return null;

  const isLoggedIn = Boolean(user);

  const go = (path) => {
    dismissPreviewLock();
    navigate(path);
  };

  return createPortal(
    <div
      onMouseDown={(e) => e.target === e.currentTarget && dismissPreviewLock()}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.66)', backdropFilter: 'blur(3px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 6000,
      }}
    >
      <div
        style={{
          width: 380, maxWidth: '92vw', background: '#161719', border: '1px solid #2a2e35',
          borderRadius: 16, padding: '28px 24px', textAlign: 'center', color: '#e9edf1',
          boxShadow: '0 30px 80px rgba(0,0,0,0.6)',
        }}
      >
        <div style={{ fontSize: 34, marginBottom: 12 }}>🔒</div>
        <h3 style={{ margin: '0 0 8px', fontSize: 17, fontWeight: 800 }}>
          무료 미리듣기 {previewLimitSeconds}초가 끝났어요
        </h3>
        <p style={{ margin: '0 0 20px', fontSize: 13, color: '#a7adb8', lineHeight: 1.6 }}>
          {isLoggedIn
            ? '이용권을 구매하면 모든 곡을 제한 없이 감상할 수 있어요.'
            : '로그인하고 이용권을 구매하면 모든 곡을 제한 없이 감상할 수 있어요.'}
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {isLoggedIn ? (
            <button
              onClick={() => go('/payment')}
              style={{
                background: '#00FFA3', color: '#04160f', border: 'none', borderRadius: 8,
                padding: '11px 16px', fontSize: 14, fontWeight: 800, cursor: 'pointer',
              }}
            >
              이용권 구매하기
            </button>
          ) : (
            <button
              onClick={() => go('/login')}
              style={{
                background: '#00FFA3', color: '#04160f', border: 'none', borderRadius: 8,
                padding: '11px 16px', fontSize: 14, fontWeight: 800, cursor: 'pointer',
              }}
            >
              로그인하기
            </button>
          )}
          <button
            onClick={dismissPreviewLock}
            style={{
              background: 'transparent', color: '#8b93a1', border: '1px solid #363b44',
              borderRadius: 8, padding: '10px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
            }}
          >
            닫기
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
