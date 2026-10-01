import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { usePlayer } from '../context/PlayerContext';
import { wsUrl } from '../utils/wsAuth';
import { resolveAndQueueWinner } from '../utils/pollWinner';

/**
 * 방송자가 라이브 방송 페이지를 벗어나 다른 페이지에 가 있어도, 유튜브 음악 플레이어처럼
 * 전역으로 재생이 이어지듯 "1위 곡 다음 재생" 트리거도 항상 받도록 하는 전역 리스너.
 * AppShell에 마운트해 로그인 상태인 동안 항상 떠 있는다 (화면에 아무것도 그리지 않음).
 *
 * 로그인한 사용자의 진행 중인 방송을 주기적으로 확인해, 있으면 그 방송의 next-song
 * 브로드캐스트(LivePoll과 동일한 토픽)를 구독한다 — 지금 어느 페이지에 있든 상관없이.
 */
export default function BroadcasterNextSongListener() {
  const { user } = useAuth();
  const { queueTrackThenList } = usePlayer();
  const [myBroadcastId, setMyBroadcastId] = useState(null);

  useEffect(() => {
    if (!user) { setMyBroadcastId(null); return; }
    let alive = true;
    const check = async () => {
      try {
        const r = await axios.get('/api/broadcast/mine', { withCredentials: true });
        const b = r.data;
        if (alive) setMyBroadcastId(b && b.status === 'ON' && b.id ? b.id : null);
      } catch (_) {
        if (alive) setMyBroadcastId(null);
      }
    };
    check();
    const t = setInterval(check, 15000);
    return () => { alive = false; clearInterval(t); };
  }, [user]);

  const clientRef = useRef(null);
  useEffect(() => {
    if (!myBroadcastId) return;
    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl('/ws-stomp')),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/broadcast/${myBroadcastId}/next-song`, (msg) => {
          try {
            const payload = JSON.parse(msg.body);
            if (payload?.songTitle || payload?.musicId) resolveAndQueueWinner(payload, queueTrackThenList);
          } catch (_) {}
        });
      },
    });
    client.activate();
    clientRef.current = client;
    return () => { client.deactivate(); clientRef.current = null; };
  }, [myBroadcastId, queueTrackThenList]);

  return null;
}
