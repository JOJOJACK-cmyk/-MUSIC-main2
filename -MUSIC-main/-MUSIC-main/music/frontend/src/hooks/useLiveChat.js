import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * 라이브 방송 채팅 STOMP 연결 공용 훅.
 * LiveChat(방송 페이지)와 ChatOverlay(OBS 소스)가 함께 사용한다.
 *
 * @param {string|number} broadcastId
 * @param {object}  opts
 * @param {string}  opts.sender   - 내 표시 이름 (입장/퇴장/발신용). 없으면 읽기 전용으로 동작
 * @param {number}  opts.limit    - 보관할 최대 메시지 수 (기본 200)
 */
export default function useLiveChat(broadcastId, { sender, limit = 200 } = {}) {
  const [messages, setMessages] = useState([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!broadcastId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws-chat'),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/sub/chat/room/${broadcastId}`, (frame) => {
          try {
            const msg = JSON.parse(frame.body);
            setMessages((prev) => {
              const next = [...prev, msg];
              return next.length > limit ? next.slice(next.length - limit) : next;
            });
          } catch (e) {
            console.error('채팅 메시지 파싱 실패:', e);
          }
        });

        if (sender) {
          client.publish({
            destination: '/pub/chat/message',
            body: JSON.stringify({
              roomId: String(broadcastId),
              sender,
              message: '',
              type: 'ENTER',
            }),
          });
        }
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => console.error('채팅 STOMP 오류:', frame.headers['message']),
      onWebSocketError: (e) => console.error('채팅 WebSocket 오류:', e),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      if (client.connected && sender) {
        client.publish({
          destination: '/pub/chat/message',
          body: JSON.stringify({
            roomId: String(broadcastId),
            sender,
            message: '',
            type: 'LEAVE',
          }),
        });
      }
      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [broadcastId, sender, limit]);

  const sendMessage = useCallback(
    (text) => {
      const body = (text || '').trim();
      if (!body || !sender) return false;
      const client = clientRef.current;
      if (!client || !client.connected) return false;
      client.publish({
        destination: '/pub/chat/message',
        body: JSON.stringify({
          roomId: String(broadcastId),
          sender,
          message: body,
          type: 'TALK',
        }),
      });
      return true;
    },
    [broadcastId, sender]
  );

  return { messages, connected, sendMessage };
}
