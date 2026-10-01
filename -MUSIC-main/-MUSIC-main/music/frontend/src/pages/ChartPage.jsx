import React, { useEffect, useState } from 'react';
import axios from 'axios';
import Header from '../components/Header';
import Top100Chart from '../components/Top100Chart';
import MusicCard from '../components/MusicCard';
import { musicApi } from '../api/musicApi';
import { usePlayer } from '../context/PlayerContext';

export default function ChartPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [chartList, setChartList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const { selectTrack } = usePlayer();

  const isSearching = searchTerm.trim().length > 0;

  useEffect(() => {
    const fetchChartData = async () => {
      try {
        setLoading(true);
        const response = await musicApi.getTop100Chart();
        setChartList(response.data || response);
      } catch (error) {
        console.error('TOP 100 차트 데이터를 불러오는 데 실패했습니다:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchChartData();
  }, []);

  // 🔎 검색: DB 전체 + 부족하면 유튜브에서 보강 (백엔드 /api/musics/search)
  useEffect(() => {
    const kw = searchTerm.trim();
    if (!kw) {
      setSearchResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const ctrl = new AbortController();
    // 입력 중에는 DB 에서만(youtube=false), 검색어가 1.5초 그대로면 유튜브 보강까지 (할당량 보호)
    let fullDone = false;
    const run = async (youtube) => {
      try {
        const res = await axios.get('/api/musics/search', {
          params: { keyword: kw, youtube },
          signal: ctrl.signal,
        });
        if (!youtube && fullDone) return;
        if (youtube) fullDone = true;
        setSearchResults(Array.isArray(res.data) ? res.data : []);
      } catch (e) {
        if (e.name !== 'CanceledError' && e.code !== 'ERR_CANCELED' && !fullDone) setSearchResults([]);
      } finally {
        if (!youtube) setSearching(false);
      }
    };
    const t1 = setTimeout(() => run(false), 400);
    const t2 = setTimeout(() => run(true), 1500);
    return () => { clearTimeout(t1); clearTimeout(t2); ctrl.abort(); };
  }, [searchTerm]);

  const handleSelectMusic = (music) =>
    selectTrack(music, isSearching ? searchResults : chartList);

  return (
    <>
      <Header searchTerm={searchTerm} setSearchTerm={setSearchTerm} />

      <div className="content-section">
        {isSearching ? (
          <>
            <div className="section-header">
              <h2>🔎 “{searchTerm.trim()}” 검색 결과</h2>
              <span style={{ color: 'var(--text-sub)', fontSize: 14 }}>
                {searching ? '검색 중…' : `${searchResults.length}곡`}
              </span>
            </div>
            {!searching && searchResults.length === 0 ? (
              <div className="lib-state">
                <i className="fa-solid fa-magnifying-glass" />
                “{searchTerm.trim()}”에 해당하는 곡이 없습니다.
              </div>
            ) : (
              <div className="library-grid">
                {searchResults.map((music) => (
                  <MusicCard key={music.id} music={music} queue={searchResults} onToggleLike={() => {}} />
                ))}
              </div>
            )}
          </>
        ) : (
          <>
            <h2>🔥 TOP 100 차트</h2>
            {loading ? (
              <p style={{ color: 'var(--text-sub)', marginTop: '12px' }}>
                실시간 음원 차트 TOP 100을 불러오는 중입니다...
              </p>
            ) : (
              <div style={{ marginTop: '20px' }}>
                <Top100Chart chartList={chartList} onSelectMusic={handleSelectMusic} />
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
}
