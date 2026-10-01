import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { wsUrl } from '../utils/wsAuth';

/**
 * 채팅창 상단에 붙는 실시간 투표 위젯.
 *  - 시청자: 번호 클릭 또는 채팅 "투표1" 로 투표 (한 계정 1표, 채팅·버튼 합산)
 *  - 스트리머(isBroadcaster): 곡 목록 등록/삭제, 표 초기화, 1위 곡 선택(→ 그 곡 재생)
 */
export default function LivePoll({ broadcastId, isBroadcaster }) {
  const { user } = useAuth();

  // 채팅 sender 와 동일한 식별자 (합산 투표를 위해 반드시 일치시킴)
  const voter = user?.nickname || user?.name || user?.email?.split('@')[0] || '';
  const canVote = Boolean(voter);

  const [options, setOptions] = useState([]); // [{index, songTitle, voteCount}]
  const [alert, setAlert] = useState('');
  const [draft, setDraft] = useState('');
  const [edit, setEdit] = useState([]); // [{ title, musicId }] — musicId 가 있으면 카탈로그 곡
  const [results, setResults] = useState([]); // 카탈로그 검색 결과
  const [searching, setSearching] = useState(false);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [myVote, setMyVote] = useState(() => {
    try {
      const v = localStorage.getItem(`poll:vote:${broadcastId}`);
      return v == null ? null : Number(v);
    } catch { return null; }
  });
  const clientRef = useRef(null);

  const load = useCallback(async () => {
    try {
      const r = await axios.get(`/api/broadcast/${broadcastId}/ranking`);
      setOptions(Array.isArray(r.data) ? r.data : []);
    } catch (_) {}
  }, [broadcastId]);

  useEffect(() => {
    if (!broadcastId) return;
    load();
    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl('/ws-stomp')),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/broadcast/${broadcastId}/ranking`, (msg) => {
          try { setOptions(JSON.parse(msg.body) || []); } catch (_) {}
        });
        client.subscribe(`/topic/broadcast/${broadcastId}/next-song`, (msg) => {
          let payload = null;
          try { payload = JSON.parse(msg.body); } catch (_) {}
          const text = payload?.message || msg.body;
          setAlert(text);
          setTimeout(() => setAlert(''), 7000);
          // 실제 재생 예약은 전역 BroadcasterNextSongListener 가 한 번만 처리한다.
          // (여기서도 처리하면 방송자가 라이브 페이지에 있을 때 같은 곡이 두 번 예약됨)
        });
      },
    });
    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); clientRef.current = null; };
  }, [broadcastId, load]);

  const totalVotes = options.reduce((s, o) => s + (o.voteCount || 0), 0);
  const leader = options.reduce(
    (best, o) => (o.voteCount > (best?.voteCount ?? -1) ? o : best),
    null
  );

  const vote = (idx0Base) => {
    if (!canVote) { alertOnce('로그인 후 투표할 수 있어요'); return; }
    if (myVote != null) { alertOnce(`이미 ${myVote + 1}번에 투표했어요`); return; }
    const c = clientRef.current;
    if (!c || !c.connected) return;
    c.publish({
      destination: `/app/broadcast/${broadcastId}/vote`,
      body: JSON.stringify({ number: idx0Base + 1 }), // 투표자는 서버가 로그인 정보로 판정
    });
    setMyVote(idx0Base);
    try { localStorage.setItem(`poll:vote:${broadcastId}`, String(idx0Base)); } catch {}
  };

  // 방송자가 버튼을 누르면 서버로 트리거만 보낸다 — 실제 재생은 STOMP 브로드캐스트를 받은
  // 사이트 탭의 전역 리스너(BroadcasterNextSongListener)가 처리한다. 그래야 OBS 채팅 독처럼 플레이어가 없는 창에서 눌러도,
  // 실제로 유튜브 플레이어가 떠 있는 사이트 탭이 똑같이 브로드캐스트를 받아 재생할 수 있다.
  const selectNext = () => {
    const c = clientRef.current;
    if (!c?.connected) { alertOnce('연결이 끊겨 있어요. 잠시 후 다시 시도해 주세요'); return; }
    if (!leader?.songTitle) return;
    c.publish({ destination: `/app/broadcast/${broadcastId}/next-song`, body: JSON.stringify({}) });
  };

  const startEdit = () => {
    setEdit(options.map((o) => ({ title: o.songTitle, musicId: o.musicId ?? null })));
    setResults([]);
    setEditing(true);
  };
  const saveEdit = async () => {
    setBusy(true);
    try {
      const payload = edit
        .filter((o) => o.musicId || o.title.trim())
        .map((o) => (o.musicId ? { musicId: o.musicId, title: o.title } : o.title.trim()));
      await axios.put(`/api/broadcast/${broadcastId}/poll`, { options: payload }, { withCredentials: true });
      setEditing(false);
      setMyVote(null);
      try { localStorage.removeItem(`poll:vote:${broadcastId}`); } catch {}
    } catch (e) { alertOnce('저장 실패'); }
    setBusy(false);
  };
  const resetVotes = async () => {
    if (!window.confirm('현재 표를 모두 초기화할까요? (곡 목록은 유지)')) return;
    try {
      await axios.post(`/api/broadcast/${broadcastId}/poll/reset`, {}, { withCredentials: true });
      setMyVote(null);
      try { localStorage.removeItem(`poll:vote:${broadcastId}`); } catch {}
    } catch (_) {}
  };
  const alertOnce = (t) => { setAlert(t); setTimeout(() => setAlert(''), 4000); };
  // 카탈로그에 없는 곡은 제목을 직접 추가 (1위가 되면 제목으로 검색해서 재생)
  const addDraft = () => {
    const t = draft.trim();
    if (!t) return;
    setEdit((prev) => [...prev, { title: t, musicId: null }].slice(0, 10));
    setDraft('');
    setResults([]);
  };
  // 카탈로그 검색 — 고른 곡은 musicId 로 연결돼 1위 시 정확히 그 곡이 재생된다
  const searchCatalog = async () => {
    const q = draft.trim();
    if (!q) return;
    setSearching(true);
    try {
      const r = await axios.get('/api/musics/search', { params: { keyword: q } });
      const list = (Array.isArray(r.data) ? r.data : []).filter((m) => m.youtubeVideoId).slice(0, 6);
      setResults(list);
      if (list.length === 0) alertOnce('카탈로그에 없는 곡이에요. "직접 추가"로 제목만 넣을 수 있어요');
    } catch (_) {
      alertOnce('검색에 실패했어요');
    }
    setSearching(false);
  };
  const addFromCatalog = (m) => {
    if (edit.some((o) => o.musicId === m.id)) return;
    const title = m.artist ? `${m.title} - ${m.artist}` : m.title;
    setEdit((prev) => [...prev, { title, musicId: m.id }].slice(0, 10));
    setResults([]);
    setDraft('');
  };

  if (isBroadcaster && editing) {
    return (
      <div className="lp-wrap">
        <div className="lp-head">
          <span className="lp-title"><i className="fa-solid fa-list-ol" /> 투표 곡 목록</span>
          <button className="lp-mini" onClick={() => setEditing(false)}>닫기</button>
        </div>
        <div className="lp-edit-list">
          {edit.map((o, i) => (
            <div key={i} className="lp-edit-row">
              <span className="lp-num">{i + 1}</span>
              {o.musicId ? (
                <span className="lp-linked" title="카탈로그 곡 — 1위 시 이 곡이 재생돼요">
                  <i className="fa-solid fa-music" /> {o.title}
                </span>
              ) : (
                <input
                  value={o.title}
                  onChange={(e) => setEdit((p) => p.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))}
                  placeholder="곡 제목"
                />
              )}
              <button onClick={() => setEdit((p) => p.filter((_, j) => j !== i))}>✕</button>
            </div>
          ))}
        </div>
        <div className="lp-add">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && searchCatalog()}
            placeholder="곡 검색 후 Enter"
          />
          <button onClick={searchCatalog} disabled={searching}>{searching ? '…' : '검색'}</button>
          <button onClick={addDraft} title="카탈로그에 없는 곡을 제목만으로 추가">직접 추가</button>
        </div>
        {results.length > 0 && (
          <div className="lp-results">
            {results.map((m) => (
              <button key={m.id} className="lp-result" onClick={() => addFromCatalog(m)}>
                {m.thumbnailUrl && <img src={m.thumbnailUrl} alt="" />}
                <span className="lp-result-text">
                  <strong>{m.title}</strong>
                  <small>{m.artist}</small>
                </span>
                <i className="fa-solid fa-plus" />
              </button>
            ))}
          </div>
        )}
        <button className="lp-save" disabled={busy} onClick={saveEdit}>
          {busy ? '저장 중…' : '저장하고 투표 시작'}
        </button>
      </div>
    );
  }

  if (options.length === 0) {
    return (
      <div className="lp-wrap">
        <div className="lp-head">
          <span className="lp-title"><i className="fa-solid fa-square-poll-vertical" /> 실시간 투표</span>
        </div>
        {isBroadcaster ? (
          <button className="lp-save" onClick={startEdit}>곡 목록 만들기</button>
        ) : (
          <div className="lp-empty">스트리머가 투표 곡을 등록하면 여기에 표시돼요.</div>
        )}
      </div>
    );
  }

  return (
    <div className="lp-wrap">
      <div className="lp-head">
        <span className="lp-title"><i className="fa-solid fa-square-poll-vertical" /> 실시간 투표</span>
        <span className="lp-total">{totalVotes}표{myVote != null ? ` · 내 투표 ${myVote + 1}번` : ''}</span>
      </div>

      {alert && <div className="lp-alert">{alert}</div>}

      <div className="lp-list">
        {options.map((o) => {
          const pct = totalVotes > 0 ? Math.round((o.voteCount / totalVotes) * 100) : 0;
          const isLeader = leader && o.index === leader.index && o.voteCount > 0;
          const mine = myVote === o.index;
          return (
            <button
              key={o.index}
              className={`lp-opt ${isLeader ? 'is-leader' : ''} ${mine ? 'is-mine' : ''}`}
              onClick={() => vote(o.index)}
              disabled={myVote != null}
              title={myVote != null ? '이미 투표했어요' : '이 곡에 투표'}
            >
              <span className="lp-fill" style={{ width: `${pct}%` }} />
              <span className="lp-num">{o.index + 1}</span>
              <span className="lp-song">
                {o.musicId && <i className="fa-solid fa-music lp-song-icon" title="카탈로그 곡" />}
                {o.songTitle}
              </span>
              <span className="lp-count">{o.voteCount}</span>
            </button>
          );
        })}
      </div>

      <div className="lp-hint">
        {myVote != null
          ? '한 계정당 한 번만 투표할 수 있어요.'
          : <>채팅에 <b>투표1</b>, <b>투표2</b> 처럼 입력해도 투표돼요.</>}
      </div>

      {isBroadcaster && (
        <div className="lp-ctrl">
          <button onClick={startEdit}><i className="fa-solid fa-pen" /> 곡 편집</button>
          <button onClick={resetVotes}><i className="fa-solid fa-rotate-left" /> 표 초기화</button>
          <button className="lp-primary" disabled={!leader} onClick={selectNext}>
            1위 곡 다음 재생
          </button>
        </div>
      )}
    </div>
  );
}
