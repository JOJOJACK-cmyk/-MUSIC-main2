import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useAuth } from '../context/AuthContext';
import { getNotifPrefs, setNotifPrefs, useNotifications } from '../context/NotificationContext';

const API = ''; // Vite 프록시로 동일 출처 요청 (세션 쿠키 전달)
const GREEN = '#00FFA3'; // 치지직 시그니처 그린

const authHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};
const req = (path, opts = {}) =>
  fetch(`${API}${path}`, {
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    credentials: 'include',
    ...opts,
  });

const TABS = [
  { key: 'studio', label: '스튜디오', icon: 'fa-sliders' },
  { key: 'channel', label: '내 채널', icon: 'fa-house-user' },
  { key: 'clips', label: '내 클립', icon: 'fa-scissors' },
  { key: 'following', label: '팔로잉 채널', icon: 'fa-heart' },
  { key: 'subscription', label: '내 구독', icon: 'fa-ticket' },
  { key: 'noti', label: '알림', icon: 'fa-bell' },
  { key: 'account', label: '내 정보', icon: 'fa-user' },
];

const field = {
  width: '100%', background: '#101216', border: '1px solid #2a2e35', borderRadius: 8,
  padding: '10px 12px', color: '#e9edf1', fontSize: 13, outline: 'none',
};
const fieldLabel = { display: 'block', fontSize: 12, color: '#8b93a1', marginBottom: 6, fontWeight: 600 };
const primaryBtn = {
  background: GREEN, color: '#04160f', border: 'none', borderRadius: 8,
  padding: '9px 16px', fontSize: 13, fontWeight: 800, cursor: 'pointer',
};
const ghostBtn = {
  background: 'transparent', color: '#c7ccd4', border: '1px solid #363b44', borderRadius: 8,
  padding: '9px 14px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
};
const dangerBtn = { ...ghostBtn, borderColor: '#5b2b2b', color: '#ff8b8b' };
const sectionTitle = { fontSize: 15, fontWeight: 800, color: '#f2f4f7', margin: '0 0 4px' };
const sectionDesc = { fontSize: 12, color: '#8b93a1', margin: '0 0 18px' };
const card = { background: '#181b20', border: '1px solid #262a31', borderRadius: 12, padding: 16 };

export default function ProfilePanel({ onClose }) {
  const { user } = useAuth();
  const [tab, setTab] = useState('studio');
  const overlayRef = useRef(null);

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const content = (
    <div
      ref={overlayRef}
      onMouseDown={(e) => e.target === overlayRef.current && onClose()}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', backdropFilter: 'blur(3px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 5000,
      }}
    >
      <div
        style={{
          width: 820, maxWidth: '95vw', height: 580, maxHeight: '92vh', background: '#141517',
          border: '1px solid #2a2e35', borderRadius: 16, display: 'flex', overflow: 'hidden',
          boxShadow: '0 30px 80px rgba(0,0,0,0.6)',
        }}
      >
        {/* 좌측 내비 */}
        <aside style={{ width: 208, background: '#0e0f11', borderRight: '1px solid #23262c', padding: '20px 12px', display: 'flex', flexDirection: 'column', gap: 3 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 8px 16px' }}>
            <Avatar url={user?.profileImageUrl} size={36} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: '#f2f4f7', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {user?.nickname || '사용자'}
              </div>
              <div style={{ fontSize: 11, color: '#6b7280' }}>내 채널 관리</div>
            </div>
          </div>
          {TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              style={{
                display: 'flex', alignItems: 'center', gap: 10, padding: '9px 10px', borderRadius: 8,
                border: 'none', cursor: 'pointer', textAlign: 'left', fontSize: 13, fontWeight: 600,
                background: tab === t.key ? '#1c1f24' : 'transparent',
                color: tab === t.key ? GREEN : '#a7adb8',
              }}
            >
              <i className={`fa-solid ${t.icon}`} style={{ width: 16, textAlign: 'center' }} />
              {t.label}
            </button>
          ))}
          <div style={{ flex: 1 }} />
          <LogoutButton />
        </aside>

        {/* 우측 콘텐츠 */}
        <section style={{ flex: 1, position: 'relative', overflowY: 'auto', padding: '22px 26px' }}>
          <button
            onClick={onClose}
            style={{ position: 'absolute', top: 16, right: 18, background: 'transparent', border: 'none', color: '#6b7280', fontSize: 16, cursor: 'pointer' }}
          >
            <i className="fa-solid fa-xmark" />
          </button>
          {tab === 'studio' && <StudioTab />}
          {tab === 'channel' && <ChannelTab user={user} />}
          {tab === 'clips' && <ClipsTab />}
          {tab === 'following' && <FollowingTab onClose={onClose} />}
          {tab === 'subscription' && <SubscriptionTab />}
          {tab === 'noti' && <NotiTab />}
          {tab === 'account' && <AccountTab user={user} />}
        </section>
      </div>
    </div>
  );

  return createPortal(content, document.body);
}

/* ---------- 공용 ---------- */
function Avatar({ url, size = 40 }) {
  return (
    <div style={{ width: size, height: size, borderRadius: '50%', overflow: 'hidden', background: '#23262c', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      {url ? <img src={url} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={(e) => (e.currentTarget.style.display = 'none')} /> : <i className="fa-solid fa-user" style={{ color: '#8b93a1', fontSize: size * 0.4 }} />}
    </div>
  );
}
function LogoutButton() {
  const { logout } = useAuth();
  return (
    <button onClick={logout} style={{ ...ghostBtn, width: '100%', borderColor: '#33383f', color: '#8b93a1' }}>
      <i className="fa-solid fa-right-from-bracket" style={{ marginRight: 6 }} />
      로그아웃
    </button>
  );
}

/* ---------- 스튜디오 ---------- */
function StudioTab() {
  const [b, setB] = useState(null);
  const [showKey, setShowKey] = useState(false);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState('');

  const load = useCallback(async () => {
    try {
      const r = await req('/api/broadcast/mine');
      if (r.ok) setB(await r.json());
    } catch (_) {}
  }, []);
  useEffect(() => { load(); }, [load]);

  const copy = (v, tag) => { navigator.clipboard?.writeText(v); setCopied(tag); setTimeout(() => setCopied(''), 1500); };

  const reissue = async () => {
    if (!window.confirm('스트림 키를 재발급하면 기존 키로는 송출할 수 없습니다. 계속할까요?')) return;
    setBusy(true);
    try {
      const r = await req('/api/broadcast/stream-key', { method: 'POST' });
      if (r.ok) { const nb = await r.json(); setB((p) => ({ ...(p || {}), streamKey: nb.streamKey, title: nb.title, status: nb.status, id: nb.id })); setShowKey(true); }
    } catch (_) {}
    setBusy(false);
  };

  const toggleLive = async () => {
    const next = b?.status === 'ON' ? 'OFF' : 'ON';
    setBusy(true);
    try {
      const r = await req(`/api/broadcast/status?status=${next}`, { method: 'PATCH' });
      if (r.ok) setB((p) => ({ ...(p || {}), status: next }));
    } catch (_) {}
    setBusy(false);
  };

  const ingest = b?.ingestUrl || 'rtmp://localhost:1935/live';
  const key = b?.streamKey || '';
  const live = b?.status === 'ON';

  const Row = ({ label, value, tag, action }) => (
    <div style={{ marginBottom: 14 }}>
      <label style={fieldLabel}>{label}</label>
      <div style={{ display: 'flex', gap: 8 }}>
        <div style={{ ...field, flex: 1, fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12, display: 'flex', alignItems: 'center', overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>{value}</div>
        {action}
        <button style={ghostBtn} onClick={() => copy(value, tag)}>{copied === tag ? '복사됨' : <i className="fa-solid fa-copy" />}</button>
      </div>
    </div>
  );

  return (
    <div>
      <h3 style={sectionTitle}>스튜디오</h3>
      <p style={sectionDesc}>OBS 등 인코더로 송출하고 방송을 시작합니다.</p>

      <div style={{ ...card, marginBottom: 14, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ width: 10, height: 10, borderRadius: '50%', background: live ? '#ff4d4f' : '#4b5563' }} />
          <span style={{ fontSize: 14, fontWeight: 700, color: live ? '#ff6b6b' : '#c7ccd4' }}>{live ? '방송 중' : '오프라인'}</span>
        </div>
        <button style={live ? dangerBtn : primaryBtn} disabled={busy || !key} onClick={toggleLive}>
          {live ? '방송 종료' : '방송 시작'}
        </button>
      </div>

      <div style={card}>
        <Row label="스트림 URL" value={ingest} tag="url" />
        <Row
          label="스트림 키"
          value={showKey ? (key || '아직 발급되지 않음') : (key ? '•'.repeat(Math.min(key.length, 28)) : '아직 발급되지 않음')}
          tag="key"
          action={<button style={ghostBtn} onClick={() => setShowKey((v) => !v)}><i className={`fa-solid ${showKey ? 'fa-eye-slash' : 'fa-eye'}`} /></button>}
        />
        <button style={{ ...primaryBtn, marginTop: 6 }} disabled={busy} onClick={reissue}>
          <i className="fa-solid fa-rotate" style={{ marginRight: 6 }} />
          {key ? '스트림 키 재발급' : '스트림 키 발급'}
        </button>
        <div style={{ marginTop: 16, padding: 12, background: '#101216', border: '1px solid #262a31', borderRadius: 8, fontSize: 12, color: '#8b93a1', lineHeight: 1.7 }}>
          <b style={{ color: '#c7ccd4' }}>연결 방법</b><br />
          OBS → 설정 → 방송 → 서비스 <span style={{ color: GREEN }}>사용자 지정</span> → 서버에 스트림 URL, 스트림 키 붙여넣기 → 송출 시작
        </div>
      </div>
    </div>
  );
}

/* ---------- 내 채널 ---------- */
function ChannelTab({ user }) {
  const [b, setB] = useState(null);
  const [followers, setFollowers] = useState(0);
  const [followerList, setFollowerList] = useState(null);
  const [showFollowers, setShowFollowers] = useState(false);
  const [form, setForm] = useState({ title: '', description: '', bannerUrl: '' });
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const r = await req('/api/broadcast/mine');
      if (r.ok) {
        const d = await r.json();
        setB(d);
        setFollowers(d.followerCount || 0);
        setForm({ title: d.title || '', description: d.description || '', bannerUrl: d.bannerUrl || '' });
      }
    } catch (_) {}
  }, []);
  useEffect(() => { load(); }, [load]);

  const loadFollowers = async () => {
    setShowFollowers((v) => !v);
    if (followerList === null) {
      try { const r = await req('/api/follows/followers'); if (r.ok) setFollowerList(await r.json()); else setFollowerList([]); }
      catch { setFollowerList([]); }
    }
  };

  const save = async () => {
    setBusy(true);
    try {
      const r = await req('/api/broadcast/channel', { method: 'PATCH', body: JSON.stringify(form) });
      if (r.ok) { setSaved(true); setTimeout(() => setSaved(false), 1500); load(); }
    } catch (_) {}
    setBusy(false);
  };

  const toggleLive = async () => {
    const next = b?.status === 'ON' ? 'OFF' : 'ON';
    setBusy(true);
    try { const r = await req(`/api/broadcast/status?status=${next}`, { method: 'PATCH' }); if (r.ok) setB((p) => ({ ...(p || {}), status: next })); }
    catch (_) {}
    setBusy(false);
  };

  const live = b?.status === 'ON';

  return (
    <div>
      <h3 style={sectionTitle}>내 채널</h3>
      <p style={sectionDesc}>다른 사용자에게 보이는 내 방송 채널입니다.</p>

      {/* 채널 프리뷰 카드 */}
      <div style={{ ...card, padding: 0, overflow: 'hidden', marginBottom: 14 }}>
        <div style={{ height: 84, background: form.bannerUrl ? `center/cover no-repeat url(${form.bannerUrl})` : 'linear-gradient(120deg,#12352a,#0e1f19)' }} />
        <div style={{ padding: '0 16px 16px', marginTop: -22, display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <div style={{ border: '3px solid #181b20', borderRadius: '50%' }}><Avatar url={user?.profileImageUrl} size={52} /></div>
          <div style={{ flex: 1, paddingBottom: 2 }}>
            <div style={{ fontSize: 15, fontWeight: 800, color: '#f2f4f7' }}>{user?.nickname || '사용자'}</div>
            <button onClick={loadFollowers} style={{ background: 'none', border: 'none', color: '#8b93a1', fontSize: 12, cursor: 'pointer', padding: 0, marginTop: 2 }}>
              <i className="fa-solid fa-heart" style={{ color: GREEN, marginRight: 4 }} />팔로워 {followers.toLocaleString()}명 <i className={`fa-solid fa-chevron-${showFollowers ? 'up' : 'down'}`} style={{ fontSize: 9 }} />
            </button>
          </div>
          <div style={{ display: 'flex', gap: 8, paddingBottom: 2 }}>
            {b?.id && <button style={{ ...ghostBtn, padding: '7px 12px' }} onClick={() => (window.location.href = `/live/${b.id}`)}>채널 방문</button>}
            <button style={{ ...(live ? dangerBtn : primaryBtn), padding: '7px 14px' }} disabled={busy || !b?.streamKey} onClick={toggleLive}>
              {live ? '방송 종료' : '방송 시작'}
            </button>
          </div>
        </div>
        {showFollowers && (
          <div style={{ borderTop: '1px solid #23262c', padding: '10px 16px', maxHeight: 140, overflowY: 'auto' }}>
            {followerList === null ? <div style={{ fontSize: 12, color: '#6b7280' }}>불러오는 중…</div>
              : followerList.length === 0 ? <div style={{ fontSize: 12, color: '#6b7280' }}>아직 팔로워가 없어요.</div>
              : followerList.map((f) => (
                <div key={f.userId} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 0' }}>
                  <Avatar url={f.profileImageUrl} size={24} />
                  <span style={{ fontSize: 12, color: '#c7ccd4' }}>{f.nickname}</span>
                </div>
              ))}
          </div>
        )}
      </div>

      {/* 채널 정보 편집 */}
      <div style={{ ...card, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div>
          <label style={fieldLabel}>방송 제목</label>
          <input style={field} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} maxLength={60} placeholder="예) 신곡 같이 들어요 🎧" />
        </div>
        <div>
          <label style={fieldLabel}>채널 소개</label>
          <textarea style={{ ...field, resize: 'vertical', minHeight: 60 }} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} maxLength={500} placeholder="채널을 소개해 주세요" />
        </div>
        <div>
          <label style={fieldLabel}>배너 이미지 URL</label>
          <input style={field} value={form.bannerUrl} onChange={(e) => setForm({ ...form, bannerUrl: e.target.value })} placeholder="https://..." />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 12, color: live ? '#ff6b6b' : '#8b93a1' }}>상태: {live ? '🔴 방송 중' : '⚫ 오프라인'}</span>
          <button style={{ ...primaryBtn, marginLeft: 'auto' }} disabled={busy} onClick={save}>채널 정보 저장</button>
          {saved && <span style={{ fontSize: 12, color: GREEN }}>저장됨</span>}
        </div>
      </div>
    </div>
  );
}

/* ---------- 내 클립 ---------- */
function ClipsTab() {
  return (
    <div>
      <h3 style={sectionTitle}>내 클립</h3>
      <p style={sectionDesc}>라이브 시청 중 만든 짧은 영상 클립이 여기에 모입니다.</p>
      <div style={{ ...card, textAlign: 'center', padding: '48px 16px', color: '#6b7280' }}>
        <i className="fa-solid fa-scissors" style={{ fontSize: 28, marginBottom: 12, display: 'block' }} />
        <div style={{ fontSize: 13 }}>아직 만든 클립이 없어요.</div>
        <div style={{ fontSize: 12, marginTop: 4 }}>라이브 방송 화면의 <b style={{ color: '#c7ccd4' }}>클립 만들기</b> 버튼으로 생성할 수 있어요.</div>
      </div>
    </div>
  );
}

/* ---------- 팔로잉 채널 ---------- */
function FollowingTab({ onClose }) {
  const { refreshFollowed } = useNotifications() || {};
  const [list, setList] = useState(null);

  const load = useCallback(async () => {
    try {
      const r = await req('/api/follows/following');
      if (r.ok) setList(await r.json());
      else setList([]);
    } catch { setList([]); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const unfollow = async (userId) => {
    const r = await req(`/api/follows/${userId}`, { method: 'POST' });
    if (r.ok) { load(); refreshFollowed?.(); }
  };

  return (
    <div>
      <h3 style={sectionTitle}>팔로잉 채널</h3>
      <p style={sectionDesc}>팔로우한 채널이 라이브를 시작하면 알림을 받습니다.</p>

      {list === null ? (
        <div style={{ color: '#6b7280', fontSize: 13, padding: 20 }}>불러오는 중…</div>
      ) : list.length === 0 ? (
        <div style={{ ...card, textAlign: 'center', padding: '44px 16px', color: '#6b7280' }}>
          <i className="fa-solid fa-heart" style={{ fontSize: 24, marginBottom: 10, display: 'block' }} />
          <div style={{ fontSize: 13 }}>팔로우한 채널이 없어요.</div>
          <div style={{ fontSize: 12, marginTop: 4 }}>라이브 방송 화면에서 채널을 팔로우해 보세요.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {list.map((c) => (
            <div key={c.userId} style={{ ...card, padding: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
              <Avatar url={c.profileImageUrl} size={40} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: '#f2f4f7', display: 'flex', alignItems: 'center', gap: 6 }}>
                  {c.nickname}
                  {c.live && <span style={{ fontSize: 10, fontWeight: 800, color: '#fff', background: '#ff4d4f', borderRadius: 4, padding: '1px 5px' }}>LIVE</span>}
                </div>
                <div style={{ fontSize: 11, color: '#8b93a1' }}>팔로워 {(c.followerCount || 0).toLocaleString()}명</div>
              </div>
              {c.live && c.broadcastId && (
                <button style={{ ...primaryBtn, padding: '6px 12px' }} onClick={() => { window.location.href = `/live/${c.broadcastId}`; onClose(); }}>
                  시청
                </button>
              )}
              <button style={{ ...ghostBtn, padding: '6px 12px' }} onClick={() => unfollow(c.userId)}>팔로잉</button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------- 내 구독 (이용권) ---------- */
function SubscriptionTab() {
  const { subscription, isPremium, refreshSubscription } = useAuth();
  useEffect(() => { refreshSubscription?.(); }, [refreshSubscription]);
  const expire = subscription?.expireDate ? new Date(subscription.expireDate) : null;

  return (
    <div>
      <h3 style={sectionTitle}>내 구독</h3>
      <p style={sectionDesc}>스트리밍 이용권(구독) 상태입니다.</p>
      <div style={card}>
        {isPremium ? (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
              <span style={{ fontSize: 11, fontWeight: 800, color: '#04160f', background: GREEN, borderRadius: 20, padding: '4px 10px' }}>이용 중</span>
              <span style={{ color: '#e9edf1', fontSize: 14, fontWeight: 700 }}>{subscription?.passName || '프리미엄 이용권'}</span>
            </div>
            {expire && <div style={{ fontSize: 13, color: '#a7adb8' }}>{expire.toLocaleDateString('ko-KR')} 까지</div>}
          </>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <div>
              <div style={{ color: '#e9edf1', fontSize: 14, fontWeight: 700 }}>무료 회원</div>
              <div style={{ fontSize: 12, color: '#8b93a1', marginTop: 4 }}>로그인 사용자는 전곡 재생이 가능합니다.</div>
            </div>
            <button style={primaryBtn} onClick={() => (window.location.href = '/payment')}>이용권 보기</button>
          </div>
        )}
      </div>
    </div>
  );
}

/* ---------- 알림 ---------- */
function NotiTab() {
  const [prefs, setPrefs] = useState(getNotifPrefs());
  const toggle = (k) => { const n = { ...prefs, [k]: !prefs[k] }; setPrefs(n); setNotifPrefs(n); };
  const Item = ({ k, title, desc }) => (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 0', borderBottom: '1px solid #23262c' }}>
      <div>
        <div style={{ fontSize: 13, color: '#e9edf1', fontWeight: 600 }}>{title}</div>
        <div style={{ fontSize: 12, color: '#8b93a1', marginTop: 2 }}>{desc}</div>
      </div>
      <button onClick={() => toggle(k)} style={{ width: 44, height: 24, borderRadius: 999, border: 'none', cursor: 'pointer', background: prefs[k] ? GREEN : '#363b44', position: 'relative', flexShrink: 0 }}>
        <span style={{ position: 'absolute', top: 2, left: prefs[k] ? 22 : 2, width: 20, height: 20, borderRadius: '50%', background: '#fff', transition: 'left 0.15s' }} />
      </button>
    </div>
  );
  return (
    <div>
      <h3 style={sectionTitle}>알림</h3>
      <p style={sectionDesc}>헤더 벨과 화면 알림으로 받을 항목을 선택합니다.</p>
      <div style={card}>
        <Item k="LIVE_START" title="라이브 방송 시작" desc="팔로우한 채널을 포함해 누군가 방송을 시작하면 알려드려요." />
        <Item k="NEW_HOT_SONG" title="새 인기곡 등장" desc="인기차트에 새 곡이 진입하면 알려드려요." />
      </div>
    </div>
  );
}

/* ---------- 내 정보 ---------- */
function AccountTab({ user }) {
  const { setUser } = useAuth();
  const [nickname, setNickname] = useState(user?.nickname || '');
  const [img, setImg] = useState(user?.profileImageUrl || '');
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState(null);

  const save = async () => {
    setSaving(true); setMsg(null);
    try {
      const r = await req('/api/auth/profile', { method: 'PATCH', body: JSON.stringify({ nickname, profileImageUrl: img }) });
      const data = await r.json().catch(() => ({}));
      if (r.ok) { setUser(data); localStorage.setItem('user', JSON.stringify(data)); setMsg({ ok: true, text: '저장되었습니다.' }); }
      else setMsg({ ok: false, text: data.message || '저장 실패' });
    } catch { setMsg({ ok: false, text: '네트워크 오류' }); }
    setSaving(false);
  };

  return (
    <div>
      <h3 style={sectionTitle}>내 정보</h3>
      <p style={sectionDesc}>계정 프로필 정보입니다.</p>
      <div style={{ ...card, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
          <Avatar url={img} size={64} />
          <div style={{ flex: 1 }}>
            <label style={fieldLabel}>프로필 이미지 URL</label>
            <input style={field} value={img} onChange={(e) => setImg(e.target.value)} placeholder="https://..." />
          </div>
        </div>
        <div>
          <label style={fieldLabel}>닉네임</label>
          <input style={field} value={nickname} onChange={(e) => setNickname(e.target.value)} maxLength={20} />
        </div>
        <div>
          <label style={fieldLabel}>이메일</label>
          <input style={{ ...field, color: '#6b7280' }} value={user?.email || ''} readOnly />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button style={primaryBtn} disabled={saving} onClick={save}>{saving ? '저장 중…' : '변경사항 저장'}</button>
          {msg && <span style={{ fontSize: 12, color: msg.ok ? GREEN : '#ff6b6b' }}>{msg.text}</span>}
        </div>
      </div>
    </div>
  );
}
