import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useAuth } from '../context/AuthContext';
import { getNotifPrefs, setNotifPrefs, useNotifications } from '../context/NotificationContext';
import { LIVE_CATEGORIES } from '../constants/liveCategories';

const API = ''; // Vite 프록시로 동일 출처 요청 (세션 쿠키 전달)
const ACCENT = '#F244CB';        // 앱 시그니처 마젠타
const ACCENT_DEEP = '#E028B7';

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
  // superAdmin 전용 (아래 render 에서 필터)
  { key: 'roles', label: '권한 관리', icon: 'fa-user-shield', superAdminOnly: true },
];

const ROLE_OPTIONS = [
  { value: 'ROLE_USER', label: '일반 회원' },
  { value: 'ROLE_SUB_ADMIN', label: '부 관리자' },
  { value: 'ROLE_ADMIN', label: '관리자' },
];
const roleLabel = (r) => ROLE_OPTIONS.find((o) => o.value === r)?.label || (r || '일반 회원');

const field = {
  width: '100%', background: '#12060f', border: '1px solid #3a1f33', borderRadius: 9,
  padding: '10px 12px', color: '#efe6ec', fontSize: 13, outline: 'none',
};
const fieldLabel = { display: 'block', fontSize: 12, color: '#a98db9', marginBottom: 6, fontWeight: 600 };
const primaryBtn = {
  background: `linear-gradient(135deg, ${ACCENT}, ${ACCENT_DEEP})`, color: '#fff', border: 'none', borderRadius: 9,
  padding: '9px 16px', fontSize: 13, fontWeight: 800, cursor: 'pointer',
  boxShadow: '0 6px 16px -6px rgba(224,40,183,0.6)',
};
const ghostBtn = {
  background: 'transparent', color: '#d9c7d4', border: '1px solid #412a3c', borderRadius: 9,
  padding: '9px 14px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
};
const dangerBtn = { ...ghostBtn, borderColor: '#6b2b3f', color: '#ff8bab' };
const sectionTitle = { fontSize: 15, fontWeight: 800, color: '#f6eef4', margin: '0 0 4px' };
const sectionDesc = { fontSize: 12, color: '#a98db9', margin: '0 0 18px' };
const card = { background: '#1d0819', border: '1px solid #38213230', borderRadius: 12, padding: 16 };

export default function ProfilePanel({ onClose }) {
  const { user, isSuperAdmin } = useAuth();
  const [tab, setTab] = useState('studio');
  const visibleTabs = TABS.filter((t) => !t.superAdminOnly || isSuperAdmin);
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
        position: 'fixed', inset: 0,
        background: 'radial-gradient(circle at 50% 0%, rgba(224,40,183,0.16), rgba(0,0,0,0.72) 60%)',
        backdropFilter: 'blur(5px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 5000,
      }}
    >
      <div
        style={{
          width: 830, maxWidth: '95vw', height: 584, maxHeight: '92vh', background: '#180712',
          border: '1px solid #3a1f33', borderRadius: 18, display: 'flex', overflow: 'hidden',
          boxShadow: '0 40px 100px -20px rgba(224,40,183,0.35), 0 20px 60px rgba(0,0,0,0.6)',
        }}
      >
        {/* 좌측 내비 */}
        <aside style={{ width: 214, background: '#0a0308', borderRight: '1px solid #23161f', padding: '20px 12px', display: 'flex', flexDirection: 'column', gap: 3 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 8px 18px' }}>
            <div style={{ borderRadius: '50%', padding: 2, background: `linear-gradient(135deg, ${ACCENT}, ${ACCENT_DEEP})` }}>
              <Avatar url={user?.profileImageUrl} size={34} />
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: '#f6eef4', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {user?.nickname || '사용자'}
              </div>
              <div style={{ fontSize: 11, color: '#8a6f83' }}>내 채널 관리</div>
            </div>
          </div>
          {visibleTabs.map((t) => {
            const active = tab === t.key;
            return (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 11, padding: '9px 12px', borderRadius: 9,
                  border: 'none', cursor: 'pointer', textAlign: 'left', fontSize: 13, fontWeight: 600,
                  position: 'relative', transition: 'background 0.15s ease, color 0.15s ease',
                  background: active ? 'rgba(224,40,183,0.14)' : 'transparent',
                  color: active ? ACCENT : '#a98db9',
                }}
                onMouseEnter={(e) => { if (!active) e.currentTarget.style.background = 'rgba(255,255,255,0.04)'; }}
                onMouseLeave={(e) => { if (!active) e.currentTarget.style.background = 'transparent'; }}
              >
                {active && (
                  <span style={{
                    position: 'absolute', left: 0, top: 8, bottom: 8, width: 3, borderRadius: 3,
                    background: `linear-gradient(${ACCENT}, ${ACCENT_DEEP})`,
                  }} />
                )}
                <i className={`fa-solid ${t.icon}`} style={{ width: 16, textAlign: 'center' }} />
                {t.label}
              </button>
            );
          })}
          <div style={{ flex: 1 }} />
          <LogoutButton />
        </aside>

        {/* 우측 콘텐츠 */}
        <section style={{ flex: 1, position: 'relative', overflowY: 'auto', padding: '24px 28px' }}>
          <button
            onClick={onClose}
            style={{ position: 'absolute', top: 16, right: 18, background: 'transparent', border: 'none', color: '#8a6f83', fontSize: 16, cursor: 'pointer' }}
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
          {tab === 'roles' && isSuperAdmin && <RolesTab me={user} />}
        </section>
      </div>
    </div>
  );

  return createPortal(content, document.body);
}

/* ---------- 공용 ---------- */
function Avatar({ url, size = 40 }) {
  return (
    <div style={{ width: size, height: size, borderRadius: '50%', overflow: 'hidden', background: '#33202e', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      {url ? <img src={url} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={(e) => (e.currentTarget.style.display = 'none')} /> : <i className="fa-solid fa-user" style={{ color: '#a98db9', fontSize: size * 0.4 }} />}
    </div>
  );
}
function LogoutButton() {
  const { logout } = useAuth();
  return (
    <button onClick={logout} style={{ ...ghostBtn, width: '100%', borderColor: '#412a3c', color: '#a98db9' }}>
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
          <span style={{ fontSize: 14, fontWeight: 700, color: live ? '#ff6b6b' : '#d9c7d4' }}>{live ? '방송 중' : '오프라인'}</span>
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
        <div style={{ marginTop: 16, padding: 12, background: '#12060f', border: '1px solid #33202e', borderRadius: 8, fontSize: 12, color: '#a98db9', lineHeight: 1.7 }}>
          <b style={{ color: '#d9c7d4' }}>연결 방법</b><br />
          OBS → 설정 → 방송 → 서비스 <span style={{ color: ACCENT }}>사용자 지정</span> → 서버에 스트림 URL, 스트림 키 붙여넣기 → 송출 시작
        </div>
      </div>

      {/* OBS 채팅 오버레이 */}
      <div style={{ ...card, marginTop: 14 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: '#d9c7d4', marginBottom: 4 }}>
          <i className="fa-solid fa-comments" style={{ marginRight: 6, color: ACCENT }} />
          OBS 채팅 오버레이
        </div>
        <div style={{ fontSize: 12, color: '#a98db9', marginBottom: 12 }}>
          아래 URL을 OBS → 소스 추가 → <b style={{ color: '#d9c7d4' }}>브라우저</b> 에 넣으면
          방송 화면에 실시간 채팅이 표시됩니다. (배경 투명)
        </div>
        {b?.id ? (
          <>
            <Row
              label="채팅 오버레이 URL (투명 배경)"
              value={`${window.location.origin}/live/${b.id}/chat`}
              tag="chat"
            />
            <Row
              label="채팅 오버레이 URL (검정 배경)"
              value={`${window.location.origin}/live/${b.id}/chat?theme=dark`}
              tag="chatd"
            />
          </>
        ) : (
          <div style={{ fontSize: 12, color: '#8a6f83' }}>
            먼저 스트림 키를 발급하면 채팅 오버레이 URL이 생성됩니다.
          </div>
        )}
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
  const [form, setForm] = useState({
    title: '', description: '', bannerUrl: '', category: '', songRequestEnabled: false,
  });
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const r = await req('/api/broadcast/mine');
      if (r.ok) {
        const d = await r.json();
        setB(d);
        setFollowers(d.followerCount || 0);
        setForm({
          title: d.title || '',
          description: d.description || '',
          bannerUrl: d.bannerUrl || '',
          category: d.category || '',
          songRequestEnabled: Boolean(d.songRequestEnabled),
        });
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
        <div style={{ height: 84, background: form.bannerUrl ? `center/cover no-repeat url(${form.bannerUrl})` : 'linear-gradient(120deg,#3a1030,#1a0716)' }} />
        <div style={{ padding: '0 16px 16px', marginTop: -22, display: 'flex', gap: 12, alignItems: 'flex-end' }}>
          <div style={{ border: '3px solid #1d0819', borderRadius: '50%' }}><Avatar url={user?.profileImageUrl} size={52} /></div>
          <div style={{ flex: 1, paddingBottom: 2 }}>
            <div style={{ fontSize: 15, fontWeight: 800, color: '#f6eef4' }}>{user?.nickname || '사용자'}</div>
            <button onClick={loadFollowers} style={{ background: 'none', border: 'none', color: '#a98db9', fontSize: 12, cursor: 'pointer', padding: 0, marginTop: 2 }}>
              <i className="fa-solid fa-heart" style={{ color: ACCENT, marginRight: 4 }} />팔로워 {followers.toLocaleString()}명 <i className={`fa-solid fa-chevron-${showFollowers ? 'up' : 'down'}`} style={{ fontSize: 9 }} />
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
          <div style={{ borderTop: '1px solid #33202e', padding: '10px 16px', maxHeight: 140, overflowY: 'auto' }}>
            {followerList === null ? <div style={{ fontSize: 12, color: '#8a6f83' }}>불러오는 중…</div>
              : followerList.length === 0 ? <div style={{ fontSize: 12, color: '#8a6f83' }}>아직 팔로워가 없어요.</div>
              : followerList.map((f) => (
                <div key={f.userId} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 0' }}>
                  <Avatar url={f.profileImageUrl} size={24} />
                  <span style={{ fontSize: 12, color: '#d9c7d4' }}>{f.nickname}</span>
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
          <label style={fieldLabel}>콘텐츠 카테고리</label>
          <select
            style={{ ...field, cursor: 'pointer' }}
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value })}
          >
            <option value="">선택 안 함</option>
            {LIVE_CATEGORIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
          <div style={{ fontSize: 11, color: '#8a6f83', marginTop: 5 }}>
            방송이 켜지면 메인 화면의 해당 카테고리 칸에 노출됩니다.
          </div>
        </div>
        <div>
          <label style={fieldLabel}>배너 이미지 URL</label>
          <input style={field} value={form.bannerUrl} onChange={(e) => setForm({ ...form, bannerUrl: e.target.value })} placeholder="https://..." />
        </div>

        {/* 신청곡 & 실시간 투표 on/off */}
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 600, color: '#efe6ec' }}>신청곡 &amp; 실시간 투표</div>
            <div style={{ fontSize: 11, color: '#8a6f83', marginTop: 4 }}>
              켜면 시청 화면 아래에 곡 신청·투표 패널이 표시됩니다.
            </div>
          </div>
          <button
            type="button"
            onClick={() => setForm({ ...form, songRequestEnabled: !form.songRequestEnabled })}
            style={{
              flexShrink: 0, width: 44, height: 24, borderRadius: 999, border: 'none', cursor: 'pointer',
              background: form.songRequestEnabled ? ACCENT : '#412a3c', position: 'relative',
            }}
          >
            <span style={{
              position: 'absolute', top: 2, left: form.songRequestEnabled ? 22 : 2,
              width: 20, height: 20, borderRadius: '50%', background: '#fff', transition: 'left 0.15s',
            }} />
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 12, color: live ? '#ff6b6b' : '#a98db9' }}>상태: {live ? '🔴 방송 중' : '⚫ 오프라인'}</span>
          <button style={{ ...primaryBtn, marginLeft: 'auto' }} disabled={busy} onClick={save}>채널 정보 저장</button>
          {saved && <span style={{ fontSize: 12, color: ACCENT }}>저장됨</span>}
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
      <div style={{ ...card, textAlign: 'center', padding: '48px 16px', color: '#8a6f83' }}>
        <i className="fa-solid fa-scissors" style={{ fontSize: 28, marginBottom: 12, display: 'block' }} />
        <div style={{ fontSize: 13 }}>아직 만든 클립이 없어요.</div>
        <div style={{ fontSize: 12, marginTop: 4 }}>라이브 방송 화면의 <b style={{ color: '#d9c7d4' }}>클립 만들기</b> 버튼으로 생성할 수 있어요.</div>
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
        <div style={{ color: '#8a6f83', fontSize: 13, padding: 20 }}>불러오는 중…</div>
      ) : list.length === 0 ? (
        <div style={{ ...card, textAlign: 'center', padding: '44px 16px', color: '#8a6f83' }}>
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
                <div style={{ fontSize: 13, fontWeight: 700, color: '#f6eef4', display: 'flex', alignItems: 'center', gap: 6 }}>
                  {c.nickname}
                  {c.live && <span style={{ fontSize: 10, fontWeight: 800, color: '#fff', background: '#ff4d4f', borderRadius: 4, padding: '1px 5px' }}>LIVE</span>}
                </div>
                <div style={{ fontSize: 11, color: '#a98db9' }}>팔로워 {(c.followerCount || 0).toLocaleString()}명</div>
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
  const { subscription, isPremium, isAdmin, refreshSubscription } = useAuth();
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
              <span style={{ fontSize: 11, fontWeight: 800, color: '#fff', background: ACCENT, borderRadius: 20, padding: '4px 10px' }}>이용 중</span>
              <span style={{ color: '#efe6ec', fontSize: 14, fontWeight: 700 }}>{subscription?.passName || '프리미엄 이용권'}</span>
            </div>
            {expire && <div style={{ fontSize: 13, color: '#a98db9' }}>{expire.toLocaleDateString('ko-KR')} 까지</div>}
          </>
        ) : isAdmin ? (
          <div>
            <div style={{ color: '#efe6ec', fontSize: 14, fontWeight: 700 }}>관리자 계정</div>
            <div style={{ fontSize: 12, color: '#a98db9', marginTop: 4 }}>관리자는 이용권 없이 전곡 재생이 가능합니다.</div>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <div>
              <div style={{ color: '#efe6ec', fontSize: 14, fontWeight: 700 }}>무료 회원</div>
              <div style={{ fontSize: 12, color: '#a98db9', marginTop: 4 }}>이용권 미보유 시 곡당·전체 미리듣기가 1분으로 제한됩니다.</div>
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
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 0', borderBottom: '1px solid #33202e' }}>
      <div>
        <div style={{ fontSize: 13, color: '#efe6ec', fontWeight: 600 }}>{title}</div>
        <div style={{ fontSize: 12, color: '#a98db9', marginTop: 2 }}>{desc}</div>
      </div>
      <button onClick={() => toggle(k)} style={{ width: 44, height: 24, borderRadius: 999, border: 'none', cursor: 'pointer', background: prefs[k] ? ACCENT : '#412a3c', position: 'relative', flexShrink: 0 }}>
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
          <input style={{ ...field, color: '#8a6f83' }} value={user?.email || ''} readOnly />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button style={primaryBtn} disabled={saving} onClick={save}>{saving ? '저장 중…' : '변경사항 저장'}</button>
          {msg && <span style={{ fontSize: 12, color: msg.ok ? ACCENT : '#ff6b6b' }}>{msg.text}</span>}
        </div>
      </div>
    </div>
  );
}

/* ---------- 권한 관리 (최고 관리자 전용) ---------- */
function RolesTab({ me }) {
  const [q, setQ] = useState('');
  const [users, setUsers] = useState(null);
  const [edited, setEdited] = useState({});
  const [savingId, setSavingId] = useState(null);
  const [rowMsg, setRowMsg] = useState({});

  const load = useCallback(async (query = '') => {
    setUsers(null);
    try {
      const r = await req(`/api/admin/users${query ? `?q=${encodeURIComponent(query)}` : ''}`);
      setUsers(r.ok ? await r.json() : []);
    } catch {
      setUsers([]);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  const roleColor = (r) =>
    r === 'ROLE_ADMIN' ? ACCENT : r === 'ROLE_SUB_ADMIN' ? '#f0b429' : '#a98db9';

  const saveRole = async (u) => {
    const next = edited[u.id];
    if (!next || next === u.role) return;
    setSavingId(u.id);
    setRowMsg((m) => ({ ...m, [u.id]: null }));
    try {
      const r = await req(`/api/admin/users/${u.id}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role: next }),
      });
      const data = await r.json().catch(() => ({}));
      if (r.ok) {
        setUsers((list) => list.map((x) => (x.id === u.id ? { ...x, role: data.role } : x)));
        setEdited((e) => { const n = { ...e }; delete n[u.id]; return n; });
        setRowMsg((m) => ({ ...m, [u.id]: { ok: true, text: '변경됨' } }));
      } else {
        setRowMsg((m) => ({ ...m, [u.id]: { ok: false, text: data.message || '실패' } }));
      }
    } catch {
      setRowMsg((m) => ({ ...m, [u.id]: { ok: false, text: '네트워크 오류' } }));
    }
    setSavingId(null);
  };

  return (
    <div>
      <h3 style={sectionTitle}>권한 관리</h3>
      <p style={sectionDesc}>
        회원에게 <b>부 관리자</b> 또는 <b>관리자</b> 권한을 부여합니다. 이 메뉴는 관리자에게만 보입니다.
      </p>

      <form
        onSubmit={(e) => { e.preventDefault(); load(q.trim()); }}
        style={{ display: 'flex', gap: 8, marginBottom: 14 }}
      >
        <input
          style={field}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="이메일 또는 닉네임 검색"
        />
        <button type="submit" style={{ ...ghostBtn, flexShrink: 0 }}>검색</button>
      </form>

      {users === null ? (
        <div style={{ fontSize: 13, color: '#8a6f83', padding: 20 }}>불러오는 중…</div>
      ) : users.length === 0 ? (
        <div style={{ ...card, textAlign: 'center', color: '#8a6f83', fontSize: 13 }}>
          검색 결과가 없습니다.
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {users.map((u) => {
            const isMe = me?.id != null && u.id === me.id;
            const cur = edited[u.id] ?? u.role ?? 'ROLE_USER';
            const dirty = cur !== (u.role ?? 'ROLE_USER');
            const rm = rowMsg[u.id];
            return (
              <div key={u.id} style={{ ...card, display: 'flex', alignItems: 'center', gap: 12, padding: 12 }}>
                <Avatar url={u.profileImageUrl} size={38} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: '#f6eef4', display: 'flex', alignItems: 'center', gap: 6 }}>
                    {u.nickname}
                    {isMe && <span style={{ fontSize: 10, color: '#8a6f83' }}>(나)</span>}
                    <span style={{ fontSize: 10, fontWeight: 700, color: roleColor(u.role), border: `1px solid ${roleColor(u.role)}55`, borderRadius: 6, padding: '1px 6px' }}>
                      {roleLabel(u.role)}
                    </span>
                  </div>
                  <div style={{ fontSize: 11, color: '#8a6f83', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {u.email}
                  </div>
                </div>

                <select
                  style={{ ...field, width: 116, cursor: isMe ? 'not-allowed' : 'pointer', opacity: isMe ? 0.5 : 1 }}
                  value={cur}
                  disabled={isMe}
                  onChange={(e) => setEdited((prev) => ({ ...prev, [u.id]: e.target.value }))}
                >
                  {ROLE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>

                <button
                  style={{
                    ...primaryBtn, padding: '8px 12px', flexShrink: 0,
                    opacity: !dirty || savingId === u.id ? 0.45 : 1,
                    cursor: !dirty || savingId === u.id ? 'default' : 'pointer',
                  }}
                  disabled={isMe || !dirty || savingId === u.id}
                  onClick={() => saveRole(u)}
                >
                  {savingId === u.id ? '…' : '저장'}
                </button>
                {rm && (
                  <span style={{ fontSize: 11, color: rm.ok ? ACCENT : '#ff8bab', flexShrink: 0 }}>{rm.text}</span>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
