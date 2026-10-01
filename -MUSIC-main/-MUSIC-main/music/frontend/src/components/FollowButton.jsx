import React, { useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNotifications } from '../context/NotificationContext';

const API = ''; // Vite 프록시로 동일 출처 요청 (세션 쿠키 전달)
const GREEN = '#00FFA3';
const authHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export default function FollowButton({ channelUserId, size = 'md' }) {
  const { user } = useAuth();
  const { refreshFollowed } = useNotifications() || {};
  const [following, setFollowing] = useState(false);
  const [count, setCount] = useState(null);
  const [busy, setBusy] = useState(false);

  const mine = user?.id && String(user.id) === String(channelUserId);

  useEffect(() => {
    if (!channelUserId) return;
    (async () => {
      try {
        const r = await fetch(`${API}/api/follows/status/${channelUserId}`, {
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          credentials: 'include',
        });
        if (r.ok) {
          const d = await r.json();
          setFollowing(!!d.following);
          setCount(d.followerCount ?? null);
        }
      } catch (_) {}
    })();
  }, [channelUserId]);

  const toggle = async () => {
    if (!user) {
      window.location.href = '/login';
      return;
    }
    setBusy(true);
    try {
      const r = await fetch(`${API}/api/follows/${channelUserId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        credentials: 'include',
      });
      if (r.ok) {
        const d = await r.json();
        setFollowing(!!d.following);
        setCount(d.followerCount ?? count);
        refreshFollowed?.();
      }
    } catch (_) {}
    setBusy(false);
  };

  if (!channelUserId || mine) return null;

  const pad = size === 'sm' ? '5px 10px' : '7px 16px';
  const fs = size === 'sm' ? 12 : 13;

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <button
        onClick={toggle}
        disabled={busy}
        style={{
          padding: pad,
          fontSize: fs,
          fontWeight: 800,
          borderRadius: 8,
          cursor: 'pointer',
          border: following ? '1px solid #3a3f47' : 'none',
          background: following ? 'transparent' : GREEN,
          color: following ? '#c7ccd4' : '#04160f',
        }}
      >
        <i className={`fa-solid ${following ? 'fa-heart' : 'fa-heart-circle-plus'}`} style={{ marginRight: 5 }} />
        {following ? '팔로잉' : '팔로우'}
      </button>
      {count != null && (
        <span style={{ fontSize: 12, color: 'var(--text-sub, #a1a1aa)' }}>{count.toLocaleString()}</span>
      )}
    </span>
  );
}
