import React, { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from '../context/AuthContext';

const LiveChat = ({ broadcastId }) => {
  const [messages, setMessages] = useState([]);
  const [message, setMessage] = useState('');
  const [connected, setConnected] = useState(false);

  const clientRef = useRef(null);
  const messageEndRef = useRef(null);

const { user } = useAuth();

const sender =
  user?.nickname ||
  user?.name ||
  user?.email?.split('@')[0] ||
  '게스트';

  // 새 메시지가 오면 맨 아래로 스크롤
  useEffect(() => {
    messageEndRef.current?.scrollIntoView({
      behavior: 'smooth',
    });
  }, [messages]);

  // WebSocket 연결
  useEffect(() => {
    if (!broadcastId) {
      return;
    }

    const client = new Client({
      webSocketFactory: () =>
        new SockJS('/ws-chat'),

      reconnectDelay: 5000,

      onConnect: () => {
        console.log(
          `방송 ${broadcastId} 채팅 WebSocket 연결 성공`
        );

        setConnected(true);

        // 현재 방송 채팅방 구독
        client.subscribe(
          `/sub/chat/room/${broadcastId}`,
          (frame) => {
            try {
              const chatMessage = JSON.parse(frame.body);

              setMessages((prev) => [
                ...prev,
                chatMessage,
              ]);
            } catch (error) {
              console.error(
                '채팅 메시지 파싱 실패:',
                error
              );
            }
          }
        );

        // 입장 메시지 전송
        client.publish({
          destination: '/pub/chat/message',

          body: JSON.stringify({
            roomId: String(broadcastId),
            sender: sender,
            message: '',
            type: 'ENTER',
          }),
        });
      },

      onDisconnect: () => {
        setConnected(false);
      },

      onStompError: (frame) => {
        console.error(
          '채팅 STOMP 오류:',
          frame.headers['message']
        );
      },

      onWebSocketError: (error) => {
        console.error(
          '채팅 WebSocket 오류:',
          error
        );
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      if (client.connected) {
        // 퇴장 메시지
        client.publish({
          destination: '/pub/chat/message',

          body: JSON.stringify({
            roomId: String(broadcastId),
            sender: sender,
            message: '',
            type: 'LEAVE',
          }),
        });
      }

      client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [broadcastId, sender]);

  // 채팅 전송
  const sendMessage = () => {
    const text = message.trim();

    if (!text) {
      return;
    }

    const client = clientRef.current;

    if (!client || !client.connected) {
      console.warn(
        '채팅 WebSocket이 연결되지 않았습니다.'
      );
      return;
    }

    client.publish({
      destination: '/pub/chat/message',

      body: JSON.stringify({
        roomId: String(broadcastId),
        sender: sender,
        message: text,
        type: 'TALK',
      }),
    });

    setMessage('');
  };

  return (
    <div
      style={{
        height: '100%',
        minHeight: '390px',
        display: 'flex',
        flexDirection: 'column',
        color: '#fff',
      }}
    >
      {/* 상단 */}
      <div
        style={{
          marginBottom: '14px',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <h3
            style={{
              margin: 0,
              fontSize: '18px',
            }}
          >
            실시간 채팅
          </h3>

          <span
            style={{
              fontSize: '11px',
              padding: '5px 8px',
              borderRadius: '999px',
              background: connected
                ? 'rgba(60, 200, 120, 0.12)'
                : '#292929',
              color: connected
                ? '#55d98b'
                : '#777',
            }}
          >
            {connected ? '연결됨' : '연결 중'}
          </span>
        </div>

        <div
          style={{
            fontSize: '12px',
            color: '#777',
            marginTop: '6px',
          }}
        >
          {sender}
        </div>
      </div>

      {/* 메시지 목록 */}
      <div
        style={{
          flex: 1,
          minHeight: 0,
          maxHeight: '300px',
          overflowY: 'auto',
          background: '#111',
          border: '1px solid #2d2d2d',
          borderRadius: '10px',
          padding: '12px',
        }}
      >
        {messages.length === 0 ? (
          <div
            style={{
              height: '100%',
              minHeight: '180px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#666',
              fontSize: '13px',
            }}
          >
            아직 채팅이 없습니다.
          </div>
        ) : (
          messages.map((chat, index) => {
            const isNotice =
              chat.type === 'ENTER' ||
              chat.type === 'LEAVE';

            return (
              <div
                key={`${chat.timestamp}-${index}`}
                style={{
                  marginBottom: '11px',
                }}
              >
                {isNotice ? (
                  <div
                    style={{
                      textAlign: 'center',
                      color: '#666',
                      fontSize: '11px',
                    }}
                  >
                    {chat.message}
                  </div>
                ) : (
                  <>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '7px',
                        marginBottom: '3px',
                      }}
                    >
                      <span
                        style={{
                          fontSize: '12px',
                          fontWeight: '700',
                          color: '#ff5b96',
                        }}
                      >
                        {chat.sender}
                      </span>

                      <span
                        style={{
                          fontSize: '10px',
                          color: '#555',
                        }}
                      >
                        {chat.timestamp
                          ? chat.timestamp.substring(11, 16)
                          : ''}
                      </span>
                    </div>

                    <div
                      style={{
                        fontSize: '13px',
                        lineHeight: '1.5',
                        color: '#ddd',
                        wordBreak: 'break-word',
                      }}
                    >
                      {chat.message}
                    </div>
                  </>
                )}
              </div>
            );
          })
        )}

        <div ref={messageEndRef} />
      </div>

      {/* 입력창 */}
      <div
        style={{
          display: 'flex',
          gap: '8px',
          marginTop: '12px',
        }}
      >
        <input
          type="text"
          value={message}
          onChange={(e) =>
            setMessage(e.target.value)
          }
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              sendMessage();
            }
          }}
          placeholder="메시지를 입력하세요"
          maxLength={300}
          style={{
            flex: 1,
            minWidth: 0,
            border: '1px solid #383838',
            background: '#111',
            color: '#fff',
            padding: '11px 12px',
            borderRadius: '8px',
            outline: 'none',
            fontSize: '13px',
          }}
        />

        <button
          onClick={sendMessage}
          disabled={!message.trim() || !connected}
          style={{
            border: 'none',
            padding: '0 15px',
            borderRadius: '8px',
            background:
              message.trim() && connected
                ? '#ff2f7d'
                : '#333',
            color:
              message.trim() && connected
                ? '#fff'
                : '#777',
            cursor:
              message.trim() && connected
                ? 'pointer'
                : 'not-allowed',
            fontWeight: '700',
          }}
        >
          전송
        </button>
      </div>
    </div>
  );
};

export default LiveChat;