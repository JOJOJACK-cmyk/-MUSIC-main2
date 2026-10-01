import React, { useEffect, useRef, useState } from 'react';
import api from '../../api/axiosInstance';
import TrackRow, { toTrack } from '../../components/mobile/TrackRow';

const RECENT_KEY = 'm:recentSearches';
const loadRecent = () => { try { return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]'); } catch { return []; } };

/** 모바일 검색: 입력하면 0.35초 뒤 검색, 최근 검색어 보관 */
export default function MobileSearch() {
  const [q, setQ] = useState('');
  const [results, setResults] = useState(null);
  const [recent, setRecent] = useState(loadRecent);
  const inputRef = useRef(null);

  useEffect(() => { inputRef.current?.focus(); }, []);

  // 입력 중에는 DB 에서만 찾고(youtube=false), 검색어가 1.5초 그대로면 유튜브 보강까지 허용 (할당량 보호).
  // 지난 검색어의 늦은 응답이 새 결과를 덮지 않도록 정리 시 요청을 취소한다.
  useEffect(() => {
    const kw = q.trim();
    if (!kw) { setResults(null); return undefined; }
    const ctrl = new AbortController();
    let fullDone = false;
    const run = async (youtube) => {
      try {
        const r = await api.get('/api/musics/search', { params: { keyword: kw, youtube }, signal: ctrl.signal });
        if (!youtube && fullDone) return;
        if (youtube) fullDone = true;
        setResults((Array.isArray(r.data) ? r.data : []).map(toTrack));
      } catch (e) {
        if (e?.code !== 'ERR_CANCELED' && !fullDone) setResults([]);
      }
    };
    const t1 = setTimeout(() => run(false), 350);
    const t2 = setTimeout(() => run(true), 1500);
    return () => { clearTimeout(t1); clearTimeout(t2); ctrl.abort(); };
  }, [q]);

  // 최근 검색어는 입력 중간 글자("블", "블랙"…)가 아니라 확정한 검색어만 — 엔터 · 결과 선택 시 저장
  const saveRecent = () => {
    const kw = q.trim();
    if (!kw) return;
    const next = [kw, ...loadRecent().filter((x) => x !== kw)].slice(0, 8);
    setRecent(next);
    try { localStorage.setItem(RECENT_KEY, JSON.stringify(next)); } catch (_) {}
  };

  const clearRecent = () => { setRecent([]); try { localStorage.removeItem(RECENT_KEY); } catch (_) {} };

  return (
    <div className="m-page">
      <form className="m-search" role="search" onSubmit={(e) => { e.preventDefault(); saveRecent(); inputRef.current?.blur(); }}>
        <i className="fa-solid fa-magnifying-glass" />
        <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="곡, 아티스트 검색" enterKeyHint="search" />
        {q && <button type="button" aria-label="지우기" onClick={() => setQ('')}><i className="fa-solid fa-circle-xmark" /></button>}
      </form>

      {results === null ? (
        recent.length > 0 && (
          <section className="m-section">
            <div className="m-section-head"><h2>최근 검색어</h2><button className="m-text-btn" onClick={clearRecent}>지우기</button></div>
            <div className="m-chips wrap">
              {recent.map((r) => <button key={r} className="m-chip" onClick={() => setQ(r)}>{r}</button>)}
            </div>
          </section>
        )
      ) : results.length === 0 ? (
        <div className="m-empty"><i className="fa-solid fa-magnifying-glass" />‘{q}’ 검색 결과가 없어요</div>
      ) : (
        <div className="m-list" onClickCapture={saveRecent}>
          {results.map((t) => <TrackRow key={t.id} track={t} queue={results} />)}
        </div>
      )}
    </div>
  );
}
