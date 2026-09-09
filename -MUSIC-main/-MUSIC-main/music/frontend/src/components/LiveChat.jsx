import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import useLiveChat from '../hooks/useLiveChat';

const AV_COLORS = ['#E028B7', '#7C5CFF', '#2FB8FF', '#22C55E', '#F59E0B', '#FF5C7A', '#14B8A6'];
const colorFor = (name = '') => {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return AV_COLORS[h % AV_COLORS.length];
};

const LiveChat = ({ broadcastId }) => {
  const { user } = useAuth();
  const sender =
    user?.nickname || user?.name || user?.email?.split('@')[0] || '게스트';

  const { messages, connected, sendMessage } = useLiveChat(broadcastId, { sender });

  const [message, setMessage] = useState('');
  const endRef = useRef(null);
  const listRef = useRef(null);
  const stickRef = useRef(true);

  // 스크롤이 거의 바닥일 때만 자동 스크롤
  useEffect(() => {
    if (stickRef.current) endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const onScroll = () => {
    const el = listRef.current;
    if (!el) return;
    stickRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
  };

  const handleSend = () => {
    if (sendMessage(message)) {
      setMessage('');
      stickRef.current = true;
    }
  };

  const canSend = message.trim() && connected;

  return (
    <div className="lc-wrap">
      <div className="lc-head">
        <span className="lc-title">
          <i className="fa-solid fa-comment-dots" /> 실시간 채팅
        </span>
        <span className={`lc-status ${connected ? 'on' : ''}`}>
          <span className="lc-status-dot" /> {connected ? '연결됨' : '연결 중'}
        </span>
      </div>

      <div className="lc-list" ref={listRef} onScroll={onScroll}>
        {messages.length === 0 ? (
          <div className="lc-empty">
            <i className="fa-regular fa-comments" />
            <span>첫 채팅을 남겨보세요</span>
          </div>
        ) : (
          messages.map((c, i) => {
            if (c.type === 'ENTER' || c.type === 'LEAVE') {
              return <div key={`${c.timestamp}-${i}`} className="lc-notice">{c.message}</div>;
            }
            if (c.type === 'VOTE') {
              return (
                <div key={`${c.timestamp}-${i}`} className="lc-vote">
                  <i className="fa-solid fa-square-poll-vertical" /> {c.message}
                </div>
              );
            }
            const mine = c.sender === sender;
            return (
              <div key={`${c.timestamp}-${i}`} className={`lc-msg ${mine ? 'mine' : ''}`}>
                <span className="lc-av" style={{ background: colorFor(c.sender || '?') }}>
                  {(c.sender || '?').trim().charAt(0).toUpperCase()}
                </span>
                <div className="lc-bubble">
                  <div className="lc-meta">
                    <span className="lc-sender">{c.sender}</span>
                    <span className="lc-time">
                      {c.timestamp ? c.timestamp.substring(11, 16) : ''}
                    </span>
                  </div>
                  <div className="lc-text">{c.message}</div>
                </div>
              </div>
            );
          })
        )}
        <div ref={endRef} />
      </div>

      <div className="lc-input">
        <input
          type="text"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSend()}
          placeholder="메시지 입력…"
          maxLength={300}
        />
        <button onClick={handleSend} disabled={!canSend} className={canSend ? 'on' : ''}>
          <i className="fa-solid fa-paper-plane" />
        </button>
      </div>
    </div>
  );
};

export default LiveChat;
