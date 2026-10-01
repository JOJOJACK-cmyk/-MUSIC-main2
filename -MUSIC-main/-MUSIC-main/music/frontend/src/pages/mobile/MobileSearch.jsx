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

  useEffect(() => {
    const kw = q.trim();
    if (!kw) { setResults(null); return undefined; }
    const t = setTimeout(async () => {
      try {
        const r = await api.get('/api/musics/search', { params: { keyword: kw } });
        setResults((Array.isArray(r.data) ? r.data : []).map(toTrack));
        const next = [kw, ...loadRecent().filter((x) => x !== kw)].slice(0, 8);
        setRecent(next);
        try { localStorage.setItem(RECENT_KEY, JSON.stringify(next)); } catch (_) {}
      } catch (_) { setResults([]); }
    }, 350);
    return () => clearTimeout(t);
  }, [q]);

  const clearRecent = () => { setRecent([]); try { localStorage.removeItem(RECENT_KEY); } catch (_) {} };

  return (
    <div className="m-page">
      <div className="m-search">
        <i className="fa-solid fa-magnifying-glass" />
        <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)} placeholder="곡, 아티스트 검색" enterKeyHint="search" />
        {q && <button aria-label="지우기" onClick={() => setQ('')}><i className="fa-solid fa-circle-xmark" /></button>}
      </div>

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
        <div className="m-list">
          {results.map((t) => <TrackRow key={t.id} track={t} queue={results} />)}
        </div>
      )}
    </div>
  );
}
