import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useNavigate } from 'react-router-dom';

const NotificationContext = createContext(null);

const API = ''; // Vite 프록시로 동일 출처 요청 (세션 쿠키 전달)
const READ_AT_KEY = 'notifications:readAt';
const PREFS_KEY = 'notifications:prefs';
const WS_URL = `${API}/ws-stomp`;

// 알림 종류별 수신 설정 (프로필 설정창에서 토글). 기본값: 전부 on
export function getNotifPrefs() {
  try {
    return { LIVE_START: true, NEW_HOT_SONG: true, ...JSON.parse(localStorage.getItem(PREFS_KEY) || '{}') };
  } catch {
    return { LIVE_START: true, NEW_HOT_SONG: true };
  }
}
export function setNotifPrefs(prefs) {
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
}

const authHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

export const NotificationProvider = ({ children }) => {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState([]);
  const [toasts, setToasts] = useState([]);
  const [followedIds, setFollowedIds] = useState(new Set());
  const [readAt, setReadAt] = useState(() => {
    const v = Number(localStorage.getItem(READ_AT_KEY));
    return Number.isFinite(v) ? v : 0;
  });

  const seenIdsRef = useRef(new Set());
  const followedRef = useRef(new Set());
  const clientRef = useRef(null);

  // 내가 팔로우한 채널 id 목록 (LIVE_START 알림 강조/필터용)
  const refreshFollowed = useCallback(async () => {
    try {
      const res = await fetch(`${API}/api/follows/following`, {
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        credentials: 'include',
      });
      if (res.ok) {
        const list = await res.json();
        const s = new Set((Array.isArray(list) ? list : []).map((c) => c.userId));
        followedRef.current = s;
        setFollowedIds(s);
      }
    } catch (_) {}
  }, []);

  useEffect(() => {
    refreshFollowed();
  }, [refreshFollowed]);

  const pushToast = useCallback((n) => {
    setToasts((prev) => [...prev, n]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== n.id));
    }, 5000);
  }, []);

  const addNotification = useCallback(
    (n, { toast = false } = {}) => {
      if (!n || !n.id || seenIdsRef.current.has(n.id)) return;
      seenIdsRef.current.add(n.id);
      setNotifications((prev) => [n, ...prev].slice(0, 50));
      if (toast) pushToast(n);
    },
    [pushToast]
  );

  // 최초 로딩 시 최근 알림 이력 불러오기
  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API}/api/notifications/recent`);
        if (res.ok) {
          const list = await res.json();
          (Array.isArray(list) ? list : [])
            .slice()
            .reverse()
            .forEach((n) => addNotification(n));
        }
      } catch (_) {}
    })();
  }, [addNotification]);

  // WebSocket(STOMP) 실시간 구독
  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/notifications', (msg) => {
          try {
            const n = JSON.parse(msg.body);
            const prefs = getNotifPrefs();
            if (n.type && prefs[n.type] === false) return;
            // 팔로우한 채널의 라이브 시작이면 강조 표시
            if (n.type === 'LIVE_START' && n.broadcasterId && followedRef.current.has(n.broadcasterId)) {
              n.followed = true;
              n.title = '⭐ ' + n.title;
            }
            addNotification(n, { toast: true });
          } catch (_) {}
        });
      },
    });
    client.activate();
    clientRef.current = client;
    return () => {
      try {
        client.deactivate();
      } catch (_) {}
    };
  }, [addNotification]);

  const unreadCount = notifications.filter((n) => (n.createdAt || 0) > readAt).length;

  const markAllRead = useCallback(() => {
    const now = Date.now();
    setReadAt(now);
    localStorage.setItem(READ_AT_KEY, String(now));
  }, []);

  const dismissToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  // 알림 클릭 → 관련 페이지로 이동 (SPA 라우팅). link 없으면 타입별 기본 경로.
  const openNotification = useCallback(
    (n) => {
      if (!n) return;
      let target = n.link;
      if (!target || target === '/') {
        if (n.type === 'NEW_HOT_SONG') target = '/charts';
        else if (n.type === 'LIVE_START') target = '/live';
        else target = '/';
      }
      try {
        navigate(target);
      } catch {
        window.location.href = target;
      }
    },
    [navigate]
  );

  return (
    <NotificationContext.Provider
      value={{ notifications, unreadCount, markAllRead, readAt, followedIds, refreshFollowed, openNotification }}
    >
      {children}
      <ToastStack toasts={toasts} onDismiss={dismissToast} onOpen={openNotification} />
    </NotificationContext.Provider>
  );
};

function iconFor(type) {
  if (type === 'LIVE_START') return 'fa-tower-broadcast';
  if (type === 'NEW_HOT_SONG') return 'fa-fire';
  return 'fa-bell';
}

function ToastStack({ toasts, onDismiss, onOpen }) {
  return (
    <div
      style={{
        position: 'fixed',
        right: 20,
        bottom: 110,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        zIndex: 9999,
        maxWidth: 340,
      }}
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          onClick={() => {
            onOpen?.(t);
            onDismiss(t.id);
          }}
          style={{
            background: t.followed
              ? 'linear-gradient(135deg, #062b1f, #0a3d2c)'
              : 'linear-gradient(135deg, #1e1b2e, #2b2140)',
            border: t.followed ? '1px solid #00FFA355' : '1px solid rgba(236,72,153,0.4)',
            borderRadius: 14,
            padding: '12px 14px',
            color: '#f4f4f5',
            boxShadow: '0 10px 30px rgba(0,0,0,0.45)',
            cursor: 'pointer',
            display: 'flex',
            gap: 12,
            alignItems: 'flex-start',
            animation: 'toastIn 0.25s ease',
          }}
        >
          <i
            className={`fa-solid ${iconFor(t.type)}`}
            style={{ color: '#ec4899', fontSize: 16, marginTop: 2 }}
          />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 2 }}>{t.title}</div>
            <div
              style={{
                fontSize: 12,
                color: '#a1a1aa',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {t.message}
            </div>
          </div>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onDismiss(t.id);
            }}
            style={{
              background: 'transparent',
              border: 'none',
              color: '#71717a',
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            <i className="fa-solid fa-xmark" />
          </button>
        </div>
      ))}
      <style>{`@keyframes toastIn{from{opacity:0;transform:translateX(20px)}to{opacity:1;transform:translateX(0)}}`}</style>
    </div>
  );
}

export const useNotifications = () => useContext(NotificationContext);
