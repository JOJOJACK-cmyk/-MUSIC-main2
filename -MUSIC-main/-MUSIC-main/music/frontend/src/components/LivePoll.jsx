import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { usePlayer } from '../context/PlayerContext';

const toTrack = (m) => ({
  id: m.id ?? m.musicId,
  youtubeVideoId: m.youtubeVideoId,
  title: m.title,
  artist: m.artist,
  thumbnailUrl: m.thumbnailUrl,
});

/**
 * 채팅창 상단에 붙는 실시간 투표 위젯.
 *  - 시청자: 번호 클릭 또는 채팅 "투표1" 로 투표 (한 계정 1표, 채팅·버튼 합산)
 *  - 스트리머(isBroadcaster): 곡 목록 등록/삭제, 표 초기화, 1위 곡 선택(→ 그 곡 재생)
 */
export default function LivePoll({ broadcastId, isBroadcaster }) {
  const { user } = useAuth();
  const { queueTrackThenList } = usePlayer();

  // 채팅 sender 와 동일한 식별자 (합산 투표를 위해 반드시 일치시킴)
  const voter = user?.nickname || user?.name || user?.email?.split('@')[0] || '';
  const canVote = Boolean(voter);

  const [options, setOptions] = useState([]); // [{index, songTitle, voteCount}]
  const [alert, setAlert] = useState('');
  const [draft, setDraft] = useState('');
  const [edit, setEdit] = useState([]);
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
      webSocketFactory: () => new SockJS('/ws-stomp'),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/broadcast/${broadcastId}/ranking`, (msg) => {
          try { setOptions(JSON.parse(msg.body) || []); } catch (_) {}
        });
        client.subscribe(`/topic/broadcast/${broadcastId}/next-song`, (msg) => {
          setAlert(msg.body);
          setTimeout(() => setAlert(''), 7000);
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
      body: JSON.stringify({ voter, number: idx0Base + 1 }),
    });
    setMyVote(idx0Base);
    try { localStorage.setItem(`poll:vote:${broadcastId}`, String(idx0Base)); } catch {}
  };

  const selectNext = async () => {
    const c = clientRef.current;
    if (c?.connected) {
      c.publish({ destination: `/app/broadcast/${broadcastId}/next-song`, body: JSON.stringify({}) });
    }
    if (!leader?.songTitle) return;
    try {
      // 1위 곡 검색 + 실시간 인기곡 목록을 함께 가져와서
      //   "지금 곡 끝나면 → 1위 곡 → 이후 실시간 인기곡" 순서로 예약
      const [songRes, chartRes] = await Promise.all([
        axios.get('/api/musics/search', { params: { keyword: leader.songTitle } }),
        axios.get('/api/chart/realtime').catch(() => ({ data: [] })),
      ]);
      const hit = (Array.isArray(songRes.data) ? songRes.data : []).find((m) => m.youtubeVideoId);
      if (!hit) { alertOnce(`"${leader.songTitle}" 검색 결과가 없어요`); return; }
      const chart = (Array.isArray(chartRes.data) ? chartRes.data : [])
        .map(toTrack)
        .filter((t) => t.youtubeVideoId);
      queueTrackThenList(hit, chart);
      alertOnce(`다음 곡: ${hit.title || leader.songTitle}`);
    } catch (_) {
      alertOnce('곡을 재생하지 못했어요');
    }
  };

  const startEdit = () => { setEdit(options.map((o) => o.songTitle)); setEditing(true); };
  const saveEdit = async () => {
    setBusy(true);
    try {
      await axios.put(`/api/broadcast/${broadcastId}/poll`, { options: edit.filter((s) => s.trim()) }, { withCredentials: true });
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
  const addDraft = () => {
    const t = draft.trim();
    if (!t) return;
    setEdit((prev) => [...prev, t].slice(0, 10));
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
          {edit.map((s, i) => (
            <div key={i} className="lp-edit-row">
              <span className="lp-num">{i + 1}</span>
              <input
                value={s}
                onChange={(e) => setEdit((p) => p.map((x, j) => (j === i ? e.target.value : x)))}
                placeholder="곡 제목"
              />
              <button onClick={() => setEdit((p) => p.filter((_, j) => j !== i))}>✕</button>
            </div>
          ))}
        </div>
        <div className="lp-add">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addDraft()}
            placeholder="곡 추가 후 Enter"
          />
          <button onClick={addDraft}>추가</button>
        </div>
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
              <span className="lp-song">{o.songTitle}</span>
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
