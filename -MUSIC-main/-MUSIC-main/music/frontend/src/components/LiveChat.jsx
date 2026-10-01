import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import useLiveChat from '../hooks/useLiveChat';
import api from '../api/axiosInstance';

const AV_COLORS = ['#E028B7', '#7C5CFF', '#2FB8FF', '#22C55E', '#F59E0B', '#FF5C7A', '#14B8A6'];
const colorFor = (name = '') => {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return AV_COLORS[h % AV_COLORS.length];
};

// isBroadcaster: 방송자 본인이면 메시지마다 삭제 / 채팅 금지 메뉴를 보여준다 (권한은 서버가 다시 검증)
// onSendNotes: 있으면 입력창 옆에 ♪(음표 보내기) 버튼을 보여준다
const LiveChat = ({ broadcastId, isBroadcaster = false, onSendNotes }) => {
  const { user } = useAuth();
  const sender =
    user?.nickname || user?.name || user?.email?.split('@')[0] || '게스트';

  const { messages, connected, sendMessage } = useLiveChat(broadcastId, { sender });

  const [message, setMessage] = useState('');
  const [menuFor, setMenuFor] = useState(null); // 관리 메뉴가 열린 messageId
  const [modError, setModError] = useState('');

  const showModError = (e, fallback) => {
    setModError(e?.response?.data?.message || fallback);
    setTimeout(() => setModError(''), 4000);
  };
  const deleteMessage = async (c) => {
    setMenuFor(null);
    try {
      await api.post(`/api/broadcast/${broadcastId}/chat/delete`, { messageId: c.messageId });
    } catch (e) { showModError(e, '메시지를 삭제하지 못했어요'); }
  };
  const muteUser = async (c, minutes) => {
    setMenuFor(null);
    try {
      await api.post(`/api/broadcast/${broadcastId}/chat/mute`, { userId: c.senderId, minutes });
    } catch (e) { showModError(e, '채팅 금지에 실패했어요'); }
  };
  const unmuteUser = async (c) => {
    setMenuFor(null);
    try {
      await api.delete(`/api/broadcast/${broadcastId}/chat/mute/${c.senderId}`);
    } catch (e) { showModError(e, '채팅 금지 해제에 실패했어요'); }
  };
  const listRef = useRef(null);
  const stickRef = useRef(true);

  // 스크롤이 거의 바닥일 때만 자동 스크롤
  useEffect(() => {
    // scrollIntoView 는 페이지 전체까지 스크롤해서(헤더가 밀려 올라감) 채팅 목록만 직접 내린다
    const el = listRef.current;
    if (stickRef.current && el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
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
  const visibleMessages = messages.filter((c) => c.type !== 'ENTER' && c.type !== 'LEAVE');

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
        {visibleMessages.length === 0 ? (
          <div className="lc-empty">
            <i className="fa-regular fa-comments" />
            <span>첫 채팅을 남겨보세요</span>
          </div>
        ) : (
          visibleMessages.map((c, i) => {
            if (c.type === 'VOTE') {
              return (
                <div key={c.messageId || `${c.timestamp}-${i}`} className="lc-vote">
                  <i className="fa-solid fa-square-poll-vertical" /> {c.message}
                </div>
              );
            }
            if (c.type === 'DONATION') {
              return (
                <div key={c.messageId || `${c.timestamp}-${i}`} className="lc-note">
                  <div className="lc-note-notes" aria-hidden="true">
                    <span>♪</span><span>♫</span><span>♪</span>
                  </div>
                  <div className="lc-note-head">
                    <span className="lc-note-icon">♪</span>
                    <span>
                      <b>{c.sender}</b> 님이 음표 <b className="lc-note-amount">{Number(c.amount || 0).toLocaleString('ko-KR')}</b>개를 보냈어요
                    </span>
                  </div>
                  {c.message && <div className="lc-note-msg">{c.message}</div>}
                </div>
              );
            }
            if (c.type === 'NOTICE') {
              return (
                <div key={c.messageId || `${c.timestamp}-${i}`} className="lc-vote lc-system">
                  <i className="fa-solid fa-shield-halved" /> {c.message}
                </div>
              );
            }
            const mine = c.sender === sender;
            // 방송자: 남의 메시지는 삭제 가능, 로그인 사용자(senderId 있음)는 채팅 금지 가능
            const canModerate = isBroadcaster && !mine && c.messageId;
            return (
              <div key={c.messageId || `${c.timestamp}-${i}`} className={`lc-msg ${mine ? 'mine' : ''}`}>
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
                {canModerate && (
                  <div className="lc-mod">
                    <button
                      className="lc-mod-btn"
                      title="채팅 관리"
                      onClick={() => setMenuFor(menuFor === c.messageId ? null : c.messageId)}
                    >
                      <i className="fa-solid fa-ellipsis-vertical" />
                    </button>
                    {menuFor === c.messageId && (
                      <div className="lc-mod-menu">
                        <button onClick={() => deleteMessage(c)}>메시지 삭제</button>
                        {c.senderId && (
                          <>
                            <button onClick={() => muteUser(c, 10)}>10분 채팅 금지</button>
                            <button onClick={() => muteUser(c, 720)}>이번 방송 동안 금지</button>
                            <button onClick={() => unmuteUser(c)}>금지 해제</button>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {modError && <div className="lc-mod-error">{modError}</div>}
      <div className="lc-input">
        {onSendNotes && (
          <button className="lc-note-btn" onClick={onSendNotes} title="음표 보내기">♪</button>
        )}
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
